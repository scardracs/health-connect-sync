package it.healthsync.app.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Wire format for one webhook POST. Short keys keep a body inside [MAX_BYTES].
 * The receiver drops anything past about 4,000 characters, so 3,500 bytes is a hard cap.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class WirePayload(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) @kotlinx.serialization.SerialName("v") val version: Int = 3,
    @kotlinx.serialization.SerialName("k") val kind: String,
    @kotlinx.serialization.SerialName("i") val index: Int,
    @kotlinx.serialization.SerialName("z") val zone: String,
    @kotlinx.serialization.SerialName("app") val app: String,
    @kotlinx.serialization.SerialName("t") val syncedAt: String,
    @kotlinx.serialization.SerialName("a") val start: String,
    @kotlinx.serialization.SerialName("b") val end: String,
    @kotlinx.serialization.SerialName("d") val days: List<WireDay>,
)

@Serializable
data class WireDay(
    @kotlinx.serialization.SerialName("d") val date: String,
    @kotlinx.serialization.SerialName("st") val steps: Long? = null,
    @kotlinx.serialization.SerialName("dm") val distanceMeters: Int? = null,
    @kotlinx.serialization.SerialName("ak") val activeKcal: Int? = null,
    @kotlinx.serialization.SerialName("tk") val totalKcal: Int? = null,
    @kotlinx.serialization.SerialName("bk") val basalKcal: Int? = null,
    @kotlinx.serialization.SerialName("hr") val heartRate: List<Int>? = null,
    @kotlinx.serialization.SerialName("rh") val restingHr: Int? = null,
    @kotlinx.serialization.SerialName("hv") val hrvMillis: List<Int>? = null,
    @kotlinx.serialization.SerialName("o2") val oxygenPercent: List<Int>? = null,
    @kotlinx.serialization.SerialName("sl") val sleep: List<WireSleep>? = null,
    @kotlinx.serialization.SerialName("wt") val weightKg: Double? = null,
    @kotlinx.serialization.SerialName("bf") val bodyFatPercent: Double? = null,
    @kotlinx.serialization.SerialName("ex") val exercise: List<WireExercise>? = null,
    @kotlinx.serialization.SerialName("fl") val floors: Int? = null,
    @kotlinx.serialization.SerialName("el") val elevationMeters: Int? = null,
    @kotlinx.serialization.SerialName("wp") val wheelchairPushes: Int? = null,
    @kotlinx.serialization.SerialName("sp") val speedMps: List<Int>? = null,
    @kotlinx.serialization.SerialName("pw") val powerWatts: List<Int>? = null,
    @kotlinx.serialization.SerialName("cc") val cyclingRpm: List<Int>? = null,
    @kotlinx.serialization.SerialName("sc") val stepCadence: List<Int>? = null,
    @kotlinx.serialization.SerialName("hy") val hydrationMl: Int? = null,
    @kotlinx.serialization.SerialName("ht") val heightCm: Int? = null,
    @kotlinx.serialization.SerialName("lm") val leanMassKg: Double? = null,
    @kotlinx.serialization.SerialName("bn") val boneMassKg: Double? = null,
    @kotlinx.serialization.SerialName("wa") val waterMassKg: Double? = null,
    @kotlinx.serialization.SerialName("bt") val bodyTempC: Double? = null,
    @kotlinx.serialization.SerialName("bb") val basalTempC: Double? = null,
    @kotlinx.serialization.SerialName("bp") val bloodPressure: List<Int>? = null,
    @kotlinx.serialization.SerialName("bg") val glucoseMmol: Double? = null,
    @kotlinx.serialization.SerialName("rr") val respiratoryRate: Double? = null,
    @kotlinx.serialization.SerialName("vo") val vo2: Double? = null,
    @kotlinx.serialization.SerialName("nu") val nutrition: List<Int>? = null,
)

@Serializable
data class WireSleep(
    @kotlinx.serialization.SerialName("m") val minutes: Int,
    @kotlinx.serialization.SerialName("s") val start: String,
    @kotlinx.serialization.SerialName("e") val end: String,
    @kotlinx.serialization.SerialName("g") val stages: Map<String, Int>? = null,
)

@Serializable
data class WireExercise(
    @kotlinx.serialization.SerialName("y") val type: String,
    @kotlinx.serialization.SerialName("m") val minutes: Int,
    @kotlinx.serialization.SerialName("k") val kcal: Int? = null,
)

data class WireBody(
    val json: String,
    val firstDate: LocalDate,
    val lastDate: LocalDate,
)

object CompactPayloads {
    const val MAX_BYTES = 3_500
    const val MIN_GAP_SECONDS = 60L
    const val KIND_BACKFILL = "b"
    const val KIND_INCREMENTAL = "i"

