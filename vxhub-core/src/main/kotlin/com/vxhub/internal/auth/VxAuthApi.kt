package com.vxhub.internal.auth

import com.vxhub.VxHubException
import com.vxhub.internal.VxLog
import com.vxhub.internal.network.AuthConfigDto
import com.vxhub.internal.network.AuthResponseDto
import com.vxhub.internal.network.AuthSessionsDto
import com.vxhub.internal.network.ChangePasswordRequest
import com.vxhub.internal.network.CodeRequest
import com.vxhub.internal.network.EmailRequest
import com.vxhub.internal.network.HttpMethod
import com.vxhub.internal.network.HttpResult
import com.vxhub.internal.network.LoginRequest
import com.vxhub.internal.network.LogoutRequest
import com.vxhub.internal.network.RefreshRequest
import com.vxhub.internal.network.RegisterRequest
import com.vxhub.internal.network.ResetPasswordRequest
import com.vxhub.internal.network.SocialLoginRequest
import com.vxhub.internal.network.SuccessDto
import com.vxhub.internal.network.UpdateProfileRequest
import com.vxhub.internal.network.UserDto
import com.vxhub.internal.network.UserResponseDto
import com.vxhub.internal.network.VxEndpoint
import com.vxhub.internal.network.VxHttpClient
import com.vxhub.internal.network.VxJson
import com.vxhub.internal.network.encode
import com.vxhub.internal.network.errorBody
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import java.io.IOException

/**
 * `app-auth/` calls. The only requests that carry a bearer token, which is why refresh handling
 * lives here rather than in the shared transport.
 */
internal class VxAuthApi(private val http: VxHttpClient) {

    /** Wired by [VxAuthManager]; used for the refresh-once-on-401 retry. */
    lateinit var session: AuthSessionHooks

    suspend fun config(): AuthConfigDto = send(get("app-auth/config"), AuthConfigDto.serializer(), authenticated = false)

    suspend fun register(email: String, password: String, name: String?): AuthResponseDto =
        send(
            post("app-auth/register", encode(RegisterRequest.serializer(), RegisterRequest(email, password, name?.takeIf { it.isNotBlank() }))),
            AuthResponseDto.serializer(),
            authenticated = false,
        )

    suspend fun login(email: String, password: String): AuthResponseDto =
        send(post("app-auth/login", encode(LoginRequest.serializer(), LoginRequest(email, password))), AuthResponseDto.serializer(), false)

    suspend fun socialLogin(request: SocialLoginRequest): AuthResponseDto =
        send(post("app-auth/social-login", encode(SocialLoginRequest.serializer(), request)), AuthResponseDto.serializer(), false)

    suspend fun refresh(refreshToken: String): AuthResponseDto =
        send(post("app-auth/refresh", encode(RefreshRequest.serializer(), RefreshRequest(refreshToken))), AuthResponseDto.serializer(), false)

    suspend fun logout(refreshToken: String?) {
        send(post("app-auth/logout", encode(LogoutRequest.serializer(), LogoutRequest(refreshToken))), SuccessDto.serializer(), false)
    }

    /** Revokes every session of the account. Needs a valid access token. */
    suspend fun logoutAll() {
        send(post("app-auth/logout-all", null, authenticated = true), SuccessDto.serializer(), true)
    }

    suspend fun forgotPassword(email: String) {
        send(post("app-auth/forgot-password", encode(EmailRequest.serializer(), EmailRequest(email))), SuccessDto.serializer(), false)
    }

    suspend fun resetPassword(email: String, code: String, newPassword: String) {
        send(
            post("app-auth/reset-password", encode(ResetPasswordRequest.serializer(), ResetPasswordRequest(email, code, newPassword))),
            SuccessDto.serializer(),
            false,
        )
    }

    suspend fun verifyEmail(code: String) {
        send(post("app-auth/verify-email", encode(CodeRequest.serializer(), CodeRequest(code)), true), SuccessDto.serializer(), true)
    }

