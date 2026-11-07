package com.vxhub.internal.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.vxhub.VxHubException
import com.vxhub.internal.network.AuthResponseDto
import com.vxhub.internal.network.UserDto
import com.vxhub.internal.network.VxHttpClient
import com.vxhub.internal.storage.SecretCipher
import com.vxhub.internal.storage.VxPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** The guarantees of iOS's VxAuthSession, on Android. */
class VxAuthManagerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val expirations = AtomicInteger()
    private lateinit var manager: VxAuthManager
    private lateinit var prefs: VxPreferences

    private object PlainCipher : SecretCipher {
        override fun encrypt(plain: String): String = plain
        override fun decrypt(encoded: String): String = encoded
    }

    @Before
    fun setUp() {
        server.start()
        val http = VxHttpClient(OkHttpClient(), server.url("/api/v1/")) { authenticated ->
            if (authenticated) manager.accessToken?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty() else emptyMap()
        }
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "test.preferences_pb") }
        prefs = VxPreferences(dataStore, scope)
        manager = VxAuthManager(VxAuthApi(http), prefs, PlainCipher, scope, onSessionExpired = { expirations.incrementAndGet() })
    }

    @After
    fun tearDown() {
        server.close()
        scope.cancel()
    }

    private fun authBody(access: String, refresh: String, expiresIn: Long = 3600) =
        """{"status":"success","access_token":"$access","refresh_token":"$refresh","expires_in":$expiresIn,""" +
            """"user":{"id":"u1","email":"a@b.c","email_verified":true,"providers":["password"]}}"""

    private suspend fun signIn(expiresIn: Long) {
        manager.adopt(AuthResponseDto(accessToken = "old", refreshToken = "r0", expiresIn = expiresIn, user = UserDto(id = "u1")))
    }

    @Test
    fun concurrentCallersShareOneRefresh() = runBlocking<Unit> {
        signIn(expiresIn = 0)
        server.enqueue(MockResponse.Builder().code(200).body(authBody("new", "r1")).headersDelay(300, java.util.concurrent.TimeUnit.MILLISECONDS).build())
        (1..5).map { async(Dispatchers.IO) { manager.ensureFreshToken() } }.awaitAll()
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(manager.accessToken).isEqualTo("new")
        assertThat(server.takeRequest().body?.utf8()).contains("\"refresh_token\":\"r0\"")
    }

    @Test
    fun a401IsRefreshedOnceAndRetried() = runBlocking<Unit> {
        signIn(expiresIn = 3600)
        server.enqueue(MockResponse.Builder().code(401).body("""{"message":"TOKEN_EXPIRED"}""").build())
        server.enqueue(MockResponse.Builder().code(200).body(authBody("new", "r1")).build())
        server.enqueue(MockResponse.Builder().code(200).body("""{"user":{"id":"u1","email":"a@b.c"}}""").build())

        val user = manager.refreshCurrentUser()

        assertThat(user.id).isEqualTo("u1")
        val first = server.takeRequest()
        val refresh = server.takeRequest()
        val retry = server.takeRequest()
        assertThat(first.target).endsWith("/app-auth/me")
        assertThat(first.headers["Authorization"]).isEqualTo("Bearer old")
        assertThat(refresh.target).endsWith("/app-auth/refresh")
        assertThat(retry.headers["Authorization"]).isEqualTo("Bearer new")
    }

    @Test
    fun aRejectedRefreshEndsTheSession() = runBlocking<Unit> {
        signIn(expiresIn = 0)
        server.enqueue(MockResponse.Builder().code(401).body("""{"message":"REFRESH_TOKEN_REUSED"}""").build())

        val error = runCatching { manager.ensureFreshToken() }.exceptionOrNull()

        assertThat(error).isInstanceOf(VxHubException.SessionExpired::class.java)
        assertThat(manager.isAuthenticated.value).isFalse()
        assertThat(manager.accessToken).isNull()
        assertThat(prefs[VxPreferences.Keys.AUTH_SESSION]).isNull()
        assertThat(expirations.get()).isEqualTo(1)
    }

    @Test
    fun beingOfflineDoesNotSignAnyoneOut() = runBlocking<Unit> {
        signIn(expiresIn = 0)
        repeat(3) { server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build()) }

        val error = runCatching { manager.ensureFreshToken() }.exceptionOrNull()

        assertThat(error).isInstanceOf(VxHubException.NetworkUnavailable::class.java)
        assertThat(manager.isAuthenticated.value).isTrue()
        assertThat(expirations.get()).isEqualTo(0)
    }

    @Test
    fun theSessionSurvivesARestart() = runBlocking<Unit> {
        signIn(expiresIn = 3600)
        val restored = VxAuthManager(VxAuthApi(VxHttpClient(OkHttpClient(), server.url("/")) { emptyMap() }), prefs, PlainCipher, scope, {})
        restored.restoreStoredSession()
        assertThat(restored.accessToken).isEqualTo("old")
        assertThat(restored.isAuthenticated.value).isTrue()
    }

    @Test
    fun signingOutClearsLocallyEvenWhenTheCallFails() = runBlocking<Unit> {
        signIn(expiresIn = 3600)
        server.enqueue(MockResponse.Builder().code(500).body("{}").build())
        manager.signOut(allDevices = false)
        assertThat(manager.isAuthenticated.value).isFalse()
        assertThat(server.takeRequest().body?.utf8()).contains("\"refresh_token\":\"r0\"")
    }

    @Test
    fun signingOutEverywhereUsesLogoutAll() = runBlocking<Unit> {
        signIn(expiresIn = 3600)
        server.enqueue(MockResponse.Builder().code(200).body("""{"success":true,"revoked_count":3}""").build())
        manager.signOut(allDevices = true)
        val request = server.takeRequest()
        assertThat(request.target).endsWith("/app-auth/logout-all")
        assertThat(request.headers["Authorization"]).isEqualTo("Bearer old")
    }

    @Test
    fun apiErrorCodesReachTheCaller() = runBlocking<Unit> {
        server.enqueue(MockResponse.Builder().code(400).body("""{"message":["PASSWORD_TOO_SHORT:10"],"error":"Bad Request"}""").build())
        val error = runCatching { manager.signUp("a@b.c", "short", null) }.exceptionOrNull()
        assertThat(error).isInstanceOf(VxHubException.AuthFailed::class.java)
        assertThat((error as VxHubException.AuthFailed).code).isEqualTo("PASSWORD_TOO_SHORT:10")
        assertThat(error.statusCode).isEqualTo(400)
    }
}