    private val json = Json {
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = false
    }
    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    fun bytes(body: String): Int = body.toByteArray(Charsets.UTF_8).size

    fun daysFrom(snapshot: HealthSnapshot): List<WireDay> {
        val zone = snapshot.zone
        val byDate = linkedMapOf<LocalDate, WireDay>()
        fun edit(date: LocalDate, block: (WireDay) -> WireDay) {
            val current = byDate[date] ?: WireDay(date = date.toString())
            byDate[date] = block(current)
        }
        for (day in snapshot.daily.sortedBy { it.date }) {
            if (!dateOverlaps(day.date, zone, snapshot.windowStart, snapshot.windowEnd)) continue
            edit(day.date) {
                it.copy(
                    steps = day.steps?.takeIf { value -> value > 0 },
                    distanceMeters = day.distanceMeters?.takeIf { value -> value > 0 }?.toInt(),
                    activeKcal = day.activeCaloriesKcal?.toInt(),
                    totalKcal = day.totalCaloriesKcal?.toInt(),
                    basalKcal = day.basalCaloriesKcal?.toInt(),
                    heartRate = day.heartRate?.let { hr -> listOf(hr.min.toInt(), hr.avg.toInt(), hr.max.toInt()) },
                    restingHr = day.restingHeartRateBpm?.toInt(),
                    floors = day.floorsClimbed?.takeIf { value -> value > 0 }?.toInt(),
                    elevationMeters = day.elevationMeters?.takeIf { value -> value > 0 }?.toInt(),
                    wheelchairPushes = day.wheelchairPushes?.takeIf { value -> value > 0 }?.toInt(),
                    speedMps = ints(day.speedMetersPerSecond),
                    powerWatts = ints(day.powerWatts),
                    cyclingRpm = ints(day.cyclingCadenceRpm),
                    stepCadence = ints(day.stepsCadencePerMinute),
                    hydrationMl = day.hydrationMilliliters?.takeIf { value -> value > 0 }?.toInt(),
                )
            }
        }
        statByDate(snapshot.hrvSamples, zone, snapshot.windowStart, snapshot.windowEnd).forEach { (date, values) ->
            edit(date) { it.copy(hrvMillis = values) }
        }
        statByDate(snapshot.oxygenSamples, zone, snapshot.windowStart, snapshot.windowEnd).forEach { (date, values) ->
            edit(date) { it.copy(oxygenPercent = values) }
        }
        attachSamples(snapshot, zone, ::edit)
        return byDate.values.filter { !it.isEmpty() }.sortedBy { it.date }
    }

    /** Backfill drops days whose only numbers are calorie estimates. */
    fun forBackfill(days: List<WireDay>): List<WireDay> = days.filterNot { it.isCalorieOnly() }

    fun pack(
        days: List<WireDay>,
        kind: String,
        zone: String,
        app: String,
        syncedAt: String,
        startIndex: Int,
    ): List<WireBody> {
        if (days.isEmpty()) return emptyList()
        val fitted = days.map { fit(it, kind, zone, app, syncedAt) }
        val groups = mutableListOf<MutableList<WireDay>>()
        for (day in fitted) {
            val current = groups.lastOrNull()
            if (current == null) {
                groups += mutableListOf(day)
                continue
            }
            val trial = current + day
            if (bytes(encode(trial, kind, zone, app, syncedAt, 1)) > MAX_BYTES) {
                groups += mutableListOf(day)
            } else {
                current += day
            }
        }
        return groups.mapIndexed { offset, group ->
            WireBody(
                json = encode(group, kind, zone, app, syncedAt, startIndex + offset),
                firstDate = LocalDate.parse(group.first().date),
                lastDate = LocalDate.parse(group.last().date),
            )
        }
    }

    fun encode(days: List<WireDay>, kind: String, zone: String, app: String, syncedAt: String, index: Int): String {
        val payload = WirePayload(
            kind = kind,
            index = index,
            zone = zone,
            app = app,
            syncedAt = syncedAt,
            start = days.first().date,
            end = days.last().date,
            days = days,
        )
        return json.encodeToString(payload)
    }

