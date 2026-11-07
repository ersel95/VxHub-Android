package com.vxhub

/**
 * Every failure a VxHub `suspend` function throws. Branch on the subtype, never on [message].
 *
 * Mirrors the iOS SDK's `VxHubError`, case for case.
 */
public sealed class VxHubException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** No usable connection: the request never got an HTTP answer. */
    public class NetworkUnavailable(cause: Throwable? = null) :
        VxHubException("Network is unavailable. Please check your connection.", cause)

    /** The server answered with a non-2xx status. */
    public class RequestFailed(public val statusCode: Int) :
        VxHubException("Request failed with status code: $statusCode")

    /** A 2xx answer arrived with an empty body where one was required. */
    public class NoData : VxHubException("Response returned with no data.")

    /** The body could not be read as the expected shape. */
    public class DecodingFailed(cause: Throwable) :
        VxHubException("Failed to decode response: ${cause.message}", cause)

    public class InvalidUrl(public val url: String) : VxHubException("Invalid URL: $url")

    public class PurchaseFailed(public val reason: String?) :
        VxHubException("Purchase failed: ${reason ?: "Unknown reason"}")

    public class SignInFailed(public val provider: String, public val reason: String, cause: Throwable? = null) :
        VxHubException("Sign in with $provider failed: $reason", cause)

    /** The person dismissed the sign-in sheet. Not an error to show. */
    public class SignInCancelled(public val provider: String) : VxHubException("Sign in with $provider was cancelled")

    public class PromoCodeInvalid(public val messages: List<String>) :
        VxHubException("Promo code invalid: ${messages.joinToString()}")

    /** The call needs a signed-in account and there is none. */
    public class NotSignedIn : VxHubException("No account is signed in.")

    /** The refresh token is spent, revoked or expired — the person has to sign in again. */
    public class SessionExpired : VxHubException("The session has expired. Please sign in again.")

    /**
     * The account API refused the request with a machine-readable [code] such as
     * `INVALID_CREDENTIALS`, `EMAIL_ALREADY_REGISTERED`, `PASSWORD_TOO_SHORT:10`, `INVALID_CODE`
     * or `EMAIL_NOT_VERIFIED`. Branch on [code] rather than on the message.
     */
    public class AuthFailed(public val code: String, public val statusCode: Int) :
        VxHubException("Authentication failed ($statusCode): $code")

    /** VxHub was used before [VxHub.initialize] or before the device was registered. */
    public class NotInitialized(detail: String) : VxHubException(detail)

    /** The feature needs a satellite module (for example `vxhub-revenuecat`) that the app does not link. */
    public class ModuleMissing(public val module: String) :
        VxHubException("This needs the $module module; add it to the app's dependencies.")

    public class Unknown(message: String, cause: Throwable? = null) : VxHubException(message, cause)
}
