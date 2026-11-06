package com.vxhub.providers

/**
 * The satellite implementations VxHub found. Each `vxhub-*` module registers itself at app start
 * through AndroidX Startup, so linking the module is all an app does — the counterpart of the iOS
 * SDK's `VxProviderRegistry.autoRegister()`.
 *
 * Register a custom implementation before [com.vxhub.VxHub.initialize] to replace one.
 */
public object VxProviderRegistry {
    @Volatile public var purchase: VxPurchaseProvider? = null

    @Volatile public var analytics: VxAnalyticsProvider? = null

    @Volatile public var attribution: VxAttributionProvider? = null

    @Volatile public var push: VxPushProvider? = null

    @Volatile public var firebase: VxFirebaseProvider? = null

    @Volatile public var crashReporting: VxCrashReportingProvider? = null

    @Volatile public var facebook: VxFacebookProvider? = null

    @Volatile public var googleSignIn: VxGoogleSignInProvider? = null

    @Volatile public var banner: VxBannerProvider? = null

    @Volatile public var webPresenter: VxWebPresenter? = null

    /** Which providers are present, for diagnostics. */
    public fun describe(): String = listOfNotNull(
        purchase?.let { "purchase=${it.javaClass.simpleName}" },
        analytics?.let { "analytics=${it.javaClass.simpleName}" },
        attribution?.let { "attribution=${it.javaClass.simpleName}" },
        push?.let { "push=${it.javaClass.simpleName}" },
        firebase?.let { "firebase=${it.javaClass.simpleName}" },
        crashReporting?.let { "crashReporting=${it.javaClass.simpleName}" },
        facebook?.let { "facebook=${it.javaClass.simpleName}" },
        googleSignIn?.let { "googleSignIn=${it.javaClass.simpleName}" },
        banner?.let { "banner=${it.javaClass.simpleName}" },
        webPresenter?.let { "web=${it.javaClass.simpleName}" },
    ).joinToString(prefix = "[", postfix = "]")
}
