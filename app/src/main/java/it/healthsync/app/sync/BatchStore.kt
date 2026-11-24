package it.healthsync.app.sync

import android.content.Context
import java.io.File

/** One prepared batch on disk. Index files are 1-based. */
class BatchStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "send-batch")

    fun replace(chunks: List<String>) {
        clear()
        dir.mkdirs()
        chunks.forEachIndexed { offset, json ->
            File(dir, "${offset + 1}.json").writeText(json, Charsets.UTF_8)
        }
    }

    fun read(index: Int): String? {
        val file = File(dir, "$index.json")
        if (!file.isFile) return null
        return file.readText(Charsets.UTF_8)
    }

    fun clear() {
        dir.deleteRecursively()
    }
}
