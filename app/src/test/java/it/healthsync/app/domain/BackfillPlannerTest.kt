package it.healthsync.app.domain

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackfillPlannerTest {
    private val rome = ZoneId.of("Europe/Rome")

    @Test
    fun monthsCoverTheRangeWithoutOverlap() {
        val start = Instant.parse("2026-01-10T05:00:00Z")
        val end = Instant.parse("2026-03-05T05:00:00Z")
        val chunks = BackfillPlanner.months(start, end, rome)
        assertEquals(start, chunks.first().start)
        assertEquals(end, chunks.last().end)
        assertTrue(chunks.zipWithNext().all { (a, b) -> a.end == b.start })
        assertTrue(chunks.all { it.start.isBefore(it.end) })
        assertEquals(2, chunks.size)
    }

    @Test
    fun oversizedMonthSplitsIntoWeeksAndAWeekSplitsIntoDays() {
        val month = OpenWindow(
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-02-01T00:00:00Z"),
        )
        val weeks = BackfillPlanner.split(month, rome)
        assertTrue(weeks.size > 1)
        assertTrue(weeks.all { ChronoUnit.DAYS.between(it.start, it.end) <= 8 })

        val fewDays = OpenWindow(
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-04T00:00:00Z"),
        )
        val days = BackfillPlanner.split(fewDays, rome)
        assertEquals(3, days.size)
    }

    @Test
    fun progressAdvancesAndCanReplaceTheCurrentChunk() {
        val chunks = BackfillPlanner.months(
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-04-01T00:00:00Z"),
            rome,
        )
        val start = BackfillState(chunks, completed = 0, historyLimited = false, finished = false)
        val replaced = start.replaceFirst(
            listOf(
                OpenWindow(chunks[0].start, chunks[0].start.plus(7, ChronoUnit.DAYS)),
                OpenWindow(chunks[0].start.plus(7, ChronoUnit.DAYS), chunks[0].end),
            ),
        )
        assertEquals(chunks.size + 1, replaced.total)
        val next = replaced.advance()
        assertEquals(1, next.completed)
        assertEquals(replaced.chunks.drop(1), next.chunks)
        assertFalse(next.finished)
    }

    @Test
    fun historyPermissionReachesBackFifteenYearsOtherwiseThirtyDaysBeforeGrant() {
        val now = Instant.parse("2026-06-01T10:00:00Z")
        val grant = Instant.parse("2026-05-01T10:00:00Z")
        assertEquals(
            now.atZone(ZoneOffset.UTC).minusYears(15).toInstant(),
            HistoryRange.earliest(now, historyGranted = true, grant),
        )
        assertEquals(grant.minus(30, ChronoUnit.DAYS), HistoryRange.earliest(now, historyGranted = false, grant))
        assertEquals(now.minus(30, ChronoUnit.DAYS), HistoryRange.earliest(now, historyGranted = false, null))
    }
}
