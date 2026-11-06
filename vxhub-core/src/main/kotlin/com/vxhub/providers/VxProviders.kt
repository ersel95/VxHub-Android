package com.vxhub.providers

import android.app.Activity
import android.content.Context
import com.vxhub.purchases.VxCustomerInfo
import com.vxhub.purchases.VxRestoreSnapshot
import com.vxhub.purchases.VxStoreProduct
import com.vxhub.purchases.VxStorePurchaseOutcome

/*
 * Contracts the satellite modules implement. Core never links a third-party SDK: a module such as
 * `vxhub-amplitude` registers its implementation in [VxProviderRegistry] at app start (through
 * AndroidX Startup), and core calls it once the backend has delivered the keys.
 *
 * An app can also register its own implementation — a fake purchase provider in tests, or a
 * different push vendor — before calling VxHub.initialize.
 */

/** Store purchases. Implemented by `vxhub-revenuecat`. */
public interface VxPurchaseProvider {
    public val isConfigured: Boolean

    public fun configure(context: Context, apiKey: String, appUserId: String)

    /** Products of the current offering. */
    public suspend fun products(): List<VxStoreProduct>

    public suspend fun purchase(activity: Activity, product: VxStoreProduct): VxStorePurchaseOutcome

    /** Restores through the store. Throws when the store restore itself fails. */
    public suspend fun restore(): VxRestoreSnapshot

    public suspend fun customerInfo(): VxCustomerInfo?

    public suspend fun isTrialOrIntroEligible(product: VxStoreProduct): Boolean

    public suspend fun logIn(appUserId: String): VxCustomerInfo?

    public suspend fun logOut()

    public suspend fun syncPurchases(): VxCustomerInfo?

    public fun syncAttributesAndOfferingsIfNeeded()

    public fun setAttributes(attributes: Map<String, String>)

    public fun setOneSignalId(id: String)

    public fun setFacebookAnonymousId(id: String)

    public fun setAppsflyerId(id: String)

    public fun setFirebaseAppInstanceId(id: String)

    public fun setEmail(email: String?)

    public fun setDisplayName(name: String?)

    public fun collectDeviceIdentifiers()
}

/** Product analytics and experiments. Implemented by `vxhub-amplitude`. */
public interface VxAnalyticsProvider {
    public fun initialize(
        context: Context,
        userId: String,
        apiKey: String,
        deploymentKey: String?,
        deviceId: String,
        isSubscriber: Boolean,
    )

    public fun logEvent(name: String, properties: Map<String, Any?>?)

    /** The variant value assigned to the user for [flagKey]. */
    public fun variant(flagKey: String): String?

    /** The variant payload for [flagKey], as plain Kotlin types. */
    public fun variantPayload(flagKey: String): Map<String, Any?>?

    public fun changeUserId(userId: String?)

    public fun setLoginData(fullName: String?, email: String?)
}

public interface VxAttributionListener {
    public fun onConversionDataSuccess(data: Map<String, Any?>)
    public fun onConversionDataFail(error: Throwable)
}

/** Install attribution. Implemented by `vxhub-appsflyer`. */
public interface VxAttributionProvider {
    public fun initialize(
        context: Context,
        devKey: String,
        appId: String,
        customerUserId: String,
        language: String,
        listener: VxAttributionListener,
    )

    public fun start(context: Context)

    public fun logEvent(name: String, values: Map<String, Any?>?)

    public fun changeCustomerUserId(customerUserId: String)

    /** AppsFlyer's id for this install; empty until initialized. */
    public val attributionId: String
}

/** Push messaging. Implemented by `vxhub-onesignal`. */
public interface VxPushProvider {
    public fun initialize(context: Context, appId: String, externalId: String)

    public fun changeExternalId(externalId: String)

    public fun addEmail(email: String)

    public fun logout()

    /** The push subscription id (OneSignal's former "player id"). */
    public val subscriptionId: String?

    /** The FCM token behind the subscription. */
    public val pushToken: String?

    /** Called with a notification's additional data when one arrives in the foreground. */
    public fun setNotificationListener(listener: (Map<String, Any?>) -> Unit)
}

/** Firebase, configured at runtime from the panel's google-services.json. Implemented by `vxhub-firebase`. */
public interface VxFirebaseProvider {
    /** Configures the default FirebaseApp from google-services.json content. Returns false when it could not. */
    public fun configure(context: Context, googleServicesJson: String): Boolean

    public val isConfigured: Boolean

    /** Firebase Analytics' app instance id, once available. */
    public suspend fun appInstanceId(): String?
}

/** Sentry settings, mirroring iOS's `VxSentryConfig`. */
public class VxSentryConfig(
    public val environment: String,
    public val enableDebug: Boolean = false,
    public val tracesSampleRate: Double = 1.0,
    public val profilesSampleRate: Double = 1.0,
    public val attachScreenshot: Boolean = false,
    public val attachViewHierarchy: Boolean = false,
)

/** Crash reporting. Implemented by `vxhub-sentry`. */
public interface VxCrashReportingProvider {
    public fun start(context: Context, dsn: String, config: VxSentryConfig)

    public fun stop()

    public fun setUserId(userId: String)
}

/** Facebook App Events. Implemented by `vxhub-facebook`. */
public interface VxFacebookProvider {
    public fun initialize(context: Context, appId: String, clientToken: String, appName: String?)

    /** Facebook's anonymous app-user id, forwarded to RevenueCat. */
    public val anonymousId: String

    public fun setAdvertiserTrackingEnabled(enabled: Boolean)
}

/** A verified Google identity. */
public class VxGoogleCredential(
    /** The Google ID token, issued for the panel's web client id. */
    public val idToken: String,
    /** Google's stable account id (the token's `sub`). */
    public val accountId: String,
    public val displayName: String?,
    public val email: String?,
    public val photoUrl: String?,
)

/** Google sign-in through Credential Manager. Implemented by `vxhub-google-signin`. */
public interface VxGoogleSignInProvider {
    /**
     * Shows Google's account picker and returns the chosen identity.
     *
     * @param serverClientId the panel's `google_client_id` — a *Web* OAuth client id, so the token's
     *   audience is the one the VxHub backend verifies.
     */
    public suspend fun signIn(activity: Activity, serverClientId: String): VxGoogleCredential

    /** Clears the remembered account so the next sign-in shows the picker again. */
    public suspend fun signOut(context: Context)
}

/** In-app message banner styles. Same cases as iOS's `VxBannerTypes`. */
public enum class VxBannerType { SUCCESS, ERROR, WARNING, INFO, DEBUG }

/** Top-of-screen banners. Implemented by `vxhub-ui`. */
public interface VxBannerProvider {
    public fun show(message: String, type: VxBannerType, actionLabel: String?, action: (() -> Unit)?)

    public fun dismissAll()
}

/** Shows a web page inside the app. Implemented by `vxhub-ui`; core falls back to a Custom Tab. */
public interface VxWebPresenter {
    public fun present(context: Context, url: String, fullScreen: Boolean, showCloseButton: Boolean)
}
