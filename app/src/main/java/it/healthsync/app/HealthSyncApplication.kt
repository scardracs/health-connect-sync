package it.healthsync.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import it.healthsync.app.sync.SyncScheduler

class HealthSyncApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("it"))
        }
        SyncScheduler.ensureDaily(this)
    }
}
