package com.vxhub

/**
 * Everything VxHub needs from the app. The only required value is [hubId]: every third-party key
 * (RevenueCat, Amplitude, AppsFlyer, OneSignal, Firebase, Sentry, Facebook, Google) comes from the
 * VxHub panel at runtime.
 *
 * @property hubId the app's id in the VxHub panel (a UUID).
 * @property environment which VxHub backend to talk to.
 * @property logLevel the lowest level that reaches Logcat. Network bodies are logged at
 *   [VxLogLevel.DEBUG] and below, with tokens and passwords redacted.
 * @property collectAdvertisingId send the Google Advertising ID with device registration, the
 *   Android counterpart of iOS's IDFA. Off by default: collecting it must be declared in the
 *   Play Console's Data safety form and needs `com.google.android.gms:play-services-ads-identifier`
 *   on the app's classpath.
 * @property autoStartOnForeground re-register the device every time the app returns to the
 *   foreground — the iOS SDK's "warm start", which iOS apps must call themselves from
 *   `applicationDidBecomeActive`. Leave it on unless the app calls [VxHub.start] itself.
 * @property retryInitializationAutomatically when initialization fails for a transport reason,
 *   try again on the next foreground or when connectivity returns, instead of staying failed
 *   until the process restarts.
 */
public class VxHubConfig(
    public val hubId: String,
    public val environment: VxEnvironment = VxEnvironment.PROD,
    public val logLevel: VxLogLevel = VxLogLevel.INFO,
    public val collectAdvertisingId: Boolean = false,
    public val autoStartOnForeground: Boolean = true,
    public val retryInitializationAutomatically: Boolean = true,
) {
    init {
        require(UUID_V4.matches(hubId)) {
            "VxHubConfig.hubId must be the app's UUID from the VxHub panel; got \"$hubId\""
        }
    }

    override fun toString(): String =
        "VxHubConfig(hubId=$hubId, environment=$environment, logLevel=$logLevel, " +
            "collectAdvertisingId=$collectAdvertisingId, autoStartOnForeground=$autoStartOnForeground)"

    private companion object {
        // Same check the backend applies to X-Hub-Id: anything else is answered with 400.
        val UUID_V4 = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
        )
    }
}

/** The VxHub backend an app talks to. */
public enum class VxEnvironment(internal val baseUrl: String) {
    STAGE("https://stage.api.volvoxhub.com/api/v1/"),
    PROD("https://vxhub-api.volvoxmobile.com/api/v1/"),
}

/** Logcat verbosity, lowest first. */
public enum class VxLogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARNING,
    ERROR,

    /** Nothing is logged. */
    NONE,
}
