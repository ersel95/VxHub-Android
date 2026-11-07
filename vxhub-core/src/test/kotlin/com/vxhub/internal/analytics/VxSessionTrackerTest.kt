package com.vxhub.internal.analytics

import com.google.common.truth.Truth.assertThat
import com.vxhub.internal.network.SessionEndRequest
import com.vxhub.internal.network.SessionEventsRequest
import com.vxhub.internal.network.SessionStartRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VxSessionTrackerTest {
    private class FakeTransport : SessionTransport {
        val starts = mutableListOf<SessionStartRequest>()
        val ends = mutableListOf<SessionEndRequest>()
        val batches = mutableListOf<SessionEventsRequest>()
        override suspend fun start(request: SessionStartRequest) = starts.add(request)
        override suspend fun end(request: SessionEndRequest) = ends.add(request)
        override suspend fun events(request: SessionEventsRequest) = batches.add(request)
    }

    private fun SessionEventsRequest.names() = events.map { it["eventName"]!!.jsonPrimitive.content }

    @Test
    fun eventsAreBatchedAfterTheDebounceInOrder() = runTest {
        val transport = FakeTransport()
        val tracker = VxSessionTracker(this, transport, { SessionMeta("1.2.0", false, "TR") }, elapsedMs = { testScheduler.currentTime })
        tracker.start()
        runCurrent()
        assertThat(transport.starts.single().appVersion).isEqualTo("1.2.0")

        tracker.trackScreen("Home")
        runCurrent()
        advanceTimeBy(1_000)
        tracker.trackScreen("Paywall")
        tracker.trackEvent("paywall_shown", mapOf("variant" to "v4"))
        runCurrent()
        assertThat(transport.batches).isEmpty()

        advanceTimeBy(VxSessionTracker.FLUSH_DEBOUNCE_MS + 1)
        runCurrent()
        val batch = transport.batches.single()
        assertThat(batch.names()).containsExactly("session_start", "screen_view", "screen_exit", "screen_view", "paywall_shown").inOrder()
        assertThat(batch.events.map { it["eventIndex"]!!.jsonPrimitive.int }).containsExactly(0, 1, 2, 3, 4).inOrder()
        assertThat(batch.events[2]["durationMs"]!!.jsonPrimitive.long).isEqualTo(1_000)
        assertThat(batch.sessionId).isEqualTo(transport.starts.single().sessionId)
    }

    @Test
    fun goingToTheBackgroundFlushesAndEndsTheSession() = runTest {
        val transport = FakeTransport()
        val tracker = VxSessionTracker(this, transport, { SessionMeta("1.0", true, "US") }, elapsedMs = { testScheduler.currentTime })
        tracker.start()
        tracker.trackScreen("Home")
        runCurrent()
        tracker.onBackground()
        advanceUntilIdle()
        assertThat(transport.batches.single().names()).containsExactly("session_start", "screen_view", "screen_exit", "session_end").inOrder()
        assertThat(transport.ends.single().exitScreen).isEqualTo("Home")

        tracker.onForeground()
        advanceUntilIdle()
        assertThat(transport.starts).hasSize(2)
        assertThat(transport.starts[1].sessionId).isNotEqualTo(transport.starts[0].sessionId)
    }

    @Test
    fun nothingIsSentBeforeStart() = runTest {
        val transport = FakeTransport()
        val tracker = VxSessionTracker(this, transport, { SessionMeta("1.0", false, "TR") })
        tracker.onBackground()
        advanceUntilIdle()
        assertThat(transport.ends).isEmpty()
        assertThat(transport.batches).isEmpty()
    }
}
