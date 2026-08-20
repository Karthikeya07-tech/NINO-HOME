package com.example.nino_home.audio

import android.os.SystemClock
import com.example.nino_home.BotService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Pushes 16 kHz mono WAV clips to `/play_wav` with `X-Nino-Stream: 1`.
 *
 * Matches wifi stream.md:
 * - Track [nextSliceStart] (advanced only after a successful POST).
 * - Prefetch: first clip, then second immediately, then keep ~1 playing + 1 queued.
 * - Pause is firmware-owned: [pauseFeeding] stops POSTs; do not re-send current clip.
 */
object PlayWavFeed {
    private val session = AtomicInteger(0)
    private val feedingPaused = AtomicBoolean(false)

    /** PCM byte where the next POST should begin. */
    @Volatile
    var nextSliceStart: Int = 0
        private set

    fun cancel() {
        feedingPaused.set(false)
        session.incrementAndGet()
    }

    /** Stop POSTing further chunks (after /play_wav/pause). Keeps nextSliceStart. */
    fun pauseFeeding() {
        feedingPaused.set(true)
    }

    /** Allow POSTs again (after /play_wav/resume). Continues from nextSliceStart. */
    fun resumeFeeding() {
        feedingPaused.set(false)
    }

    fun resetSliceStart() {
        nextSliceStart = 0
    }

    suspend fun stream(
        bot: BotService,
        pcm: ByteArray,
        startPcmOffset: Int = 0,
        onPlayhead: (pcmOffset: Int) -> Unit = {},
    ): Boolean {
        nextSliceStart = startPcmOffset.coerceIn(0, pcm.size) and 1.inv()
        feedingPaused.set(false)
        return feed(bot, onPlayhead) { first ->
            val offset = nextSliceStart
            if (offset >= pcm.size) return@feed null
            val want = if (first) WavPcm.FIRST_CHUNK_PCM_BYTES else WavPcm.CHUNK_PCM_BYTES
            val slice = want.coerceAtMost(pcm.size - offset) and 1.inv()
            if (slice <= 0) return@feed null
            pcm.copyOfRange(offset, offset + slice)
        }
    }

    suspend fun streamLive(
        bot: BotService,
        decoder: StreamingPcmDecoder,
        startPcmOffset: Int = 0,
        onPlayhead: (pcmOffset: Int) -> Unit = {},
    ): Boolean {
        val start = startPcmOffset.coerceAtLeast(0) and 1.inv()
        nextSliceStart = start
        feedingPaused.set(false)
        if (start > 0) decoder.seekToPcmByte(start)
        return feed(bot, onPlayhead) { first ->
            val want = if (first) WavPcm.FIRST_CHUNK_PCM_BYTES else WavPcm.CHUNK_PCM_BYTES
            val chunk = decoder.read(want)
            if (chunk.isEmpty()) null else chunk
        }
    }

