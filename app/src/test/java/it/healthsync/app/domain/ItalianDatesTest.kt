package it.healthsync.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItalianDatesTest {
    private val rome = ZoneId.of("Europe/Rome")

    @Test
    fun formatsLocalDateAsDayMonthYear() {
        assertEquals("16-01-2026", ItalianDates.format(LocalDate.parse("2026-01-16")))
        assertEquals("05-11-2026", ItalianDates.format(LocalDate.of(2026, 11, 5)))
    }

    @Test
    fun formatsInstantWithZoneAsDayMonthYearAnd24HourTime() {
        val instant = Instant.parse("2026-01-16T05:00:00Z")
        assertEquals("16-01-2026 06:00", ItalianDates.formatDateTime(instant, rome))
        val evening = Instant.parse("2026-11-18T22:30:00Z")
        assertEquals("18-11-2026 23:30", ItalianDates.formatDateTime(evening, rome))
    }

    @Test
    fun displayRoundTripStoresIso() {
        assertEquals("", ItalianDates.toStoredDate("  "))
        assertEquals("2026-01-16", ItalianDates.toStoredDate("16-01-2026"))
        assertNull(ItalianDates.toStoredDate("2026-01-16"))
        assertNull(ItalianDates.toStoredDate("32-01-2026"))
        assertEquals("16-01-2026", ItalianDates.fromStoredDate("2026-01-16"))
        assertEquals("", ItalianDates.fromStoredDate(""))
    }
}
