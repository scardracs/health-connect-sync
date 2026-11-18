package it.healthsync.app.health

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord

data class MetricPermission(
    val id: String,
    val permission: String,
    val label: String,
)

object HealthPermissions {
    val metrics: List<MetricPermission> = listOf(
        MetricPermission("steps", HealthPermission.getReadPermission(StepsRecord::class), "Passi"),
        MetricPermission("distance", HealthPermission.getReadPermission(DistanceRecord::class), "Distanza"),
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
        MetricPermission("exercise_session", HealthPermission.getReadPermission(ExerciseSessionRecord::class), "Allenamenti"),
        MetricPermission("sleep_session", HealthPermission.getReadPermission(SleepSessionRecord::class), "Sonno"),
        MetricPermission("weight", HealthPermission.getReadPermission(WeightRecord::class), "Peso"),
        MetricPermission("body_fat", HealthPermission.getReadPermission(BodyFatRecord::class), "Massa grassa"),
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
        MetricPermission("nutrition", HealthPermission.getReadPermission(NutritionRecord::class), "Nutrizione"),
        MetricPermission("hydration", HealthPermission.getReadPermission(HydrationRecord::class), "Idratazione"),
    )

    val readPermissions: Set<String> = metrics.map { it.permission }.toSet()

    val background: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    fun label(permission: String): String {
        return metrics.firstOrNull { it.permission == permission }?.label ?: permission
    }
}
