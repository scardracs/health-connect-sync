package it.healthsync.app.domain

/**
 * Which language the app should store. Android types stay out of this file so the
 * choice can be unit tested. "it" and "en" are the only tags in res/xml/locales_config.xml.
 */
object AppLanguage {
    const val ITALIAN = "it"
    const val ENGLISH = "en"

    fun normalize(tag: String?): String {
        val language = tag
            ?.trim()
            ?.substringBefore(',')
            ?.substringBefore('-')
            ?.substringBefore('_')
            ?.lowercase()
            .orEmpty()
        return if (language == ENGLISH) ENGLISH else ITALIAN
    }

    /**
     * Tag to apply, or null when the stored choice should be left alone.
     * A user tap ([selected]) always wins. On startup ([selected] is null) an empty
     * store gets Italian, and an existing choice is not overwritten.
     */
    fun localeToApply(selected: String?, persisted: String?): String? {
        if (!selected.isNullOrBlank()) return normalize(selected)
        if (persisted.isNullOrBlank()) return ITALIAN
        return null
    }
}
