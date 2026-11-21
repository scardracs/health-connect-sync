package it.healthsync.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactPayloadTest {
    private val rome = ZoneId.of("Europe/Rome")

    @Test
    fun everyBodyStaysUnderTheHardCap() {
        val fat = WireDay(
            date = "2024-06-01",
            steps = 18_432,
            distanceMeters = 14_200,
            activeKcal = 890,
            totalKcal = 2600,
            basalKcal = 1700,
            heartRate = listOf(48, 92, 188),
            restingHr = 51,
            hrvMillis = listOf(12, 40, 90),
            oxygenPercent = listOf(94, 97, 99),
            sleep = List(6) { index ->
                WireSleep(
                    minutes = 80,
                    start = "06-01 0$index:10",
                    end = "06-01 0${index + 1}:30",
                    stages = mapOf("d" to 40, "l" to 20, "r" to 10, "a" to 10),
                )
            },
            weightKg = 74.25,
            bodyFatPercent = 18.4,
            exercise = List(20) {
                WireExercise("high_intensity_interval_training_extra", 95, 640)
            },
            floors = 40,
            elevationMeters = 800,
            wheelchairPushes = 100,
            speedMps = listOf(1, 3, 8),
            powerWatts = listOf(40, 180, 400),
            cyclingRpm = listOf(50, 80, 110),
            stepCadence = listOf(80, 140, 190),
            hydrationMl = 2400,
            heightCm = 178,
            leanMassKg = 60.5,
            boneMassKg = 3.2,
            waterMassKg = 42.0,
            bodyTempC = 36.7,
            basalTempC = 36.4,
            bloodPressure = listOf(128, 82),
            glucoseMmol = 5.6,
            respiratoryRate = 15.0,
            vo2 = 46.2,
            nutrition = listOf(2200, 120, 240, 70),
        )
        val bodies = CompactPayloads.pack(listOf(fat), CompactPayloads.KIND_BACKFILL, "Europe/Rome", "1.2.0", "2026-11-18T16:00:00+01:00", 1)
        assertEquals(1, bodies.size)
        assertTrue(CompactPayloads.bytes(bodies.single().json) <= CompactPayloads.MAX_BYTES)
        assertTrue(bodies.single().json.contains("\"v\":3"))
        assertTrue(bodies.single().json.contains("\"k\":\"b\""))
    }

    @Test
    fun manyDaysAreSplitSoEachRequestFits() {
        val days = (1..40).map { index ->
            WireDay(
                date = LocalDate.of(2024, 1, 1).plusDays(index.toLong() - 1).toString(),
                steps = 5000L + index,
                distanceMeters = 4000,
                activeKcal = 300,
                totalKcal = 2100,
                heartRate = listOf(50, 70, 150),
                restingHr = 55,
                sleep = listOf(WireSleep(420, "01-01 23:10", "01-02 07:10", mapOf("d" to 90, "l" to 200, "r" to 80))),
                weightKg = 74.2,
                exercise = listOf(WireExercise("run", 40, 380), WireExercise("walk", 25, 120)),
            )
        }
        val bodies = CompactPayloads.pack(days, CompactPayloads.KIND_BACKFILL, "Europe/Rome", "1.2.0", "2026-11-18T16:00:00+01:00", 7)
        assertTrue(bodies.size > 1)
        assertTrue(bodies.all { CompactPayloads.bytes(it.json) <= CompactPayloads.MAX_BYTES })
        assertEquals(7, bodies.first().json.substringAfter("\"i\":").substringBefore(",").toInt())
        assertEquals(days.first().date, bodies.first().firstDate.toString())
        assertEquals(days.last().date, bodies.last().lastDate.toString())
    }

    @Test
    fun backfillDropsCalorieOnlyDays() {
        val calories = WireDay(date = "2012-01-01", activeKcal = 1800, totalKcal = 2200, basalKcal = 1500)
        val walked = calories.copy(date = "2012-01-02", steps = 4200)
        assertTrue(calories.isCalorieOnly())
        assertFalse(walked.isCalorieOnly())
        assertEquals(listOf("2012-01-02"), CompactPayloads.forBackfill(listOf(calories, walked)).map { it.date })
    }

    @Test
    fun snapshotKeepsStepsSleepWeightAndWorkoutsOnTheRightDay() {
        val start = Instant.parse("2026-01-16T05:00:00Z")
        val end = Instant.parse("2026-01-17T05:00:00Z")
        val snapshot = HealthSnapshot(
            windowStart = start,
            windowEnd = end,
            zone = rome,
            daily = listOf(
                DailyMetrics(
                    date = LocalDate.parse("2026-01-16"),
                    steps = 8432,
                    distanceMeters = 6120.4,
                    activeCaloriesKcal = 320.2,
                    totalCaloriesKcal = 2100.0,
                    heartRate = BpmStats(52, 72, 145, 120),
                    restingHeartRateBpm = 58,
                ),
            ),
            sleep = listOf(
                SleepSessionInput(
                    start = Instant.parse("2026-01-16T22:00:00Z"),
                    end = Instant.parse("2026-01-17T06:00:00Z"),
                    title = null,
                    notes = null,
                    stages = listOf(
                        SleepStageInput("deep", Instant.parse("2026-01-16T22:30:00Z"), Instant.parse("2026-01-17T00:00:00Z")),
                    ),
                ),
            ),
            weight = listOf(WeightInput(Instant.parse("2026-01-16T07:00:00Z"), 74.2)),
            exercise = listOf(
                ExerciseInput(
                    start = Instant.parse("2026-01-16T17:00:00Z"),
                    end = Instant.parse("2026-01-16T17:45:00Z"),
                    exerciseType = "running",
                    title = "Parco",
                    notes = null,
                    activeCaloriesKcal = 420.0,
                    distanceMeters = 7000.0,
                ),
            ),
            counts = mapOf("steps" to 12, "sleep" to 1, "weight" to 1, "exercise" to 1, "calories" to 1),
        )
        val day = CompactPayloads.daysFrom(snapshot).single()
        assertEquals(8432L, day.steps)
        assertEquals(listOf(52, 72, 145), day.heartRate)
        assertEquals(58, day.restingHr)
        assertEquals(74.2, day.weightKg!!, 0.05)
        assertEquals("run", day.exercise!!.single().type)
        assertEquals(45, day.exercise.single().minutes)
        assertEquals(480, day.sleep!!.single().minutes)
        assertEquals(90, day.sleep.single().stages!!["d"])
        val body = CompactPayloads.pack(listOf(day), CompactPayloads.KIND_INCREMENTAL, "Europe/Rome", "1.2.0", "2026-01-17T06:00:00+01:00", 1).single()
        assertTrue(CompactPayloads.bytes(body.json) <= CompactPayloads.MAX_BYTES)
        assertTrue(body.json.contains("\"st\":8432"))
        assertTrue(body.json.contains("2026-01-16"))
        assertFalse(body.json.contains("16-01-2026"))
    }
}
