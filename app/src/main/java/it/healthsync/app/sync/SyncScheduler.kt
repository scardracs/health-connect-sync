package it.healthsync.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import it.healthsync.app.data.SettingsRepository
import it.healthsync.app.domain.MorningSchedule
import it.healthsync.app.domain.SyncInterval
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object SyncScheduler {
    const val DAILY = "salute_sync_daily"
    const val QUEUE = "salute_sync_queue"

    private fun constraints(context: Context, manual: Boolean): Constraints {
        val settings = SettingsRepository(context)
        val network = if (!manual && settings.wifiOnly()) NetworkType.UNMETERED else NetworkType.CONNECTED
        return Constraints.Builder()
            .setRequiredNetworkType(network)
            .setRequiresCharging(!manual && settings.chargingOnly())
            .build()
    }

    fun ensureDaily(context: Context, update: Boolean = false) {
        val hours = SettingsRepository(context).intervalHours()
        val delayMs = if (hours == SyncInterval.DEFAULT_HOURS) {
            MorningSchedule.delayUntilNext(Instant.now(), ZoneId.systemDefault()).toMillis()
        } else {
            TimeUnit.MINUTES.toMillis(15)
        }.coerceAtLeast(15_000L)
        val request = PeriodicWorkRequestBuilder<DailyKickWorker>(hours, TimeUnit.HOURS, 15, TimeUnit.MINUTES)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(constraints(context, manual = false))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY,
            if (update) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelDaily(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DAILY)
    }

    /** Start the queue if it is idle. [replace] cancels the current chain first. */
    fun kick(context: Context, replace: Boolean) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints(context, manual = SettingsRepository(context).plan()?.manual == true))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 60, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            QUEUE,
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Run another step after this one finishes.
     * APPEND chains onto the unique work that is still running, then the delay starts.
     */
    fun scheduleNext(context: Context, delaySeconds: Long) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(delaySeconds.coerceAtLeast(1), TimeUnit.SECONDS)
            .setConstraints(constraints(context, manual = SettingsRepository(context).plan()?.manual == true))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 60, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            QUEUE,
            ExistingWorkPolicy.APPEND,
            request,
        )
    }
}
