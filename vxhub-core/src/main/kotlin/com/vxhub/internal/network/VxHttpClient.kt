package com.vxhub.internal.network

import com.vxhub.VxLogLevel
import com.vxhub.internal.VxLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal enum class HttpMethod { GET, POST, PATCH, DELETE }

/**
 * One API call. [body] is already-encoded JSON; a POST/PATCH without one sends `{}`, because the
 * backend rejects an empty body declared as `application/json` (the fix iOS needed for
 * `device/restore`).
 */
internal class VxEndpoint(
    val method: HttpMethod,
    val path: String,
    val body: String? = null,
    /** Sends the signed-in account's bearer token. */
    val authenticated: Boolean = false,
)

/** An HTTP answer of any status. Transport failures are thrown as [IOException] instead. */
internal class HttpResult(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
    override fun toString(): String = "HttpResult($code)"
}

internal fun interface HeaderProvider {
    fun headers(authenticated: Boolean): Map<String, String>
}

/**
 * The SDK's transport: OkHttp, mutual TLS, VxHub headers and the iOS retry policy (two retries,
 * one second apart, for transport errors only — an HTTP error is an answer and is never retried).
 */
internal class VxHttpClient(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl,
    private val headerProvider: HeaderProvider,
) {
    suspend fun execute(endpoint: VxEndpoint, maxRetries: Int = DEFAULT_RETRIES): HttpResult {
        val request = buildRequest(endpoint)
        logRequest(request, endpoint.body)
        var attempt = 0
        while (true) {
            try {
                val result = client.newCall(request).await()
                logResponse(request, result)
                return result
            } catch (e: IOException) {
                if (attempt >= maxRetries) {
                    VxLog.w("${endpoint.method} ${endpoint.path} failed: ${e.javaClass.simpleName}: ${e.message}")
                    throw e
                }
                attempt++
                VxLog.w("${endpoint.path} transport error (${e.javaClass.simpleName}), retrying… (${maxRetries - attempt + 1} left)")
                delay(RETRY_DELAY_MS)
            }
        }
    }

    private fun buildRequest(endpoint: VxEndpoint): Request {
        val url = baseUrl.resolve(endpoint.path) ?: error("Bad endpoint path: ${endpoint.path}")
        val builder = Request.Builder().url(url)
        headerProvider.headers(endpoint.authenticated).forEach { (name, value) ->
            if (value.isNotEmpty()) builder.header(name, value)
        }
        builder.header("Accept", "application/json")
        val body: RequestBody? = when (endpoint.method) {
            HttpMethod.GET -> null
            HttpMethod.DELETE -> endpoint.body?.toRequestBody(JSON)
            HttpMethod.POST, HttpMethod.PATCH -> (endpoint.body ?: "{}").toRequestBody(JSON)
        }
        return builder.method(endpoint.method.name, body).build()
    }

    private fun logRequest(request: Request, body: String?) {
        if (!VxLog.isEnabled(VxLogLevel.DEBUG)) return
        val headers = request.headers.joinToString("\n") { (name, value) ->
            "  $name: ${if (name.equals("Authorization", ignoreCase = true)) "Bearer <redacted>" else value}"
        }
        VxLog.d { "→ ${request.method} ${request.url}\n$headers${body?.let { "\n  ${redact(it)}" } ?: ""}" }
    }

    private fun logResponse(request: Request, result: HttpResult) {
        if (!VxLog.isEnabled(VxLogLevel.DEBUG)) return
        VxLog.d { "← ${result.code} ${request.url.encodedPath}\n  ${redact(result.body).take(MAX_LOGGED_BODY)}" }
    }

    internal companion object {
        const val DEFAULT_RETRIES = 2
        const val RETRY_DELAY_MS = 1_000L
        private const val MAX_LOGGED_BODY = 4_000
        private val JSON = "application/json; charset=utf-8".toMediaType()

        private val SECRET_FIELDS = Regex(
            "\"(password|new_password|current_password|access_token|refresh_token|token)\"\\s*:\\s*\"[^\"]*\"",
        )

        fun redact(json: String): String = SECRET_FIELDS.replace(json) { "\"${it.groupValues[1]}\":\"<redacted>\"" }
    }
}

/** Runs the call on OkHttp's dispatcher and reads the body there, off the caller's thread. */
private suspend fun Call.await(): HttpResult = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use { HttpResult(it.code, it.body.string()) }
                } catch (e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                    return
                }
                if (continuation.isActive) continuation.resume(result)
            }
        },
    )
}
