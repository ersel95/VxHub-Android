package com.vxhub

/** Where VxHub is in its lifecycle. Observe it through [VxHub.state]. */
public sealed interface VxHubState {
    /** [VxHub.initialize] has not been called. */
    public data object NotInitialized : VxHubState

    /** Registering the device and loading config, products and localization. */
    public data object Initializing : VxHubState

    /** Ready: config, products and localization are loaded. */
    public data object Ready : VxHubState

    /** The panel banned this device. Nothing else will be loaded. */
    public data object Banned : VxHubState

    /** The panel requires a newer app version, and that version is live on Google Play. */
    public data object ForceUpdateRequired : VxHubState

    /**
     * Initialization failed. With [VxHubConfig.retryInitializationAutomatically] VxHub tries again
     * on the next foreground or when the network returns.
     */
    public data class Failed(val error: VxHubException) : VxHubState
}

/** How [VxHub.awaitInitialization] ended. Same cases as iOS's `VxHubInitResult`. */
public enum class VxHubInitResult {
    SUCCESS,
    BANNED,
    FORCE_UPDATE_REQUIRED,
}

/** Network transport currently in use. */
public enum class VxConnectionType {
    WIFI,
    CELLULAR,
    ETHERNET,
    OTHER,
    NONE,
}
