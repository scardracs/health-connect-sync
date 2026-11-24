package it.healthsync.app.domain

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Pause between webhook POSTs. The receiver drops bursts, so values under a minute are allowed but warned. */
object SendPause {
    const val MIN_SECONDS = 10L
    const val DEFAULT_SECONDS = 60L

    fun coerce(raw: Long?): Long = (raw ?: DEFAULT_SECONDS).coerceAtLeast(MIN_SECONDS)

    fun warnsRateLimit(seconds: Long): Boolean = seconds < DEFAULT_SECONDS
}

/** How often the background sync is eligible to run. */
object SyncInterval {
    const val MIN_HOURS = 1L
    const val MAX_HOURS = 168L
    const val DEFAULT_HOURS = 24L

    fun coerce(raw: Long?): Long = (raw ?: DEFAULT_HOURS).coerceIn(MIN_HOURS, MAX_HOURS)
}

/** Consecutive failures. One miss stays quiet. The notification fires when the streak first hits [NOTIFY_AT]. */
object FailureStreak {
    const val NOTIFY_AT = 3

    fun record(previous: Int, success: Boolean): Int = if (success) 0 else previous + 1

    fun shouldNotify(streak: Int): Boolean = streak == NOTIFY_AT
}

/** Inclusive local dates the user asked to resend. Stored and sent as ISO yyyy-MM-dd. */
object DateRangePlan {
    data class Range(val start: LocalDate, val endInclusive: LocalDate)

    fun parse(startIso: String, endIso: String): Range? {
        val start = parseIso(startIso) ?: return null
        val end = parseIso(endIso) ?: return null
        if (end.isBefore(start)) return null
        return Range(start, end)
    }

    private fun parseIso(text: String): LocalDate? {
        return try {
            LocalDate.parse(text.trim())
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
