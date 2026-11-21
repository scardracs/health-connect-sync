package it.healthsync.app.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguageTest {
    @Test
    fun englishTagsNormalizeToEnglishAndEverythingElseToItalian() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.normalize("en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.normalize("EN"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.normalize("en-US"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.normalize("en_GB"))
        assertEquals(AppLanguage.ITALIAN, AppLanguage.normalize("it"))
        assertEquals(AppLanguage.ITALIAN, AppLanguage.normalize("it-IT"))
        assertEquals(AppLanguage.ITALIAN, AppLanguage.normalize("fr"))
        assertEquals(AppLanguage.ITALIAN, AppLanguage.normalize(null))
        assertEquals(AppLanguage.ITALIAN, AppLanguage.normalize("  "))
    }

    @Test
    fun aChoiceIsWhatGetsStored() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.localeToApply("en", "it"))
        assertEquals(AppLanguage.ITALIAN, AppLanguage.localeToApply("it", "en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.localeToApply("en-US", null))
    }

    @Test
    fun startupKeepsAStoredChoiceAndDefaultsToItalian() {
        assertEquals(AppLanguage.ITALIAN, AppLanguage.localeToApply(selected = null, persisted = null))
        assertEquals(AppLanguage.ITALIAN, AppLanguage.localeToApply(selected = null, persisted = ""))
        assertEquals(AppLanguage.ITALIAN, AppLanguage.localeToApply(selected = null, persisted = "   "))
        assertNull(AppLanguage.localeToApply(selected = null, persisted = "en"))
        assertNull(AppLanguage.localeToApply(selected = null, persisted = "it"))
        assertNull(AppLanguage.localeToApply(selected = null, persisted = "en-US"))
    }

    @Test
    fun localeConfigDeclaresTheSameLanguagesThePickerCanStore() {
        val xml = File("src/main/res/xml/locales_config.xml").readText()
        assertTrue(xml.contains("android:name=\"it\""))
        assertTrue(xml.contains("android:name=\"en\""))
    }
}
