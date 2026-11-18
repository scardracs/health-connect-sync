package it.healthsync.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import it.healthsync.app.sync.SyncScheduler
import java.util.Locale

class HealthSyncApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(wrapItalian(base))
    }

    override fun onCreate() {
        super.onCreate()
        SyncScheduler.ensureDaily(this)
    }
}

fun wrapItalian(base: Context): Context {
    val locale = Locale.ITALY
    Locale.setDefault(locale)
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    return base.createConfigurationContext(config)
}
