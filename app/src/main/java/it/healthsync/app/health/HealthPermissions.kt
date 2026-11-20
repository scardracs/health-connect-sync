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
    val labelRes: Int,
)

object HealthPermissions {
    val metrics: List<MetricPermission> = listOf(
        MetricPermission("steps", HealthPermission.getReadPermission(StepsRecord::class), it.healthsync.app.R.string.metric_steps),
        MetricPermission("distance", HealthPermission.getReadPermission(DistanceRecord::class), it.healthsync.app.R.string.metric_distance),
        MetricPermission("floors", HealthPermission.getReadPermission(FloorsClimbedRecord::class), it.healthsync.app.R.string.metric_floors),
        MetricPermission("elevation", HealthPermission.getReadPermission(ElevationGainedRecord::class), it.healthsync.app.R.string.metric_elevation),
        MetricPermission("speed", HealthPermission.getReadPermission(SpeedRecord::class), it.healthsync.app.R.string.metric_speed),
        MetricPermission("power", HealthPermission.getReadPermission(PowerRecord::class), it.healthsync.app.R.string.metric_power),
        MetricPermission(
            "active_calories",
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            it.healthsync.app.R.string.metric_active_calories,
        ),
        MetricPermission(
            "total_calories",
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            it.healthsync.app.R.string.metric_total_calories,
        ),
        MetricPermission(
            "basal_metabolic_rate",
            HealthPermission.getReadPermission(BasalMetabolicRateRecord::class),
            it.healthsync.app.R.string.metric_basal,
        ),
        MetricPermission("exercise_session", HealthPermission.getReadPermission(ExerciseSessionRecord::class), it.healthsync.app.R.string.metric_exercise),
        MetricPermission(
            "wheelchair_pushes",
            HealthPermission.getReadPermission(WheelchairPushesRecord::class),
            it.healthsync.app.R.string.metric_wheelchair,
        ),
        MetricPermission("sleep_session", HealthPermission.getReadPermission(SleepSessionRecord::class), it.healthsync.app.R.string.metric_sleep),
        MetricPermission("weight", HealthPermission.getReadPermission(WeightRecord::class), it.healthsync.app.R.string.metric_weight),
        MetricPermission("body_fat", HealthPermission.getReadPermission(BodyFatRecord::class), it.healthsync.app.R.string.metric_body_fat),
        MetricPermission("height", HealthPermission.getReadPermission(HeightRecord::class), it.healthsync.app.R.string.metric_height),
        MetricPermission("lean_body_mass", HealthPermission.getReadPermission(LeanBodyMassRecord::class), it.healthsync.app.R.string.metric_lean),
        MetricPermission("bone_mass", HealthPermission.getReadPermission(BoneMassRecord::class), it.healthsync.app.R.string.metric_bone),
        MetricPermission("body_water_mass", HealthPermission.getReadPermission(BodyWaterMassRecord::class), it.healthsync.app.R.string.metric_water),
        MetricPermission("heart_rate", HealthPermission.getReadPermission(HeartRateRecord::class), it.healthsync.app.R.string.metric_heart_rate),
        MetricPermission(
            "resting_heart_rate",
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            it.healthsync.app.R.string.metric_resting_hr,
        ),
        MetricPermission(
            "heart_rate_variability",
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            it.healthsync.app.R.string.metric_hrv,
        ),
        MetricPermission(
            "oxygen_saturation",
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            it.healthsync.app.R.string.metric_oxygen,
        ),
        MetricPermission(
            "blood_pressure",
            HealthPermission.getReadPermission(BloodPressureRecord::class),
            it.healthsync.app.R.string.metric_blood_pressure,
        ),
        MetricPermission(
            "blood_glucose",
            HealthPermission.getReadPermission(BloodGlucoseRecord::class),
            it.healthsync.app.R.string.metric_glucose,
        ),
        MetricPermission(
            "body_temperature",
            HealthPermission.getReadPermission(BodyTemperatureRecord::class),
            it.healthsync.app.R.string.metric_temperature,
        ),
        MetricPermission(
            "basal_body_temperature",
            HealthPermission.getReadPermission(BasalBodyTemperatureRecord::class),
            it.healthsync.app.R.string.metric_basal_temp,
        ),
        MetricPermission(
            "respiratory_rate",
            HealthPermission.getReadPermission(RespiratoryRateRecord::class),
            it.healthsync.app.R.string.metric_respiratory,
        ),
        MetricPermission("vo2_max", HealthPermission.getReadPermission(Vo2MaxRecord::class), it.healthsync.app.R.string.metric_vo2),
        MetricPermission("nutrition", HealthPermission.getReadPermission(NutritionRecord::class), it.healthsync.app.R.string.metric_nutrition),
        MetricPermission("hydration", HealthPermission.getReadPermission(HydrationRecord::class), it.healthsync.app.R.string.metric_hydration),
    )

    val readPermissions: Set<String> = metrics.map { it.permission }.toSet()

    val background: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    val history: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY

    fun labelRes(permission: String): Int {
        return metrics.firstOrNull { it.permission == permission }?.labelRes
            ?: it.healthsync.app.R.string.metric_steps
    }
}