    private suspend fun feed(
        bot: BotService,
        onPlayhead: (pcmOffset: Int) -> Unit,
        nextPcm: suspend (first: Boolean) -> ByteArray?,
    ): Boolean {
        val id = session.incrementAndGet()
        onPlayhead(nextSliceStart)

        var first = true
        var clipsSent = 0
        var lastPostAt = 0L
        var lastChunkMs = 0
        var timelineOrigin = 0L
        var pcmAtOrigin = nextSliceStart

        while (true) {
            coroutineContext.ensureActive()
            if (session.get() != id) return false

            // Firmware pause: do not send more until resumeFeeding().
            while (feedingPaused.get()) {
                coroutineContext.ensureActive()
                if (session.get() != id) return false
                delay(120)
            }

            // Prefetch pacing after the first two clips are already on the robot.
            if (clipsSent >= 2) {
                waitUntilRoomForNextClip(
                    sessionId = id,
                    bot = bot,
                    lastPostAt = lastPostAt,
                    lastChunkMs = lastChunkMs,
                    timelineOrigin = timelineOrigin,
                    pcmAtOrigin = pcmAtOrigin,
                    postedEnd = nextSliceStart,
                    onPlayhead = onPlayhead,
                ) || return false
            }

            val pcmChunk = nextPcm(first) ?: break
            first = false
            if (pcmChunk.isEmpty()) break

            if (session.get() != id) return false
            while (feedingPaused.get()) {
                coroutineContext.ensureActive()
                if (session.get() != id) return false
                delay(120)
            }

            PlayWavClient.postWav(bot, WavPcm.wrapPcm(pcmChunk))
            nextSliceStart = (nextSliceStart + pcmChunk.size) and 1.inv()

            val chunkMs = WavPcm.durationMsForPcmBytes(pcmChunk.size)
            val now = SystemClock.elapsedRealtime()
            if (clipsSent == 0) {
                timelineOrigin = now
                pcmAtOrigin = nextSliceStart - pcmChunk.size
            }
            lastPostAt = now
            lastChunkMs = chunkMs
            clipsSent++
            updatePlayhead(pcmAtOrigin, timelineOrigin, now, nextSliceStart, onPlayhead)

            // Clip #2 goes immediately (1 playing + 1 queued).
            if (clipsSent == 1) continue
        }

        // Drain UI playhead for audio already queued on the robot.
        if (clipsSent > 0 && timelineOrigin > 0L) {
            val endAt = lastPostAt + lastChunkMs
            while (session.get() == id && !feedingPaused.get()) {
                coroutineContext.ensureActive()
                val now = SystemClock.elapsedRealtime()
                updatePlayhead(pcmAtOrigin, timelineOrigin, now, nextSliceStart, onPlayhead)
                if (now >= endAt) break
                delay(100)
            }
        }
        onPlayhead(nextSliceStart)
        return session.get() == id && !feedingPaused.get()
    }

    /**
     * Wait until status.queued is 0/1, or ~chunkDuration − 1.5 s since last POST.
     */
    private suspend fun waitUntilRoomForNextClip(
        sessionId: Int,
        bot: BotService,
        lastPostAt: Long,
        lastChunkMs: Int,
        timelineOrigin: Long,
        pcmAtOrigin: Int,
        postedEnd: Int,
        onPlayhead: (pcmOffset: Int) -> Unit,
    ): Boolean {
        val durationGate = (lastChunkMs - WavPcm.PRELOAD_LEAD_MS).coerceAtLeast(50)
        while (true) {
            coroutineContext.ensureActive()
            if (session.get() != sessionId) return false
            while (feedingPaused.get()) {
                coroutineContext.ensureActive()
                if (session.get() != sessionId) return false
                delay(120)
            }

            val now = SystemClock.elapsedRealtime()
            updatePlayhead(pcmAtOrigin, timelineOrigin, now, postedEnd, onPlayhead)

            val elapsed = (now - lastPostAt).toInt()
            if (elapsed >= durationGate) return true

            val status = runCatching { PlayWavClient.status(bot) }.getOrNull()
            if (status != null) {
                if (status.paused) {
                    // Mirror firmware pause if UI somehow missed it.
                    feedingPaused.set(true)
                    continue
                }
                if (status.queued <= 1) return true
            }

            delay(120)
        }
    }

    private fun updatePlayhead(
        pcmAtOrigin: Int,
        timelineOrigin: Long,
        now: Long,
        postedEnd: Int,
        onPlayhead: (pcmOffset: Int) -> Unit,
    ) {
        if (timelineOrigin == 0L) {
            onPlayhead(pcmAtOrigin)
            return
        }
        val elapsed = (now - timelineOrigin).coerceAtLeast(0L).toInt()
        val timed = pcmAtOrigin + (WavPcm.pcmBytesForDurationMs(elapsed) and 1.inv())
        onPlayhead(timed.coerceAtMost(postedEnd))
    }
}
