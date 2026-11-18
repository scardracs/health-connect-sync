package it.healthsync.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object PayloadBuilder {
    private val json = Json {
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = false
    }

    fun buildJson(
        snapshot: HealthSnapshot,
        appVersion: String,
        syncedAt: Instant,
        kind: String = HealthPayload.KIND_INCREMENTAL,
        chunkIndex: Int? = null,
        chunkTotal: Int? = null,
    ): String {
        return json.encodeToString(build(snapshot, appVersion, syncedAt, kind, chunkIndex, chunkTotal))
    }

    fun build(
        snapshot: HealthSnapshot,
        appVersion: String,
        syncedAt: Instant,
        kind: String = HealthPayload.KIND_INCREMENTAL,
        chunkIndex: Int? = null,
        chunkTotal: Int? = null,
    ): HealthPayload {
        val zone = snapshot.zone
        val hrv = statsByDay(snapshot.hrvSamples, zone, snapshot.windowStart, snapshot.windowEnd)
        val oxygen = statsByDay(snapshot.oxygenSamples, zone, snapshot.windowStart, snapshot.windowEnd)
        val meals = nutritionByDay(snapshot.nutrition, zone, snapshot.windowStart, snapshot.windowEnd)
        val dailyByDate = snapshot.daily
            .filter { dateOverlaps(it.date, zone, snapshot.windowStart, snapshot.windowEnd) }
            .associateBy { it.date }

        val dates = (dailyByDate.keys + hrv.keys + oxygen.keys + meals.keys).sorted()
        val days = dates.mapNotNull { date ->
            val base = dailyByDate[date]
            val summary = DaySummaryDto(
                date = date.toString(),
                steps = base?.steps,
                distanceMeters = base?.distanceMeters?.rounded(),
                activeCaloriesKcal = base?.activeCaloriesKcal?.rounded(),
                totalCaloriesKcal = base?.totalCaloriesKcal?.rounded(),
                heartRate = base?.heartRate,
                restingHeartRateBpm = base?.restingHeartRateBpm,
                heartRateVariabilityRmssdMillis = hrv[date],
                oxygenSaturationPercent = oxygen[date],
                nutrition = meals[date],
                hydrationMilliliters = base?.hydrationMilliliters?.rounded(),
                floorsClimbed = base?.floorsClimbed?.rounded(),
                elevationMeters = base?.elevationMeters?.rounded(),
                wheelchairPushes = base?.wheelchairPushes,
                speedMetersPerSecond = base?.speedMetersPerSecond?.toStats(),
                powerWatts = base?.powerWatts?.toStats(),
                cyclingCadenceRpm = base?.cyclingCadenceRpm?.toStats(),
                stepsCadencePerMinute = base?.stepsCadencePerMinute?.toStats(),
                basalCaloriesKcal = base?.basalCaloriesKcal?.rounded(),
            )
            summary.takeUnless { it.isEmpty() }
        }

        val window = TimeWindowDto(
            start = formatInstant(snapshot.windowStart, zone),
            end = formatInstant(snapshot.windowEnd, zone),
        )
        val chunk = if (kind == HealthPayload.KIND_BACKFILL && chunkIndex != null && chunkTotal != null) {
            ChunkDto(index = chunkIndex, total = chunkTotal, start = window.start, end = window.end)
        } else {
            null
        }
        return HealthPayload(
            kind = kind,
            chunk = chunk,
            syncedAt = formatInstant(syncedAt, zone),
            timezone = zone.id,
            appVersion = appVersion,
            historyLimited = snapshot.historyLimited,
            window = window,
            days = days,
            sleepSessions = snapshot.sleep
                .filter { inWindow(it.start, snapshot.windowStart, snapshot.windowEnd) }
                .sortedBy { it.start }
                .map { it.toDto(zone) },
            weightReadings = snapshot.weight
                .filter { inWindow(it.time, snapshot.windowStart, snapshot.windowEnd) }
                .sortedBy { it.time }
                .map { WeightReadingDto(formatInstant(it.time, zone), it.weightKg.rounded()) },
            bodyFatReadings = snapshot.bodyFat
                .filter { inWindow(it.time, snapshot.windowStart, snapshot.windowEnd) }
                .sortedBy { it.time }
                .map { BodyFatReadingDto(formatInstant(it.time, zone), it.percentage.rounded()) },
            exerciseSessions = snapshot.exercise
                .filter { inWindow(it.start, snapshot.windowStart, snapshot.windowEnd) }
                .sortedBy { it.start }
                .map { it.toDto(zone) },
            heightReadings = snapshot.heightMeters.toScalars(zone, snapshot.windowStart, snapshot.windowEnd),
            leanBodyMassReadings = snapshot.leanBodyMassKg.toScalars(zone, snapshot.windowStart, snapshot.windowEnd),
            boneMassReadings = snapshot.boneMassKg.toScalars(zone, snapshot.windowStart, snapshot.windowEnd),
            bodyWaterMassReadings = snapshot.bodyWaterMassKg.toScalars(zone, snapshot.windowStart, snapshot.windowEnd),
            respiratoryRateReadings = snapshot.respiratoryRate.toScalars(zone, snapshot.windowStart, snapshot.windowEnd),
            basalMetabolicRateReadings = snapshot.basalMetabolicRateKcalPerDay.toScalars(zone, snapshot.windowStart, snapshot.windowEnd),
            bodyTemperatureReadings = snapshot.bodyTemperature.toTemperatures(zone, snapshot.windowStart, snapshot.windowEnd),
            basalBodyTemperatureReadings = snapshot.basalBodyTemperature.toTemperatures(zone, snapshot.windowStart, snapshot.windowEnd),
            bloodPressureReadings = snapshot.bloodPressure
                .filter { inWindow(it.time, snapshot.windowStart, snapshot.windowEnd) }
                .sortedBy { it.time }
                .map {
                    BloodPressureReadingDto(
                        formatInstant(it.time, zone),
                        it.systolicMmHg.rounded(),
                        it.diastolicMmHg.rounded(),
                        it.bodyPosition,
                        it.measurementLocation,
                    )
                },
            bloodGlucoseReadings = snapshot.bloodGlucose
                .filter { inWindow(it.time, snapshot.windowStart, snapshot.windowEnd) }
                .sortedBy { it.time }
                .map {
                    GlucoseReadingDto(
                        formatInstant(it.time, zone),
                        it.millimolesPerLiter.rounded(),
                        it.specimenSource,
                        it.relationToMeal,
                    )
                },
            vo2MaxReadings = snapshot.vo2Max
                .filter { inWindow(it.time, snapshot.windowStart, snapshot.windowEnd) }
                .sortedBy { it.time }
                .map {
                    Vo2ReadingDto(
                        formatInstant(it.time, zone),
                        it.millilitersPerMinutePerKg.rounded(),
                        it.method,
                    )
                },
            unavailableMetrics = snapshot.unavailableMetrics.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted(),
        )
    }

    private fun statsByDay(
        samples: List<TimedValue>,
        zone: ZoneId,
        start: Instant,
        end: Instant,
    ): Map<LocalDate, DoubleStats> {
        return samples
            .filter { inWindow(it.time, start, end) }
            .groupBy { it.time.atZone(zone).toLocalDate() }
            .mapValues { (_, values) ->
                val nums = values.map { it.value }
                DoubleStats(
                    min = nums.min().rounded(),
                    avg = (nums.sum() / nums.size).rounded(),
                    max = nums.max().rounded(),
                    sampleCount = nums.size,
                )
            }
    }

    private fun nutritionByDay(
        samples: List<NutritionSample>,
        zone: ZoneId,
        start: Instant,
        end: Instant,
    ): Map<LocalDate, NutritionDayDto> {
        return samples
            .filter { inWindow(it.start, start, end) }
            .groupBy { it.start.atZone(zone).toLocalDate() }
            .mapValues { (_, items) ->
                NutritionDayDto(
                    energyKcal = items.mapNotNull { it.energyKcal }.takeIf { it.isNotEmpty() }?.sum()?.rounded(),
                    proteinGrams = items.mapNotNull { it.proteinGrams }.takeIf { it.isNotEmpty() }?.sum()?.rounded(),
                    carbohydrateGrams = items.mapNotNull { it.carbohydrateGrams }.takeIf { it.isNotEmpty() }?.sum()?.rounded(),
                    fatGrams = items.mapNotNull { it.fatGrams }.takeIf { it.isNotEmpty() }?.sum()?.rounded(),
                    mealCount = items.size,
                )
            }
    }
}

