package it.healthsync.app.sync

import android.content.Context
import android.util.Log
import it.healthsync.app.BuildConfig
import it.healthsync.app.data.SettingsRepository
import it.healthsync.app.domain.BackfillPlanner
import it.healthsync.app.domain.BackfillState
import it.healthsync.app.domain.HealthPayload
import it.healthsync.app.domain.HistoryRange
import it.healthsync.app.domain.PayloadBuilder
import it.healthsync.app.domain.SyncWindowResolver
import it.healthsync.app.domain.validateWebhookSettings
import it.healthsync.app.health.HealthPermissions
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
    suspend fun sync(origin: SyncOrigin): SyncOutcome = locked(origin, restartHistory = false)

    suspend fun importHistory(origin: SyncOrigin): SyncOutcome = locked(origin, restartHistory = true)

    private suspend fun locked(origin: SyncOrigin, restartHistory: Boolean): SyncOutcome = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) return@withContext SyncOutcome.Busy
        try {
            if (restartHistory) settings.clearBackfill()
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
            val existing = settings.backfillState()
            if (existing == null || !existing.finished) {
                return backfill(origin, attemptAt, zoneId, config.url, config.headerName, config.headerValue, existing)
            }
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

    private suspend fun backfill(
        origin: SyncOrigin,
        attemptAt: Instant,
        zoneId: java.time.ZoneId,
        url: String,
        headerName: String,
        headerValue: String,
        existing: BackfillState?,
    ): SyncOutcome {
        val granted = gateway.grantedPermissions()
        if (granted.any { it in HealthPermissions.readPermissions }) {
            settings.rememberFirstGrant(attemptAt)
        }
        var state = existing ?: run {
            val historyGranted = gateway.historySupported() && HealthPermissions.history in granted
            val start = HistoryRange.earliest(attemptAt, historyGranted, settings.firstGrant())
            val chunks = BackfillPlanner.months(start, attemptAt, zoneId)
            BackfillState(
                chunks = chunks,
                completed = 0,
                historyLimited = !historyGranted,
                finished = chunks.isEmpty(),
            ).also { settings.saveBackfill(it) }
        }
        var lastCode = 0
        while (state.chunks.isNotEmpty()) {
            val window = state.chunks.first()
            val index = state.completed + 1
            val total = state.total
            when (val read = gateway.read(window.start, window.end, zoneId, state.historyLimited, allowHistoryClamp = false)) {
                is ReadOutcome.Unavailable -> return fail(attemptAt, read.message, retryable = false)
                is ReadOutcome.PermissionDenied -> return fail(attemptAt, read.message, retryable = false)
                is ReadOutcome.Ok -> {
                    val body = PayloadBuilder.buildJson(
                        read.snapshot,
                        appVersion,
                        attemptAt,
                        kind = HealthPayload.KIND_BACKFILL,
                        chunkIndex = index,
                        chunkTotal = total,
                    )
                    val tooBig = body.toByteArray(Charsets.UTF_8).size > BackfillPlanner.MAX_CHUNK_BYTES
                    val parts = if (tooBig) BackfillPlanner.split(window, zoneId) else emptyList()
                    if (tooBig && parts.size > 1) {
                        state = state.replaceFirst(parts)
                        settings.saveBackfill(state)
                        settings.noteProgress(attemptAt, "Un blocco dello storico è troppo grande: lo divido e riprovo.")
                        continue
                    }
                    when (val posted = poster.post(url, headerName, headerValue, body)) {
                        is PostResult.Ok -> {
                            lastCode = posted.code
                            state = state.advance()
                            settings.saveBackfill(state)
                            settings.noteProgress(
                                attemptAt,
                                "Storico: blocco $index di $total inviato.",
                            )
                        }
                        is PostResult.HttpError -> {
                            val base = "Il webhook ha risposto HTTP ${posted.code} durante l'importazione storica."
                            return fail(attemptAt, withRetryHint(base, posted.retryable, origin), posted.retryable, posted.code)
                        }
                        is PostResult.NetworkError -> {
                            return fail(
                                attemptAt,
                                withRetryHint("Connessione al webhook non riuscita durante l'importazione storica.", true, origin),
                                retryable = true,
                            )
                        }
                    }
                }
            }
        }
        val limited = if (state.historyLimited) {
            " Senza il permesso di lettura dello storico la finestra parte da 30 giorni prima della prima concessione."
        } else {
            ""
        }
        val message = "Importazione storica completata: ${state.completed} blocchi.$limited"
        settings.markSuccess(attemptAt, attemptAt, message, lastCode)
        return SyncOutcome.Success(message, lastCode)
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
            payload: HealthPayload,
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
