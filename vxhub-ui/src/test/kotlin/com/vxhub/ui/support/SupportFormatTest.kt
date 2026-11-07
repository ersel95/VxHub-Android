package com.vxhub.ui.support

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.TimeZone

class SupportTimeTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val istanbul = TimeZone.getTimeZone("Europe/Istanbul") // UTC+3 all year.

    @Test
    fun `formats the clock time as iOS does`() {
        assertThat(SupportTime.format("2025-02-05T13:04:05.123Z", utc)).isEqualTo("01.04 PM")
    }

    @Test
    fun `shows the time in the device's zone`() {
        assertThat(SupportTime.format("2025-02-05T13:04:05.123Z", istanbul)).isEqualTo("04.04 PM")
    }

    @Test
    fun `midnight and noon read 12, mornings are zero padded`() {
        assertThat(SupportTime.format("2025-02-05T00:30:00.000Z", utc)).isEqualTo("12.30 AM")
        assertThat(SupportTime.format("2025-02-05T12:00:00.000Z", utc)).isEqualTo("12.00 PM")
        assertThat(SupportTime.format("2025-02-05T09:07:00.000Z", utc)).isEqualTo("09.07 AM")
        assertThat(SupportTime.format("2025-02-05T23:59:59.999Z", utc)).isEqualTo("11.59 PM")
    }

    @Test
    fun `parses to epoch milliseconds`() {
        assertThat(SupportTime.parse("1970-01-01T00:00:00.000Z")).isEqualTo(0L)
        assertThat(SupportTime.parse("1970-01-01T00:00:01.5Z")).isEqualTo(1_500L)
        assertThat(SupportTime.parse("2025-02-05T13:04:05.123Z")).isEqualTo(1_738_760_645_123L)
    }

    @Test
    fun `accepts no fraction, longer fractions and a missing zone`() {
        assertThat(SupportTime.parse("2025-02-05T13:04:05Z")).isEqualTo(1_738_760_645_000L)
        assertThat(SupportTime.parse("2025-02-05T13:04:05.123456Z")).isEqualTo(1_738_760_645_123L)
        assertThat(SupportTime.parse("2025-02-05T13:04:05.123")).isEqualTo(1_738_760_645_123L)
        assertThat(SupportTime.parse("2025-02-05 13:04")).isEqualTo(1_738_760_640_000L)
    }

    @Test
    fun `applies the timestamp's own offset`() {
        assertThat(SupportTime.parse("2025-02-05T15:04:05.500+02:00")).isEqualTo(1_738_760_645_500L)
        assertThat(SupportTime.parse("2025-02-05T15:04:05.500+0200")).isEqualTo(1_738_760_645_500L)
        assertThat(SupportTime.parse("2025-02-05T10:04:05.500-03")).isEqualTo(1_738_760_645_500L)
    }

    @Test
    fun `keeps text that is not a timestamp, as iOS`() {
        assertThat(SupportTime.format("yesterday", utc)).isEqualTo("yesterday")
        assertThat(SupportTime.format("2025-13-40T10:00:00.000Z", utc)).isEqualTo("2025-13-40T10:00:00.000Z")
        assertThat(SupportTime.format("", utc)).isEqualTo("")
        assertThat(SupportTime.parse("2025-02-30T10:00:00Z")).isNull()
    }

    @Test
    fun `no time stays no time`() {
        assertThat(SupportTime.format(null, utc)).isNull()
        assertThat(SupportTime.parse(null)).isNull()
    }
}

class SupportConversationOrderTest {
    private fun message(id: String, at: String?, text: String = "text $id") = SupportMessage(id, text, isFromDevice = true, createdAt = at)

    @Test
    fun `orders oldest first whatever order the backend used`() {
        val newestFirst = listOf(
            message("3", "2025-02-05T10:03:00.000Z"),
            message("2", "2025-02-05T10:02:00.000Z"),
            message("1", "2025-02-05T10:01:00.000Z"),
        )
        assertThat(newestFirst.oldestFirst().map { it.id }).containsExactly("1", "2", "3").inOrder()
        assertThat(newestFirst.reversed().oldestFirst().map { it.id }).containsExactly("1", "2", "3").inOrder()
    }

    @Test
    fun `messages without a readable time go last, in the order they came`() {
        val messages = listOf(message("x", null), message("2", "2025-02-05T10:02:00.000Z"), message("y", "soon"), message("1", "2025-02-05T10:01:00.000Z"))
        assertThat(messages.oldestFirst().map { it.id }).containsExactly("1", "2", "x", "y").inOrder()
    }

    @Test
    fun `drops repeated ids`() {
        val messages = listOf(message("1", "2025-02-05T10:01:00.000Z"), message("1", "2025-02-05T10:01:00.000Z"))
        assertThat(messages.oldestFirst()).hasSize(1)
    }

    @Test
    fun `a reload keeps a sent message it does not have yet`() {
        val fetched = listOf(message("1", "2025-02-05T10:01:00.000Z"), message("2", "2025-02-05T10:02:00.000Z"))
        val sent = message("3", "2025-02-05T10:03:00.000Z")
        assertThat(mergeConversation(fetched, listOf(sent)).map { it.id }).containsExactly("1", "2", "3").inOrder()
    }

    @Test
    fun `a reload that has the sent message wins over the local copy`() {
        val fetched = listOf(message("1", "2025-02-05T10:01:00.000Z"), message("3", "2025-02-05T10:03:00.000Z", text = "from server"))
        val merged = mergeConversation(fetched, listOf(message("3", "2025-02-05T10:03:00.000Z", text = "local")))
        assertThat(merged.map { it.text }).containsExactly("text 1", "from server").inOrder()
    }
}

class SupportColorsTest {
    @Test
    fun `unspecified colours take iOS's light and dark defaults`() {
        val light = VxSupportConfiguration().colors(dark = false)
        val dark = VxSupportConfiguration().colors(dark = true)
        assertThat(light.background).isEqualTo(Color.White)
        assertThat(dark.background).isEqualTo(Color.Black)
        assertThat(light.userBackground).isEqualTo(Color(0xFF478AFF))
        assertThat(dark.userBackground).isEqualTo(Color.White)
        assertThat(light.userDate).isEqualTo(Color(0xFFFFD8F0))
        assertThat(dark.userDate).isEqualTo(Color(0xFF808080))
        assertThat(light.listingDescription).isEqualTo(Color(0xFF636973))
        assertThat(dark.adminBorder).isEqualTo(Color(0xFF3C3C43))
        assertThat(light.sheetBackground).isEqualTo(Color(0xFFE9EBF0))
        assertThat(dark.messageFieldBackground).isEqualTo(Color(0xFF0E0E0E))
        assertThat(dark.bottomLine).isEqualTo(Color(0xFF131313))
    }

    @Test
    fun `a colour passed in is used in light and dark mode`() {
        val configuration = VxSupportConfiguration(backgroundColor = Color.Red)
        assertThat(configuration.colors(dark = false).background).isEqualTo(Color.Red)
        assertThat(configuration.colors(dark = true).background).isEqualTo(Color.Red)
    }
}
