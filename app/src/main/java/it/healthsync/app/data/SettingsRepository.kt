package it.healthsync.app.data

import android.content.Context
import android.content.SharedPreferences
import it.healthsync.app.domain.DEFAULT_AUTH_HEADER
import it.healthsync.app.domain.normalizeHeaderName
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SyncStatus {
    NEVER,
    RUNNING,
    SUCCESS,
    FAILURE,
}

data class WebhookConfig(
    val url: String,
    val headerName: String,
    val headerValue: String,
)

data class SyncState(
    val status: SyncStatus = SyncStatus.NEVER,
    val lastSuccess: Instant? = null,
    val lastAttempt: Instant? = null,
    val message: String? = null,
    val httpCode: Int? = null,
)

class SettingsRepository(context: Context) {
    private val prefs = SecureStore.open(context)
    private val _syncState = MutableStateFlow(readSyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _syncState.value = readSyncState()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun webhook(): WebhookConfig {
        return WebhookConfig(
            url = prefs.getString(KEY_URL, "").orEmpty(),
            headerName = normalizeHeaderName(prefs.getString(KEY_HEADER_NAME, DEFAULT_AUTH_HEADER).orEmpty()),
            headerValue = prefs.getString(KEY_HEADER_VALUE, "").orEmpty(),
        )
    }

    fun saveWebhook(url: String, headerName: String, headerValue: String) {
        prefs.edit()
            .putString(KEY_URL, url.trim())
            .putString(KEY_HEADER_NAME, normalizeHeaderName(headerName))
            .putString(KEY_HEADER_VALUE, headerValue.trim())
            .commit()
    }

    fun reload() {
        _syncState.value = readSyncState()
    }

    fun markSuccess(now: Instant, windowEnd: Instant, message: String, httpCode: Int) {
        prefs.edit()
            .putString(KEY_STATUS, SyncStatus.SUCCESS.name)
            .putLong(KEY_LAST_ATTEMPT, now.toEpochMilli())
            .putLong(KEY_LAST_SUCCESS, windowEnd.toEpochMilli())
            .putString(KEY_MESSAGE, message)
            .putInt(KEY_HTTP, httpCode)
            .commit()
        _syncState.value = readSyncState()
    }

    fun markFailure(now: Instant, message: String, httpCode: Int?) {
        val editor = prefs.edit()
            .putString(KEY_STATUS, SyncStatus.FAILURE.name)
            .putLong(KEY_LAST_ATTEMPT, now.toEpochMilli())
            .putString(KEY_MESSAGE, message)
        if (httpCode == null) editor.remove(KEY_HTTP) else editor.putInt(KEY_HTTP, httpCode)
        editor.commit()
        _syncState.value = readSyncState()
    }

    private fun readSyncState(): SyncState {
        val status = runCatching { SyncStatus.valueOf(prefs.getString(KEY_STATUS, SyncStatus.NEVER.name).orEmpty()) }
            .getOrDefault(SyncStatus.NEVER)
        return SyncState(
            status = status,
            lastSuccess = prefs.getLong(KEY_LAST_SUCCESS, 0L).takeIf { prefs.contains(KEY_LAST_SUCCESS) }?.let(Instant::ofEpochMilli),
            lastAttempt = prefs.getLong(KEY_LAST_ATTEMPT, 0L).takeIf { prefs.contains(KEY_LAST_ATTEMPT) }?.let(Instant::ofEpochMilli),
            message = prefs.getString(KEY_MESSAGE, null),
            httpCode = if (prefs.contains(KEY_HTTP)) prefs.getInt(KEY_HTTP, 0) else null,
        )
    }

    companion object {
        private const val KEY_URL = "webhook_url"
        private const val KEY_HEADER_NAME = "auth_header_name"
        private const val KEY_HEADER_VALUE = "auth_header_value"
        private const val KEY_LAST_SUCCESS = "last_success_epoch_ms"
        private const val KEY_LAST_ATTEMPT = "last_attempt_epoch_ms"
        private const val KEY_STATUS = "last_status"
        private const val KEY_MESSAGE = "last_message"
        private const val KEY_HTTP = "last_http_code"
    }
}
