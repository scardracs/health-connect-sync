package it.healthsync.app

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import it.healthsync.app.domain.AppLanguage
import java.util.Locale

/**
 * Applies the in-app language.
 *
 * [AppCompatDelegate.setApplicationLocales] on API 33+ only talks to [LocaleManager]
 * when an [androidx.appcompat.app.AppCompatActivity] is already alive. [MainActivity]
 * used to be a [androidx.activity.ComponentActivity], so the call returned without
 * writing anything. The choice then vanished on the next launch.
 *
 * API 33+ writes [LocaleManager] from the application context, which is what
 * Settings > Apps > Language reads and what survives process death.
 * API 28–32 keeps the tag in this app's own preferences and tells AppCompat,
 * which recreates the activity. Both paths refresh the application resources
 * so a later sync message uses the new language.
 */
object AppLocales {
    private const val PREFS = "salute_sync_language"
    private const val KEY = "tag"

    fun ensureDefault(context: Context) {
        val persisted = readPersisted(context)
        val next = AppLanguage.localeToApply(selected = null, persisted = persisted)
            ?: AppLanguage.normalize(persisted)
        apply(context, next)
    }

    fun apply(context: Context, tag: String) {
        val normalized = AppLanguage.normalize(tag)
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, normalized)
            .commit()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.getSystemService(LocaleManager::class.java)?.applicationLocales =
                LocaleList.forLanguageTags(normalized)
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(normalized))
        applyToProcessResources(appContext, normalized)
    }

    fun current(context: Context): String {
        return AppLanguage.normalize(readPersisted(context) ?: AppLanguage.ITALIAN)
    }

    fun readPersisted(context: Context): String? {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val framework = appContext.getSystemService(LocaleManager::class.java)
                ?.applicationLocales
                ?.toLanguageTags()
            if (!framework.isNullOrBlank()) return framework
        }
        return appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.takeIf { it.isNotBlank() }
    }

    private fun applyToProcessResources(appContext: Context, tag: String) {
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(appContext.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(tag))
        @Suppress("DEPRECATION")
        appContext.resources.updateConfiguration(config, appContext.resources.displayMetrics)
    }
}
