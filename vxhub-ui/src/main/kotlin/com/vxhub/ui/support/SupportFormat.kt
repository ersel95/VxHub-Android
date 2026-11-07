package com.vxhub.ui.support

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/** A row of the ticket list. */
internal data class SupportTicket(
    val id: String,
    /** The panel's category key; shown through the panel's translations, as iOS's `localize()`. */
    val category: String,
    val lastMessage: String?,
    /** ISO-8601, as the backend sends it. */
    val lastMessageAt: String?,
    /** The user has seen the latest answer. */
    val isSeen: Boolean,
)

/** One message of a conversation. */
internal data class SupportMessage(
    val id: String,
    val text: String,
    /** Written by the user (true) or by a support agent (false). */
    val isFromDevice: Boolean,
    /** ISO-8601, as the backend sends it. */
    val createdAt: String?,
)

/**
 * Ticket times — iOS's `String.formattedDate()`: the clock time as `hh.mm a` in the device's time
 * zone (`03.45 PM`), whatever the day, with the backend's text as is when it is not a timestamp.
 *
 * Parsed by hand because `java.time` needs API 26 (minSdk is 24) and `SimpleDateFormat` misreads
 * fractions longer than milliseconds. iOS only accepts timestamps with fractional seconds; this
 * accepts them with or without, and a missing zone as UTC.
 */
internal object SupportTime {
    private val ISO_8601 = Regex(
        """(\d{4})-(\d{2})-(\d{2})[Tt ](\d{2}):(\d{2})(?::(\d{2})(?:[.,](\d{1,9}))?)?\s*([Zz]|[+-]\d{2}(?::?\d{2})?)?""",
    )

    /** Milliseconds since the epoch, or null when [text] is not an ISO-8601 timestamp. */
    fun parse(text: String?): Long? {
        val match = text?.trim()?.let(ISO_8601::matchEntire) ?: return null
        val part = match.groupValues
        val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            isLenient = false
            clear()
            set(part[1].toInt(), part[2].toInt() - 1, part[3].toInt(), part[4].toInt(), part[5].toInt(), part[6].ifEmpty { "0" }.toInt())
            set(Calendar.MILLISECOND, part[7].take(3).padEnd(3, '0').toInt())
        }
        val wallClock = try {
            calendar.timeInMillis
        } catch (e: IllegalArgumentException) {
            return null // A field out of range, such as month 13.
        }
        return wallClock - offsetMinutes(part[8]) * 60_000L
    }

    /** iOS's `formattedDate()`: `hh.mm a` in [zone], [text] itself when unparsable, null for null. */
    fun format(text: String?, zone: TimeZone = TimeZone.getDefault()): String? {
        if (text == null) return null
        val millis = parse(text) ?: return text
        val calendar = Calendar.getInstance(zone, Locale.US).apply { timeInMillis = millis }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val clockHour = if (hour % 12 == 0) 12 else hour % 12
        // en_US_POSIX on iOS: ASCII digits and AM/PM whatever the device's language.
        return String.format(Locale.US, "%02d.%02d %s", clockHour, minute, if (hour < 12) "AM" else "PM")
    }

    private fun offsetMinutes(zone: String): Int {
        if (zone.isEmpty() || zone.equals("Z", ignoreCase = true)) return 0
        val sign = if (zone.startsWith('-')) -1 else 1
        val digits = zone.substring(1).replace(":", "")
        return sign * (digits.take(2).toInt() * 60 + digits.drop(2).ifEmpty { "0" }.toInt())
    }
}

/**
 * The conversation oldest first, by timestamp. The core documents its messages as oldest first,
 * while iOS draws them as the backend's newest-first list; sorting settles it either way. Messages
 * without a readable time go last, in the order they came.
 */
internal fun List<SupportMessage>.oldestFirst(): List<SupportMessage> =
    distinctBy { it.id }
        .map { it to (SupportTime.parse(it.createdAt) ?: Long.MAX_VALUE) }
        .sortedBy { it.second }
        .map { it.first }

/**
 * A reloaded conversation plus the messages sent from this screen that it does not contain yet,
 * so a reload that was already on its way when a message went out cannot hide that message.
 */
internal fun mergeConversation(fetched: List<SupportMessage>, pendingSent: List<SupportMessage>): List<SupportMessage> {
    val known = fetched.mapTo(HashSet()) { it.id }
    return (fetched + pendingSent.filter { it.id !in known }).oldestFirst()
}
