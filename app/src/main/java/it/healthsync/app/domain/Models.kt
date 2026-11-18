package it.healthsync.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.Serializable

/** Inclusive start, exclusive end. */
data class OpenWindow(
    val start: Instant,
    val end: Instant,
    val historyLimited: Boolean = false,
)

data class TimedValue(
    val time: Instant,
    val value: Double,
)

data class NutritionSample(
    val start: Instant,
    val energyKcal: Double?,
    val proteinGrams: Double?,
    val carbohydrateGrams: Double?,
    val fatGrams: Double?,
)

data class SleepStageInput(
    val stage: String,
    val start: Instant,
    val end: Instant,
)

data class SleepSessionInput(
    val start: Instant,
    val end: Instant,
    val title: String?,
    val notes: String?,
    val stages: List<SleepStageInput>,
)

data class WeightInput(
    val time: Instant,
    val weightKg: Double,
)

data class BodyFatInput(
    val time: Instant,
    val percentage: Double,
)

data class ExerciseInput(
    val start: Instant,
    val end: Instant,
    val exerciseType: String,
    val title: String?,
    val notes: String?,
    val activeCaloriesKcal: Double?,
    val distanceMeters: Double?,
)

/**
 * Daily totals already aggregated by Health Connect for the overlap between
 * the calendar day and the sync window. Null means the metric was not read
 * or Health Connect had no samples.
 */
data class DailyMetrics(
    val date: LocalDate,
    val steps: Long? = null,
    val distanceMeters: Double? = null,
    val activeCaloriesKcal: Double? = null,
    val totalCaloriesKcal: Double? = null,
    val heartRate: BpmStats? = null,
    val restingHeartRateBpm: Long? = null,
    val hydrationMilliliters: Double? = null,
)

data class HealthSnapshot(
    val windowStart: Instant,
    val windowEnd: Instant,
    val zone: ZoneId,
    val historyLimited: Boolean = false,
    val daily: List<DailyMetrics> = emptyList(),
    val hrvSamples: List<TimedValue> = emptyList(),
    val oxygenSamples: List<TimedValue> = emptyList(),
    val nutrition: List<NutritionSample> = emptyList(),
    val sleep: List<SleepSessionInput> = emptyList(),
    val weight: List<WeightInput> = emptyList(),
    val bodyFat: List<BodyFatInput> = emptyList(),
    val exercise: List<ExerciseInput> = emptyList(),
    val unavailableMetrics: List<String> = emptyList(),
)

@Serializable
data class HealthPayload(
    val schemaVersion: Int = SCHEMA_VERSION,
    val syncedAt: String,
    val timezone: String,
    val appVersion: String,
    val historyLimited: Boolean = false,
    val window: TimeWindowDto,
    val days: List<DaySummaryDto>,
    val sleepSessions: List<SleepSessionDto>,
    val weightReadings: List<WeightReadingDto>,
    val bodyFatReadings: List<BodyFatReadingDto>,
    val exerciseSessions: List<ExerciseSessionDto>,
    val unavailableMetrics: List<String> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class TimeWindowDto(
    val start: String,
    val end: String,
)

@Serializable
data class BpmStats(
    val min: Long,
    val avg: Long,
    val max: Long,
    val sampleCount: Long,
)

@Serializable
data class DoubleStats(
    val min: Double,
    val avg: Double,
    val max: Double,
    val sampleCount: Int,
)

@Serializable
data class NutritionDayDto(
    val energyKcal: Double? = null,
    val proteinGrams: Double? = null,
    val carbohydrateGrams: Double? = null,
    val fatGrams: Double? = null,
    val mealCount: Int,
)

@Serializable
data class DaySummaryDto(
    val date: String,
    val steps: Long? = null,
    val distanceMeters: Double? = null,
    val activeCaloriesKcal: Double? = null,
    val totalCaloriesKcal: Double? = null,
    val heartRate: BpmStats? = null,
    val restingHeartRateBpm: Long? = null,
    val heartRateVariabilityRmssdMillis: DoubleStats? = null,
    val oxygenSaturationPercent: DoubleStats? = null,
    val nutrition: NutritionDayDto? = null,
    val hydrationMilliliters: Double? = null,
)

@Serializable
data class SleepStageDto(
    val stage: String,
    val start: String,
    val end: String,
    val durationMinutes: Long,
)

@Serializable
data class SleepSessionDto(
    val start: String,
    val end: String,
    val durationMinutes: Long,
    val title: String? = null,
    val notes: String? = null,
    val stages: List<SleepStageDto> = emptyList(),
)

@Serializable
data class WeightReadingDto(
    val time: String,
    val weightKg: Double,
)

@Serializable
data class BodyFatReadingDto(
    val time: String,
    val percentage: Double,
)

@Serializable
data class ExerciseSessionDto(
    val start: String,
    val end: String,
    val durationMinutes: Long,
    val exerciseType: String,
    val title: String? = null,
    val notes: String? = null,
    val activeCaloriesKcal: Double? = null,
    val distanceMeters: Double? = null,
)
