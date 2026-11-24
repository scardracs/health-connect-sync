package it.healthsync.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SendPolicyTest {
    @Test
    fun pauseAllowsTenSecondsAndWarnsBelowAMinute() {
        assertEquals(60L, SendPause.coerce(null))
        assertEquals(60L, SendPause.coerce(60))
        assertEquals(10L, SendPause.coerce(10))
        assertEquals(10L, SendPause.coerce(1))
        assertEquals(90L, SendPause.coerce(90))
        assertTrue(SendPause.warnsRateLimit(10))
        assertFalse(SendPause.warnsRateLimit(60))
    }

    @Test
    fun intervalStaysBetweenOneHourAndAWeek() {
        assertEquals(24L, SyncInterval.coerce(null))
        assertEquals(1L, SyncInterval.coerce(0))
        assertEquals(6L, SyncInterval.coerce(6))
        assertEquals(168L, SyncInterval.coerce(500))
    }

    @Test
    fun failureNotificationWaitsForTheThirdMissAndResetsAfterSuccess() {
        assertEquals(1, FailureStreak.record(0, success = false))
        assertFalse(FailureStreak.shouldNotify(1))
        assertFalse(FailureStreak.shouldNotify(2))
        assertEquals(3, FailureStreak.record(2, success = false))
        assertTrue(FailureStreak.shouldNotify(3))
        assertFalse(FailureStreak.shouldNotify(4))
        assertEquals(0, FailureStreak.record(3, success = true))
    }

    @Test
    fun rangeRequiresIsoDatesInOrder() {
        val range = DateRangePlan.parse("2024-01-02", "2024-01-05")
        assertEquals(LocalDate.parse("2024-01-02"), range!!.start)
        assertEquals(LocalDate.parse("2024-01-05"), range.endInclusive)
        val sameDay = DateRangePlan.parse("2024-03-01", "2024-03-01")
        assertEquals(sameDay!!.start, sameDay.endInclusive)
        assertNull(DateRangePlan.parse("2024-02-02", "2024-02-01"))
        assertNull(DateRangePlan.parse("02-01-2024", "2024-02-03"))
        assertNull(DateRangePlan.parse("", "2024-02-03"))
    }
}
