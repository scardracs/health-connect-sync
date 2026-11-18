package it.healthsync.app.domain

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SyncWindowTest {
    private val rome = ZoneId.of("Europe/Rome")

    @Test
    fun firstSyncLooksBackSevenDays() {
        val now = Instant.parse("2026-01-17T05:00:00Z")
        val window = SyncWindowResolver.resolve(null, now)
        assertEquals(now.minus(7, ChronoUnit.DAYS), window.start)
        assertEquals(now, window.end)
        assertFalse(window.historyLimited)
    }

    @Test
    fun laterSyncStartsAtPreviousSuccess() {
        val now = Instant.parse("2026-01-17T05:05:00Z")
        val last = Instant.parse("2026-01-16T05:00:00Z")
        val window = SyncWindowResolver.resolve(last, now)
        assertEquals(last, window.start)
        assertEquals(now, window.end)
    }

    @Test
    fun clockGoingBackwardsFallsBackToSevenDays() {
        val now = Instant.parse("2026-01-17T05:00:00Z")
        val future = now.plus(2, ChronoUnit.HOURS)
        val window = SyncWindowResolver.resolve(future, now)
        assertEquals(now.minus(7, ChronoUnit.DAYS), window.start)
    }

    @Test
    fun datesCoverPartialEdgeDaysAndStopAtExclusiveEnd() {
        val start = Instant.parse("2026-01-10T05:00:00Z") // 06:00 Rome
        val end = Instant.parse("2026-01-12T05:00:00Z") // 06:00 Rome, two days later
        assertEquals(
            listOf("2026-01-10", "2026-01-11", "2026-01-12").map { java.time.LocalDate.parse(it) },
            datesOverlapping(start, end, rome),
        )
        val midnight = Instant.parse("2026-01-10T23:00:00Z") // 00:00 Rome on 11 Jan
        assertEquals(
            listOf(java.time.LocalDate.parse("2026-01-10")),
            datesOverlapping(start, midnight, rome),
        )
    }

    @Test
    fun morningDelayTargetsSixInRome() {
        val before = Instant.parse("2026-01-15T04:00:00Z") // 05:00 Rome
        assertEquals(Duration.ofHours(1), MorningSchedule.delayUntilNext(before, rome))

        val exactly = Instant.parse("2026-01-15T05:00:00Z") // 06:00 Rome
        assertEquals(Duration.ofHours(24), MorningSchedule.delayUntilNext(exactly, rome))

        val after = Instant.parse("2026-01-15T05:30:00Z") // 06:30 Rome
        assertEquals(Duration.ofHours(23).plusMinutes(30), MorningSchedule.delayUntilNext(after, rome))
    }
}
