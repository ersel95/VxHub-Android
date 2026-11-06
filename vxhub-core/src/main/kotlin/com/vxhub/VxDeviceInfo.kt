package com.vxhub

/**
 * What the backend returned for this device on the last registration. Read it through
 * [VxHub.deviceInfo].
 */
public class VxDeviceInfo internal constructor(
    /** The backend's id for this device (a UUID). Every other call is keyed on it. */
    public val vid: String?,
    public val profile: VxDeviceProfile,
    public val appConfig: VxAppConfig,
    public val support: VxSupportInfo,
    public val social: VxSocialInfo,
    /** The app's store id from the panel (the package name for an Android app). */
    public val storeAppId: String?,
) {
    override fun toString(): String =
        "VxDeviceInfo(vid=$vid, premium=${profile.isPremium}, balance=${profile.balance}, banned=${profile.isBanned})"
}

public class VxDeviceProfile internal constructor(
    public val isPremium: Boolean,
    public val isBanned: Boolean,
    public val userType: String?,
    public val hasPushToken: Boolean,
    public val balance: Int,
    public val retentionGiftClaimed: Boolean,
    public val retentionGiftAmount: Int,
)

public class VxAppConfig internal constructor(
    /** The version the panel requires when [forceUpdate] is on. */
    public val storeVersion: String?,
    public val forceUpdate: Boolean,
    public val localizationUrl: String?,
    public val supportEmail: String?,
    public val supportedLanguages: List<String>,
    public val eulaUrl: String?,
    public val privacyUrl: String?,
)

public class VxSupportInfo internal constructor(
    /** A support agent answered a ticket the device has not seen yet. */
    public val hasUnseenResponse: Boolean,
    public val categories: List<String>,
)

/** The social account linked to this device through `device/social-login`, if any. */
public class VxSocialInfo internal constructor(
    public val isLinked: Boolean,
    public val provider: String?,
    public val id: String?,
    public val name: String?,
    public val email: String?,
    public val photoUrl: String?,
)