    private fun fit(day: WireDay, kind: String, zone: String, app: String, syncedAt: String): WireDay {
        var current = day
        val steps: List<(WireDay) -> WireDay> = listOf(
            { it.copy(sleep = it.sleep?.map { session -> session.copy(stages = null) }) },
            { it.copy(exercise = it.exercise?.take(6)) },
            { it.copy(hrvMillis = null, oxygenPercent = null, speedMps = null, powerWatts = null, cyclingRpm = null, stepCadence = null) },
            { it.copy(nutrition = null, glucoseMmol = null, bodyTempC = null, basalTempC = null, vo2 = null, bloodPressure = null, heightCm = null) },
            { it.copy(exercise = it.exercise?.take(1)) },
            { it.copy(sleep = it.sleep?.take(1)?.map { session -> session.copy(stages = null) }) },
            { it.copy(leanMassKg = null, boneMassKg = null, waterMassKg = null, respiratoryRate = null, hydrationMl = null) },
        )
        for (step in steps) {
            if (bytes(encode(listOf(current), kind, zone, app, syncedAt, 1)) <= MAX_BYTES) return current
            current = step(current)
        }
        check(bytes(encode(listOf(current), kind, zone, app, syncedAt, 1)) <= MAX_BYTES) {
            "A single day still exceeds $MAX_BYTES bytes after shrinking"
        }
        return current
    }

    private fun attachSamples(
        snapshot: HealthSnapshot,
        zone: ZoneId,
        edit: (LocalDate, (WireDay) -> WireDay) -> Unit,
    ) {
        val start = snapshot.windowStart
        val end = snapshot.windowEnd
        fun dateOf(time: java.time.Instant) = time.atZone(zone).toLocalDate()
        snapshot.sleep.filter { inWindow(it.start, start, end) }.groupBy { dateOf(it.start) }.forEach { (date, sessions) ->
            edit(date) {
                it.copy(sleep = sessions.map { session -> session.toWire(zone) })
            }
        }
        snapshot.weight.filter { inWindow(it.time, start, end) }.groupBy { dateOf(it.time) }.forEach { (date, rows) ->
            edit(date) { it.copy(weightKg = rows.maxBy { row -> row.time }.weightKg.rounded()) }
        }
        snapshot.bodyFat.filter { inWindow(it.time, start, end) }.groupBy { dateOf(it.time) }.forEach { (date, rows) ->
            edit(date) { it.copy(bodyFatPercent = rows.maxBy { row -> row.time }.percentage.rounded()) }
        }
        snapshot.exercise.filter { inWindow(it.start, start, end) }.groupBy { dateOf(it.start) }.forEach { (date, rows) ->
            edit(date) {
                it.copy(exercise = rows.map { row ->
                    WireExercise(shortExercise(row.exerciseType), durationMinutes(row.start, row.end).toInt(), row.activeCaloriesKcal?.toInt())
                })
            }
        }
        last(snapshot.heightMeters, zone, start, end) { date, value -> edit(date) { it.copy(heightCm = (value * 100).toInt()) } }
        last(snapshot.leanBodyMassKg, zone, start, end) { date, value -> edit(date) { it.copy(leanMassKg = value.rounded()) } }
        last(snapshot.boneMassKg, zone, start, end) { date, value -> edit(date) { it.copy(boneMassKg = value.rounded()) } }
        last(snapshot.bodyWaterMassKg, zone, start, end) { date, value -> edit(date) { it.copy(waterMassKg = value.rounded()) } }
        last(snapshot.respiratoryRate, zone, start, end) { date, value -> edit(date) { it.copy(respiratoryRate = value.rounded()) } }
        lastTemp(snapshot.bodyTemperature, zone, start, end) { date, row -> edit(date) { it.copy(bodyTempC = row.celsius.rounded()) } }
        lastTemp(snapshot.basalBodyTemperature, zone, start, end) { date, row -> edit(date) { it.copy(basalTempC = row.celsius.rounded()) } }
        snapshot.bloodPressure.filter { inWindow(it.time, start, end) }.groupBy { dateOf(it.time) }.forEach { (date, rows) ->
            val lastRow = rows.maxBy { it.time }
            edit(date) { it.copy(bloodPressure = listOf(lastRow.systolicMmHg.toInt(), lastRow.diastolicMmHg.toInt())) }
        }
        last(snapshot.bloodGlucose.map { TimedValue(it.time, it.millimolesPerLiter) }, zone, start, end) { date, value ->
            edit(date) { it.copy(glucoseMmol = value.rounded()) }
        }
        last(snapshot.vo2Max.map { TimedValue(it.time, it.millilitersPerMinutePerKg) }, zone, start, end) { date, value ->
            edit(date) { it.copy(vo2 = value.rounded()) }
        }
        snapshot.nutrition.filter { inWindow(it.start, start, end) }.groupBy { dateOf(it.start) }.forEach { (date, meals) ->
            val energy = meals.mapNotNull { it.energyKcal }.sum().toInt()
            val protein = meals.mapNotNull { it.proteinGrams }.sum().toInt()
            val carbs = meals.mapNotNull { it.carbohydrateGrams }.sum().toInt()
            val fat = meals.mapNotNull { it.fatGrams }.sum().toInt()
            if (energy + protein + carbs + fat > 0) {
                edit(date) { it.copy(nutrition = listOf(energy, protein, carbs, fat)) }
            }
        }
    }

