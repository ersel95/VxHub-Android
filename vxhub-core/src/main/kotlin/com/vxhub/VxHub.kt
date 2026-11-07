package com.vxhub

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.vxhub.internal.VxHubEngine
import com.vxhub.internal.VxLog
import com.vxhub.internal.VxStateHolder
import com.vxhub.internal.content.BundledStrings
import com.vxhub.internal.media.ImageCompressor
import com.vxhub.internal.network.DeviceSocialLoginRequest
import com.vxhub.internal.network.SocialLoginRequest
import com.vxhub.internal.storage.VxPreferences
import com.vxhub.internal.system.ActivityTracker
import com.vxhub.internal.system.ConnectivityMonitor
import com.vxhub.providers.VxBannerType
import com.vxhub.providers.VxProviderRegistry
import com.vxhub.providers.VxSentryConfig
import com.vxhub.purchases.VxProduct
import com.vxhub.purchases.VxPurchaseResult
import com.vxhub.purchases.VxRestoreResult
import com.vxhub.purchases.VxStoreProduct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * VxHub for Android: a server-driven monetization and engagement SDK. The app supplies a hub id;
 * the panel supplies every third-party key, the remote config, the products and the translations.
 *
 * ```
 * class App : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         VxHub.initialize(this, VxHubConfig(hubId = "…"))
 *     }
 * }
 * ```
 *
 * State is exposed as [StateFlow]s ([isPremium], [balance], [products], …) and every operation is
 * a main-safe `suspend` function. Unlike iOS, there is no warm start to wire up: VxHub
 * re-registers the device whenever the app returns to the foreground.
 */
public object VxHub {

    /** Version of this SDK. */
    public const val SDK_VERSION: String = "0.1.0"

    private val stateHolder = VxStateHolder()

    @Volatile
    private var engineOrNull: VxHubEngine? = null

    private val engine: VxHubEngine
        get() = engineOrNull ?: throw VxHubException.NotInitialized("Call VxHub.initialize() in Application.onCreate first")

    // region Lifecycle

