package it.healthsync.app.domain

import java.net.URI

const val DEFAULT_AUTH_HEADER = "Authorization"

object FieldError {
    const val URL_REQUIRED = "url_required"
    const val URL_TOO_LONG = "url_too_long"
    const val URL_WHITESPACE = "url_whitespace"
    const val URL_INVALID = "url_invalid"
    const val URL_SCHEME = "url_scheme"
    const val URL_HOST = "url_host"
    const val HEADER_NAME_TOO_LONG = "header_name_too_long"
    const val HEADER_NAME_INVALID = "header_name_invalid"
    const val HEADER_VALUE_REQUIRED = "header_value_required"
    const val HEADER_VALUE_TOO_LONG = "header_value_too_long"
    const val HEADER_VALUE_NEWLINE = "header_value_newline"
}

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
    if (url.isEmpty()) return FieldError.URL_REQUIRED
    if (url.length > 2048) return FieldError.URL_TOO_LONG
    if (url.any { it.isWhitespace() }) return FieldError.URL_WHITESPACE
    val uri = try {
        URI(url)
    } catch (_: Exception) {
        return FieldError.URL_INVALID
    }
    val scheme = uri.scheme?.lowercase()
    if (scheme != "https" && scheme != "http") {
        return FieldError.URL_SCHEME
    }
    if (uri.host.isNullOrBlank()) return FieldError.URL_HOST
    return null
}

private val headerToken = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")

private fun validateHeaderName(name: String): String? {
    if (name.length > 128) return FieldError.HEADER_NAME_TOO_LONG
    if (!headerToken.matches(name)) {
        return FieldError.HEADER_NAME_INVALID
    }
    return null
}

private fun validateHeaderValue(raw: String): String? {
    val value = raw.trim()
    if (value.isEmpty()) return FieldError.HEADER_VALUE_REQUIRED
    if (value.length > 4096) return FieldError.HEADER_VALUE_TOO_LONG
    if (value.any { it == '\r' || it == '\n' }) {
        return FieldError.HEADER_VALUE_NEWLINE
    }
    return null
}
