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
data class TripleStat(
    val min: Double,
    val avg: Double,
    val max: Double,
)

data class TemperatureInput(
    val time: Instant,
    val celsius: Double,
    val location: String,
)

data class BloodPressureInput(
    val time: Instant,
    val systolicMmHg: Double,
    val diastolicMmHg: Double,
    val bodyPosition: String,
    val measurementLocation: String,
)

data class GlucoseInput(
    val time: Instant,
    val millimolesPerLiter: Double,
    val specimenSource: String,
    val relationToMeal: String,
)

data class Vo2Input(
    val time: Instant,
    val millilitersPerMinutePerKg: Double,
    val method: String,
)

data class DailyMetrics(
    val date: LocalDate,
    val steps: Long? = null,
    val distanceMeters: Double? = null,
    val activeCaloriesKcal: Double? = null,
    val totalCaloriesKcal: Double? = null,
    val heartRate: BpmStats? = null,
    val restingHeartRateBpm: Long? = null,
    val hydrationMilliliters: Double? = null,
    val floorsClimbed: Double? = null,
    val elevationMeters: Double? = null,
    val wheelchairPushes: Long? = null,
    val speedMetersPerSecond: TripleStat? = null,
    val powerWatts: TripleStat? = null,
    val cyclingCadenceRpm: TripleStat? = null,
    val stepsCadencePerMinute: TripleStat? = null,
    val basalCaloriesKcal: Double? = null,
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
    val heightMeters: List<TimedValue> = emptyList(),
    val leanBodyMassKg: List<TimedValue> = emptyList(),
    val boneMassKg: List<TimedValue> = emptyList(),
    val bodyWaterMassKg: List<TimedValue> = emptyList(),
    val respiratoryRate: List<TimedValue> = emptyList(),
    val basalMetabolicRateKcalPerDay: List<TimedValue> = emptyList(),
    val bodyTemperature: List<TemperatureInput> = emptyList(),
    val basalBodyTemperature: List<TemperatureInput> = emptyList(),
    val bloodPressure: List<BloodPressureInput> = emptyList(),
    val bloodGlucose: List<GlucoseInput> = emptyList(),
    val vo2Max: List<Vo2Input> = emptyList(),
    val unavailableMetrics: List<String> = emptyList(),
)

@Serializable
data class HealthPayload(
    val schemaVersion: Int = SCHEMA_VERSION,
    val kind: String = KIND_INCREMENTAL,
    val chunk: ChunkDto? = null,
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
    val heightReadings: List<ScalarReadingDto> = emptyList(),
    val leanBodyMassReadings: List<ScalarReadingDto> = emptyList(),
    val boneMassReadings: List<ScalarReadingDto> = emptyList(),
    val bodyWaterMassReadings: List<ScalarReadingDto> = emptyList(),
    val respiratoryRateReadings: List<ScalarReadingDto> = emptyList(),
    val basalMetabolicRateReadings: List<ScalarReadingDto> = emptyList(),
    val bodyTemperatureReadings: List<TemperatureReadingDto> = emptyList(),
    val basalBodyTemperatureReadings: List<TemperatureReadingDto> = emptyList(),
    val bloodPressureReadings: List<BloodPressureReadingDto> = emptyList(),
    val bloodGlucoseReadings: List<GlucoseReadingDto> = emptyList(),
    val vo2MaxReadings: List<Vo2ReadingDto> = emptyList(),
    val unavailableMetrics: List<String> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION = 2
        const val KIND_INCREMENTAL = "incremental"
        const val KIND_BACKFILL = "backfill"
    }
}

@Serializable
data class ChunkDto(
    val index: Int,
    val total: Int,
    val start: String,
    val end: String,
)

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
    val floorsClimbed: Double? = null,
    val elevationMeters: Double? = null,
    val wheelchairPushes: Long? = null,
    val speedMetersPerSecond: DoubleStats? = null,
    val powerWatts: DoubleStats? = null,
    val cyclingCadenceRpm: DoubleStats? = null,
    val stepsCadencePerMinute: DoubleStats? = null,
    val basalCaloriesKcal: Double? = null,
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
data class ScalarReadingDto(
    val time: String,
    val value: Double,
)

@Serializable
data class TemperatureReadingDto(
    val time: String,
    val celsius: Double,
    val location: String,
)

@Serializable
data class BloodPressureReadingDto(
    val time: String,
    val systolicMmHg: Double,
    val diastolicMmHg: Double,
    val bodyPosition: String,
    val measurementLocation: String,
)

@Serializable
data class GlucoseReadingDto(
    val time: String,
    val millimolesPerLiter: Double,
    val specimenSource: String,
    val relationToMeal: String,
)

@Serializable
data class Vo2ReadingDto(
    val time: String,
    val millilitersPerMinutePerKg: Double,
    val method: String,
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
