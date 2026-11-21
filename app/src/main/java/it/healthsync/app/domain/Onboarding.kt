package it.healthsync.app.domain

/**
 * First-launch choices. Missing values mean "not finished" and "keep the morning sync",
 * which is how the app behaved before this screen existed.
 */
data class OnboardingRecord(
    val completed: Boolean = false,
    val dailySync: Boolean = true,
)

object Onboarding {
    fun shouldShow(record: OnboardingRecord): Boolean = !record.completed

    /** Continue stores the checkbox. Skip only marks the screen finished. */
    fun complete(record: OnboardingRecord, dailySync: Boolean): OnboardingRecord {
        return record.copy(completed = true, dailySync = dailySync)
    }

    fun skip(record: OnboardingRecord): OnboardingRecord = record.copy(completed = true)

    fun setDailySync(record: OnboardingRecord, enabled: Boolean): OnboardingRecord {
        return record.copy(dailySync = enabled)
    }
}
