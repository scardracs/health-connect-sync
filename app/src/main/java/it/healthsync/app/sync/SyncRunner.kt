package it.healthsync.app.sync

import android.content.Context
import android.util.Log
import it.healthsync.app.BuildConfig
import it.healthsync.app.data.SettingsRepository
import it.healthsync.app.domain.CompactPayloads
import it.healthsync.app.domain.HistoryRange
import it.healthsync.app.domain.SendPlan
import it.healthsync.app.domain.calorieOnlySourceHint
import it.healthsync.app.domain.validateWebhookSettings
import it.healthsync.app.health.HealthConnectGateway
import it.healthsync.app.health.HealthPermissions
import it.healthsync.app.health.ReadOutcome
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

enum class SyncOrigin { FOREGROUND, WORKER }

sealed class SyncOutcome {
    data class Success(val message: String, val httpCode: Int) : SyncOutcome()
    data class Failure(val message: String, val retryable: Boolean, val httpCode: Int? = null) : SyncOutcome()
    data object Busy : SyncOutcome()
}

/** What the one-step worker should do after a single pass. */
sealed class StepResult {
    data class Posted(val gapSeconds: Long) : StepResult()
    data class ScanMore(val delaySeconds: Long) : StepResult()
    data object Finished : StepResult()
    data class Failed(val failure: SyncOutcome.Failure) : StepResult()
}

