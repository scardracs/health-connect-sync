package it.healthsync.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingTest {
    @Test
    fun aNewInstallShowsTheScreenAndKeepsTheMorningSync() {
        val fresh = OnboardingRecord()
        assertTrue(Onboarding.shouldShow(fresh))
        assertTrue(fresh.dailySync)
    }

    @Test
    fun continueStoresTheCheckboxAndHidesTheScreen() {
        val done = Onboarding.complete(OnboardingRecord(), dailySync = false)
        assertFalse(Onboarding.shouldShow(done))
        assertFalse(done.dailySync)
        val kept = Onboarding.complete(OnboardingRecord(), dailySync = true)
        assertTrue(kept.dailySync)
        assertTrue(kept.completed)
    }

    @Test
    fun skipFinishesWithoutTurningOffTheMorningSync() {
        val skipped = Onboarding.skip(OnboardingRecord(dailySync = true))
        assertFalse(Onboarding.shouldShow(skipped))
        assertTrue(skipped.dailySync)
        val alreadyOff = Onboarding.skip(OnboardingRecord(dailySync = false))
        assertFalse(alreadyOff.dailySync)
    }

    @Test
    fun settingsCanChangeTheMorningSyncAfterOnboarding() {
        val done = Onboarding.complete(OnboardingRecord(), dailySync = true)
        val off = Onboarding.setDailySync(done, enabled = false)
        assertTrue(off.completed)
        assertFalse(off.dailySync)
        val on = Onboarding.setDailySync(off, enabled = true)
        assertTrue(on.dailySync)
        assertFalse(Onboarding.shouldShow(on))
    }
}
