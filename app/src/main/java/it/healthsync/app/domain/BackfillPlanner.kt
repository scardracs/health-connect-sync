package it.healthsync.app.domain

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable

object HistoryRange {
    /** With the history permission, scan this far back. Older empty months are still checked. */
    const val HORIZON_YEARS = 15L

    /** Without history permission, Health Connect allows 30 days before the first grant. */
    const val FALLBACK_DAYS = 30L

    fun earliest(now: Instant, historyGranted: Boolean, firstGrant: Instant?): Instant {
        if (historyGranted) return now.atZone(ZoneOffset.UTC).minusYears(HORIZON_YEARS).toInstant()
        return (firstGrant ?: now).minus(FALLBACK_DAYS, ChronoUnit.DAYS)
    }
}

object BackfillPlanner {
    const val MAX_CHUNK_BYTES = 1_500_000

    /** Half-open monthly slices in [zone], from [start] up to [end]. */
    fun months(start: Instant, end: Instant, zone: ZoneId): List<OpenWindow> {
        return slices(start, end, zone) { cursor -> cursor.plusMonths(1) }
    }

    /**
     * A month that would exceed [MAX_CHUNK_BYTES] becomes weeks.
     * A week becomes days. A single day is left as-is and may exceed the cap.
     */
    fun split(window: OpenWindow, zone: ZoneId): List<OpenWindow> {
        val days = Duration.between(window.start, window.end).toDays()
        val step: (java.time.ZonedDateTime) -> java.time.ZonedDateTime = when {
            days > 20 -> { cursor -> cursor.plusWeeks(1) }
            days > 1 -> { cursor -> cursor.plusDays(1) }
            else -> return listOf(window)
        }
        val parts = slices(window.start, window.end, zone, step)
        return if (parts.size <= 1) listOf(window) else parts
    }

    private fun slices(
        start: Instant,
        end: Instant,
        zone: ZoneId,
        advance: (java.time.ZonedDateTime) -> java.time.ZonedDateTime,
    ): List<OpenWindow> {
        if (!start.isBefore(end)) return emptyList()
        val out = mutableListOf<OpenWindow>()
        var cursor = start.atZone(zone)
        while (cursor.toInstant().isBefore(end)) {
            val next = advance(cursor).toInstant()
            val chunkEnd = if (next.isBefore(end)) next else end
            if (chunkEnd.isAfter(cursor.toInstant())) {
                out += OpenWindow(cursor.toInstant(), chunkEnd)
            }
            if (!chunkEnd.isAfter(cursor.toInstant())) break
            cursor = chunkEnd.atZone(zone)
        }
        return out
    }
}

data class BackfillState(
    val chunks: List<OpenWindow>,
    val completed: Int,
    val historyLimited: Boolean,
    val finished: Boolean,
) {
    val total: Int get() = completed + chunks.size

    fun advance(): BackfillState {
        if (chunks.isEmpty()) return copy(finished = true)
        val remaining = chunks.drop(1)
        return copy(chunks = remaining, completed = completed + 1, finished = remaining.isEmpty())
    }

    fun replaceFirst(parts: List<OpenWindow>): BackfillState {
        if (chunks.isEmpty() || parts.isEmpty()) return this
        return copy(chunks = parts + chunks.drop(1))
    }
}

@Serializable
data class StoredWindow(
    val startEpochMs: Long,
    val endEpochMs: Long,
)

@Serializable
data class StoredBackfill(
    val completed: Int,
    val historyLimited: Boolean,
    val finished: Boolean,
    val chunks: List<StoredWindow>,
)

fun BackfillState.toStored(): StoredBackfill {
    return StoredBackfill(
        completed = completed,
        historyLimited = historyLimited,
        finished = finished,
        chunks = chunks.map { StoredWindow(it.start.toEpochMilli(), it.end.toEpochMilli()) },
    )
}

fun StoredBackfill.toState(): BackfillState {
    return BackfillState(
        chunks = chunks.map { OpenWindow(Instant.ofEpochMilli(it.startEpochMs), Instant.ofEpochMilli(it.endEpochMs)) },
        completed = completed,
        historyLimited = historyLimited,
        finished = finished,
    )
}