class SyncRunner(
    private val settings: SettingsRepository,
    private val gateway: HealthConnectGateway,
    private val poster: WebhookPoster,
    private val appVersion: String,
    private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    /** Resume an in-progress export, or start today's incremental window. */
    fun armSync() {
        val existing = settings.plan()
        if (existing != null && !existing.finished) return
        val attemptAt = now()
        val start = settings.syncState.value.lastSuccess ?: attemptAt.minus(7, ChronoUnit.DAYS)
        settings.savePlan(
            SendPlan(
                mode = SendPlan.INCREMENTAL,
                phase = SendPlan.SEND,
                cursorEpochMs = start.toEpochMilli(),
                endEpochMs = attemptAt.toEpochMilli(),
            ),
        )
    }

    /** Drop progress and import history again from the configured start date. */
    fun armReimport() {
        val attemptAt = now()
        val historyGranted = false
        settings.clearPlan()
        settings.savePlan(
            SendPlan(
                mode = SendPlan.BACKFILL,
                phase = SendPlan.SCAN,
                cursorEpochMs = attemptAt.toEpochMilli(),
                endEpochMs = attemptAt.toEpochMilli(),
                historyLimited = !historyGranted,
            ),
        )
    }

    suspend fun step(origin: SyncOrigin): StepResult = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) return@withContext StepResult.Failed(
            SyncOutcome.Failure("C'è già un invio in corso.", retryable = true),
        )
        try {
            performStep(origin)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun performStep(origin: SyncOrigin): StepResult {
        val attemptAt = now()
        val config = settings.webhook()
        val validation = validateWebhookSettings(config.url, config.headerName, config.headerValue)
        if (!validation.ok) {
            val message = validation.urlError ?: validation.headerNameError ?: validation.headerValueError
                ?: "Impostazioni webhook non valide."
            val failure = SyncOutcome.Failure(message, retryable = false)
            settings.markFailure(attemptAt, failure.message, null)
            return StepResult.Failed(failure)
        }
        val zoneId = zone()
        var plan = settings.plan() ?: SendPlan(
            mode = SendPlan.BACKFILL,
            phase = SendPlan.SCAN,
            cursorEpochMs = attemptAt.toEpochMilli(),
            endEpochMs = attemptAt.toEpochMilli(),
        )
        if (plan.finished) return StepResult.Finished
        if (plan.phase == SendPlan.SCAN) {
            plan = scan(plan, attemptAt, zoneId)
            settings.savePlan(plan)
            if (plan.finished) {
                settings.markSuccess(attemptAt, Instant.ofEpochMilli(plan.endEpochMs), planMessage(plan), 0)
                return StepResult.Finished
            }
        }
        return try {
            sendWindow(plan, attemptAt, zoneId, config.url, config.headerName, config.headerValue, origin)
        } catch (error: Exception) {
            Log.e(TAG, "step failed: ${error.javaClass.simpleName}")
            val failure = SyncOutcome.Failure(
                withRetryHint("Errore imprevisto durante l'invio.", retryable = true, origin),
                retryable = true,
            )
            settings.markFailure(attemptAt, failure.message, null)
            StepResult.Failed(failure)
        }
    }

    private suspend fun scan(plan: SendPlan, now: Instant, zoneId: ZoneId): SendPlan {
        val granted = gateway.grantedPermissions()
        if (granted.any { it in HealthPermissions.readPermissions }) settings.rememberFirstGrant(now)
        val historyGranted = gateway.historySupported() && HealthPermissions.history in granted
        val missing = HealthPermissions.metrics.filter { it.permission !in granted }.map { it.id }
        Log.i(TAG, "permissions missing=${missing.joinToString()}")
        val horizonStart = HistoryRange.earliest(now, historyGranted, settings.firstGrant())
        val userDate = settings.backfillStartDate().takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
        settings.noteProgress(now, "Cerco il primo giorno con dati diversi dalle calorie…")
        val discovered = userDate ?: gateway.findEarliestNonCalorieDay(horizonStart, now, zoneId)
        if (discovered == null) {
            settings.noteProgress(
                now,
                "Nessun giorno con passi, sonno, peso o allenamenti. Controlla le autorizzazioni di Samsung Health.",
            )
            return plan.copy(finished = true, historyLimited = !historyGranted, endEpochMs = now.toEpochMilli())
        }
        val start = discovered.atStartOfDay(zoneId).toInstant().let { if (it.isBefore(horizonStart)) horizonStart else it }
        return plan.copy(
            phase = SendPlan.SEND,
            cursorEpochMs = start.toEpochMilli(),
            endEpochMs = now.toEpochMilli(),
            historyLimited = !historyGranted,
            finished = !start.isBefore(now),
        )
    }

    private suspend fun sendWindow(
        plan: SendPlan,
        now: Instant,
        zoneId: ZoneId,
        url: String,
        headerName: String,
        headerValue: String,
        origin: SyncOrigin,
    ): StepResult {
        var cursor = Instant.ofEpochMilli(plan.cursorEpochMs)
        val end = Instant.ofEpochMilli(plan.endEpochMs)
        var sent = plan.sent
        var hops = 0
        while (cursor.isBefore(end) && hops < 12) {
            val windowEnd = minOf(cursor.plus(7, ChronoUnit.DAYS), end)
            when (val read = gateway.read(cursor, windowEnd, zoneId, plan.historyLimited, allowHistoryClamp = false)) {
                is ReadOutcome.Unavailable -> return failStep(now, read.message, false)
                is ReadOutcome.PermissionDenied -> return failStep(now, read.message, false)
                is ReadOutcome.Ok -> {
                    settings.saveCounts(read.snapshot.counts)
                    Log.i(TAG, "counts " + read.snapshot.counts.entries.joinToString(" ") { "${it.key}=${it.value}" })
                    var days = CompactPayloads.daysFrom(read.snapshot)
                    if (plan.mode == SendPlan.BACKFILL) days = CompactPayloads.forBackfill(days)
                    if (days.isEmpty()) {
                        cursor = windowEnd
                        hops += 1
                        settings.savePlan(plan.copy(cursorEpochMs = cursor.toEpochMilli()))
                        continue
                    }
                    val syncedAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                        now.atZone(zoneId).truncatedTo(ChronoUnit.SECONDS),
                    )
                    val bodies = CompactPayloads.pack(
                        days = days,
                        kind = if (plan.mode == SendPlan.BACKFILL) CompactPayloads.KIND_BACKFILL else CompactPayloads.KIND_INCREMENTAL,
                        zone = zoneId.id,
                        app = appVersion,
                        syncedAt = syncedAt,
                        startIndex = sent + 1,
                    )
                    val body = bodies.first()
                    check(CompactPayloads.bytes(body.json) <= CompactPayloads.MAX_BYTES)
                    when (val posted = poster.post(url, headerName, headerValue, body.json)) {
                        is PostResult.Ok -> {
                            sent += 1
                            val nextDay = body.lastDate.plusDays(1).atStartOfDay(zoneId).toInstant()
                            val advanced = if (nextDay.isAfter(cursor)) nextDay else windowEnd
                            val finished = !advanced.isBefore(end)
                            val updated = plan.copy(cursorEpochMs = advanced.toEpochMilli(), sent = sent, finished = finished)
                            settings.savePlan(updated)
                            val hint = if (calorieOnlySourceHint(read.snapshot.counts)) {
                                " Solo calorie: in Health Connect apri Autorizzazioni app, Samsung Health, e abilita la scrittura di passi, sonno, peso e allenamenti."
                            } else {
                                ""
                            }
                            settings.noteProgress(now, "Invio $sent accettato (fino al ${body.lastDate}).$hint")
                            return if (finished) {
                                settings.markSuccess(now, end, "Invio completato: $sent richieste.$hint", posted.code)
                                StepResult.Finished
                            } else {
                                StepResult.Posted(settings.gapSeconds())
                            }
                        }
                        is PostResult.HttpError -> {
                            val message = "Il webhook ha risposto HTTP ${posted.code}."
                            return failStep(now, withRetryHint(message, posted.retryable, origin), posted.retryable, posted.code)
                        }
                        is PostResult.NetworkError -> {
                            return failStep(
                                now,
                                withRetryHint("Connessione al webhook non riuscita.", true, origin),
                                true,
                            )
                        }
                    }
                }
            }
        }
        val finished = !cursor.isBefore(end)
        settings.savePlan(plan.copy(cursorEpochMs = cursor.toEpochMilli(), finished = finished))
        return if (finished) {
            settings.markSuccess(now, end, "Nessun altro giorno da inviare.", 0)
            StepResult.Finished
        } else {
            settings.noteProgress(now, "Nessun dato da inviare fino al ${cursor.atZone(zoneId).toLocalDate()}. Continuo la ricerca.")
            StepResult.ScanMore(5)
        }
    }

    private fun failStep(now: Instant, message: String, retryable: Boolean, httpCode: Int? = null): StepResult {
        settings.markFailure(now, message, httpCode)
        return StepResult.Failed(SyncOutcome.Failure(message, retryable, httpCode))
    }

    private fun planMessage(plan: SendPlan): String {
        return if (plan.historyLimited) {
            "Importazione ferma: nessun dato oltre le calorie nella finestra consentita."
        } else {
            "Importazione ferma: nessun giorno con dati diversi dalle calorie."
        }
    }

    companion object {
        private const val TAG = "SaluteSync"
        private val mutex = Mutex()

        fun create(context: Context): SyncRunner {
            val appContext = context.applicationContext
            return SyncRunner(
                settings = SettingsRepository(appContext),
                gateway = HealthConnectGateway(appContext),
                poster = WebhookClient(BuildConfig.VERSION_NAME),
                appVersion = BuildConfig.VERSION_NAME,
            )
        }

        private fun withRetryHint(base: String, retryable: Boolean, origin: SyncOrigin): String {
            val hint = when {
                retryable && origin == SyncOrigin.WORKER -> " Nuovo tentativo automatico."
                retryable -> " L'invio riprende da solo."
                else -> ""
            }
            return base + hint
        }
    }
}
