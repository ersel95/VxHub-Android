package com.vxhub.internal.network

import com.google.common.truth.Truth.assertThat
import com.vxhub.VxRemoteConfig
import com.vxhub.internal.toDeviceInfo
import org.junit.Test

/** The register response exactly as the backend's buildDeviceResponse shapes it. */
class DeviceRegisterDecodingTest {
    private val response = """
        {"status":"success","message":"Device handled successfully","vid":"8a1d6f0e-3c2b-4d5e-9f10-111213141516",
         "social":{"status":false,"provider":null,"id":null,"name":null,"email":null,"photo":null},
         "device":{"premium_status":false,"ban_status":false,"user_type":"regular","one_signal_status":true,
                   "balance":"150","retention_gift_claimed":false,"retention_gift_amount":5},
         "config":{"store_version":"1.2.0","force_update":false,"localization_url":"https://cdn.example.com/en.json",
                   "support_email":"help@example.com","supported_languages":["en","tr"],
                   "tos_url":"https://example.com/tos","privacy_policy_url":"https://example.com/privacy"},
         "third_party":{"revenue_cat_api_key":"goog_abc","appsflyer_dev_key":null,"appsflyer_app_id":null,
                   "one_signal_api_key":"os-app","amplitude_api_key":"amp","facebook_app_id":null,
                   "facebook_client_token":null,"facebook_display_name":null,"firebase_plist_url":"https://cdn/gs.json",
                   "amplitude_deployment_key":null,"app_store_app_id":"com.example.app","sentry_dsn":null,
                   "google_client_id":"web.apps.googleusercontent.com","some_future_key":"ignored"},
         "remote_config":{"paywall_variant":"v4","show_ads":"true","max_items":"3","ratio":"0.25","layout":"{\"a\":1}"},
         "support":{"unseen_response":true,"categories":["bug","billing"]}}
    """.trimIndent()

    @Test
    fun everyFieldMapsOntoThePublicModel() {
        val dto = VxJson.decodeFromString(DeviceRegisterResponseDto.serializer(), response)
        val info = dto.toDeviceInfo()
        assertThat(info.vid).isEqualTo("8a1d6f0e-3c2b-4d5e-9f10-111213141516")
        assertThat(info.profile.balance).isEqualTo(150)
        assertThat(info.profile.hasPushToken).isTrue()
        assertThat(info.profile.retentionGiftAmount).isEqualTo(5)
        assertThat(info.appConfig.supportedLanguages).containsExactly("en", "tr").inOrder()
        assertThat(info.appConfig.eulaUrl).isEqualTo("https://example.com/tos")
        assertThat(info.support.hasUnseenResponse).isTrue()
        assertThat(info.social.isLinked).isFalse()
        assertThat(info.storeAppId).isEqualTo("com.example.app")
        assertThat(dto.thirdParty?.oneSignalAppId).isEqualTo("os-app")
        assertThat(dto.thirdParty?.firebaseConfigUrl).isEqualTo("https://cdn/gs.json")
    }

    @Test
    fun remoteConfigValuesParseFromText() {
        val dto = VxJson.decodeFromString(DeviceRegisterResponseDto.serializer(), response)
        val config = VxRemoteConfig(dto.remoteConfig!!)
        assertThat(config.getString("paywall_variant")).isEqualTo("v4")
        assertThat(config.getBoolean("show_ads")).isTrue()
        assertThat(config.getInt("max_items")).isEqualTo(3)
        assertThat(config.getDouble("ratio")).isEqualTo(0.25)
        assertThat(config.getJson("layout")).isEqualTo("{\"a\":1}")
        assertThat(config.getBoolean("missing", default = true)).isTrue()
        assertThat(config.asMap()).containsEntry("max_items", "3")
    }

    @Test
    fun aMinimalBodyStillDecodes() {
        val dto = VxJson.decodeFromString(DeviceRegisterResponseDto.serializer(), """{"vid":"x"}""")
        val info = dto.toDeviceInfo()
        assertThat(info.profile.isPremium).isFalse()
        assertThat(info.appConfig.supportedLanguages).isEmpty()
    }

    @Test
    fun theRegisterRequestUsesTheBackendsFieldNames() {
        val json = VxJson.encodeToString(
            DeviceRegisterRequest.serializer(),
            DeviceRegisterRequest(
                deviceType = "phone", deviceBrand = "Google", deviceModel = "Pixel9", countryCode = "TR",
                languageCode = "tr", opRegion = "TR", carrierRegion = "TR", os = "16", resolution = "1080x2400",
                dpi = "420", appVersion = "1.0.0", installedApps = mapOf("whatsapp" to true),
            ),
        )
        listOf(
            "\"device_platform\":\"android\"", "\"user_type\":\"regular\"", "\"country_code\":\"TR\"",
            "\"language_code\":\"tr\"", "\"app_version\":\"1.0.0\"", "\"installed_apps\":{\"whatsapp\":true}",
        ).forEach { assertThat(json).contains(it) }
        assertThat(json).doesNotContain("firebase_id")
    }
}
