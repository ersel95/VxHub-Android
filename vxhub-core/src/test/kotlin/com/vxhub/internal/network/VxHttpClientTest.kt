package com.vxhub.internal.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class VxHttpClientTest {
    private val server = MockWebServer()
    private lateinit var client: VxHttpClient
    private var token: String? = null

    @Before
    fun setUp() {
        server.start()
        client = VxHttpClient(
            OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build(),
            server.url("/api/v1/"),
        ) { authenticated ->
            buildMap {
                put("X-Hub-Id", "hub")
                put("X-Hub-Device-Id", "device")
                if (authenticated) token?.let { put("Authorization", "Bearer $it") }
            }
        }
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun sendsVxHubHeadersAndJson() = runBlocking<Unit> {
        server.enqueue(MockResponse.Builder().code(200).body("""{"ok":true}""").build())
        val result = client.execute(VxEndpoint(HttpMethod.POST, "device/register", """{"a":1}"""))
        assertThat(result.code).isEqualTo(200)
        val request = server.takeRequest()
        assertThat(request.target).isEqualTo("/api/v1/device/register")
        assertThat(request.headers["X-Hub-Id"]).isEqualTo("hub")
        assertThat(request.headers["X-Hub-Device-Id"]).isEqualTo("device")
        assertThat(request.headers["Content-Type"]).startsWith("application/json")
        assertThat(request.body?.utf8()).isEqualTo("""{"a":1}""")
    }

    @Test
    fun aPostWithoutBodySendsAnEmptyObject() = runBlocking<Unit> {
        server.enqueue(MockResponse.Builder().code(200).body("{}").build())
        client.execute(VxEndpoint(HttpMethod.POST, "device/restore"))
        assertThat(server.takeRequest().body?.utf8()).isEqualTo("{}")
    }

    @Test
    fun onlyAuthenticatedCallsCarryTheBearerToken() = runBlocking<Unit> {
        token = "secret"
        server.enqueue(MockResponse.Builder().code(200).body("{}").build())
        server.enqueue(MockResponse.Builder().code(200).body("{}").build())
        client.execute(VxEndpoint(HttpMethod.GET, "app-auth/config"))
        client.execute(VxEndpoint(HttpMethod.GET, "app-auth/me", authenticated = true))
        assertThat(server.takeRequest().headers["Authorization"]).isNull()
        assertThat(server.takeRequest().headers["Authorization"]).isEqualTo("Bearer secret")
    }

    @Test
    fun httpErrorsAreAnswersAndAreNotRetried() = runBlocking<Unit> {
        server.enqueue(MockResponse.Builder().code(500).body("boom").build())
        val result = client.execute(VxEndpoint(HttpMethod.GET, "product/app"))
        assertThat(result.code).isEqualTo(500)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun transportErrorsAreRetriedTwiceThenThrown() = runBlocking<Unit> {
        repeat(3) { server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build()) }
        val error = runCatching { client.execute(VxEndpoint(HttpMethod.GET, "product/app")) }.exceptionOrNull()
        assertThat(error).isInstanceOf(IOException::class.java)
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test
    fun aTransportErrorFollowedBySuccessRecovers() = runBlocking<Unit> {
        server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
        server.enqueue(MockResponse.Builder().code(200).body("[]").build())
        assertThat(client.execute(VxEndpoint(HttpMethod.GET, "product/app")).body).isEqualTo("[]")
    }

    @Test
    fun noRetriesWhenTheCallerOwnsTheRetryPolicy() = runBlocking<Unit> {
        server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
        val error = runCatching { client.execute(VxEndpoint(HttpMethod.POST, "device/after-purchase"), maxRetries = 0) }
        assertThat(error.isFailure).isTrue()
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun secretsAreRedactedFromLogs() {
        val redacted = VxHttpClient.redact("""{"email":"a@b.c","password":"hunter2","refresh_token":"r1","new_password":"x"}""")
        assertThat(redacted).contains("\"email\":\"a@b.c\"")
        assertThat(redacted).doesNotContain("hunter2")
        assertThat(redacted).doesNotContain("r1")
        assertThat(redacted).contains("\"new_password\":\"<redacted>\"")
    }
}
