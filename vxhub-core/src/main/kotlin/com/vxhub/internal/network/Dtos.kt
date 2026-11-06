@file:OptIn(ExperimentalSerializationApi::class)

package com.vxhub.internal.network

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Wire formats of the VxHub API. Internal on purpose: the public models in `com.vxhub` are mapped
 * from these, so a backend field rename never becomes a breaking change for apps.
 *
 * Every field the backend may omit is nullable with a default — one missing key must not fail a
 * whole device registration.
 */
internal val VxJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
    encodeDefaults = true
}

// region Device registration

@Serializable
internal data class DeviceRegisterRequest(
    @SerialName("user_type") val userType: String = "regular",
    @SerialName("device_platform") val devicePlatform: String = "android",
    @SerialName("device_type") val deviceType: String,
    @SerialName("device_brand") val deviceBrand: String,
    @SerialName("device_model") val deviceModel: String,
    @SerialName("country_code") val countryCode: String,
    @SerialName("language_code") val languageCode: String,
    val idfa: String = "",
    @SerialName("appsflyer_id") val appsflyerId: String = "",
    @SerialName("firebase_id") val firebaseId: String? = null,
    @SerialName("op_region") val opRegion: String,
    @SerialName("carrier_region") val carrierRegion: String,
    val os: String,
    val resolution: String,
    val dpi: String,
    @SerialName("one_signal_token") val oneSignalToken: String = "",
    @SerialName("one_signal_player_id") val oneSignalPlayerId: String = "",
    @SerialName("app_version") val appVersion: String,
    @SerialName("startup_time_ms") val startupTimeMs: Long = 0,
    @SerialName("installed_apps") val installedApps: Map<String, Boolean> = emptyMap(),
)

@Serializable
internal data class DeviceRegisterResponseDto(
    val status: String? = null,
    val message: String? = null,
    val vid: String? = null,
    val device: DeviceProfileDto? = null,
    val config: AppConfigDto? = null,
    @SerialName("third_party") val thirdParty: ThirdPartyDto? = null,
    val support: SupportDto? = null,
    val social: SocialDto? = null,
    @SerialName("remote_config") val remoteConfig: JsonObject? = null,
)

@Serializable
internal data class DeviceProfileDto(
    @SerialName("premium_status") val premiumStatus: Boolean? = null,
    @SerialName("ban_status") val banStatus: Boolean? = null,
    @SerialName("user_type") val userType: String? = null,
    // The backend writes `one_signal_status`; iOS reads `onesignal_status` and so never sees it.
    @JsonNames("onesignal_status")
    @SerialName("one_signal_status") val oneSignalStatus: Boolean? = null,
    @Serializable(with = FlexibleIntSerializer::class) val balance: Int? = null,
    @SerialName("retention_gift_claimed") val retentionGiftClaimed: Boolean? = null,
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("retention_gift_amount") val retentionGiftAmount: Int? = null,
)

@Serializable
internal data class AppConfigDto(
    @SerialName("store_version") val storeVersion: String? = null,
    @SerialName("force_update") val forceUpdate: Boolean? = null,
    @SerialName("localization_url") val localizationUrl: String? = null,
    @SerialName("support_email") val supportEmail: String? = null,
    @SerialName("supported_languages") val supportedLanguages: List<String>? = null,
    @SerialName("tos_url") val eulaUrl: String? = null,
    @SerialName("privacy_policy_url") val privacyUrl: String? = null,
)

@Serializable
internal data class ThirdPartyDto(
    @SerialName("revenue_cat_api_key") val revenueCatApiKey: String? = null,
    @SerialName("appsflyer_dev_key") val appsflyerDevKey: String? = null,
    @SerialName("appsflyer_app_id") val appsflyerAppId: String? = null,
    // Named "api key" on the wire, but it carries the OneSignal *app id*.
    @SerialName("one_signal_api_key") val oneSignalAppId: String? = null,
    @SerialName("amplitude_api_key") val amplitudeApiKey: String? = null,
    @SerialName("amplitude_deployment_key") val amplitudeDeploymentKey: String? = null,
    // For an Android app in the panel this points at google-services.json.
    @SerialName("firebase_plist_url") val firebaseConfigUrl: String? = null,
    @SerialName("facebook_app_id") val facebookAppId: String? = null,
    @SerialName("facebook_client_token") val facebookClientToken: String? = null,
    @SerialName("facebook_display_name") val facebookDisplayName: String? = null,
    @SerialName("app_store_app_id") val storeAppId: String? = null,
    @SerialName("sentry_dsn") val sentryDsn: String? = null,
    @SerialName("google_client_id") val googleClientId: String? = null,
)

