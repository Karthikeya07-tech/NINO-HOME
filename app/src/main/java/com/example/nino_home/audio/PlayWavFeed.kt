package com.example.nino_home.audio

import android.os.SystemClock
import com.example.nino_home.BotService
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Pushes 16 kHz mono WAV clips to `/play_wav`.
 *
 * Decodes the next clip first, then POSTs when the estimated robot queue is
 * down to ~[WavPcm.PRELOAD_LEAD_MS]. That overlaps decode with playback and
 * keeps about one clip queued to reduce gaps.
 */
object PlayWavFeed {
    private val session = AtomicInteger(0)

    fun cancel() {
        session.incrementAndGet()
    }

    suspend fun stream(
        bot: BotService,
        pcm: ByteArray,
        startPcmOffset: Int,
        onPlayhead: (pcmOffset: Int) -> Unit,
    ): Boolean {
        var offset = startPcmOffset.coerceIn(0, pcm.size) and 1.inv()
        return feed(bot, onPlayhead, offset) { first ->
            if (offset >= pcm.size) return@feed null
            val want = if (first) WavPcm.FIRST_CHUNK_PCM_BYTES else WavPcm.CHUNK_PCM_BYTES
            val slice = want.coerceAtMost(pcm.size - offset) and 1.inv()
            if (slice <= 0) return@feed null
            val chunk = pcm.copyOfRange(offset, offset + slice)
            offset += slice
            chunk
        }
    }

    suspend fun streamLive(
        bot: BotService,
        decoder: StreamingPcmDecoder,
        startPcmOffset: Int,
        onPlayhead: (pcmOffset: Int) -> Unit,
    ): Boolean {
        val start = startPcmOffset.coerceAtLeast(0) and 1.inv()
        if (start > 0) decoder.seekToPcmByte(start)
        var offset = start
        return feed(bot, onPlayhead, offset) { first ->
            val want = if (first) WavPcm.FIRST_CHUNK_PCM_BYTES else WavPcm.CHUNK_PCM_BYTES
            val chunk = decoder.read(want)
            if (chunk.isEmpty()) return@feed null
            offset += chunk.size
            chunk
        }
    }

    private suspend fun feed(
        bot: BotService,
        onPlayhead: (pcmOffset: Int) -> Unit,
        startingOffset: Int,
        nextPcm: suspend (first: Boolean) -> ByteArray?,
    ): Boolean {
        val id = session.incrementAndGet()
        var pcmPlayhead = startingOffset and 1.inv()
        onPlayhead(pcmPlayhead)

        var first = true
        var queueEndAt = 0L
        var timelineOrigin = 0L
        var pcmAtOrigin = startingOffset and 1.inv()

        while (true) {
            coroutineContext.ensureActive()
            if (session.get() != id) return false

            // Decode/prepare next clip while the robot is still playing.
            val pcmChunk = nextPcm(first) ?: break
            first = false
            if (pcmChunk.isEmpty()) break

            // Wait to send until the queue is near the preload lead.
            if (queueEndAt > 0L) {
                while (true) {
                    coroutineContext.ensureActive()
                    if (session.get() != id) return false
                    val now = SystemClock.elapsedRealtime()
                    val sendAt = queueEndAt - WavPcm.PRELOAD_LEAD_MS
                    updatePlayhead(pcmAtOrigin, timelineOrigin, now, pcmPlayhead, onPlayhead)
                    if (now >= sendAt) break
                    delay(minOf(80L, (sendAt - now).coerceAtLeast(1L)))
                }
            }

            if (session.get() != id) return false
            PlayWavClient.postWav(bot, WavPcm.wrapPcm(pcmChunk))

            val chunkMs = WavPcm.durationMsForPcmBytes(pcmChunk.size)
            val now = SystemClock.elapsedRealtime()
            if (queueEndAt == 0L) {
                timelineOrigin = now
                pcmAtOrigin = pcmPlayhead
                queueEndAt = now
            }
            if (queueEndAt < now) queueEndAt = now
            queueEndAt += chunkMs
            pcmPlayhead += pcmChunk.size
            updatePlayhead(pcmAtOrigin, timelineOrigin, now, pcmPlayhead, onPlayhead)
        }

        while (session.get() == id) {
            coroutineContext.ensureActive()
            val now = SystemClock.elapsedRealtime()
            updatePlayhead(pcmAtOrigin, timelineOrigin, now, pcmPlayhead, onPlayhead)
            if (queueEndAt == 0L || now >= queueEndAt) break
            delay(100)
        }
        onPlayhead(pcmPlayhead)
        return session.get() == id
    }

    private fun updatePlayhead(
        pcmAtOrigin: Int,
        timelineOrigin: Long,
        now: Long,
        postedPcmEnd: Int,
        onPlayhead: (pcmOffset: Int) -> Unit,
    ) {
        if (timelineOrigin == 0L) {
            onPlayhead(pcmAtOrigin)
            return
        }
        val elapsed = (now - timelineOrigin).coerceAtLeast(0L).toInt()
        val timed = pcmAtOrigin + (WavPcm.pcmBytesForDurationMs(elapsed) and 1.inv())
        onPlayhead(timed.coerceAtMost(postedPcmEnd))
    }
}
