package com.vxhub.internal

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.SystemClock
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import com.vxhub.VxConnectionType
import com.vxhub.VxHub
import com.vxhub.VxHubConfig
import com.vxhub.VxHubException
import com.vxhub.VxHubListener
import com.vxhub.VxHubState
import com.vxhub.VxLogLevel
import com.vxhub.internal.analytics.SessionMeta
import com.vxhub.internal.analytics.SessionTransport
import com.vxhub.internal.analytics.VxSessionTracker
import com.vxhub.internal.analytics.toJsonObject
import com.vxhub.internal.auth.VxAuthApi
import com.vxhub.internal.auth.VxAuthManager
import com.vxhub.internal.content.VxDownloader
import com.vxhub.internal.content.VxLocalizer
import com.vxhub.internal.device.DeviceConfig
import com.vxhub.internal.device.DeviceIdentity
import com.vxhub.internal.device.compareVersions
import com.vxhub.internal.device.readAdvertisingId
import com.vxhub.internal.network.DeviceRegisterRequest
import com.vxhub.internal.network.DeviceRegisterResponseDto
import com.vxhub.internal.network.HeaderProvider
import com.vxhub.internal.network.MutualTls
import com.vxhub.internal.network.SessionEndRequest
import com.vxhub.internal.network.SessionEventsRequest
import com.vxhub.internal.network.SessionStartRequest
import com.vxhub.internal.network.ThirdPartyDto
import com.vxhub.internal.network.VxApi
import com.vxhub.internal.network.VxHttpClient
import com.vxhub.internal.network.VxJson
import com.vxhub.internal.purchase.PendingPurchaseStore
import com.vxhub.internal.purchase.VxPurchaseManager
import com.vxhub.internal.storage.KeystoreSecretCipher
import com.vxhub.internal.storage.VxFiles
import com.vxhub.internal.storage.VxPreferences
import com.vxhub.providers.VxAttributionListener
import com.vxhub.providers.VxProviderRegistry
import com.vxhub.providers.VxSentryConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns every moving part of a running VxHub and the two flows that move them: the cold start
 * ([runInitialization], iOS's `configureHub` + `downloadExternalAssets`) and the warm start
 * ([warmStart], iOS's `startHub`).
 */
internal class VxHubEngine(
    val app: Application,
    val config: VxHubConfig,
    @Volatile var listener: VxHubListener?,
    val state: VxStateHolder,
    private val initStartedAt: Long,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> VxLog.e("Unhandled VxHub error", e) },
    )

    val prefs: VxPreferences = VxPreferences.create(app, CoroutineScope(scope.coroutineContext + Dispatchers.IO))
    val identity = DeviceIdentity(app, prefs)

    @Volatile
    var device: DeviceConfig? = null
        private set

    private val apiClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .apply { MutualTls.apply(app, this) }
        .build()

    // Shares the API client's connection pool and threads; adds an HTTP cache and room for videos.
    private val downloadClient: OkHttpClient = apiClient.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .cache(Cache(File(VxFiles.baseDir(app), "http-cache"), 20L * 1024 * 1024))
        .build()

    private val http = VxHttpClient(apiClient, config.environment.baseUrl.toHttpUrl(), HeaderProvider(::headers))
    val api = VxApi(http)
    val downloader = VxDownloader(app, downloadClient, prefs)
    val localizer = VxLocalizer(app, downloader)

    val auth = VxAuthManager(
        api = VxAuthApi(http),
        prefs = prefs,
        cipher = KeystoreSecretCipher(),
        scope = scope,
        onSessionExpired = { notify { it.onUserSessionExpired() } },
    )

    val purchases = VxPurchaseManager(
        api = api,
        prefs = prefs,
        state = state,
        pending = PendingPurchaseStore(VxPurchaseManager.persistence(prefs)),
        nonConsumableIds = config.nonConsumableProductIds,
    )

    val sessionTracker = VxSessionTracker(
        scope = scope,
        transport = object : SessionTransport {
            override suspend fun start(request: SessionStartRequest) = api.sessionStart(request)
            override suspend fun end(request: SessionEndRequest) = api.sessionEnd(request)
            override suspend fun events(request: SessionEventsRequest) = api.sessionEvents(request)
        },
        meta = { SessionMeta(device?.appVersion.orEmpty(), state.isPremium.value, device?.countryCode.orEmpty()) },
    )

    private val initMutex = Mutex()
    private var initJob: Job? = null
    private val firstLaunch = AtomicBoolean(true)
    private val thirdPartiesConfigured = AtomicBoolean(false)
    private val attributionStarted = AtomicBoolean(false)

    @Volatile
    private var startupTimeMs: Long = 0

    @Volatile
    private var advertisingId: String? = null

    private val userAgent: String by lazy {
        val d = device
        "VxHub-Android/${VxHub.SDK_VERSION} (${app.packageName}/${d?.appVersion.orEmpty()}; " +
            "Android ${Build.VERSION.RELEASE}; ${Build.MODEL})"
    }

    // region Headers

    val currentLanguage: String
        get() = prefs[VxPreferences.Keys.PREFERRED_LANGUAGE]
            ?: device?.deviceLanguage
            ?: Locale.getDefault().language.ifBlank { "en" }

    private fun headers(authenticated: Boolean): Map<String, String> = buildMap {
        put("X-Hub-Id", config.hubId)
        put("X-Hub-Device-Id", identity.deviceId)
        state.vid?.takeIf { UUID_V4.matches(it) }?.let { put("X-Hub-Vid", it) }
        // Decides the language of verification and reset emails.
        put("X-Language-Code", currentLanguage)
        put("X-Platform", "android")
        put("User-Agent", userAgent)
        if (authenticated) auth.accessToken?.let { put("Authorization", "Bearer $it") }
    }

    // endregion

    // region Cold start

    /** Starts initialization unless it is running or done. */
    fun beginInitialization() {
        scope.launch {
            initMutex.withLock {
                if (initJob?.isActive == true) return@launch
                val current = state.state.value
                if (current is VxHubState.Ready || current is VxHubState.Banned || current is VxHubState.ForceUpdateRequired) {
                    return@launch
                }
                initJob = scope.launch { runInitialization() }
            }
        }
    }

    private suspend fun runInitialization() {
        state.state.value = VxHubState.Initializing
        try {
            prepareLocalState()
            val response = registerDevice()

            if (response.device?.banStatus == true) {
                state.state.value = VxHubState.Banned
                notify { it.onBanned() }
                return
            }
            if (isForceUpdateRequired(response)) {
                state.state.value = VxHubState.ForceUpdateRequired
                notify { it.onForceUpdateRequired() }
                return
            }

            configureThirdParties(response)
            downloadExternalAssets(response)

            firstLaunch.set(false)
            state.state.value = VxHubState.Ready
            VxLog.i("Initialized successfully in ${SystemClock.elapsedRealtime() - initStartedAt} ms ${VxProviderRegistry.describe()}")
            notify { it.onInitialized() }

            sessionTracker.start()
            scope.launch { purchases.retryPendingVerifications() }
            // The stored account session and the panel's sign-in methods, so the app can render
            // its signed-in state right after initialization.
            scope.launch { auth.refreshStateAfterInitialization() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = e as? VxHubException ?: VxHubException.Unknown(e.message ?: e.javaClass.simpleName, e)
            VxLog.e("VxHub failed to initialize: ${error.message}")
            state.state.value = VxHubState.Failed(error)
            notify { it.onFailed(error) }
        }
    }

    /** Everything that needs only the disk: runs before the first request. */
    private suspend fun prepareLocalState() {
        prefs.load()
        purchases.publishOwnedNonConsumables()
        auth.restoreStoredSession()
        withContext(Dispatchers.IO) {
            if (device == null) device = DeviceConfig.collect(app)
            if (config.collectAdvertisingId && advertisingId == null) advertisingId = readAdvertisingId(app)
        }
        localizer.loadCached()
        // Last-known state, so premium and remote config are right even before (or without) the network.
        if (state.deviceInfo.value == null) {
            prefs[VxPreferences.Keys.LAST_REGISTRATION]
                ?.let { runCatching { VxJson.decodeFromString(DeviceRegisterResponseDto.serializer(), it) }.getOrNull() }
                ?.let { state.apply(it) }
        }
        configureFirebaseFromCache()
    }

    // endregion

    // region Warm start

    /**
     * Re-registers the device (premium, balance, config), retries pending purchase verifications
     * and, with [restoreTransactions], reloads localization and products.
     */
    suspend fun warmStart(restoreTransactions: Boolean): Boolean {
        if (firstLaunch.get() || state.state.value !is VxHubState.Ready) return false
        return try {
            val response = registerDevice()
            if (restoreTransactions) downloadExternalAssets(response)
            scope.launch { purchases.retryPendingVerifications() }
            notify { it.onStarted() }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: VxHubException) {
            VxLog.w("Warm start failed: ${e.message}")
            notify { it.onFailed(e) }
            false
        }
    }

    // endregion

    // region Registration

    suspend fun registerDevice(): DeviceRegisterResponseDto {
        val d = device ?: withContext(Dispatchers.IO) { DeviceConfig.collect(app) }.also { device = it }
        val firebaseId = VxProviderRegistry.firebase?.takeIf { it.isConfigured }
            ?.let { withTimeoutOrNull(FIREBASE_ID_TIMEOUT_MS) { runCatching { it.appInstanceId() }.getOrNull() } }
        val push = VxProviderRegistry.push
        val request = DeviceRegisterRequest(
            deviceType = d.deviceType,
            deviceBrand = d.brand,
            deviceModel = d.model,
            countryCode = d.countryCode,
            languageCode = currentLanguage,
            idfa = advertisingId.orEmpty(),
            appsflyerId = VxProviderRegistry.attribution?.attributionId.orEmpty(),
            firebaseId = firebaseId,
            opRegion = d.countryCode,
            carrierRegion = d.carrierRegion,
            os = d.osVersion,
            resolution = d.resolution,
            dpi = d.dpi,
            oneSignalToken = push?.pushToken.orEmpty(),
            oneSignalPlayerId = push?.subscriptionId.orEmpty(),
            appVersion = d.appVersion,
            startupTimeMs = startupTimeMs,
            installedApps = d.installedApps,
        )
        val response = api.registerDevice(request)
        if (startupTimeMs == 0L) {
            startupTimeMs = SystemClock.elapsedRealtime() - initStartedAt
            VxLog.i("VxHub startup time: $startupTimeMs ms")
        }
        adopt(response)
        return response
    }

    /** Applies a registration or social-login response and keeps it for the next offline start. */
    fun adopt(response: DeviceRegisterResponseDto) {
        state.apply(response)
        prefs.editAsync {
            it[VxPreferences.Keys.LAST_REGISTRATION] = VxJson.encodeToString(DeviceRegisterResponseDto.serializer(), response)
        }
    }

    private suspend fun isForceUpdateRequired(response: DeviceRegisterResponseDto): Boolean {
        val required = response.config?.storeVersion?.takeIf { it.isNotBlank() } ?: return false
        if (response.config.forceUpdate != true) return false
        val installed = device?.appVersion.orEmpty()
        if (installed.isNotBlank() && compareVersions(installed, required) >= 0) return false
        // iOS only forces once the App Store serves the version; Play's update info is the same
        // guard here, so a version still in review can never lock people out.
        val available = withTimeoutOrNull(PLAY_UPDATE_TIMEOUT_MS) {
            runCatching {
                AppUpdateManagerFactory.create(app).requestAppUpdateInfo().updateAvailability() ==
                    UpdateAvailability.UPDATE_AVAILABLE
            }.getOrDefault(false)
        } ?: false
        VxLog.i("Force update: installed=$installed required=$required playHasUpdate=$available")
        return available
    }

    // endregion

    // region Third parties

    /** Hands each satellite its keys. Once per process, as iOS's `setFirstLaunch`. */
    private suspend fun configureThirdParties(response: DeviceRegisterResponseDto) {
        if (!thirdPartiesConfigured.compareAndSet(false, true)) return
        val keys = response.thirdParty ?: ThirdPartyDto()
        val udid = identity.deviceId
        val userId = response.vid ?: udid

        withContext(mainDispatcher) {
            val attribution = VxProviderRegistry.attribution
            if (attribution != null && !keys.appsflyerDevKey.isNullOrBlank() && !keys.appsflyerAppId.isNullOrBlank()) {
                guarded("AppsFlyer") {
                    attribution.initialize(app, keys.appsflyerDevKey, keys.appsflyerAppId, userId, currentLanguage, attributionListener)
                    if (attributionStarted.compareAndSet(false, true)) attribution.start(app)
                }
            }

            val facebook = VxProviderRegistry.facebook
            if (facebook != null && !keys.facebookAppId.isNullOrBlank() && !keys.facebookClientToken.isNullOrBlank()) {
                guarded("Facebook") {
                    facebook.initialize(app, keys.facebookAppId, keys.facebookClientToken, keys.facebookDisplayName ?: appLabel())
                }
            }

            val push = VxProviderRegistry.push
            if (push != null && !keys.oneSignalAppId.isNullOrBlank()) {
                guarded("OneSignal") {
                    push.initialize(app, keys.oneSignalAppId, userId)
                    push.setNotificationListener { data -> notify { it.onPushNotificationReceived(data) } }
                }
            }

            val analytics = VxProviderRegistry.analytics
            if (analytics != null && !keys.amplitudeApiKey.isNullOrBlank()) {
                guarded("Amplitude") {
                    analytics.initialize(app, userId, keys.amplitudeApiKey, keys.amplitudeDeploymentKey, udid, state.isPremium.value)
                }
            }

            if (!keys.sentryDsn.isNullOrBlank()) startSentry(keys.sentryDsn, null)

            val purchase = VxProviderRegistry.purchase
            if (!keys.revenueCatApiKey.isNullOrBlank()) {
                if (purchase == null) {
                    VxLog.w("RevenueCat key found but vxhub-revenuecat is not linked")
                } else {
                    guarded("RevenueCat") {
                        purchase.configure(app, keys.revenueCatApiKey, userId)
                        push?.subscriptionId?.let(purchase::setOneSignalId)
                        purchase.setAttributes(mapOf("\$amplitudeDeviceId" to udid, "\$amplitudeUserId" to userId))
                        VxProviderRegistry.facebook?.anonymousId?.takeIf { it.isNotBlank() }?.let(purchase::setFacebookAnonymousId)
                        VxProviderRegistry.attribution?.attributionId?.takeIf { it.isNotBlank() }?.let(purchase::setAppsflyerId)
                        if (config.collectAdvertisingId) purchase.collectDeviceIdentifiers()
                        purchase.syncAttributesAndOfferingsIfNeeded()
                    }
                }
            }
        }
    }

    fun startSentry(dsn: String, sentryConfig: VxSentryConfig?) {
        val provider = VxProviderRegistry.crashReporting ?: run {
            VxLog.d { "Sentry DSN present but vxhub-sentry is not linked" }
            return
        }
        val resolved = sentryConfig ?: VxSentryConfig(
            environment = if (config.environment == com.vxhub.VxEnvironment.STAGE) "Debug" else "Release",
            enableDebug = config.logLevel == VxLogLevel.VERBOSE,
        )
        guarded("Sentry") {
            provider.start(app, dsn, resolved)
            provider.setUserId(identity.deviceId)
        }
    }

    private val attributionListener = object : VxAttributionListener {
        override fun onConversionDataSuccess(data: Map<String, Any?>) {
            scope.launch { api.sendConversionData(data.toJsonObject()) }
            notify { it.onConversionDataSuccess(data) }
        }

        override fun onConversionDataFail(error: Throwable) {
            notify { it.onConversionDataFail(error) }
        }
    }

    // endregion

    // region External assets

    /** Localization, Firebase config and products, in parallel — iOS's `downloadExternalAssets`. */
    private suspend fun downloadExternalAssets(response: DeviceRegisterResponseDto) = coroutineScope {
        listOf(
            async { localizer.refresh(response.config?.localizationUrl) },
            async { configureFirebaseFromPanel(response.thirdParty?.firebaseConfigUrl) },
            async { runCatching { purchases.loadProducts() }.onFailure { VxLog.w("Products could not be loaded: ${it.message}") } },
        ).awaitAll()
    }

    private val googleServicesFile: File get() = File(VxFiles.thirdPartyDir(app), "google-services.json")

    /** Configures Firebase from the copy saved on a previous launch, before any network work. */
    private suspend fun configureFirebaseFromCache() {
        val firebase = VxProviderRegistry.firebase ?: return
        if (firebase.isConfigured) return
        val json = withContext(Dispatchers.IO) { googleServicesFile.takeIf { it.exists() }?.readText() } ?: return
        withContext(mainDispatcher) { guarded("Firebase") { firebase.configure(app, json) } }
    }

    private suspend fun configureFirebaseFromPanel(url: String?) {
        val firebase = VxProviderRegistry.firebase ?: return
        if (url.isNullOrBlank()) return
        val file = try {
            downloader.download(url, VxFiles.thirdPartyDir(app), googleServicesFile.name)
        } catch (e: Exception) {
            VxLog.w("google-services.json could not be downloaded: ${e.message}")
            return
        }
        if (!firebase.isConfigured) {
            val json = withContext(Dispatchers.IO) { file.readText() }
            withContext(mainDispatcher) { guarded("Firebase") { firebase.configure(app, json) } }
        }
        if (firebase.isConfigured) {
            val instanceId = withTimeoutOrNull(FIREBASE_ID_TIMEOUT_MS * 4) { runCatching { firebase.appInstanceId() }.getOrNull() }
            if (!instanceId.isNullOrBlank()) VxProviderRegistry.purchase?.takeIf { it.isConfigured }?.setFirebaseAppInstanceId(instanceId)
        }
    }

    suspend fun reloadLocalization(): Boolean = localizer.refresh(state.deviceInfo.value?.appConfig?.localizationUrl)

    // endregion

    // region Process lifecycle and connectivity

    fun onForeground() {
        sessionTracker.onForeground()
        when {
            state.state.value is VxHubState.Failed && config.retryInitializationAutomatically -> beginInitialization()
            config.autoStartOnForeground && !firstLaunch.get() -> scope.launch { warmStart(restoreTransactions = false) }
        }
    }

    fun onBackground() {
        sessionTracker.onBackground()
    }

    fun onConnectivityChanged(isConnected: Boolean, type: VxConnectionType) {
        val changed = state.isConnected.value != isConnected || state.connectionType.value != type
        state.isConnected.value = isConnected
        state.connectionType.value = type
        if (!changed) return
        VxLog.d { "Connectivity: connected=$isConnected type=$type" }
        notify { it.onNetworkStatusChanged(isConnected, type) }
        if (isConnected && state.state.value is VxHubState.Failed && config.retryInitializationAutomatically) {
            beginInitialization()
        }
    }

    // endregion

    // region Helpers

    /** Calls the listener on the main thread. */
    fun notify(block: (VxHubListener) -> Unit) {
        val target = listener ?: return
        scope.launch(mainDispatcher) {
            try {
                block(target)
            } catch (e: Exception) {
                VxLog.e("VxHubListener threw", e)
            }
        }
    }

    private inline fun guarded(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            // One misbehaving vendor SDK must not take the rest of initialization down with it.
            VxLog.e("$name could not be configured", e)
        }
    }

    private fun appLabel(): String? = runCatching {
        val info: ApplicationInfo = app.applicationInfo
        app.packageManager.getApplicationLabel(info).toString()
    }.getOrNull()

    val isFirstLaunch: Boolean get() = firstLaunch.get()

    // endregion

    private companion object {
        const val FIREBASE_ID_TIMEOUT_MS = 500L
        const val PLAY_UPDATE_TIMEOUT_MS = 5_000L
        val UUID_V4 = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
    }
}
