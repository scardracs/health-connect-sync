package it.healthsync.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.WheelchairPushesRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import it.healthsync.app.domain.BodyFatInput
import it.healthsync.app.domain.BpmStats
import it.healthsync.app.domain.DailyMetrics
import it.healthsync.app.domain.ExerciseInput
import it.healthsync.app.domain.HealthSnapshot
import it.healthsync.app.domain.NutritionSample
import it.healthsync.app.domain.SleepSessionInput
import it.healthsync.app.domain.SleepStageInput
import it.healthsync.app.domain.TimedValue
import it.healthsync.app.domain.TripleStat
import it.healthsync.app.domain.WeightInput
import it.healthsync.app.domain.datesOverlapping
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

sealed class ReadOutcome {
    data class Ok(val snapshot: HealthSnapshot) : ReadOutcome()
    data class Unavailable(val message: String) : ReadOutcome()
    data class PermissionDenied(val message: String) : ReadOutcome()
}

class HealthConnectGateway(private val context: Context) {
    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    fun clientOrNull(): HealthConnectClient? {
        if (sdkStatus() != HealthConnectClient.SDK_AVAILABLE) return null
        return try {
            HealthConnectClient.getOrCreate(context)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun grantedPermissions(): Set<String> {
        val client = clientOrNull() ?: return emptySet()
        return try {
            client.permissionController.getGrantedPermissions()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun backgroundSupported(): Boolean = featureAvailable(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)

    fun historySupported(): Boolean = featureAvailable(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY)

    private fun featureAvailable(feature: Int): Boolean {
        val client = clientOrNull() ?: return false
        return try {
            client.features.getFeatureStatus(feature) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        } catch (_: Exception) {
            false
        }
    }

    suspend fun read(
        windowStart: Instant,
        windowEnd: Instant,
        zone: ZoneId,
        historyLimited: Boolean = false,
        allowHistoryClamp: Boolean = true,
    ): ReadOutcome {
        if (!windowStart.isBefore(windowEnd)) {
            return ReadOutcome.Ok(
                HealthSnapshot(
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                    zone = zone,
                    historyLimited = historyLimited,
                ),
            )
        }
        return try {
            readOnce(windowStart, windowEnd, zone, historyLimited)
        } catch (denied: AccessDenied) {
            denied.outcome
        } catch (e: Exception) {
            if (allowHistoryClamp && !historyLimited && isHistoryLimit(e) && ChronoUnit.DAYS.between(windowStart, windowEnd) > 30) {
                val clamped = windowEnd.minus(30, ChronoUnit.DAYS)
                if (clamped.isAfter(windowStart)) {
                    return read(clamped, windowEnd, zone, historyLimited = true)
                }
            }
            ReadOutcome.Unavailable("Lettura da Health Connect non riuscita.")
        }
    }

    private suspend fun readOnce(
        windowStart: Instant,
        windowEnd: Instant,
        zone: ZoneId,
        historyLimited: Boolean,
    ): ReadOutcome {
        when (sdkStatus()) {
            HealthConnectClient.SDK_UNAVAILABLE ->
                return ReadOutcome.Unavailable("Health Connect non è disponibile su questo dispositivo.")
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                return ReadOutcome.Unavailable(
                    "Health Connect non è installato o va aggiornato. Installalo dal Play Store e riprova.",
                )
        }
        val client = try {
            HealthConnectClient.getOrCreate(context)
        } catch (_: Exception) {
            return ReadOutcome.Unavailable("Impossibile collegarsi a Health Connect.")
        }
        val granted = try {
            client.permissionController.getGrantedPermissions()
        } catch (_: Exception) {
            return ReadOutcome.Unavailable("Impossibile leggere i permessi di Health Connect.")
        }
        val grantedIds = HealthPermissions.metrics.filter { it.permission in granted }.map { it.id }.toSet()
        if (grantedIds.isEmpty()) {
            return ReadOutcome.PermissionDenied(
                "Nessun permesso di lettura concesso. Concedi i permessi di Health Connect e riprova.",
            )
        }
        val unavailable = HealthPermissions.metrics.filter { it.id !in grantedIds }.map { it.id }.toMutableList()

        val daily = readDaily(client, grantedIds, windowStart, windowEnd, zone)
        val sleep = if ("sleep_session" in grantedIds) {
            readAll<SleepSessionRecord>(client, windowStart, windowEnd).map { record ->
                SleepSessionInput(
                    start = record.startTime,
                    end = record.endTime,
                    title = record.title,
                    notes = record.notes,
                    stages = record.stages.map { stage ->
                        SleepStageInput(
                            stage = sleepStageName(stage.stage),
                            start = stage.startTime,
                            end = stage.endTime,
                        )
                    },
                )
            }
        } else {
            emptyList()
        }
        val weight = if ("weight" in grantedIds) {
            readAll<WeightRecord>(client, windowStart, windowEnd).map { WeightInput(it.time, it.weight.inKilograms) }
        } else {
            emptyList()
        }
        val bodyFat = if ("body_fat" in grantedIds) {
            readAll<BodyFatRecord>(client, windowStart, windowEnd).map {
                BodyFatInput(it.time, it.percentage.value)
            }
        } else {
            emptyList()
        }
        val hrv = if ("heart_rate_variability" in grantedIds) {
            readAll<HeartRateVariabilityRmssdRecord>(client, windowStart, windowEnd).map {
                TimedValue(it.time, it.heartRateVariabilityMillis)
            }
        } else {
            emptyList()
        }
        val oxygen = if ("oxygen_saturation" in grantedIds) {
            readAll<OxygenSaturationRecord>(client, windowStart, windowEnd).map {
                TimedValue(it.time, it.percentage.value)
            }
        } else {
            emptyList()
        }
        val nutrition = if ("nutrition" in grantedIds) {
            readAll<NutritionRecord>(client, windowStart, windowEnd).map { record ->
                NutritionSample(
                    start = record.startTime,
                    energyKcal = record.energy?.inKilocalories,
                    proteinGrams = record.protein?.inGrams,
                    carbohydrateGrams = record.totalCarbohydrate?.inGrams,
                    fatGrams = record.totalFat?.inGrams,
                )
            }
        } else {
            emptyList()
        }
        val exercise = if ("exercise_session" in grantedIds) {
            readExercise(client, grantedIds, windowStart, windowEnd)
        } else {
            emptyList()
        }
        val points = readPointRecords(client, grantedIds, windowStart, windowEnd)
        val stepRecords = if ("steps" in grantedIds) readAll<StepsRecord>(client, windowStart, windowEnd).size else 0
        val calorieDays = daily.count {
            (it.activeCaloriesKcal ?: 0.0) > 0.0 ||
                (it.totalCaloriesKcal ?: 0.0) > 0.0 ||
                (it.basalCaloriesKcal ?: 0.0) > 0.0
        }
        val counts = mapOf(
            "steps" to stepRecords,
            "sleep" to sleep.size,
            "weight" to weight.size,
            "exercise" to exercise.size,
            "calories" to calorieDays,
            "heart_rate" to daily.sumOf { (it.heartRate?.sampleCount ?: 0L) }.toInt(),
        )
        android.util.Log.i(
            "SaluteSync",
            "read ${windowStart}..${windowEnd} " + counts.entries.joinToString(" ") { "${it.key}=${it.value}" },
        )

        return ReadOutcome.Ok(
            HealthSnapshot(
                windowStart = windowStart,
                windowEnd = windowEnd,
                zone = zone,
                historyLimited = historyLimited,
                daily = daily,
                hrvSamples = hrv,
                oxygenSamples = oxygen,
                nutrition = nutrition,
                sleep = sleep,
                weight = weight,
                bodyFat = bodyFat,
                exercise = exercise,
                heightMeters = points.heightMeters,
                leanBodyMassKg = points.leanBodyMassKg,
                boneMassKg = points.boneMassKg,
                bodyWaterMassKg = points.bodyWaterMassKg,
                respiratoryRate = points.respiratoryRate,
                basalMetabolicRateKcalPerDay = points.basalMetabolicRateKcalPerDay,
                bodyTemperature = points.bodyTemperature,
                basalBodyTemperature = points.basalBodyTemperature,
                bloodPressure = points.bloodPressure,
                bloodGlucose = points.bloodGlucose,
                vo2Max = points.vo2Max,
                unavailableMetrics = unavailable,
                counts = counts,
            ),
        )
    }

    /**
     * First local day in [start, end) that has steps, distance, sleep, exercise,
     * heart-rate samples, weight, or body fat. Calorie estimates alone do not count.
     */
    suspend fun findEarliestNonCalorieDay(start: Instant, end: Instant, zone: ZoneId): java.time.LocalDate? {
        val client = clientOrNull() ?: return null
        val granted = try {
            client.permissionController.getGrantedPermissions()
        } catch (_: Exception) {
            return null
        }
        val grantedIds = HealthPermissions.metrics.filter { it.permission in granted }.map { it.id }.toSet()
        var month = start.atZone(zone).toLocalDate().withDayOfMonth(1)
        val last = end.minusMillis(1).atZone(zone).toLocalDate()
        while (!month.isAfter(last)) {
            val from = maxOf(month.atStartOfDay(zone).toInstant(), start)
            val to = minOf(month.plusMonths(1).atStartOfDay(zone).toInstant(), end)
            if (from.isBefore(to) && rangeHasSignal(client, grantedIds, from, to)) {
                var day = from.atZone(zone).toLocalDate()
                val stop = to.minusMillis(1).atZone(zone).toLocalDate()
                while (!day.isAfter(stop)) {
                    val dayFrom = maxOf(day.atStartOfDay(zone).toInstant(), from)
                    val dayTo = minOf(day.plusDays(1).atStartOfDay(zone).toInstant(), to)
                    if (dayFrom.isBefore(dayTo) && rangeHasSignal(client, grantedIds, dayFrom, dayTo)) return day
                    day = day.plusDays(1)
                }
            }
            month = month.plusMonths(1)
        }
        return null
    }

    private suspend fun rangeHasSignal(
        client: HealthConnectClient,
        grantedIds: Set<String>,
        from: Instant,
        to: Instant,
    ): Boolean {
        val metrics = linkedSetOf<androidx.health.connect.client.aggregate.AggregateMetric<*>>()
        if ("steps" in grantedIds) metrics += StepsRecord.COUNT_TOTAL
        if ("distance" in grantedIds) metrics += DistanceRecord.DISTANCE_TOTAL
        if ("sleep_session" in grantedIds) metrics += androidx.health.connect.client.records.SleepSessionRecord.SLEEP_DURATION_TOTAL
        if ("exercise_session" in grantedIds) metrics += ExerciseSessionRecord.EXERCISE_DURATION_TOTAL
        if ("heart_rate" in grantedIds) metrics += HeartRateRecord.MEASUREMENTS_COUNT
        if (metrics.isNotEmpty()) {
            val result = try {
                client.aggregate(androidx.health.connect.client.request.AggregateRequest(metrics, androidx.health.connect.client.time.TimeRangeFilter.between(from, to)))
            } catch (error: Exception) {
                if (isHistoryLimit(error)) return false
                throwIfDenied(error)
                return false
            }
            if ((result[StepsRecord.COUNT_TOTAL] ?: 0L) > 0L) return true
            if ((result[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0) > 0.0) return true
            val sleep = result[androidx.health.connect.client.records.SleepSessionRecord.SLEEP_DURATION_TOTAL]
            if (sleep != null && !sleep.isZero) return true
            val exercise = result[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]
            if (exercise != null && !exercise.isZero) return true
            if ((result[HeartRateRecord.MEASUREMENTS_COUNT] ?: 0L) > 0L) return true
        }
        if ("weight" in grantedIds && hasOne<androidx.health.connect.client.records.WeightRecord>(client, from, to)) return true
        if ("body_fat" in grantedIds && hasOne<androidx.health.connect.client.records.BodyFatRecord>(client, from, to)) return true
        return false
    }

    private suspend inline fun <reified T : androidx.health.connect.client.records.Record> hasOne(
        client: HealthConnectClient,
        from: Instant,
        to: Instant,
    ): Boolean {
        return try {
            client.readRecords(
                androidx.health.connect.client.request.ReadRecordsRequest<T>(
                    timeRangeFilter = androidx.health.connect.client.time.TimeRangeFilter.between(from, to),
                    pageSize = 1,
                ),
            ).records.isNotEmpty()
        } catch (error: Exception) {
            if (isHistoryLimit(error)) return false
            throwIfDenied(error)
            false
        }
    }

    private suspend fun readDaily(
        client: HealthConnectClient,
        grantedIds: Set<String>,
        windowStart: Instant,
        windowEnd: Instant,
        zone: ZoneId,
    ): List<DailyMetrics> {
        val days = mutableListOf<DailyMetrics>()
        for (date in datesOverlapping(windowStart, windowEnd, zone)) {
            val dayStart = date.atStartOfDay(zone).toInstant()
            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
            val from = maxOf(dayStart, windowStart)
            val to = minOf(dayEnd, windowEnd)
            if (!from.isBefore(to)) continue
            val metrics = linkedSetOf<AggregateMetric<*>>()
            if ("steps" in grantedIds) metrics += StepsRecord.COUNT_TOTAL
            if ("distance" in grantedIds) metrics += DistanceRecord.DISTANCE_TOTAL
            if ("active_calories" in grantedIds) metrics += ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL
            if ("total_calories" in grantedIds) metrics += TotalCaloriesBurnedRecord.ENERGY_TOTAL
            if ("heart_rate" in grantedIds) {
                metrics += HeartRateRecord.BPM_MIN
                metrics += HeartRateRecord.BPM_MAX
                metrics += HeartRateRecord.BPM_AVG
                metrics += HeartRateRecord.MEASUREMENTS_COUNT
            }
            if ("resting_heart_rate" in grantedIds) metrics += RestingHeartRateRecord.BPM_AVG
            if ("hydration" in grantedIds) metrics += HydrationRecord.VOLUME_TOTAL
            if ("floors" in grantedIds) metrics += FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL
            if ("elevation" in grantedIds) metrics += ElevationGainedRecord.ELEVATION_GAINED_TOTAL
            if ("wheelchair_pushes" in grantedIds) metrics += WheelchairPushesRecord.COUNT_TOTAL
            if ("speed" in grantedIds) {
                metrics += SpeedRecord.SPEED_MIN
                metrics += SpeedRecord.SPEED_AVG
                metrics += SpeedRecord.SPEED_MAX
            }
            if ("power" in grantedIds) {
                metrics += PowerRecord.POWER_MIN
                metrics += PowerRecord.POWER_AVG
                metrics += PowerRecord.POWER_MAX
            }
            if ("exercise_session" in grantedIds) {
                metrics += CyclingPedalingCadenceRecord.RPM_MIN
                metrics += CyclingPedalingCadenceRecord.RPM_AVG
                metrics += CyclingPedalingCadenceRecord.RPM_MAX
            }
            if ("steps" in grantedIds) {
                metrics += StepsCadenceRecord.RATE_MIN
                metrics += StepsCadenceRecord.RATE_AVG
                metrics += StepsCadenceRecord.RATE_MAX
            }
            if ("basal_metabolic_rate" in grantedIds) metrics += BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL
            if (metrics.isEmpty()) continue
            val result = try {
                client.aggregate(AggregateRequest(metrics, TimeRangeFilter.between(from, to)))
            } catch (e: Exception) {
                if (isHistoryLimit(e)) throw e
                throwIfDenied(e)
                continue
            }
            val sampleCount = if ("heart_rate" in grantedIds) result[HeartRateRecord.MEASUREMENTS_COUNT] else null
            val heart = if (sampleCount != null && sampleCount > 0) {
                val min = result[HeartRateRecord.BPM_MIN]
                val avg = result[HeartRateRecord.BPM_AVG]
                val max = result[HeartRateRecord.BPM_MAX]
                if (min != null && avg != null && max != null) BpmStats(min, avg, max, sampleCount) else null
            } else {
                null
            }
            days += DailyMetrics(
                date = date,
                steps = if ("steps" in grantedIds) result[StepsRecord.COUNT_TOTAL] else null,
                distanceMeters = if ("distance" in grantedIds) result[DistanceRecord.DISTANCE_TOTAL]?.inMeters else null,
                activeCaloriesKcal = if ("active_calories" in grantedIds) {
                    result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
                } else {
                    null
                },
                totalCaloriesKcal = if ("total_calories" in grantedIds) {
                    result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
                } else {
                    null
                },
                heartRate = heart,
                restingHeartRateBpm = if ("resting_heart_rate" in grantedIds) result[RestingHeartRateRecord.BPM_AVG] else null,
                hydrationMilliliters = if ("hydration" in grantedIds) {
                    result[HydrationRecord.VOLUME_TOTAL]?.inMilliliters
                } else {
                    null
                },
                floorsClimbed = if ("floors" in grantedIds) result[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL] else null,
                elevationMeters = if ("elevation" in grantedIds) {
                    result[ElevationGainedRecord.ELEVATION_GAINED_TOTAL]?.inMeters
                } else {
                    null
                },
                wheelchairPushes = if ("wheelchair_pushes" in grantedIds) result[WheelchairPushesRecord.COUNT_TOTAL] else null,
                speedMetersPerSecond = if ("speed" in grantedIds) {
                    triple(
                        result[SpeedRecord.SPEED_MIN]?.inMetersPerSecond,
                        result[SpeedRecord.SPEED_AVG]?.inMetersPerSecond,
                        result[SpeedRecord.SPEED_MAX]?.inMetersPerSecond,
                    )
                } else {
                    null
                },
                powerWatts = if ("power" in grantedIds) {
                    triple(
                        result[PowerRecord.POWER_MIN]?.inWatts,
                        result[PowerRecord.POWER_AVG]?.inWatts,
                        result[PowerRecord.POWER_MAX]?.inWatts,
                    )
                } else {
                    null
                },
                cyclingCadenceRpm = if ("exercise_session" in grantedIds) {
                    triple(
                        result[CyclingPedalingCadenceRecord.RPM_MIN],
                        result[CyclingPedalingCadenceRecord.RPM_AVG],
                        result[CyclingPedalingCadenceRecord.RPM_MAX],
                    )
                } else {
                    null
                },
                stepsCadencePerMinute = if ("steps" in grantedIds) {
                    triple(
                        result[StepsCadenceRecord.RATE_MIN],
                        result[StepsCadenceRecord.RATE_AVG],
                        result[StepsCadenceRecord.RATE_MAX],
                    )
                } else {
                    null
                },
                basalCaloriesKcal = if ("basal_metabolic_rate" in grantedIds) {
                    result[BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL]?.inKilocalories
                } else {
                    null
                },
            )
        }
        return days
    }

    private suspend fun readExercise(
        client: HealthConnectClient,
        grantedIds: Set<String>,
        windowStart: Instant,
        windowEnd: Instant,
    ): List<ExerciseInput> {
        return readAll<ExerciseSessionRecord>(client, windowStart, windowEnd).map { record ->
            val calories = if ("active_calories" in grantedIds && record.startTime.isBefore(record.endTime)) {
                try {
                    client.aggregate(
                        AggregateRequest(
                            setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                            TimeRangeFilter.between(record.startTime, record.endTime),
                        ),
                    )[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
                } catch (e: Exception) {
                    if (isHistoryLimit(e)) throw e
                    throwIfDenied(e)
                    null
                }
            } else {
                null
            }
            val distance = if ("distance" in grantedIds && record.startTime.isBefore(record.endTime)) {
                try {
                    client.aggregate(
                        AggregateRequest(
                            setOf(DistanceRecord.DISTANCE_TOTAL),
                            TimeRangeFilter.between(record.startTime, record.endTime),
                        ),
                    )[DistanceRecord.DISTANCE_TOTAL]?.inMeters
                } catch (e: Exception) {
                    if (isHistoryLimit(e)) throw e
                    throwIfDenied(e)
                    null
                }
            } else {
                null
            }
            ExerciseInput(
                start = record.startTime,
                end = record.endTime,
                exerciseType = exerciseTypeName(record.exerciseType),
                title = record.title,
                notes = record.notes,
                activeCaloriesKcal = calories,
                distanceMeters = distance,
            )
        }
    }

    internal suspend inline fun <reified T : Record> readAll(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
    ): List<T> {
        val out = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val response = try {
                client.readRecords(
                    ReadRecordsRequest<T>(
                        timeRangeFilter = TimeRangeFilter.between(start, end),
                        pageSize = 1000,
                        pageToken = pageToken,
                    ),
                )
            } catch (e: Exception) {
                if (isHistoryLimit(e)) throw e
                throwIfDenied(e)
                return out
            }
            out += response.records
            pageToken = response.pageToken
        } while (!pageToken.isNullOrEmpty())
        return out
    }

    private fun triple(min: Double?, avg: Double?, max: Double?): TripleStat? {
        if (min == null && avg == null && max == null) return null
        val present = listOfNotNull(min, avg, max)
        return TripleStat(min = present.min(), avg = avg ?: present.average(), max = present.max())
    }
}

internal class AccessDenied(val outcome: ReadOutcome) : RuntimeException(outcome.toString())

internal fun throwIfDenied(error: Exception) {
    val denied = generateSequence<Throwable>(error) { it.cause }.any { it is SecurityException }
    if (denied || isBackgroundDenial(error)) {
        throw AccessDenied(ReadOutcome.PermissionDenied(permissionDeniedMessage(error)))
    }
}

internal fun isBackgroundDenial(error: Throwable): Boolean {
    val text = generateSequence(error) { it.cause }
        .mapNotNull { it.message }
        .joinToString(" ")
    return text.contains("background", ignoreCase = true)
}

internal fun permissionDeniedMessage(error: Throwable): String {
    return if (isBackgroundDenial(error)) {
        "Lettura in background non consentita. Concedi il permesso di lettura in background, oppure tieni aperta l'app e usa Sincronizza ora."
    } else {
        "Health Connect ha negato la lettura. Controlla i permessi e riprova."
    }
}

internal fun isHistoryLimit(error: Throwable): Boolean {
    val text = generateSequence(error) { it.cause }
        .mapNotNull { it.message }
        .joinToString(" ")
    if (!text.contains("30")) return false
    return text.contains("day", ignoreCase = true) ||
        text.contains("histor", ignoreCase = true) ||
        text.contains("giorn", ignoreCase = true)
}
