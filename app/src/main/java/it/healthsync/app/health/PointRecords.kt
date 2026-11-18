package it.healthsync.app.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import it.healthsync.app.domain.BloodPressureInput
import it.healthsync.app.domain.GlucoseInput
import it.healthsync.app.domain.TemperatureInput
import it.healthsync.app.domain.TimedValue
import it.healthsync.app.domain.Vo2Input
import java.time.Instant

internal data class PointBundle(
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
)

/**
 * Paginated raw reads for instantaneous records. Daily totals stay on the aggregate path.
 * Menstruation, ovulation, sexual activity, and mindfulness are intentionally not read.
 */
internal suspend fun HealthConnectGateway.readPointRecords(
    client: HealthConnectClient,
    grantedIds: Set<String>,
    start: Instant,
    end: Instant,
): PointBundle {
    return PointBundle(
        heightMeters = if ("height" in grantedIds) {
            readAll<HeightRecord>(client, start, end).map { TimedValue(it.time, it.height.inMeters) }
        } else {
            emptyList()
        },
        leanBodyMassKg = if ("lean_body_mass" in grantedIds) {
            readAll<LeanBodyMassRecord>(client, start, end).map { TimedValue(it.time, it.mass.inKilograms) }
        } else {
            emptyList()
        },
        boneMassKg = if ("bone_mass" in grantedIds) {
            readAll<BoneMassRecord>(client, start, end).map { TimedValue(it.time, it.mass.inKilograms) }
        } else {
            emptyList()
        },
        bodyWaterMassKg = if ("body_water_mass" in grantedIds) {
            readAll<BodyWaterMassRecord>(client, start, end).map { TimedValue(it.time, it.mass.inKilograms) }
        } else {
            emptyList()
        },
        respiratoryRate = if ("respiratory_rate" in grantedIds) {
            readAll<RespiratoryRateRecord>(client, start, end).map { TimedValue(it.time, it.rate) }
        } else {
            emptyList()
        },
        basalMetabolicRateKcalPerDay = if ("basal_metabolic_rate" in grantedIds) {
            readAll<BasalMetabolicRateRecord>(client, start, end).map {
                TimedValue(it.time, it.basalMetabolicRate.inKilocaloriesPerDay)
            }
        } else {
            emptyList()
        },
        bodyTemperature = if ("body_temperature" in grantedIds) {
            readAll<BodyTemperatureRecord>(client, start, end).map {
                TemperatureInput(it.time, it.temperature.inCelsius, bodyTemperatureLocation(it.measurementLocation))
            }
        } else {
            emptyList()
        },
        basalBodyTemperature = if ("basal_body_temperature" in grantedIds) {
            readAll<BasalBodyTemperatureRecord>(client, start, end).map {
                TemperatureInput(it.time, it.temperature.inCelsius, bodyTemperatureLocation(it.measurementLocation))
            }
        } else {
            emptyList()
        },
        bloodPressure = if ("blood_pressure" in grantedIds) {
            readAll<BloodPressureRecord>(client, start, end).map {
                BloodPressureInput(
                    time = it.time,
                    systolicMmHg = it.systolic.inMillimetersOfMercury,
                    diastolicMmHg = it.diastolic.inMillimetersOfMercury,
                    bodyPosition = bloodPressurePosition(it.bodyPosition),
                    measurementLocation = bloodPressureLocation(it.measurementLocation),
                )
            }
        } else {
            emptyList()
        },
        bloodGlucose = if ("blood_glucose" in grantedIds) {
            readAll<BloodGlucoseRecord>(client, start, end).map {
                GlucoseInput(
                    time = it.time,
                    millimolesPerLiter = it.level.inMillimolesPerLiter,
                    specimenSource = glucoseSpecimen(it.specimenSource),
                    relationToMeal = glucoseRelationToMeal(it.relationToMeal),
                )
            }
        } else {
            emptyList()
        },
        vo2Max = if ("vo2_max" in grantedIds) {
            readAll<Vo2MaxRecord>(client, start, end).map {
                Vo2Input(it.time, it.vo2MillilitersPerMinuteKilogram, vo2Method(it.measurementMethod))
            }
        } else {
            emptyList()
        },
    )
}
