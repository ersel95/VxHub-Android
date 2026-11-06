package com.vxhub.internal.auth

import com.vxhub.VxAuthConfig
import com.vxhub.VxAuthDeviceSession
import com.vxhub.VxHubException
import com.vxhub.VxUser
import com.vxhub.internal.VxLog
import com.vxhub.internal.network.AuthConfigDto
import com.vxhub.internal.network.AuthResponseDto
import com.vxhub.internal.network.SocialLoginRequest
import com.vxhub.internal.network.UserDto
import com.vxhub.internal.network.VxJson
import com.vxhub.internal.storage.SecretCipher
import com.vxhub.internal.storage.VxPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/** Tokens of the signed-in account. Persisted encrypted (see [SecretCipher]). */
@Serializable
internal data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMs: Long,
)

/**
 * Holds the signed-in session and keeps its access token usable.
 *
 * Refreshing must happen once: several screens can hit a 401 at the same moment, and since the
 * server rotates refresh tokens, a second refresh with the same token looks stolen and ends every
 * session. So concurrent callers share one in-flight attempt ([refreshNow]), the job of iOS's
 * `VxAuthSession` actor.
 */
internal class VxAuthManager(
    private val api: VxAuthApi,
    private val prefs: VxPreferences,
    private val cipher: SecretCipher,
    private val scope: CoroutineScope,
    private val onSessionExpired: () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) : AuthSessionHooks {

    private val mutex = Mutex()
    private var refreshInFlight: Deferred<Boolean>? = null
    private var refreshGeneration = 0L

    @Volatile
    private var session: StoredSession? = null

    private val _user = MutableStateFlow<VxUser?>(null)
    val user: StateFlow<VxUser?> = _user.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _config = MutableStateFlow<VxAuthConfig?>(null)
    val config: StateFlow<VxAuthConfig?> = _config.asStateFlow()

    /** Bearer token for request headers. */
    val accessToken: String? get() = session?.accessToken

    init {
        api.session = this
    }

    /** Reads the stored session from disk. Called once the preferences are loaded. */
    fun restoreStoredSession() {
        val stored = prefs[VxPreferences.Keys.AUTH_SESSION]
            ?.let { cipher.decrypt(it) }
            ?.let { runCatching { VxJson.decodeFromString(StoredSession.serializer(), it) }.getOrNull() }
        if (stored == null && prefs[VxPreferences.Keys.AUTH_SESSION] != null) {
            // Undecryptable: the Keystore key is gone (reinstall, restore onto another device).
            prefs.editAsync { it.remove(VxPreferences.Keys.AUTH_SESSION) }
        }
        session = stored
        _isAuthenticated.value = stored != null
    }

    /**
     * Fetches the panel's auth config and, for a stored session, the current profile. A stored
     * session is trusted until the API says otherwise, so the app can render its signed-in state
     * immediately.
     */
    suspend fun refreshStateAfterInitialization() {
        runCatching { api.config() }
            .onSuccess { _config.value = it.toPublic() }
            .onFailure { VxLog.d { "Auth config unavailable: ${it.message}" } }
        if (session == null) return
        try {
            _user.value = api.me().toPublic()
        } catch (e: VxHubException) {
            VxLog.w("Stored session could not be restored: ${e.message}")
        }
    }

    // region Session lifecycle

    suspend fun adopt(response: AuthResponseDto): VxUser {
        val user = response.user.toPublic()
        store(
            StoredSession(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                expiresAtEpochMs = clock() + response.expiresIn * 1_000,
            ),
            user,
        )
        return user
    }

    private suspend fun store(newSession: StoredSession, user: VxUser?) {
        // Memory first: request headers read `accessToken`, and a refresh that only reached disk
        // would keep sending the old token into another 401.
        session = newSession
        if (user != null) _user.value = user
        _isAuthenticated.value = true
        val encrypted = cipher.encrypt(VxJson.encodeToString(StoredSession.serializer(), newSession))
        prefs.edit {
            if (encrypted != null) it[VxPreferences.Keys.AUTH_SESSION] = encrypted else it.remove(VxPreferences.Keys.AUTH_SESSION)
        }
    }

    fun updateUser(user: VxUser) {
        _user.value = user
    }

    suspend fun clear() {
        mutex.withLock {
            // Not cancelled: clear() can run inside that very refresh (a rejected token). Bumping
            // the generation is enough — the attempt checks it before adopting anything.
            refreshInFlight = null
            refreshGeneration++
        }
        session = null
        _user.value = null
        _isAuthenticated.value = false
        prefs.edit { it.remove(VxPreferences.Keys.AUTH_SESSION) }
    }

    val refreshTokenForLogout: String? get() = session?.refreshToken

    // endregion

    // region Refresh (AuthSessionHooks)

    override suspend fun ensureFreshToken() {
        val current = session ?: throw VxHubException.NotSignedIn()
        // Refreshed a little early, so a call does not fail on a token that dies in flight.
        if (current.expiresAtEpochMs - clock() > REFRESH_MARGIN_MS) return
        if (!refreshNow()) throw VxHubException.SessionExpired()
    }

    override suspend fun refreshNow(): Boolean {
        val attempt = mutex.withLock {
            refreshInFlight ?: run {
                val refreshToken = session?.refreshToken ?: return false
                val generation = ++refreshGeneration
                // In VxHub's scope, not the caller's: one caller giving up must not cancel the
                // refresh everyone else is waiting on.
                scope.async { performRefresh(refreshToken, generation) }.also { refreshInFlight = it }
            }
        }
        return attempt.await()
    }

    private suspend fun performRefresh(refreshToken: String, generation: Long): Boolean {
        try {
            val response = api.refresh(refreshToken)
            mutex.withLock { if (refreshGeneration != generation) return false }
            adopt(response)
            return true
        } catch (e: VxHubException.NetworkUnavailable) {
            // Offline is not a rejection: keep the session and try again on the next call. (iOS
            // signs the person out here, which logs everyone out on a flaky connection.)
            VxLog.w("Session refresh deferred: network unavailable")
            return false
        } catch (e: VxHubException) {
            VxLog.w("Session refresh rejected, signing out: ${e.message}")
            if (mutex.withLock { refreshGeneration == generation }) sessionRejected()
            return false
        } finally {
            mutex.withLock { if (refreshGeneration == generation) refreshInFlight = null }
        }
    }

    override suspend fun sessionRejected() {
        val hadSession = session != null
        clear()
        if (hadSession) onSessionExpired()
    }

    // endregion

    // region Account API

    suspend fun signUp(email: String, password: String, name: String?): VxUser = adopt(api.register(email.trim(), password, name))

    suspend fun signIn(email: String, password: String): VxUser = adopt(api.login(email.trim(), password))

    suspend fun forgotPassword(email: String) = api.forgotPassword(email.trim())

    suspend fun resetPassword(email: String, code: String, newPassword: String) =
        api.resetPassword(email.trim(), code.trim(), newPassword)

    suspend fun verifyEmail(code: String) {
        api.verifyEmail(code.trim())
        runCatching { refreshCurrentUser() }
    }

    suspend fun resendVerificationCode() = api.resendVerification()

    suspend fun changePassword(current: String, new: String) {
        api.changePassword(current, new)
        runCatching { refreshCurrentUser() }
    }

    suspend fun updateProfile(name: String?, profilePicture: String?): VxUser =
        api.updateProfile(name, profilePicture).toPublic().also { _user.value = it }

    suspend fun refreshCurrentUser(): VxUser = api.me().toPublic().also { _user.value = it }

    suspend fun listSessions(): List<VxAuthDeviceSession> = api.sessions().sessions.map {
        VxAuthDeviceSession(it.id, it.platform, it.userAgent, it.createdAt, it.lastUsedAt, it.expiresAt)
    }

    /** The local session is cleared whatever the server says: a failed call must not keep someone signed in. */
    suspend fun signOut(allDevices: Boolean) {
        try {
            if (allDevices) api.logoutAll() else api.logout(refreshTokenForLogout)
        } catch (e: VxHubException) {
            VxLog.w("Sign-out call failed, clearing locally anyway: ${e.message}")
        }
        clear()
    }

    suspend fun deleteAccount() {
        api.deleteAccount()
        clear()
    }

    /**
     * Routes a verified provider token to the account endpoint when the panel enabled accounts for
     * that provider. Returns true when an account session was established; false means the caller
     * continues with the device-level flow, which is what keeps apps without accounts working.
     */
    suspend fun completeSocialSignIn(request: SocialLoginRequest): Boolean {
        val cfg = _config.value
        val enabledForProvider = when (request.provider) {
            "google" -> cfg?.isGoogleEnabled
            "apple" -> cfg?.isAppleEnabled
            else -> false
        }
        if (cfg?.isAuthEnabled != true || enabledForProvider != true) return false
        return try {
            adopt(api.socialLogin(request))
            true
        } catch (e: VxHubException) {
            VxLog.w("Account social sign-in failed, falling back to the device flow: ${e.message}")
            false
        }
    }

    // endregion

    private companion object {
        const val REFRESH_MARGIN_MS = 30_000L
    }
}

internal fun UserDto.toPublic(): VxUser = VxUser(
    id = id,
    email = email,
    isEmailVerified = emailVerified,
    name = name,
    profilePicture = profilePicture,
    providers = providers,
    isPremium = premiumStatus,
    premiumEndDate = premiumEndDate,
    balance = balance,
    mustChangePassword = mustChangePassword,
)

internal fun AuthConfigDto.toPublic(): VxAuthConfig = VxAuthConfig(
    isAuthEnabled = authEnabled,
    isPasswordEnabled = passwordEnabled,
    isGoogleEnabled = googleEnabled,
    isAppleEnabled = appleEnabled,
    requiresEmailVerification = requireEmailVerification,
    minPasswordLength = minPasswordLength,
    requiresPasswordComplexity = requirePasswordComplexity,
    googleClientId = googleClientId,
)
