package it.healthsync.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadBuilderTest {
    private val rome = ZoneId.of("Europe/Rome")
    private val start = Instant.parse("2026-01-16T05:00:00Z") // 06:00 CET
    private val end = Instant.parse("2026-01-17T05:00:00Z") // 06:00 CET next day

    @Test
    fun buildsDailyAggregatesSleepWeightAndWorkouts() {
        val snapshot = HealthSnapshot(
            windowStart = start,
            windowEnd = end,
            zone = rome,
            daily = listOf(
                DailyMetrics(date = LocalDate.parse("2026-01-15")),
                DailyMetrics(
                    date = LocalDate.parse("2026-01-16"),
                    steps = 8432,
                    distanceMeters = 6120.5,
                    activeCaloriesKcal = 320.4,
                    totalCaloriesKcal = 2100.0,
                    heartRate = BpmStats(min = 52, avg = 72, max = 145, sampleCount = 120),
                    restingHeartRateBpm = 58,
                    hydrationMilliliters = 1800.0,
                ),
                DailyMetrics(
                    date = LocalDate.parse("2026-02-01"),
                    steps = 99,
                ),
            ),
            hrvSamples = listOf(
                TimedValue(Instant.parse("2026-01-16T06:00:00Z"), 20.0),
                TimedValue(Instant.parse("2026-01-16T20:00:00Z"), 65.0),
                TimedValue(Instant.parse("2026-01-15T22:30:00Z"), 5.0),
            ),
            oxygenSamples = listOf(
                TimedValue(Instant.parse("2026-01-16T07:00:00Z"), 97.0),
                TimedValue(Instant.parse("2026-01-16T22:00:00Z"), 99.0),
            ),
            nutrition = listOf(
                NutritionSample(Instant.parse("2026-01-16T07:30:00Z"), 300.0, 20.0, 25.0, 8.0),
                NutritionSample(Instant.parse("2026-01-16T12:00:00Z"), 200.0, 10.0, 15.0, 2.0),
            ),
            sleep = listOf(
                SleepSessionInput(
                    start = Instant.parse("2026-01-15T20:00:00Z"),
                    end = Instant.parse("2026-01-16T04:00:00Z"),
                    title = "notte precedente",
                    notes = null,
                    stages = emptyList(),
                ),
                SleepSessionInput(
                    start = Instant.parse("2026-01-16T22:00:00Z"),
                    end = Instant.parse("2026-01-17T06:00:00Z"),
                    title = "  ",
                    notes = "Sveglia \"presto\"",
                    stages = listOf(
                        SleepStageInput("deep", Instant.parse("2026-01-16T22:30:00Z"), Instant.parse("2026-01-17T00:00:00Z")),
                        SleepStageInput("rem", Instant.parse("2026-01-17T04:00:00Z"), Instant.parse("2026-01-17T05:00:00Z")),
                    ),
                ),
            ),
            weight = listOf(
                WeightInput(Instant.parse("2026-01-16T07:00:00Z"), 74.2),
                WeightInput(end, 80.0),
            ),
            bodyFat = listOf(BodyFatInput(Instant.parse("2026-01-16T07:00:00Z"), 18.5)),
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
            unavailableMetrics = listOf("oxygen_saturation_extra", "steps", "steps"),
        )

        val json = PayloadBuilder.buildJson(snapshot, "1.0.0", end)
        assertFalse(json.contains(":null"))
        assertFalse(json.contains("Bearer"))

        val root = Json.parseToJsonElement(json).jsonObject
        assertEquals(2, root.getValue("schemaVersion").jsonPrimitive.int)
        assertEquals("incremental", root.getValue("kind").jsonPrimitive.content)
        assertFalse(root.containsKey("chunk"))
        assertEquals("Europe/Rome", root.getValue("timezone").jsonPrimitive.content)
        assertEquals("1.0.0", root.getValue("appVersion").jsonPrimitive.content)
        assertEquals("2026-01-17T06:00:00+01:00", root.getValue("syncedAt").jsonPrimitive.content)
        assertFalse(root.getValue("historyLimited").jsonPrimitive.boolean)

        val window = root.getValue("window").jsonObject
        assertEquals("2026-01-16T06:00:00+01:00", window.getValue("start").jsonPrimitive.content)
        assertEquals("2026-01-17T06:00:00+01:00", window.getValue("end").jsonPrimitive.content)

        val days = root.getValue("days").jsonArray
        assertEquals(1, days.size)
        val day = days[0].jsonObject
        assertEquals("2026-01-16", day.getValue("date").jsonPrimitive.content)
        assertEquals(8432L, day.getValue("steps").jsonPrimitive.long)
        assertEquals(6120.5, day.getValue("distanceMeters").jsonPrimitive.content.toDouble(), 0.001)
        assertEquals(320.4, day.getValue("activeCaloriesKcal").jsonPrimitive.content.toDouble(), 0.001)
        assertEquals(2100.0, day.getValue("totalCaloriesKcal").jsonPrimitive.content.toDouble(), 0.001)
        val hr = day.getValue("heartRate").jsonObject
        assertEquals(52L, hr.getValue("min").jsonPrimitive.long)
        assertEquals(72L, hr.getValue("avg").jsonPrimitive.long)
        assertEquals(145L, hr.getValue("max").jsonPrimitive.long)
        assertEquals(120L, hr.getValue("sampleCount").jsonPrimitive.long)
        assertEquals(58L, day.getValue("restingHeartRateBpm").jsonPrimitive.long)
        val hrv = day.getValue("heartRateVariabilityRmssdMillis").jsonObject
        assertEquals(20.0, hrv.getValue("min").jsonPrimitive.content.toDouble(), 0.001)
        assertEquals(42.5, hrv.getValue("avg").jsonPrimitive.content.toDouble(), 0.001)
        assertEquals(65.0, hrv.getValue("max").jsonPrimitive.content.toDouble(), 0.001)
        assertEquals(2, hrv.getValue("sampleCount").jsonPrimitive.int)
        val spo2 = day.getValue("oxygenSaturationPercent").jsonObject
        assertEquals(97.0, spo2.getValue("min").jsonPrimitive.content.toDouble(), 0.001)
        assertEquals(98.0, spo2.getValue("avg").jsonPrimitive.content.toDouble(), 0.001)
        assertEquals(99.0, spo2.getValue("max").jsonPrimitive.content.toDouble(), 0.001)
        val meals = day.getValue("nutrition").jsonObject
        assertEquals(500.0, meals.getValue("energyKcal").jsonPrimitive.content.toDouble(), 0.001)
        assertEquals(30.0, meals.getValue("proteinGrams").jsonPrimitive.content.toDouble(), 0.001)
        assertEquals(40.0, meals.getValue("carbohydrateGrams").jsonPrimitive.content.toDouble(), 0.001)
        assertEquals(10.0, meals.getValue("fatGrams").jsonPrimitive.content.toDouble(), 0.001)
        assertEquals(2, meals.getValue("mealCount").jsonPrimitive.int)
        assertEquals(1800.0, day.getValue("hydrationMilliliters").jsonPrimitive.content.toDouble(), 0.001)

        val sleep = root.getValue("sleepSessions").jsonArray
        assertEquals(1, sleep.size)
        val session = sleep[0].jsonObject
        assertEquals("2026-01-16T23:00:00+01:00", session.getValue("start").jsonPrimitive.content)
        assertEquals("2026-01-17T07:00:00+01:00", session.getValue("end").jsonPrimitive.content)
        assertEquals(480L, session.getValue("durationMinutes").jsonPrimitive.long)
        assertFalse(session.containsKey("title"))
        assertEquals("Sveglia \"presto\"", session.getValue("notes").jsonPrimitive.content)
        val stages = session.getValue("stages").jsonArray
        assertEquals("deep", stages[0].jsonObject.getValue("stage").jsonPrimitive.content)
        assertEquals(90L, stages[0].jsonObject.getValue("durationMinutes").jsonPrimitive.long)
        assertEquals("rem", stages[1].jsonObject.getValue("stage").jsonPrimitive.content)
        assertEquals(60L, stages[1].jsonObject.getValue("durationMinutes").jsonPrimitive.long)

        val weight = root.getValue("weightReadings").jsonArray
        assertEquals(1, weight.size)
        assertEquals(74.2, weight[0].jsonObject.getValue("weightKg").jsonPrimitive.content.toDouble(), 0.001)
        val fat = root.getValue("bodyFatReadings").jsonArray
        assertEquals(18.5, fat[0].jsonObject.getValue("percentage").jsonPrimitive.content.toDouble(), 0.001)

        val workout = root.getValue("exerciseSessions").jsonArray[0].jsonObject
        assertEquals("running", workout.getValue("exerciseType").jsonPrimitive.content)
        assertEquals(45L, workout.getValue("durationMinutes").jsonPrimitive.long)
        assertEquals(420.0, workout.getValue("activeCaloriesKcal").jsonPrimitive.content.toDouble(), 0.001)
        assertEquals(7000.0, workout.getValue("distanceMeters").jsonPrimitive.content.toDouble(), 0.001)
        assertEquals("Parco", workout.getValue("title").jsonPrimitive.content)
        assertFalse(workout.containsKey("notes"))

        val unavailable = root.getValue("unavailableMetrics").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("oxygen_saturation_extra", "steps"), unavailable)
    }

    @Test
    fun attributesSamplesToRomeAcrossUtcMidnight() {
        val sampleAtRomeNextMorning = Instant.parse("2026-01-15T23:30:00Z") // 00:30 on 16 Jan in Rome
        val snapshot = HealthSnapshot(
            windowStart = Instant.parse("2026-01-15T20:00:00Z"),
            windowEnd = Instant.parse("2026-01-16T04:00:00Z"),
            zone = rome,
            hrvSamples = listOf(TimedValue(sampleAtRomeNextMorning, 40.0)),
        )
        val payload = PayloadBuilder.build(snapshot, "1.0.0", snapshot.windowEnd)
        assertEquals(1, payload.days.size)
        assertEquals("2026-01-16", payload.days[0].date)
        assertEquals(40.0, payload.days[0].heartRateVariabilityRmssdMillis!!.avg, 0.001)
    }

    @Test
    fun emptySnapshotStillDeclaresTimezoneAndVersion() {
        val snapshot = HealthSnapshot(
            windowStart = start,
            windowEnd = end,
            zone = rome,
        )
        val payload = PayloadBuilder.build(snapshot, "1.0.0", end)
        assertEquals("Europe/Rome", payload.timezone)
        assertEquals("1.0.0", payload.appVersion)
        assertTrue(payload.days.isEmpty())
        assertTrue(payload.sleepSessions.isEmpty())
        assertNull(payload.days.firstOrNull())
    }

    @Test
    fun backfillChunkIsMarkedAndResumableByIndex() {
        val snapshot = HealthSnapshot(
            windowStart = start,
            windowEnd = end,
            zone = rome,
            historyLimited = true,
            daily = listOf(DailyMetrics(date = java.time.LocalDate.parse("2026-01-16"), floorsClimbed = 12.0)),
        )
        val payload = PayloadBuilder.build(
            snapshot,
            "1.1.0",
            end,
            kind = HealthPayload.KIND_BACKFILL,
            chunkIndex = 2,
            chunkTotal = 10,
        )
        assertEquals(HealthPayload.KIND_BACKFILL, payload.kind)
        assertEquals(2, payload.chunk!!.index)
        assertEquals(10, payload.chunk.total)
        assertEquals("2026-01-16T06:00:00+01:00", payload.chunk.start)
        assertEquals("2026-01-17T06:00:00+01:00", payload.chunk.end)
        assertEquals(12.0, payload.days.single().floorsClimbed!!, 0.001)
        assertTrue(payload.historyLimited)
        val json = PayloadBuilder.buildJson(
            snapshot,
            "1.1.0",
            end,
            HealthPayload.KIND_BACKFILL,
            2,
            10,
        )
        assertTrue(json.contains("\"kind\":\"backfill\""))
        assertFalse(json.contains(":null"))
    }

    @Test
    fun keepsZeroStepsBecauseZeroIsARealTotal() {
        val snapshot = HealthSnapshot(
            windowStart = start,
            windowEnd = end,
            zone = rome,
            daily = listOf(DailyMetrics(date = LocalDate.parse("2026-01-16"), steps = 0)),
        )
        val payload = PayloadBuilder.build(snapshot, "1.0.0", end)
        assertEquals(0L, payload.days.single().steps)
    }
}
