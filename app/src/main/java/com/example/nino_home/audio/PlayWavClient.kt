package com.example.nino_home.audio

import com.example.nino_home.BotService
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

object PlayWavClient {
    fun postWav(bot: BotService, wav: ByteArray) {
        val url = URL("http://${bot.host}:${bot.port}/play_wav")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "audio/wav")
            setFixedLengthStreamingMode(wav.size)
        }
        try {
            conn.outputStream.use { it.write(wav) }
            val code = conn.responseCode
            val body = runCatching {
                (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }
            }.getOrNull().orEmpty()
            if (code !in 200..299) {
                val hint = when (code) {
                    413 -> "Clip too large for the robot"
                    503 -> "Robot audio queue is not running"
                    500 -> "Robot out of memory"
                    else -> body.ifBlank { "HTTP $code" }
                }
                throw IOException("play_wav failed: $hint")
            }
            if (body.isNotBlank()) {
                val json = runCatching { JSONObject(body) }.getOrNull()
                if (json != null && json.has("ok") && !json.optBoolean("ok", true)) {
                    throw IOException("play_wav rejected: $body")
                }
            }
        } finally {
            conn.disconnect()
        }
    }
}
