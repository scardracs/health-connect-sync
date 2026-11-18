package it.healthsync.app.domain

import java.net.URI

const val DEFAULT_AUTH_HEADER = "Authorization"

data class SettingsValidation(
    val urlError: String?,
    val headerNameError: String?,
    val headerValueError: String?,
) {
    val ok: Boolean get() = urlError == null && headerNameError == null && headerValueError == null
}

fun normalizeHeaderName(raw: String): String {
    val trimmed = raw.trim()
    return trimmed.ifEmpty { DEFAULT_AUTH_HEADER }
}

fun validateWebhookSettings(url: String, headerName: String, headerValue: String): SettingsValidation {
    return SettingsValidation(
        urlError = validateUrl(url),
        headerNameError = validateHeaderName(normalizeHeaderName(headerName)),
        headerValueError = validateHeaderValue(headerValue),
    )
}

fun isRetryableHttpStatus(code: Int): Boolean {
    return code == 408 || code == 429 || code in 500..599
}

private fun validateUrl(raw: String): String? {
    val url = raw.trim()
    if (url.isEmpty()) return "Inserisci l'URL del webhook."
    if (url.length > 2048) return "L'URL è troppo lungo."
    if (url.any { it.isWhitespace() }) return "L'URL non può contenere spazi."
    val uri = try {
        URI(url)
    } catch (_: Exception) {
        return "L'URL non è valido."
    }
    val scheme = uri.scheme?.lowercase()
    if (scheme != "https" && scheme != "http") {
        return "L'URL deve iniziare con https:// o http://."
    }
    if (uri.host.isNullOrBlank()) return "L'URL non contiene un host."
    return null
}

private val headerToken = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")

private fun validateHeaderName(name: String): String? {
    if (name.length > 128) return "Il nome dell'header è troppo lungo."
    if (!headerToken.matches(name)) {
        return "Il nome dell'header contiene caratteri non validi."
    }
    return null
}

private fun validateHeaderValue(raw: String): String? {
    val value = raw.trim()
    if (value.isEmpty()) return "Inserisci la chiave da inviare nell'header."
    if (value.length > 4096) return "La chiave è troppo lunga."
    if (value.any { it == '\r' || it == '\n' }) {
        return "La chiave non può andare a capo."
    }
    return null
}
