package it.healthsync.app.domain

import kotlinx.serialization.Serializable

/** Cursor for one in-flight export. One webhook POST is sent per worker step. */
@Serializable
data class SendPlan(
    val mode: String,
    val phase: String,
    val cursorEpochMs: Long,
    val endEpochMs: Long,
    val sent: Int = 0,
    val historyLimited: Boolean = false,
    val finished: Boolean = false,
    /** Started from a button, so Wi-Fi and charging limits do not hold this queue. */
    val manual: Boolean = false,
    val batchId: String = "",
    val batchTotal: Int = 0,
    val nextChunk: Int = 1,
) {
    companion object {
        const val BACKFILL = "backfill"
        const val INCREMENTAL = "incremental"
        const val RANGE = "range"
        const val SCAN = "scan"
        const val PREPARE = "prepare"
        const val SEND = "send"
    }
}