internal fun DaySummaryDto.isEmpty(): Boolean {
    return steps == null &&
        distanceMeters == null &&
        activeCaloriesKcal == null &&
        totalCaloriesKcal == null &&
        heartRate == null &&
        restingHeartRateBpm == null &&
        heartRateVariabilityRmssdMillis == null &&
        oxygenSaturationPercent == null &&
        nutrition == null &&
        hydrationMilliliters == null &&
        floorsClimbed == null &&
        elevationMeters == null &&
        wheelchairPushes == null &&
        speedMetersPerSecond == null &&
        powerWatts == null &&
        cyclingCadenceRpm == null &&
        stepsCadencePerMinute == null &&
        basalCaloriesKcal == null
}

private fun TripleStat.toStats(): DoubleStats {
    return DoubleStats(min.rounded(), avg.rounded(), max.rounded(), sampleCount = 0)
}

private fun List<TimedValue>.toScalars(zone: ZoneId, start: Instant, end: Instant): List<ScalarReadingDto> {
    return filter { inWindow(it.time, start, end) }
        .sortedBy { it.time }
        .map { ScalarReadingDto(formatInstant(it.time, zone), it.value.rounded()) }
}

private fun List<TemperatureInput>.toTemperatures(
    zone: ZoneId,
    start: Instant,
    end: Instant,
): List<TemperatureReadingDto> {
    return filter { inWindow(it.time, start, end) }
        .sortedBy { it.time }
        .map { TemperatureReadingDto(formatInstant(it.time, zone), it.celsius.rounded(), it.location) }
}