    private fun ints(stat: TripleStat?): List<Int>? = stat?.let { listOf(it.min.toInt(), it.avg.toInt(), it.max.toInt()) }
}

private fun statByDate(
    samples: List<TimedValue>,
    zone: ZoneId,
    start: java.time.Instant,
    end: java.time.Instant,
): Map<LocalDate, List<Int>> {
    return samples.filter { inWindow(it.time, start, end) }
        .groupBy { it.time.atZone(zone).toLocalDate() }
        .mapValues { (_, rows) ->
            val values = rows.map { it.value }
            listOf(values.min().toInt(), (values.sum() / values.size).toInt(), values.max().toInt())
        }
}

fun WireDay.isEmpty(): Boolean {
    return steps == null && distanceMeters == null && activeKcal == null && totalKcal == null && basalKcal == null &&
        heartRate == null && restingHr == null && hrvMillis == null && oxygenPercent == null && sleep == null &&
        weightKg == null && bodyFatPercent == null && exercise == null && floors == null && elevationMeters == null &&
        wheelchairPushes == null && speedMps == null && powerWatts == null && cyclingRpm == null && stepCadence == null &&
        hydrationMl == null && heightCm == null && leanMassKg == null && boneMassKg == null && waterMassKg == null &&
        bodyTempC == null && basalTempC == null && bloodPressure == null && glucoseMmol == null && respiratoryRate == null &&
        vo2 == null && nutrition == null
}

/** True when Health Connect only supplied calorie estimates for the day. */
fun WireDay.isCalorieOnly(): Boolean {
    if (isEmpty()) return false
    val copy = copy(activeKcal = null, totalKcal = null, basalKcal = null)
    return copy.isEmpty()
}

private fun SleepSessionInput.toWire(zone: ZoneId): WireSleep {
    val stages = stages.groupBy { stageCode(it.stage) }
        .mapValues { (_, rows) -> rows.sumOf { durationMinutes(it.start, it.end).toInt() } }
        .filterValues { it > 0 }
    return WireSleep(
        minutes = durationMinutes(start, end).toInt(),
        start = start.atZone(zone).truncatedTo(ChronoUnit.MINUTES).format(DateTimeFormatter.ofPattern("MM-dd HH:mm")),
        end = end.atZone(zone).truncatedTo(ChronoUnit.MINUTES).format(DateTimeFormatter.ofPattern("MM-dd HH:mm")),
        stages = stages.ifEmpty { null },
    )
}

private fun stageCode(stage: String): String = when (stage) {
    "deep" -> "d"
    "light" -> "l"
    "rem" -> "r"
    "awake" -> "a"
    "sleeping" -> "s"
    "out_of_bed" -> "o"
    "awake_in_bed" -> "i"
    else -> "u"
}

private fun shortExercise(type: String): String = when (type) {
    "running" -> "run"
    "running_treadmill" -> "run_tm"
    "walking" -> "walk"
    "biking" -> "bike"
    "biking_stationary" -> "bike_st"
    "strength_training" -> "strength"
    "high_intensity_interval_training" -> "hiit"
    "swimming_pool" -> "swim"
    "swimming_open_water" -> "swim_ow"
    else -> if (type.length <= 12) type else type.take(12)
}

private fun last(
    samples: List<TimedValue>,
    zone: ZoneId,
    start: java.time.Instant,
    end: java.time.Instant,
    apply: (LocalDate, Double) -> Unit,
) {
    samples.filter { inWindow(it.time, start, end) }
        .groupBy { it.time.atZone(zone).toLocalDate() }
        .forEach { (date, rows) -> apply(date, rows.maxBy { it.time }.value) }
}

private fun lastTemp(
    samples: List<TemperatureInput>,
    zone: ZoneId,
    start: java.time.Instant,
    end: java.time.Instant,
    apply: (LocalDate, TemperatureInput) -> Unit,
) {
    samples.filter { inWindow(it.time, start, end) }
        .groupBy { it.time.atZone(zone).toLocalDate() }
        .forEach { (date, rows) -> apply(date, rows.maxBy { it.time }) }
}

private fun dateOverlaps(date: LocalDate, zone: ZoneId, start: java.time.Instant, end: java.time.Instant): Boolean {
    val dayStart = date.atStartOfDay(zone).toInstant()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
    return dayStart.isBefore(end) && dayEnd.isAfter(start)
}

private fun inWindow(time: java.time.Instant, start: java.time.Instant, end: java.time.Instant): Boolean {
    return !time.isBefore(start) && time.isBefore(end)
}
