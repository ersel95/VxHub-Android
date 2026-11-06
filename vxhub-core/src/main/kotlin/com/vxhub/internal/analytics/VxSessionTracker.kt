package com.vxhub.internal.analytics

import android.os.SystemClock
import com.vxhub.internal.VxLog
import com.vxhub.internal.network.SessionEndRequest
import com.vxhub.internal.network.SessionEventsRequest
import com.vxhub.internal.network.SessionStartRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** What a session reports about the device when it begins. */
internal class SessionMeta(val appVersion: String, val isPremium: Boolean, val countryCode: String)

/** The three session-analytics calls, behind an interface so the tracker can be tested without HTTP. */
internal interface SessionTransport {
    suspend fun start(request: SessionStartRequest): Boolean
    suspend fun end(request: SessionEndRequest): Boolean
    suspend fun events(request: SessionEventsRequest): Boolean
}

/**
 * User-journey tracking: screen views, screen time and custom events, batched to
 * `session-analytics/`. Port of iOS's `VxSessionTracker` — same event names, same 5-second
 * debounce, an immediate flush when the app goes to the background, and a new session on every
 * return to the foreground.
 */
internal class VxSessionTracker(
    private val scope: CoroutineScope,
    private val transport: SessionTransport,
    private val meta: () -> SessionMeta,
    private val elapsedMs: () -> Long = SystemClock::elapsedRealtime,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var started = false
    private var sessionId = ""
    private var eventIndex = 0
    private var currentScreen: String? = null
    private var screenEnteredAt = 0L
    private val pendingEvents = mutableListOf<JsonObject>()
    private var flushJob: Job? = null

    fun start() {
        scope.launch {
            mutex.withLock {
                if (started) return@launch
                started = true
                beginNewSessionLocked()
            }
        }
    }

    fun trackScreen(name: String) {
        scope.launch {
            mutex.withLock {
                currentScreen?.let { previous ->
                    enqueueLocked("screen_exit", previous, null, elapsedMs() - screenEnteredAt)
                }
                currentScreen = name
                screenEnteredAt = elapsedMs()
                enqueueLocked("screen_view", name, null, null)
                scheduleFlushLocked()
            }
        }
    }

    fun trackEvent(name: String, properties: Map<String, Any?>?) {
        scope.launch {
            mutex.withLock {
                enqueueLocked(name, currentScreen, properties?.toJsonObject(), null)
                scheduleFlushLocked()
            }
        }
    }

    fun onForeground() {
        scope.launch {
            mutex.withLock {
                if (!started) return@launch
                beginNewSessionLocked()
            }
        }
    }

    fun onBackground() {
        scope.launch {
            val endRequest = mutex.withLock {
                if (!started) return@launch
                currentScreen?.let { enqueueLocked("screen_exit", it, null, elapsedMs() - screenEnteredAt) }
                enqueueLocked("session_end", currentScreen, null, null)
                flushLocked()
                SessionEndRequest(sessionId, currentScreen.orEmpty())
            }
            if (!transport.end(endRequest)) VxLog.d { "session/end not delivered" }
        }
    }

    private suspend fun beginNewSessionLocked() {
        sessionId = UUID.randomUUID().toString()
        eventIndex = 0
        currentScreen = null
        val info = meta()
        val start = SessionStartRequest(sessionId, info.appVersion, info.isPremium, info.countryCode)
        scope.launch { if (!transport.start(start)) VxLog.d { "session/start not delivered" } }
        enqueueLocked(
            "session_start",
            null,
            buildJsonObject {
                put("appVersion", info.appVersion)
                put("isPremium", info.isPremium)
                put("countryCode", info.countryCode)
            },
            null,
        )
    }

    private fun enqueueLocked(eventName: String, screenName: String?, properties: JsonObject?, durationMs: Long?) {
        pendingEvents += buildJsonObject {
            put("eventName", eventName)
            put("eventIndex", eventIndex)
            put("timestamp", isoTimestamp(now()))
            screenName?.let { put("screenName", it) }
            properties?.let { put("properties", it) }
            durationMs?.let { put("durationMs", it) }
        }
        eventIndex++
    }

    private fun scheduleFlushLocked() {
        flushJob?.cancel()
        flushJob = scope.launch {
            delay(FLUSH_DEBOUNCE_MS)
            mutex.withLock {
                // Cleared first so flushLocked() does not cancel the coroutine it runs in.
                flushJob = null
                flushLocked()
            }
        }
    }

    private fun flushLocked() {
        flushJob?.cancel()
        flushJob = null
        if (pendingEvents.isEmpty()) return
        val batch = SessionEventsRequest(sessionId, pendingEvents.toList())
        pendingEvents.clear()
        scope.launch {
            if (transport.events(batch)) {
                VxLog.d { "Session events flushed (${batch.events.size})" }
            } else {
                VxLog.d { "Session events flush failed (${batch.events.size} dropped)" }
            }
        }
    }

    internal companion object {
        const val FLUSH_DEBOUNCE_MS = 5_000L

        fun isoTimestamp(epochMs: Long): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date(epochMs))
    }
}

/** Converts event properties to JSON; values that are not JSON-shaped are sent as their text. */
internal fun Map<String, Any?>.toJsonObject(): JsonObject = JsonObject(mapValues { (_, v) -> v.toJsonElement() })

internal fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { (k, v) -> k.toString() to v.toJsonElement() })
    is Iterable<*> -> JsonArray(map { it.toJsonElement() })
    is Array<*> -> JsonArray(map { it.toJsonElement() })
    else -> JsonPrimitive(toString())
}