    /**
     * Starts VxHub: registers the device, then loads config, products and localization, and
     * configures every linked satellite with the keys the panel returned. Returns immediately;
     * follow progress through [state], [listener] callbacks or [awaitInitialization].
     *
     * Call it once, from `Application.onCreate`, on the main thread.
     */
    public fun initialize(application: Application, config: VxHubConfig, listener: VxHubListener? = null) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "VxHub.initialize must be called on the main thread" }
        val existing = engineOrNull
        if (existing != null) {
            if (existing.config.hubId != config.hubId) {
                VxLog.e("VxHub is already initialized with hub ${existing.config.hubId}; ignoring ${config.hubId}")
            }
            if (listener != null) existing.listener = listener
            existing.beginInitialization()
            return
        }
        VxLog.level = config.logLevel
        ActivityTracker.install(application)

        val engine = VxHubEngine(
            app = application,
            config = config,
            listener = listener,
            state = stateHolder,
            initStartedAt = SystemClock.elapsedRealtime(),
        )
        engineOrNull = engine

        ConnectivityMonitor(application, engine::onConnectivityChanged).start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                private var hasStarted = false

                override fun onStart(owner: LifecycleOwner) {
                    // The first ON_START is the launch itself, which initialization covers.
                    if (hasStarted) engine.onForeground() else hasStarted = true
                }

                override fun onStop(owner: LifecycleOwner) = engine.onBackground()
            },
        )
        VxLog.i("Initializing VxHub $SDK_VERSION for ${config.environment} ($config)")
        engine.beginInitialization()
    }

    /** Replaces the listener given to [initialize]. */
    public fun setListener(listener: VxHubListener?) {
        engine.listener = listener
    }

    /**
     * Suspends until initialization finishes. Throws the [VxHubException] it failed with.
     * The coroutine counterpart of iOS's `async initialize(...) -> VxHubInitResult`.
     */
    public suspend fun awaitInitialization(): VxHubInitResult {
        engine
        return when (val terminal = stateHolder.state.first { it !is VxHubState.Initializing && it !is VxHubState.NotInitialized }) {
            VxHubState.Ready -> VxHubInitResult.SUCCESS
            VxHubState.Banned -> VxHubInitResult.BANNED
            VxHubState.ForceUpdateRequired -> VxHubInitResult.FORCE_UPDATE_REQUIRED
            is VxHubState.Failed -> throw terminal.error
            VxHubState.Initializing, VxHubState.NotInitialized -> error("unreachable")
        }
    }

    /**
     * Warm start: re-registers the device to refresh premium, balance and config, and retries
     * pending purchase verifications. VxHub already does this on every return to the foreground
     * (see [VxHubConfig.autoStartOnForeground]); call it after something changed server-side.
     *
     * @param restoreTransactions also reload localization and products.
     * @return false when VxHub is not initialized yet or the request failed.
     */
    public suspend fun start(restoreTransactions: Boolean = false): Boolean = engine.warmStart(restoreTransactions)

    /** Where VxHub is in its lifecycle. */
    public val state: StateFlow<VxHubState> = stateHolder.state.asStateFlow()

    // endregion

    // region State

    /** Premium as the backend sees it. Updated by registration, purchases and restores. */
    public val isPremium: StateFlow<Boolean> = stateHolder.isPremium.asStateFlow()

    /** The device's VxHub coin balance. */
    public val balance: StateFlow<Int> = stateHolder.balance.asStateFlow()

    /** Products to offer, populated after initialization. Empty until then. */
    public val products: StateFlow<List<VxProduct>> = stateHolder.products.asStateFlow()

    /** The last registration's response. */
    public val deviceInfo: StateFlow<VxDeviceInfo?> = stateHolder.deviceInfo.asStateFlow()

    /** The panel's remote config for this device. */
    public val remoteConfig: StateFlow<VxRemoteConfig> = stateHolder.remoteConfig.asStateFlow()

    public val isConnected: StateFlow<Boolean> = stateHolder.isConnected.asStateFlow()

    public val connectionType: StateFlow<VxConnectionType> = stateHolder.connectionType.asStateFlow()

    /** The id VxHub identifies this device with (`X-Hub-Device-Id`). */
    public val deviceId: String get() = engine.identity.deviceId

    /** The backend's id for this device, once registered. */
    public val vid: String? get() = stateHolder.vid

    /** The panel's store id for this app. */
    public val storeAppId: String get() = stateHolder.deviceInfo.value?.storeAppId.orEmpty()

    /** Languages the panel has translations for. */
    public val supportedLanguages: List<String>
        get() = stateHolder.deviceInfo.value?.appConfig?.supportedLanguages.orEmpty()

    /** The language VxHub uses: the one set with [changePreferredLanguage], else the device's. */
    public val preferredLanguage: String get() = engine.currentLanguage

    // endregion

    // region Purchases

    /**
     * Buys [product] and has the VxHub backend verify it. Premium is only ever what the backend
     * says. If the store charged but verification cannot finish now, the transaction is persisted
     * and re-sent on every start until verified, and the result is [VxPurchaseResult.PendingVerification].
     */
    public suspend fun purchase(activity: Activity, product: VxProduct): VxPurchaseResult =
        purchase(activity, product.storeProduct)

    public suspend fun purchase(activity: Activity, product: VxStoreProduct): VxPurchaseResult =
        engine.purchases.purchase(activity, product)

    /**
     * Restores through the store, then has the backend re-verify the account. Backends without
     * `device/restore` fall back to a registration, which returns the backend's premium.
     */
    public suspend fun restorePurchases(): VxRestoreResult = engine.purchases.restore(
        registerFallback = { runCatching { engine.registerDevice() }.isSuccess },
    )

    /** Re-sends every unverified transaction now. Returns how many were verified. */
    public suspend fun retryPendingPurchaseVerifications(): Int = engine.purchases.retryPendingVerifications()

    /** A charged transaction is still waiting for backend verification. */
    public val hasPendingPurchaseVerifications: Boolean get() = engine.purchases.hasPendingVerifications()

    /** The store's own view of premium (any active subscription), bypassing the backend. */
    public suspend fun storePremiumState(): Boolean = engine.purchases.storePremiumState()

    /** Records a non-consumable as owned, so it is no longer offered in [products]. */
    public suspend fun saveNonConsumablePurchase(productId: String) {
        engine.purchases.markNonConsumableOwned(productId)
    }

    /** Reloads products from the store and the panel. */
    public suspend fun reloadProducts() {
        engine.purchases.loadProducts()
    }

    // endregion

    // region Coins, promo codes, retention gift

    /**
     * Spends the device's VxHub coins. A premium device is never charged. On any backend answer
     * [balance] is updated to what it reported.
     *
     * @param amount coins to spend, 1–100.
     * @param reason what they were spent on, e.g. "mig_calculation" (max 64 characters).
     * @param parameters optional context stored with the transaction; keep it small.
     */
    public suspend fun spendCoins(amount: Int = 1, reason: String, parameters: Map<String, String>? = null): VxSpendCoinsResult {
        if (amount !in 1..100) return VxSpendCoinsResult.Failed("amount must be 1...100")
        if (stateHolder.vid == null) return VxSpendCoinsResult.Failed("VxHub has not registered this device yet")
        val result = engine.api.spendCoins(amount, reason.take(64), parameters)
        result.balance?.let { stateHolder.balance.value = it }
        return result
    }

    /** Redeems a promo code. Throws [VxHubException.NetworkUnavailable] when offline. */
    public suspend fun usePromoCode(code: String): VxPromoCodeResult {
        val result = engine.api.usePromoCode(code.trim())
        // The backend applied premium or coins; pick them up without waiting for the next start.
        if (result is VxPromoCodeResult.Redeemed) engine.scope.launch { runCatching { engine.registerDevice() } }
        return result
    }

    public suspend fun claimRetentionCoinGift(): VxRetentionCoinGift = engine.api.claimRetentionCoin().also {
        if (it.claimed) engine.scope.launch { runCatching { engine.registerDevice() } }
    }

    /** Marks the retention coin gift as given on this install. */
    public fun markRetentionCoinAsGiven() {
        engine.prefs.editAsync { it[VxPreferences.Keys.RETENTION_COIN_GIVEN] = true }
    }

    public fun hasGivenRetentionCoin(): Boolean = engine.prefs[VxPreferences.Keys.RETENTION_COIN_GIVEN] == true

    // endregion

    // region Device account

    /** Approves a QR login shown on another device (the web panel). */
    public suspend fun validateQrCode(token: String) {
        engine.api.approveQrLogin(token)
    }

    /**
     * Deletes this device's record on the backend, then re-registers so the app continues as a
     * fresh device. Returns false when the backend refused.
     */
    public suspend fun deleteAccount(): Boolean {
        val deleted = try {
            engine.api.deleteDevice()
        } catch (e: VxHubException) {
            VxProviderRegistry.banner?.show(e.message.orEmpty(), VxBannerType.ERROR, null, null)
            throw e
        }
        if (!deleted) return false
        return engine.warmStart(restoreTransactions = true)
    }

    /**
     * Re-points every service at this device's vid after the person signed out — AppsFlyer,
     * OneSignal and Amplitude ids, and the RevenueCat user (log out, log in, sync).
     * Returns false when the device is not registered or purchases are not configured.
     */
    public suspend fun handleLogout(): Boolean {
        val vid = stateHolder.vid ?: return false
        val keys = engine.state.thirdParty
        if (!keys.appsflyerDevKey.isNullOrBlank()) VxProviderRegistry.attribution?.changeCustomerUserId(vid)
        if (!keys.oneSignalAppId.isNullOrBlank()) VxProviderRegistry.push?.changeExternalId(vid)
        if (!keys.amplitudeApiKey.isNullOrBlank()) VxProviderRegistry.analytics?.changeUserId(vid)
        val purchase = VxProviderRegistry.purchase?.takeIf { it.isConfigured } ?: return false
        runCatching { purchase.logOut() }.onFailure { VxLog.d { "RevenueCat logOut: ${it.message}" } }
        runCatching { purchase.logIn(vid) }.onFailure { VxLog.w("RevenueCat logIn failed: ${it.message}") }
        return runCatching { purchase.syncPurchases() }.isSuccess
    }

    /**
     * Signs in with Google (needs `vxhub-google-signin`). Links the Google account to this device
     * and, when the panel enabled accounts for Google, also opens an account session.
     */
    public suspend fun signInWithGoogle(activity: Activity): Boolean {
        val clientId = authConfig.value?.googleClientId?.takeIf { it.isNotBlank() }
            ?: engine.state.thirdParty.googleClientId?.takeIf { it.isNotBlank() }
            ?: throw VxHubException.SignInFailed("Google", "Could not find Google Client Key In Response")
        val provider = VxProviderRegistry.googleSignIn ?: throw VxHubException.ModuleMissing("vxhub-google-signin")
        val credential = provider.signIn(activity, clientId)
        return completeDeviceSocialSignIn(
            provider = "google",
            token = credential.idToken,
            accountId = credential.accountId,
            name = credential.displayName,
            email = credential.email,
            photoUrl = credential.photoUrl,
        )
    }

    private suspend fun completeDeviceSocialSignIn(
        provider: String,
        token: String,
        accountId: String,
        name: String?,
        email: String?,
        photoUrl: String?,
    ): Boolean = coroutineScope {
        // The account session (when the panel has accounts on) and the device link run together;
        // the device call runs either way so purchases and support stay in step.
        val account = async {
            engine.auth.completeSocialSignIn(SocialLoginRequest(provider, token, accountId, name, email, photoUrl))
        }
        val response = engine.api.deviceSocialLogin(
            DeviceSocialLoginRequest(provider, token, accountId, name.orEmpty(), email.orEmpty()),
        )
        engine.adopt(response)
        account.await()
        if (response.social?.status != true) throw VxHubException.SignInFailed(provider, "Sign in failed")
        VxProviderRegistry.purchase?.takeIf { it.isConfigured }?.apply {
            setEmail(email)
            setDisplayName(name)
        }
        if (!email.isNullOrBlank()) VxProviderRegistry.push?.addEmail(email)
        VxProviderRegistry.analytics?.setLoginData(name, email)
        VxLog.i("Sign in with $provider succeeded")
        true
    }

    // endregion

    // region Accounts (email + password)

    /** The signed-in account, or null. */
    public val currentUser: StateFlow<VxUser?> get() = engine.auth.user

    public val isAuthenticated: StateFlow<Boolean> get() = engine.auth.isAuthenticated

    /** Which sign-in methods the panel enabled. Fetched after initialization. */
    public val authConfig: StateFlow<VxAuthConfig?> get() = engine.auth.config

    public suspend fun signUp(email: String, password: String, name: String? = null): VxUser =
        engine.auth.signUp(email, password, name)

    public suspend fun signIn(email: String, password: String): VxUser = engine.auth.signIn(email, password)

    /**
     * Asks for a reset code. Always succeeds, whether or not the address has an account — the API
     * will not confirm which; say "check your inbox" either way.
     */
    public suspend fun forgotPassword(email: String) {
        engine.auth.forgotPassword(email)
    }

    public suspend fun resetPassword(email: String, code: String, newPassword: String) {
        engine.auth.resetPassword(email, code, newPassword)
    }

    public suspend fun verifyEmail(code: String) {
        engine.auth.verifyEmail(code)
    }

    public suspend fun resendVerificationCode() {
        engine.auth.resendVerificationCode()
    }

    public suspend fun changePassword(current: String, new: String) {
        engine.auth.changePassword(current, new)
    }

    public suspend fun updateProfile(name: String? = null, profilePicture: String? = null): VxUser =
        engine.auth.updateProfile(name, profilePicture)

    public suspend fun refreshCurrentUser(): VxUser = engine.auth.refreshCurrentUser()

    /** The account's active sessions on every platform. */
    public suspend fun listAuthSessions(): List<VxAuthDeviceSession> = engine.auth.listSessions()

    /**
     * Signs out. The local session is cleared whatever the server says. [allDevices] revokes every
     * session of the account. The device keeps its own identity, so purchases and support carry on.
     */
    public suspend fun signOut(allDevices: Boolean = false) {
        engine.auth.signOut(allDevices)
        VxProviderRegistry.googleSignIn?.let { runCatching { it.signOut(engine.app) } }
        handleLogout()
    }

    /** Deletes the signed-in account. The device stays, unattached. */
    public suspend fun deleteAuthenticatedAccount() {
        engine.auth.deleteAccount()
    }

    // endregion

    // region Support

    /** Whether support answered a ticket the device has not seen. */
    public suspend fun ticketsUnseenStatus(): Boolean = engine.api.ticketsUnseenStatus()

    public suspend fun supportTickets(): List<VxTicket> = engine.api.tickets().map {
        VxTicket(it.id, it.category.orEmpty(), it.status, it.state, it.lastMessage, it.lastMessageCreatedAt, it.createdAt, it.isSeen ?: true)
    }

    public suspend fun createSupportTicket(category: String, message: String): VxTicket =
        engine.api.createTicket(category, message).let {
            VxTicket(it.id, it.category ?: category, it.status, it.state, message, it.createdAt, it.createdAt, true)
        }

    public suspend fun supportTicket(ticketId: String): VxTicketDetail = engine.api.ticketDetail(ticketId).let { dto ->
        VxTicketDetail(
            id = dto.id,
            category = dto.category.orEmpty(),
            status = dto.status,
            state = dto.state,
            createdAt = dto.createdAt,
            messages = dto.messages.map { VxTicketMessage(it.id, it.message, it.isFromDevice, it.createdAt) },
        )
    }

    public suspend fun sendSupportMessage(ticketId: String, message: String): VxTicketMessage =
        engine.api.sendTicketMessage(ticketId, message).let { VxTicketMessage(it.id, it.message, it.isFromDevice, it.createdAt) }

    // endregion

    // region Analytics

    /** Tracks a screen view for session analytics. Use the same names as on iOS. */
    public fun trackScreen(name: String) {
        engine.sessionTracker.trackScreen(name)
    }

    /** Tracks a custom event for session analytics. */
    public fun trackEvent(name: String, properties: Map<String, Any?>? = null) {
        engine.sessionTracker.trackEvent(name, properties)
    }

    /** Logs an Amplitude event (needs `vxhub-amplitude`). */
    public fun logAmplitudeEvent(eventName: String, properties: Map<String, Any?>? = null) {
        VxProviderRegistry.analytics?.logEvent(eventName, properties)
    }

    /** Logs an AppsFlyer event (needs `vxhub-appsflyer`). */
    public fun logAppsFlyerEvent(eventName: String, values: Map<String, Any?>? = null) {
        VxProviderRegistry.attribution?.logEvent(eventName, values)
    }

    /** The Amplitude Experiment variant assigned for [flagKey]. */
    public fun getVariant(flagKey: String): String? = VxProviderRegistry.analytics?.variant(flagKey)

    /** The Amplitude Experiment payload for [flagKey]. */
    public fun getVariantPayload(flagKey: String): Map<String, Any?>? = VxProviderRegistry.analytics?.variantPayload(flagKey)

    /** Starts Sentry with an explicit DSN (needs `vxhub-sentry`); VxHub does it with the panel's DSN. */
    public fun startSentry(dsn: String, config: VxSentryConfig? = null) {
        engine.startSentry(dsn, config)
    }

    public fun stopSentry() {
        VxProviderRegistry.crashReporting?.stop()
    }

    // endregion

    // region Localization

    /**
     * The panel's text for [key] in the current language; else VxHub's bundled English for its own
     * keys; else [default]; else [key] itself. So a key a newer SDK added never shows up raw before
     * the panel's translations catch up — iOS's `localize(defaultValue:)`.
     */
    public fun localize(key: String, default: String? = null): String =
        engineOrNull?.localizer?.localize(key) ?: BundledStrings.values[key] ?: default ?: key

    /**
     * Switches the language: re-registers with it and downloads its translations.
     * Returns false when either failed.
     */
    public suspend fun changePreferredLanguage(languageCode: String): Boolean {
        val engine = engine
        engine.prefs.edit { it[VxPreferences.Keys.PREFERRED_LANGUAGE] = languageCode }
        return try {
            engine.registerDevice()
            engine.reloadLocalization()
        } catch (e: VxHubException) {
            VxLog.e("Language change failed: ${e.message}")
            false
        }
    }

    // endregion

    // region Legal pages, web, store, review

    /** Opens the panel's terms of use. Shows an error banner and returns false when offline. */
    public fun showEula(context: Context, fullScreen: Boolean = false, showCloseButton: Boolean = false): Boolean =
        openPanelUrl(context, stateHolder.deviceInfo.value?.appConfig?.eulaUrl, fullScreen, showCloseButton)

    /** Opens the panel's privacy policy. Shows an error banner and returns false when offline. */
    public fun showPrivacy(context: Context, fullScreen: Boolean = false, showCloseButton: Boolean = false): Boolean =
        openPanelUrl(context, stateHolder.deviceInfo.value?.appConfig?.privacyUrl, fullScreen, showCloseButton)

    private fun openPanelUrl(context: Context, url: String?, fullScreen: Boolean, showCloseButton: Boolean): Boolean {
        if (!stateHolder.isConnected.value) {
            showBanner(localize("InternetConnection_CheckYourInternetConnection", "Please check your internet connection"), VxBannerType.ERROR)
            return false
        }
        if (url.isNullOrBlank()) return false
        presentWebUrl(context, url, fullScreen, showCloseButton)
        return true
    }

    /** Shows [url] in the app: `vxhub-ui`'s web screen when linked, else a Custom Tab. */
    public fun presentWebUrl(context: Context, url: String, fullScreen: Boolean = false, showCloseButton: Boolean = false) {
        VxProviderRegistry.webPresenter?.let {
            it.present(context, url, fullScreen, showCloseButton)
            return
        }
        try {
            val intent = CustomTabsIntent.Builder().setShowTitle(true).build()
            if (context !is Activity) intent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.launchUrl(context, url.toUri())
        } catch (e: ActivityNotFoundException) {
            VxLog.w("No browser can open $url")
        }
    }

    /**
     * Asks for a review: Google Play's in-app review sheet at most once every 30 days, the app's
     * Play Store page in between — the same policy as iOS.
     */
    public suspend fun requestReview(activity: Activity) {
        val prefs = engine.prefs
        val last = prefs[VxPreferences.Keys.LAST_REVIEW_REQUEST_AT]
        val now = System.currentTimeMillis()
        if (last == null || now - last >= REVIEW_INTERVAL_MS) {
            try {
                val manager = ReviewManagerFactory.create(activity)
                val info = manager.requestReview()
                manager.launchReview(activity, info)
                prefs.edit { it[VxPreferences.Keys.LAST_REVIEW_REQUEST_AT] = now }
                return
            } catch (e: Exception) {
                VxLog.w("In-app review unavailable (${e.javaClass.simpleName}); opening the store page")
            }
        }
        openStorePage(activity)
    }

    /** Opens the app's Google Play page. */
    public fun openStorePage(context: Context) {
        val packageName = context.packageName
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
        for (intent in listOf(market, web)) {
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                continue
            }
        }
    }

    // endregion

    // region Banner

    /** Shows an in-app banner (needs `vxhub-ui`). */
    public fun showBanner(message: String, type: VxBannerType = VxBannerType.SUCCESS, actionLabel: String? = null, action: (() -> Unit)? = null) {
        val provider = VxProviderRegistry.banner ?: run {
            VxLog.d { "showBanner ignored: vxhub-ui is not linked" }
            return
        }
        provider.show(message, type, actionLabel, action)
    }

    // endregion

    // region Media

    /** Downloads an image into VxHub's cache. [isLocalized] keeps one copy per language. */
    public suspend fun downloadImage(url: String, isLocalized: Boolean = false) {
        engine.downloader.downloadImage(url, isLocalized)
    }

    /** Downloads several images in parallel; returns the URLs that succeeded, in input order. */
    public suspend fun downloadImages(urls: List<String>, isLocalized: Boolean = false): List<String> = coroutineScope {
        urls.map { url ->
            async {
                try {
                    engine.downloader.downloadImage(url, isLocalized)
                    url
                } catch (e: IOException) {
                    VxLog.w("Image download failed: ${e.message}")
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    /** The cached file of a downloaded image, or null. */
    public fun downloadedImageFile(url: String, isLocalized: Boolean = false): File? =
        engine.downloader.imageFile(url, isLocalized)

    /** Decodes a downloaded image, off the main thread. */
    public suspend fun getDownloadedImage(url: String, isLocalized: Boolean = false): Bitmap? = withContext(Dispatchers.IO) {
        downloadedImageFile(url, isLocalized)?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }

    public suspend fun downloadVideo(url: String) {
        engine.downloader.downloadVideo(url)
    }

    /** The cached file of a downloaded video, or null. */
    public fun downloadedVideoFile(url: String): File? = engine.downloader.videoFile(url)

    public fun isDownloaded(url: String): Boolean = engine.downloader.isDownloaded(url)

    /**
     * Scales [bitmap] down to [maxDimension] and JPEG-encodes it, lowering quality until it fits
     * [maxBytes] — iOS's `compressImage`.
     */
    public suspend fun compressImage(
        bitmap: Bitmap,
        maxDimension: Int = 2048,
        quality: Float = 1f,
        maxBytes: Int = 2 * 1024 * 1024,
    ): ByteArray = withContext(Dispatchers.Default) { ImageCompressor.compress(bitmap, maxDimension, quality, maxBytes) }

    // endregion

    private const val REVIEW_INTERVAL_MS = 30L * 24 * 60 * 60 * 1_000

    /** For VxHub's own modules (`vxhub-ui` and friends); not part of the public API. */
    @androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)
    public object Internal {
        /** The foreground activity, when there is one. */
        public val currentActivity: Activity? get() = ActivityTracker.current

        public val isInitialized: Boolean get() = engineOrNull != null

        /** Logs through VxHub's logger, so satellites honour [VxHubConfig.logLevel]. */
        public fun log(level: VxLogLevel, message: String, error: Throwable? = null) {
            when (level) {
                VxLogLevel.VERBOSE -> VxLog.v { message }
                VxLogLevel.DEBUG -> VxLog.d { message }
                VxLogLevel.INFO -> VxLog.i(message)
                VxLogLevel.WARNING -> VxLog.w(message, error)
                VxLogLevel.ERROR -> VxLog.e(message, error)
                VxLogLevel.NONE -> Unit
            }
        }
    }
}
