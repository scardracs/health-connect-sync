package it.healthsync.app.sync

import android.content.Context
import android.util.Log
import it.healthsync.app.BuildConfig
import it.healthsync.app.data.SettingsRepository
import it.healthsync.app.R
import it.healthsync.app.domain.CompactPayloads
import it.healthsync.app.domain.FailureStreak
import it.healthsync.app.domain.FieldError
import it.healthsync.app.domain.HistoryRange
import it.healthsync.app.domain.ItalianDates
import it.healthsync.app.domain.SendPlan
import it.healthsync.app.domain.WireDay
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
    private val context: Context,
    private val settings: SettingsRepository,
    private val gateway: HealthConnectGateway,
    private val poster: WebhookPoster,
    private val appVersion: String,
    private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    /** First run: history scan. Later runs call [armSync]. */
    fun armInitial(manual: Boolean) {
        val existing = settings.plan()
        if (existing != null && !existing.finished) return
        val attemptAt = now()
        BatchStore(context).clear()
        settings.savePlan(
            SendPlan(
                mode = SendPlan.BACKFILL,
                phase = SendPlan.SCAN,
                cursorEpochMs = attemptAt.toEpochMilli(),
                endEpochMs = attemptAt.toEpochMilli(),
                manual = manual,
            ),
        )
    }

    /** Resume an in-progress export, or start today's incremental window. */
    fun armSync(manual: Boolean) {
        val existing = settings.plan()
        if (existing != null && !existing.finished) return
        val attemptAt = now()
        val start = settings.syncState.value.lastSuccess ?: attemptAt.minus(7, ChronoUnit.DAYS)
        BatchStore(context).clear()
        settings.savePlan(
            SendPlan(
                mode = SendPlan.INCREMENTAL,
                phase = SendPlan.PREPARE,
                cursorEpochMs = start.toEpochMilli(),
                endEpochMs = attemptAt.toEpochMilli(),
                manual = manual,
            ),
        )
    }

    /** Drop progress and import history again from the configured start date. */
    fun armReimport() {
        val attemptAt = now()
        settings.clearPlan()
        BatchStore(context).clear()
        settings.savePlan(
            SendPlan(
                mode = SendPlan.BACKFILL,
                phase = SendPlan.SCAN,
                cursorEpochMs = attemptAt.toEpochMilli(),
                endEpochMs = attemptAt.toEpochMilli(),
                manual = true,
            ),
        )
    }

    /** Send one inclusive local range, including calorie-only days. */
    fun armRange(start: java.time.LocalDate, endInclusive: java.time.LocalDate) {
        val zoneId = zone()
        val startInstant = start.atStartOfDay(zoneId).toInstant()
        val endInstant = endInclusive.plusDays(1).atStartOfDay(zoneId).toInstant()
        settings.clearPlan()
        BatchStore(context).clear()
        settings.savePlan(
            SendPlan(
                mode = SendPlan.RANGE,
                phase = SendPlan.PREPARE,
                cursorEpochMs = startInstant.toEpochMilli(),
                endEpochMs = endInstant.toEpochMilli(),
                manual = true,
            ),
        )
    }

    suspend fun step(origin: SyncOrigin): StepResult = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) return@withContext StepResult.Failed(
            SyncOutcome.Failure(context.getString(R.string.msg_busy), retryable = true),
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
            val failure = SyncOutcome.Failure(explain(validation.urlError ?: validation.headerNameError ?: validation.headerValueError), retryable = false)
            recordFailure(attemptAt, failure.message, null)
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
            if (plan.phase == SendPlan.PREPARE || plan.batchTotal == 0) {
                prepare(plan, attemptAt, zoneId)
            } else {
                sendChunk(plan, attemptAt, config.url, config.headerName, config.headerValue, origin)
            }
        } catch (error: Exception) {
            Log.e(TAG, "step failed: ${error.javaClass.simpleName}")
            val failure = SyncOutcome.Failure(
                withRetryHint(context.getString(R.string.msg_unexpected), retryable = true, origin),
                retryable = true,
            )
            recordFailure(attemptAt, failure.message, null)
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
        settings.noteProgress(now, context.getString(R.string.msg_scanning))
        val discovered = userDate ?: gateway.findEarliestNonCalorieDay(horizonStart, now, zoneId)
        if (discovered == null) {
            settings.noteProgress(now, context.getString(R.string.msg_no_real_days))
            return plan.copy(finished = true, historyLimited = !historyGranted, endEpochMs = now.toEpochMilli())
        }
        val start = discovered.atStartOfDay(zoneId).toInstant().let { if (it.isBefore(horizonStart)) horizonStart else it }
        return plan.copy(
            phase = SendPlan.PREPARE,
            cursorEpochMs = start.toEpochMilli(),
            endEpochMs = now.toEpochMilli(),
            historyLimited = !historyGranted,
            finished = !start.isBefore(now),
        )
    }

    private suspend fun prepare(plan: SendPlan, now: Instant, zoneId: ZoneId): StepResult {
        var cursor = Instant.ofEpochMilli(plan.cursorEpochMs)
        val end = Instant.ofEpochMilli(plan.endEpochMs)
        val days = mutableListOf<WireDay>()
        var guard = 0
        while (cursor.isBefore(end) && guard < 2_000) {
            val windowEnd = minOf(cursor.plus(7, ChronoUnit.DAYS), end)
            when (val read = gateway.read(cursor, windowEnd, zoneId, plan.historyLimited, allowHistoryClamp = false)) {
                is ReadOutcome.Unavailable -> return failStep(now, read.message, false)
                is ReadOutcome.PermissionDenied -> return failStep(now, read.message, false)
                is ReadOutcome.Ok -> {
                    settings.saveCounts(read.snapshot.counts)
                    var found = CompactPayloads.daysFrom(read.snapshot)
                    if (plan.mode == SendPlan.BACKFILL) found = CompactPayloads.forBackfill(found)
                    days += found
                    cursor = windowEnd
                }
            }
            guard += 1
        }
        val uniqueDays = days.associateBy { it.date }.values.sortedBy { it.date }
        if (uniqueDays.isEmpty()) {
            settings.savePlan(plan.copy(finished = true, phase = SendPlan.SEND))
            settings.markSuccess(now, end, context.getString(R.string.msg_nothing_left), 0)
            return StepResult.Finished
        }
        val batchId = now.toEpochMilli().toString(16).padStart(12, '0').takeLast(12)
        val kind = when (plan.mode) {
            SendPlan.BACKFILL -> CompactPayloads.KIND_BACKFILL
            SendPlan.RANGE -> CompactPayloads.KIND_RANGE
            else -> CompactPayloads.KIND_INCREMENTAL
        }
        val syncedAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            now.atZone(zoneId).truncatedTo(ChronoUnit.SECONDS),
        )
        val bodies = CompactPayloads.pack(uniqueDays, kind, zoneId.id, appVersion, syncedAt, batchId)
        BatchStore(context).replace(bodies.map { it.json })
        val ready = plan.copy(
            phase = SendPlan.SEND,
            batchId = batchId,
            batchTotal = bodies.size,
            nextChunk = 1,
            sent = 0,
            cursorEpochMs = end.toEpochMilli(),
        )
        settings.savePlan(ready)
        settings.noteProgress(now, context.getString(R.string.msg_batch_ready, bodies.size))
        return StepResult.ScanMore(1)
    }

    private suspend fun sendChunk(
        plan: SendPlan,
        now: Instant,
        url: String,
        headerName: String,
        headerValue: String,
        origin: SyncOrigin,
    ): StepResult {
        val json = BatchStore(context).read(plan.nextChunk)
            ?: return failStep(now, context.getString(R.string.msg_unexpected), true)
        check(CompactPayloads.bytes(json) <= CompactPayloads.MAX_BYTES)
        return when (val posted = poster.post(url, headerName, headerValue, json)) {
            is PostResult.Ok -> {
                val done = plan.nextChunk >= plan.batchTotal
                if (done) {
                    BatchStore(context).clear()
                    settings.savePlan(plan.copy(sent = plan.sent + 1, finished = true))
                    settings.markSuccess(
                        now,
                        Instant.ofEpochMilli(plan.endEpochMs),
                        context.getString(R.string.msg_send_complete, plan.batchTotal),
                        posted.code,
                    )
                    StepResult.Finished
                } else {
                    val updated = plan.copy(nextChunk = plan.nextChunk + 1, sent = plan.sent + 1)
                    settings.savePlan(updated)
                    settings.noteProgress(
                        now,
                        context.getString(R.string.msg_batch_progress, updated.sent, plan.batchTotal),
                    )
                    StepResult.Posted(settings.gapSeconds())
                }
            }
            is PostResult.HttpError -> {
                val message = context.getString(R.string.msg_http, posted.code)
                failStep(now, withRetryHint(message, posted.retryable, origin), posted.retryable, posted.code)
            }
            is PostResult.NetworkError -> {
                failStep(now, withRetryHint(context.getString(R.string.msg_network), true, origin), true)
            }
        }
    }

    private fun failStep(now: Instant, message: String, retryable: Boolean, httpCode: Int? = null): StepResult {
        recordFailure(now, message, httpCode)
        return StepResult.Failed(SyncOutcome.Failure(message, retryable, httpCode))
    }

    private fun recordFailure(now: Instant, message: String, httpCode: Int?) {
        settings.markFailure(now, message, httpCode)
        val streak = settings.bumpFailureStreak()
        if (FailureStreak.shouldNotify(streak)) SyncNotifier.showRepeatedFailure(context, streak)
    }

    private fun planMessage(plan: SendPlan): String {
        val res = if (plan.historyLimited) R.string.msg_stopped_limited else R.string.msg_stopped_empty
        return context.getString(res)
    }

    private fun explain(code: String?): String {
        val res = when (code) {
            FieldError.URL_REQUIRED -> R.string.err_url_required
            FieldError.URL_TOO_LONG -> R.string.err_url_too_long
            FieldError.URL_WHITESPACE -> R.string.err_url_whitespace
            FieldError.URL_INVALID -> R.string.err_url_invalid
            FieldError.URL_SCHEME -> R.string.err_url_scheme
            FieldError.URL_HOST -> R.string.err_url_host
            FieldError.HEADER_NAME_TOO_LONG -> R.string.err_header_name_too_long
            FieldError.HEADER_NAME_INVALID -> R.string.err_header_name_invalid
            FieldError.HEADER_VALUE_REQUIRED -> R.string.err_header_value_required
            FieldError.HEADER_VALUE_TOO_LONG -> R.string.err_header_value_too_long
            FieldError.HEADER_VALUE_NEWLINE -> R.string.err_header_value_newline
            else -> R.string.msg_webhook_invalid
        }
        return context.getString(res)
    }

    private fun withRetryHint(base: String, retryable: Boolean, origin: SyncOrigin): String {
        val hint = when {
            retryable && origin == SyncOrigin.WORKER -> context.getString(R.string.msg_retry_auto)
            retryable -> context.getString(R.string.msg_retry_resume)
            else -> ""
        }
        return base + hint
    }

    companion object {
        private const val TAG = "SaluteSync"
        private val mutex = Mutex()

        fun create(context: Context): SyncRunner {
            val appContext = context.applicationContext
            return SyncRunner(
                context = appContext,
                settings = SettingsRepository(appContext),
                gateway = HealthConnectGateway(appContext),
                poster = WebhookClient(BuildConfig.VERSION_NAME),
                appVersion = BuildConfig.VERSION_NAME,
            )
        }

    }
}
