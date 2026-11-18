package it.healthsync.app.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object SyncWindowResolver {
    const val INITIAL_LOOKBACK_DAYS = 7L

    /**
     * First successful sync covers the previous 7 days. Later syncs start at the
     * previous window end, so the ranges meet without overlap: [start, end).
     * If the clock moved backwards, fall back to the 7-day window.
     */
    fun resolve(lastSuccess: Instant?, now: Instant): OpenWindow {
        val fallback = now.minus(INITIAL_LOOKBACK_DAYS, ChronoUnit.DAYS)
        val start = when {
            lastSuccess == null -> fallback
            lastSuccess.isBefore(now) -> lastSuccess
            else -> fallback
        }
        return OpenWindow(start = start, end = now, historyLimited = false)
    }
}

fun datesOverlapping(start: Instant, end: Instant, zone: ZoneId): List<LocalDate> {
    if (!start.isBefore(end)) return emptyList()
    var date = start.atZone(zone).toLocalDate()
    val last = end.minusMillis(1).atZone(zone).toLocalDate()
    val dates = mutableListOf<LocalDate>()
    while (!date.isAfter(last)) {
        dates += date
        date = date.plusDays(1)
    }
    return dates
}

object MorningSchedule {
    const val HOUR = 6

    /** Delay until the next 06:00 in [zone]. Exactly 06:00 waits until the next day. */
    fun delayUntilNext(now: Instant, zone: ZoneId, hour: Int = HOUR): Duration {
        val zoned = now.atZone(zone)
        var next = zoned.toLocalDate().atTime(hour, 0).atZone(zone)
        if (!next.isAfter(zoned)) {
            next = next.plusDays(1)
        }
        return Duration.between(zoned, next)
    }
}
