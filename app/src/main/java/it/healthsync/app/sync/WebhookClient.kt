package it.healthsync.app.sync

import it.healthsync.app.domain.isRetryableHttpStatus
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed class PostResult {
    data class Ok(val code: Int) : PostResult()
    data class HttpError(val code: Int, val retryable: Boolean) : PostResult()
    data class NetworkError(val message: String) : PostResult()
}

fun interface WebhookPoster {
    fun post(url: String, headerName: String, headerValue: String, json: String): PostResult
}

class WebhookClient(private val appVersion: String) : WebhookPoster {
    override fun post(url: String, headerName: String, headerValue: String, json: String): PostResult {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("User-Agent", "SaluteSync/$appVersion")
            setRequestProperty(headerName, headerValue)
        }
        return try {
            connection.outputStream.use { stream ->
                stream.write(json.toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            if (code in 200..299) {
                PostResult.Ok(code)
            } else {
                PostResult.HttpError(code, isRetryableHttpStatus(code))
            }
        } catch (error: IOException) {
            PostResult.NetworkError(error.message ?: "errore di rete")
        } finally {
            connection.disconnect()
        }
    }
}
