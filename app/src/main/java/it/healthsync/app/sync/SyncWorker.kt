package it.healthsync.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val outcome = SyncRunner.create(applicationContext).sync(SyncOrigin.WORKER)
        return when (outcome) {
            is SyncOutcome.Success -> Result.success()
            SyncOutcome.Busy -> Result.retry()
            is SyncOutcome.Failure ->
                if (outcome.retryable && runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}
