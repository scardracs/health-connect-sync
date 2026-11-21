package it.healthsync.app.data

import android.content.Context
import it.healthsync.app.domain.OnboardingRecord

/** Plain preferences. The flag is not a secret, and older installs simply do not have it. */
object OnboardingStore {
    private const val PREFS = "salute_sync_onboarding"
    private const val COMPLETED = "completed"
    private const val DAILY = "daily_sync"

    fun read(context: Context): OnboardingRecord {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return OnboardingRecord(
            completed = prefs.getBoolean(COMPLETED, false),
            dailySync = prefs.getBoolean(DAILY, true),
        )
    }

    fun write(context: Context, record: OnboardingRecord) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(COMPLETED, record.completed)
            .putBoolean(DAILY, record.dailySync)
            .commit()
    }
}