@Serializable
internal data class SupportDto(
    @SerialName("unseen_response") val unseenResponse: Boolean? = null,
    val categories: List<String>? = null,
)

@Serializable
internal data class SocialDto(
    val status: Boolean? = null,
    val provider: String? = null,
    val id: String? = null,
    val name: String? = null,
    val email: String? = null,
    val photo: String? = null,
)

// endregion

// region Products, purchases, coins

@Serializable
internal data class BackendProductDto(
    @SerialName("store_identifier") val storeIdentifier: String? = null,
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("initial_bonus") val initialBonus: Int? = null,
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("renewal_bonus") val renewalBonus: Int? = null,
    val type: String? = null,
)

@Serializable
internal data class PurchaseDataRequest(
    @SerialName("transaction_id") val transactionId: String,
    @SerialName("product_id") val productId: String,
)

/** Body of `device/after-purchase` and `device/restore`. All fields required, as on iOS. */
@Serializable
internal data class PurchaseVerificationDto(
    val status: String,
    val vid: String,
    val verified: Boolean? = null,
    val device: VerifiedDeviceDto,
)

@Serializable
internal data class VerifiedDeviceDto(
    @SerialName("premium_status") val premiumStatus: Boolean,
    @Serializable(with = FlexibleIntSerializer::class) val balance: Int?,
)

@Serializable
internal data class SpendCoinsRequest(
    val amount: Int,
    val reason: String,
    val parameters: Map<String, String>? = null,
)

@Serializable
internal data class SpendCoinsResponseDto(
    val status: String? = null,
    @Serializable(with = FlexibleIntSerializer::class) val balance: Int? = null,
    val premium: Boolean? = null,
)

@Serializable
internal data class SpendCoinsFailureDto(
    val errorCode: String? = null,
    @Serializable(with = FlexibleIntSerializer::class) val balance: Int? = null,
)

/** `device/retention/claim`. The backend answers `{success, amount}`; older builds `{status, gift_amount}`. */
@Serializable
internal data class RetentionClaimDto(
    val success: Boolean? = null,
    val status: String? = null,
    @JsonNames("gift_amount")
    @Serializable(with = FlexibleIntSerializer::class) val amount: Int? = null,
    val message: String? = null,
)

// endregion

// region Promo codes, QR login, account deletion

@Serializable
internal data class PromoCodeRequest(val code: String)

@Serializable
internal data class PromoCodeSuccessDto(
    val success: Boolean? = null,
    @SerialName("action_type") val actionType: String? = null,
    // A string on the wire today; read as an element so a number does not fail the whole body.
    @SerialName("action_meta") val actionMeta: JsonElement? = null,
    @SerialName("extra_data") val extraData: Map<String, String>? = null,
)

/** NestJS error body: `message` is a string or (from validation) a list of strings. */
@Serializable
internal data class ApiErrorDto(
    val message: JsonElement? = null,
    val error: String? = null,
    val statusCode: Int? = null,
) {
    val messages: List<String>
        get() = when (val m = message) {
            is JsonArray -> m.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            is JsonPrimitive -> listOfNotNull(m.contentOrNull)
            else -> emptyList()
        }
}

@Serializable
internal data class TokenRequest(val token: String)

@Serializable
internal data class SuccessDto(
    val success: Boolean? = null,
    @SerialName("email_verified") val emailVerified: Boolean? = null,
)

// endregion

// region Support

@Serializable
internal data class TicketDto(
    val id: String,
    val category: String? = null,
    val status: String? = null,
    val state: String? = null,
    val lastMessage: String? = null,
    val lastMessageCreatedAt: String? = null,
    val createdAt: String? = null,
    val isSeen: Boolean? = null,
)

@Serializable
internal data class CreateTicketRequest(val category: String, val message: String)

@Serializable
internal data class MessageRequest(val message: String)

@Serializable
internal data class TicketDetailDto(
    val id: String,
    val category: String? = null,
    val status: String? = null,
    val state: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val messages: List<TicketMessageDto> = emptyList(),
)

@Serializable
internal data class TicketMessageDto(
    val id: String,
    val message: String = "",
    val ticketId: String? = null,
    val userId: String? = null,
    val isFromDevice: Boolean = false,
    val deviceId: String? = null,
    val createdAt: String? = null,
)

