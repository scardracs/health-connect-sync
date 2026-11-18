package it.healthsync.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import it.healthsync.app.domain.MorningSchedule
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object SyncScheduler {
    const val UNIQUE_NAME = "salute_sync_daily"

    fun ensureDaily(context: Context) {
        val delayMs = MorningSchedule
            .delayUntilNext(Instant.now(), ZoneId.systemDefault())
            .toMillis()
            .coerceAtLeast(15_000L)
        val request = PeriodicWorkRequestBuilder<SyncWorker>(24, TimeUnit.HOURS, 30, TimeUnit.MINUTES)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