internal fun formatInstant(instant: Instant, zone: ZoneId): String {
    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
        instant.atZone(zone).truncatedTo(ChronoUnit.SECONDS),
    )
}

internal fun durationMinutes(start: Instant, end: Instant): Long {
    val seconds = Duration.between(start, end).seconds
    if (seconds <= 0) return 0
    return (seconds / 60.0).roundToLong()
}

internal fun Double.rounded(decimals: Int = 2): Double {
    return BigDecimal.valueOf(this).setScale(decimals, RoundingMode.HALF_UP).toDouble()
}

private fun inWindow(time: Instant, start: Instant, end: Instant): Boolean {
    return !time.isBefore(start) && time.isBefore(end)
}

private fun dateOverlaps(date: LocalDate, zone: ZoneId, start: Instant, end: Instant): Boolean {
    val dayStart = date.atStartOfDay(zone).toInstant()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
    return dayStart.isBefore(end) && dayEnd.isAfter(start)
}

private fun SleepSessionInput.toDto(zone: ZoneId): SleepSessionDto {
    return SleepSessionDto(
        start = formatInstant(start, zone),
        end = formatInstant(end, zone),
        durationMinutes = durationMinutes(start, end),
        title = title.blankToNull(),
        notes = notes.blankToNull(),
        stages = stages.sortedBy { it.start }.map { stage ->
            SleepStageDto(
                stage = stage.stage,
                start = formatInstant(stage.start, zone),
                end = formatInstant(stage.end, zone),
                durationMinutes = durationMinutes(stage.start, stage.end),
            )
        },
    )
}

private fun ExerciseInput.toDto(zone: ZoneId): ExerciseSessionDto {
    return ExerciseSessionDto(
        start = formatInstant(start, zone),
        end = formatInstant(end, zone),
        durationMinutes = durationMinutes(start, end),
        exerciseType = exerciseType,
        title = title.blankToNull(),
        notes = notes.blankToNull(),
        activeCaloriesKcal = activeCaloriesKcal?.rounded(),
        distanceMeters = distanceMeters?.rounded(),
    )
}

private fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
