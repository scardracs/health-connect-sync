package it.healthsync.app

import android.app.Application
import it.healthsync.app.data.OnboardingStore
import it.healthsync.app.sync.SyncScheduler

class HealthSyncApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLocales.ensureDefault(this)
        if (OnboardingStore.read(this).dailySync) {
            SyncScheduler.ensureDaily(this)
        }
    }
}
