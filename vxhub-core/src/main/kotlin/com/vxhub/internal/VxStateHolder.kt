package com.vxhub.internal

import com.vxhub.VxAppConfig
import com.vxhub.VxConnectionType
import com.vxhub.VxDeviceInfo
import com.vxhub.VxDeviceProfile
import com.vxhub.VxHubState
import com.vxhub.VxRemoteConfig
import com.vxhub.VxSocialInfo
import com.vxhub.VxSupportInfo
import com.vxhub.internal.network.DeviceRegisterResponseDto
import com.vxhub.internal.network.ThirdPartyDto
import com.vxhub.purchases.VxProduct
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject

/**
 * Everything observable about VxHub, as StateFlows: thread-safe to read from anywhere, and
 * collectable from Compose. The counterpart of the iOS SDK's lock-guarded properties plus its
 * `vxHubStateDidChange` notification.
 */
internal class VxStateHolder {
    val state = MutableStateFlow<VxHubState>(VxHubState.NotInitialized)
    val isPremium = MutableStateFlow(false)
    val balance = MutableStateFlow(0)
    val products = MutableStateFlow<List<VxProduct>>(emptyList())
    val deviceInfo = MutableStateFlow<VxDeviceInfo?>(null)
    val remoteConfig = MutableStateFlow(VxRemoteConfig.EMPTY)
    val isConnected = MutableStateFlow(true)
    val connectionType = MutableStateFlow(VxConnectionType.OTHER)

    /** Third-party keys stay internal: satellites receive them through their provider calls. */
    @Volatile
    var thirdParty: ThirdPartyDto = ThirdPartyDto()

    /** Applies a registration (or social-login) response, as iOS's `configureRegisterResponse`. */
    fun apply(response: DeviceRegisterResponseDto) {
        thirdParty = response.thirdParty ?: ThirdPartyDto()
        val info = response.toDeviceInfo()
        deviceInfo.value = info
        remoteConfig.value = VxRemoteConfig(response.remoteConfig ?: JsonObject(emptyMap()))
        isPremium.value = info.profile.isPremium
        balance.value = info.profile.balance
    }

    val vid: String? get() = deviceInfo.value?.vid
}

internal fun DeviceRegisterResponseDto.toDeviceInfo(): VxDeviceInfo = VxDeviceInfo(
    vid = vid,
    profile = VxDeviceProfile(
        isPremium = device?.premiumStatus == true,
        isBanned = device?.banStatus == true,
        userType = device?.userType,
        hasPushToken = device?.oneSignalStatus == true,
        balance = device?.balance ?: 0,
        retentionGiftClaimed = device?.retentionGiftClaimed == true,
        retentionGiftAmount = device?.retentionGiftAmount ?: 0,
    ),
    appConfig = VxAppConfig(
        storeVersion = config?.storeVersion,
        forceUpdate = config?.forceUpdate == true,
        localizationUrl = config?.localizationUrl,
        supportEmail = config?.supportEmail,
        supportedLanguages = config?.supportedLanguages.orEmpty(),
        eulaUrl = config?.eulaUrl,
        privacyUrl = config?.privacyUrl,
    ),
    support = VxSupportInfo(
        hasUnseenResponse = support?.unseenResponse == true,
        categories = support?.categories.orEmpty(),
    ),
    social = VxSocialInfo(
        isLinked = social?.status == true,
        provider = social?.provider,
        id = social?.id,
        name = social?.name,
        email = social?.email,
        photoUrl = social?.photo,
    ),
    storeAppId = thirdParty?.storeAppId,
)