@Serializable
internal data class UnseenStatusDto(
    val status: String? = null,
    @SerialName("unseen_response") val unseenResponse: Boolean? = null,
)

// endregion

// region Session analytics

@Serializable
internal data class SessionStartRequest(
    val sessionId: String,
    val appVersion: String,
    val isPremium: Boolean,
    val countryCode: String,
)

@Serializable
internal data class SessionEndRequest(val sessionId: String, val exitScreen: String)

@Serializable
internal data class SessionEventsRequest(val sessionId: String, val events: List<JsonObject>)

// endregion

// region Account authentication

@Serializable
internal data class AuthConfigDto(
    @SerialName("auth_enabled") val authEnabled: Boolean = false,
    @SerialName("password_enabled") val passwordEnabled: Boolean = false,
    @SerialName("google_enabled") val googleEnabled: Boolean = false,
    @SerialName("apple_enabled") val appleEnabled: Boolean = false,
    @SerialName("require_email_verification") val requireEmailVerification: Boolean = false,
    @SerialName("min_password_length") val minPasswordLength: Int = 8,
    @SerialName("require_password_complexity") val requirePasswordComplexity: Boolean = false,
    @SerialName("google_client_id") val googleClientId: String? = null,
)

@Serializable
internal data class UserDto(
    val id: String,
    val email: String? = null,
    @SerialName("email_verified") val emailVerified: Boolean = false,
    val name: String? = null,
    @SerialName("profile_picture") val profilePicture: String? = null,
    val providers: List<String> = emptyList(),
    @SerialName("premium_status") val premiumStatus: Boolean = false,
    @SerialName("premium_end_date") val premiumEndDate: String? = null,
    @Serializable(with = FlexibleIntSerializer::class) val balance: Int? = null,
    @SerialName("must_change_password") val mustChangePassword: Boolean = false,
)

@Serializable
internal data class AuthResponseDto(
    val status: String? = null,
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    val user: UserDto,
    val vid: String? = null,
)

@Serializable
internal data class UserResponseDto(val user: UserDto)

@Serializable
internal data class AuthSessionsDto(val sessions: List<AuthSessionDto> = emptyList())

@Serializable
internal data class AuthSessionDto(
    val id: String,
    val platform: String? = null,
    @SerialName("user_agent") val userAgent: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_used_at") val lastUsedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
internal data class RegisterRequest(val email: String, val password: String, val name: String? = null)

@Serializable
internal data class LoginRequest(val email: String, val password: String)

@Serializable
internal data class SocialLoginRequest(
    val provider: String,
    val token: String,
    @SerialName("account_id") val accountId: String? = null,
    val name: String? = null,
    val email: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
)

/** The device-level `device/social-login` body, which (unlike the account one) wants every key present. */
@Serializable
internal data class DeviceSocialLoginRequest(
    val provider: String,
    val token: String,
    @SerialName("account_id") val accountId: String,
    val name: String,
    val email: String,
)

@Serializable
internal data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
internal data class LogoutRequest(@SerialName("refresh_token") val refreshToken: String? = null)

@Serializable
internal data class EmailRequest(val email: String)

@Serializable
internal data class ResetPasswordRequest(
    val email: String,
    val code: String,
    @SerialName("new_password") val newPassword: String,
)

@Serializable
internal data class CodeRequest(val code: String)

@Serializable
internal data class ChangePasswordRequest(
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_password") val newPassword: String,
)

@Serializable
internal data class UpdateProfileRequest(
    val name: String? = null,
    @SerialName("profile_picture") val profilePicture: String? = null,
)

// endregion

/**
 * Reads an Int whether the backend sent `150`, `150.0` or `"150"`. Balances come from a numeric
 * database column, and TypeORM hands those to JSON as strings on some code paths.
 */
internal object FlexibleIntSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.vxhub.FlexibleInt", PrimitiveKind.INT).nullable

    override fun deserialize(decoder: Decoder): Int? {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return decoder.decodeInt()
        val primitive = element as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return primitive.contentOrNull?.toIntOrNull()
            ?: primitive.doubleOrNull?.toInt()
            ?: primitive.booleanOrNull?.let { if (it) 1 else 0 }
    }

    override fun serialize(encoder: Encoder, value: Int?) {
        if (value == null) encoder.encodeNull() else encoder.encodeInt(value)
    }
}
