package it.healthsync.app.health

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.WheelchairPushesRecord

data class MetricPermission(
    val id: String,
    val permission: String,
    val label: String,
)

object HealthPermissions {
    val metrics: List<MetricPermission> = listOf(
        MetricPermission("steps", HealthPermission.getReadPermission(StepsRecord::class), "Passi"),
        MetricPermission("distance", HealthPermission.getReadPermission(DistanceRecord::class), "Distanza"),
        MetricPermission("floors", HealthPermission.getReadPermission(FloorsClimbedRecord::class), "Piani"),
        MetricPermission("elevation", HealthPermission.getReadPermission(ElevationGainedRecord::class), "Dislivello"),
        MetricPermission("speed", HealthPermission.getReadPermission(SpeedRecord::class), "Velocità"),
        MetricPermission("power", HealthPermission.getReadPermission(PowerRecord::class), "Potenza"),
        MetricPermission(
            "active_calories",
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            "Calorie attive",
        ),
        MetricPermission(
            "total_calories",
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            "Calorie totali",
        ),
        MetricPermission(
            "basal_metabolic_rate",
            HealthPermission.getReadPermission(BasalMetabolicRateRecord::class),
            "Metabolismo basale",
        ),
        MetricPermission("exercise_session", HealthPermission.getReadPermission(ExerciseSessionRecord::class), "Allenamenti"),
        MetricPermission(
            "wheelchair_pushes",
            HealthPermission.getReadPermission(WheelchairPushesRecord::class),
            "Spinte in carrozzina",
        ),
        MetricPermission("sleep_session", HealthPermission.getReadPermission(SleepSessionRecord::class), "Sonno"),
        MetricPermission("weight", HealthPermission.getReadPermission(WeightRecord::class), "Peso"),
        MetricPermission("body_fat", HealthPermission.getReadPermission(BodyFatRecord::class), "Massa grassa"),
        MetricPermission("height", HealthPermission.getReadPermission(HeightRecord::class), "Altezza"),
        MetricPermission("lean_body_mass", HealthPermission.getReadPermission(LeanBodyMassRecord::class), "Massa magra"),
        MetricPermission("bone_mass", HealthPermission.getReadPermission(BoneMassRecord::class), "Massa ossea"),
        MetricPermission("body_water_mass", HealthPermission.getReadPermission(BodyWaterMassRecord::class), "Acqua corporea"),
        MetricPermission("heart_rate", HealthPermission.getReadPermission(HeartRateRecord::class), "Frequenza cardiaca"),
        MetricPermission(
            "resting_heart_rate",
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            "Frequenza a riposo",
        ),
        MetricPermission(
            "heart_rate_variability",
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            "Variabilità cardiaca",
        ),
        MetricPermission(
            "oxygen_saturation",
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            "Saturazione ossigeno",
        ),
        MetricPermission(
            "blood_pressure",
            HealthPermission.getReadPermission(BloodPressureRecord::class),
            "Pressione",
        ),
        MetricPermission(
            "blood_glucose",
            HealthPermission.getReadPermission(BloodGlucoseRecord::class),
            "Glicemia",
        ),
        MetricPermission(
            "body_temperature",
            HealthPermission.getReadPermission(BodyTemperatureRecord::class),
            "Temperatura",
        ),
        MetricPermission(
            "basal_body_temperature",
            HealthPermission.getReadPermission(BasalBodyTemperatureRecord::class),
            "Temperatura basale",
        ),
        MetricPermission(
            "respiratory_rate",
            HealthPermission.getReadPermission(RespiratoryRateRecord::class),
            "Frequenza respiratoria",
        ),
        MetricPermission("vo2_max", HealthPermission.getReadPermission(Vo2MaxRecord::class), "VO2 max"),
        MetricPermission("nutrition", HealthPermission.getReadPermission(NutritionRecord::class), "Nutrizione"),
        MetricPermission("hydration", HealthPermission.getReadPermission(HydrationRecord::class), "Idratazione"),
    )

    val readPermissions: Set<String> = metrics.map { it.permission }.toSet()

    val background: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    val history: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY

    fun label(permission: String): String {
        return metrics.firstOrNull { it.permission == permission }?.label ?: permission
    }
}
