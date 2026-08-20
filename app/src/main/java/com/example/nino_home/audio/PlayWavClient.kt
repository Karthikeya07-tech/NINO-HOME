package com.example.nino_home.audio

import android.util.Log
import com.example.nino_home.BotService
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

data class PlayWavStatus(
    val playing: Boolean,
    val paused: Boolean,
    val suspended: Boolean,
    val queued: Int,
)

object PlayWavClient {
    private const val TAG = "PlayWavClient"

    fun postWav(bot: BotService, wav: ByteArray) {
        val url = URL("http://${bot.host}:${bot.port}/play_wav")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "audio/wav")
            // Required for gapless music path (do not send for voice/TTS).
            setRequestProperty("X-Nino-Stream", "1")
            setFixedLengthStreamingMode(wav.size)
        }
        try {
            conn.outputStream.use { it.write(wav) }
            val code = conn.responseCode
            val body = readBody(conn, code)
            if (code !in 200..299) {
                throw IOException(errorHint(code, body))
            }
            ensureOk(body)
        } finally {
            conn.disconnect()
        }
    }

    fun pause(bot: BotService) {
        Log.i(TAG, "POST /play_wav/pause → ${bot.host}:${bot.port}")
        postEmpty(bot, "/play_wav/pause")
        Log.i(TAG, "POST /play_wav/pause ok")
    }

    fun resume(bot: BotService) {
        Log.i(TAG, "POST /play_wav/resume → ${bot.host}:${bot.port}")
        postEmpty(bot, "/play_wav/resume")
        Log.i(TAG, "POST /play_wav/resume ok")
    }

    fun stop(bot: BotService) {
        Log.i(TAG, "POST /play_wav/stop → ${bot.host}:${bot.port}")
        postEmpty(bot, "/play_wav/stop")
        Log.i(TAG, "POST /play_wav/stop ok")
    }

    fun status(bot: BotService): PlayWavStatus {
        val url = URL("http://${bot.host}:${bot.port}/play_wav/status")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 2_000
            readTimeout = 2_000
        }
        return try {
            val code = conn.responseCode
            val body = readBody(conn, code)
            if (code !in 200..299) {
                throw IOException("play_wav status failed ($code)${body.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}")
            }
            val json = JSONObject(body.ifBlank { "{}" })
            PlayWavStatus(
                playing = json.optBoolean("playing", false),
                paused = json.optBoolean("paused", false),
                suspended = json.optBoolean("suspended", false),
                queued = json.optInt("queued", 0),
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun postEmpty(bot: BotService, path: String) {
        val url = URL("http://${bot.host}:${bot.port}$path")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 2_000
            readTimeout = 2_000
            doOutput = true
            setRequestProperty("Content-Type", "application/octet-stream")
            setRequestProperty("Content-Length", "0")
            setFixedLengthStreamingMode(0)
        }
        try {
            // Flush an empty body so ESP HTTP servers accept the POST.
            conn.outputStream.use { /* empty */ }
            val code = conn.responseCode
            val body = readBody(conn, code)
            Log.i(TAG, "$path → HTTP $code ${body.take(120)}")
            if (code !in 200..299) {
                throw IOException("$path failed ($code)${body.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}")
            }
            ensureOk(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun readBody(conn: HttpURLConnection, code: Int): String =
        runCatching {
            (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }
        }.getOrNull().orEmpty()

    private fun ensureOk(body: String) {
        if (body.isBlank()) return
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return
        if (json.has("ok") && !json.optBoolean("ok", true)) {
            throw IOException("play_wav rejected: $body")
        }
    }

    private fun errorHint(code: Int, body: String): String {
        val hint = when (code) {
            413 -> "Clip too large for the robot"
            503 -> "Robot audio queue is not running"
            500 -> "Robot out of memory"
            else -> body.ifBlank { "HTTP $code" }
        }
        return "play_wav failed: $hint"
    }
}
