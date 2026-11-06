package com.vxhub

/**
 * Optional callbacks, all delivered on the main thread. Every method has an empty default, so
 * implement only what the app needs. The same events are also observable as state through
 * [VxHub.state], [VxHub.isPremium] and friends.
 *
 * The Android counterpart of iOS's `VxHubDelegate`.
 */
public interface VxHubListener {
    /** Config, products and localization are loaded. */
    public fun onInitialized() {}

    /** A warm start (re-registration on returning to the foreground) finished. */
    public fun onStarted() {}

    /** Initialization or a warm start failed. */
    public fun onFailed(error: VxHubException) {}

    /** The panel banned this device. */
    public fun onBanned() {}

    /** The panel requires a newer app version and it is available on Google Play. */
    public fun onForceUpdateRequired() {}

    /** AppsFlyer conversion data arrived (needs `vxhub-appsflyer`). */
    public fun onConversionDataSuccess(data: Map<String, Any?>) {}

    /** AppsFlyer could not deliver conversion data (needs `vxhub-appsflyer`). */
    public fun onConversionDataFail(error: Throwable) {}

    /** A push notification arrived while the app was running (needs `vxhub-onesignal`). */
    public fun onPushNotificationReceived(data: Map<String, Any?>) {}

    /** Connectivity changed. */
    public fun onNetworkStatusChanged(isConnected: Boolean, connectionType: VxConnectionType) {}

    /**
     * The signed-in account's session ended and could not be renewed — the refresh token was
     * spent or revoked, or the account was banned or deleted. VxHub has already cleared it; show
     * the sign-in screen.
     */
    public fun onUserSessionExpired() {}
}
