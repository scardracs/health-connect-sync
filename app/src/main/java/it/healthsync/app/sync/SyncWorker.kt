package it.healthsync.app.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val result = SyncRunner.create(applicationContext).step(SyncOrigin.WORKER)
        return when (result) {
            is StepResult.Posted -> {
                SyncScheduler.scheduleNext(applicationContext, result.gapSeconds)
                Result.success()
            }
            is StepResult.ScanMore -> {
                SyncScheduler.scheduleNext(applicationContext, result.delaySeconds)
                Result.success()
            }
            StepResult.Finished -> Result.success()
            is StepResult.Failed -> {
                Log.w("SaluteSync", "step failed retryable=${result.failure.retryable}")
                if (result.failure.retryable && runAttemptCount < 5) Result.retry() else Result.failure()
            }
        }
    }
}
