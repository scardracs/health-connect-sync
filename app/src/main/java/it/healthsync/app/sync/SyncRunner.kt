package it.healthsync.app.sync

import android.content.Context
import android.util.Log
import it.healthsync.app.BuildConfig
import it.healthsync.app.data.SettingsRepository
import it.healthsync.app.domain.PayloadBuilder
import it.healthsync.app.domain.SyncWindowResolver
import it.healthsync.app.domain.validateWebhookSettings
import it.healthsync.app.health.HealthConnectGateway
import it.healthsync.app.health.ReadOutcome
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

enum class SyncOrigin { FOREGROUND, WORKER }

sealed class SyncOutcome {
    data class Success(val message: String, val httpCode: Int) : SyncOutcome()
    data class Failure(val message: String, val retryable: Boolean, val httpCode: Int? = null) : SyncOutcome()
    data object Busy : SyncOutcome()
}

class SyncRunner(
    private val settings: SettingsRepository,
    private val gateway: HealthConnectGateway,
    private val poster: WebhookPoster,
    private val appVersion: String,
    private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    suspend fun sync(origin: SyncOrigin): SyncOutcome = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) return@withContext SyncOutcome.Busy
        try {
            perform(origin)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun perform(origin: SyncOrigin): SyncOutcome {
        val attemptAt = now()
        val config = settings.webhook()
        val validation = validateWebhookSettings(config.url, config.headerName, config.headerValue)
        if (!validation.ok) {
            val message = validation.urlError ?: validation.headerNameError ?: validation.headerValueError
                ?: "Impostazioni webhook non valide."
            val failure = SyncOutcome.Failure(message, retryable = false)
            settings.markFailure(attemptAt, failure.message, null)
            return failure
        }
        return try {
            val zoneId = zone()
            val storedSuccess = settings.syncState.value.lastSuccess
            val window = SyncWindowResolver.resolve(storedSuccess, attemptAt)
            when (val read = gateway.read(window.start, window.end, zoneId, window.historyLimited)) {
                is ReadOutcome.Unavailable -> fail(attemptAt, read.message, retryable = false)
                is ReadOutcome.PermissionDenied -> fail(attemptAt, read.message, retryable = false)
                is ReadOutcome.Ok -> {
                    val payload = PayloadBuilder.build(read.snapshot, appVersion, attemptAt)
                    val body = PayloadBuilder.buildJson(read.snapshot, appVersion, attemptAt)
                    when (val posted = poster.post(config.url, config.headerName, config.headerValue, body)) {
                        is PostResult.Ok -> {
                            val message = successMessage(payload, posted.code, zoneId)
                            settings.markSuccess(attemptAt, window.end, message, posted.code)
                            SyncOutcome.Success(message, posted.code)
                        }
                        is PostResult.HttpError -> {
                            val base = "Il webhook ha risposto HTTP ${posted.code}."
                            fail(attemptAt, withRetryHint(base, posted.retryable, origin), posted.retryable, posted.code)
                        }
                        is PostResult.NetworkError -> {
                            val base = "Connessione al webhook non riuscita."
                            fail(attemptAt, withRetryHint(base, retryable = true, origin), retryable = true)
                        }
                    }
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "sync failed: ${error.javaClass.simpleName}")
            fail(
                attemptAt,
                withRetryHint("Errore imprevisto durante la sincronizzazione.", retryable = true, origin),
                retryable = true,
            )
        }
    }

    private fun fail(now: Instant, message: String, retryable: Boolean, httpCode: Int? = null): SyncOutcome.Failure {
        settings.markFailure(now, message, httpCode)
        return SyncOutcome.Failure(message, retryable, httpCode)
    }

    companion object {
        private const val TAG = "SaluteSync"
        private val mutex = Mutex()

        fun create(context: Context): SyncRunner {
            val appContext = context.applicationContext
            val version = BuildConfig.VERSION_NAME
            return SyncRunner(
                settings = SettingsRepository(appContext),
                gateway = HealthConnectGateway(appContext),
                poster = WebhookClient(version),
                appVersion = version,
            )
        }

        private fun successMessage(
            payload: it.healthsync.app.domain.HealthPayload,
            httpCode: Int,
            zone: ZoneId,
        ): String {
            val start = payload.window.start
            val end = payload.window.end
            val counts = listOf(
                count(payload.days.size, "giorno", "giorni"),
                count(payload.sleepSessions.size, "sonno", "sonni"),
                count(payload.weightReadings.size, "pesata", "pesate"),
                count(payload.exerciseSessions.size, "allenamento", "allenamenti"),
            ).joinToString(", ")
            val limited = if (payload.historyLimited) " Finestra limitata agli ultimi 30 giorni." else ""
            return "Inviati $counts (HTTP $httpCode). Dal $start al $end. Fuso ${zone.id}.$limited"
        }

        private fun count(n: Int, singular: String, plural: String): String {
            return if (n == 1) "1 $singular" else "$n $plural"
        }

        private fun withRetryHint(base: String, retryable: Boolean, origin: SyncOrigin): String {
            val hint = when {
                retryable && origin == SyncOrigin.WORKER -> " Nuovo tentativo automatico."
                retryable -> " Puoi riprovare con Sincronizza ora."
                else -> ""
            }
            return base + hint
        }
    }
}