    suspend fun resendVerification() {
        send(post("app-auth/resend-verification", null, true), SuccessDto.serializer(), true)
    }

    suspend fun changePassword(current: String, new: String) {
        send(
            post("app-auth/change-password", encode(ChangePasswordRequest.serializer(), ChangePasswordRequest(current, new)), true),
            SuccessDto.serializer(),
            true,
        )
    }

    suspend fun me(): UserDto = send(get("app-auth/me", true), UserResponseDto.serializer(), true).user

    suspend fun updateProfile(name: String?, profilePicture: String?): UserDto =
        send(
            VxEndpoint(
                HttpMethod.PATCH,
                "app-auth/me",
                encode(UpdateProfileRequest.serializer(), UpdateProfileRequest(name, profilePicture)),
                authenticated = true,
            ),
            UserResponseDto.serializer(),
            true,
        ).user

    suspend fun deleteAccount() {
        send(VxEndpoint(HttpMethod.DELETE, "app-auth/me", authenticated = true), SuccessDto.serializer(), true)
    }

    suspend fun sessions(): AuthSessionsDto = send(get("app-auth/sessions", true), AuthSessionsDto.serializer(), true)

    // region Transport

    /**
     * Sends a request and decodes it, refreshing the access token once if the server rejects it.
     *
     * A 401 on an authenticated call is how an expired token normally shows up; refreshing and
     * retrying once keeps that invisible. A second 401 on a token minted seconds ago means the
     * session itself is gone, and the caller gets [VxHubException.SessionExpired].
     */
    private suspend fun <T> send(endpoint: VxEndpoint, deserializer: DeserializationStrategy<T>, authenticated: Boolean): T {
        if (authenticated) session.ensureFreshToken()
        try {
            return perform(endpoint, deserializer)
        } catch (e: VxHubException.RequestFailed) {
            if (!authenticated || e.statusCode != 401) throw e
        }
        if (!session.refreshNow()) throw VxHubException.SessionExpired()
        try {
            return perform(endpoint, deserializer)
        } catch (e: VxHubException.RequestFailed) {
            if (e.statusCode == 401) {
                session.sessionRejected()
                throw VxHubException.SessionExpired()
            }
            throw e
        }
    }

    private suspend fun <T> perform(endpoint: VxEndpoint, deserializer: DeserializationStrategy<T>): T {
        val result: HttpResult = try {
            http.execute(endpoint)
        } catch (e: IOException) {
            // A dropped tunnel is not an auth problem; "check your connection" sends people to their
            // Wi-Fi settings instead of to support.
            VxLog.w("Auth request transport failure: ${e.javaClass.simpleName}")
            throw VxHubException.NetworkUnavailable(e)
        }
        if (!result.isSuccessful) {
            if (result.code == 401) throw VxHubException.RequestFailed(401)
            val error = result.errorBody()
            val code = error?.messages?.firstOrNull() ?: error?.error ?: "REQUEST_FAILED"
            throw VxHubException.AuthFailed(code, result.code)
        }
        if (result.body.isBlank()) throw VxHubException.NoData()
        return try {
            VxJson.decodeFromString(deserializer, result.body)
        } catch (e: SerializationException) {
            VxLog.e("Could not decode ${endpoint.path}: ${e.message}")
            throw VxHubException.DecodingFailed(e)
        } catch (e: IllegalArgumentException) {
            throw VxHubException.DecodingFailed(e)
        }
    }

    private fun get(path: String, authenticated: Boolean = false) = VxEndpoint(HttpMethod.GET, path, authenticated = authenticated)

    private fun post(path: String, body: String?, authenticated: Boolean = false) =
        VxEndpoint(HttpMethod.POST, path, body, authenticated)

    // endregion
}

/** What the transport needs from the session holder. */
internal interface AuthSessionHooks {
    suspend fun ensureFreshToken()
    suspend fun refreshNow(): Boolean
    suspend fun sessionRejected()
}
