package it.healthsync.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Morning alarm. Starts or resumes the paced send queue. It does not POST by itself. */
class DailyKickWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val runner = SyncRunner.create(applicationContext)
        val plan = it.healthsync.app.data.SettingsRepository(applicationContext).plan()
        if (plan != null && plan.finished) runner.armSync()
        SyncScheduler.kick(applicationContext, replace = false)
        return Result.success()
    }
}
