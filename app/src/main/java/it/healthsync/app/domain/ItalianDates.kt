package it.healthsync.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Dates shown in the app. Webhook JSON and stored dates stay ISO (yyyy-MM-dd).
 * The formatters themselves have no zone; instants are converted with [zone] first.
 */
object ItalianDates {
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")

    fun format(date: LocalDate): String = date.format(dateFormatter)

    fun formatDateTime(instant: Instant?, zone: ZoneId = ZoneId.systemDefault()): String {
        if (instant == null) return "—"
        return dateTimeFormatter.format(instant.atZone(zone))
    }

    /** Blank input stores as empty. Invalid input returns null. Valid input stores as ISO. */
    fun toStoredDate(display: String): String? {
        val trimmed = display.trim()
        if (trimmed.isEmpty()) return ""
        return parse(trimmed)?.toString()
    }

    /** Stored ISO yyyy-MM-dd becomes GG-MM-AAAA. Blank stays blank. */
    fun fromStoredDate(stored: String): String {
        val trimmed = stored.trim()
        if (trimmed.isEmpty()) return ""
        return try {
            format(LocalDate.parse(trimmed))
        } catch (_: DateTimeParseException) {
            trimmed
        }
    }

    fun parse(text: String): LocalDate? {
        return try {
            LocalDate.parse(text.trim(), dateFormatter)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
