package com.example.nino_home.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavPcm {
    const val SAMPLE_RATE = 16_000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
    const val BYTES_PER_SAMPLE = 2
    const val WAV_HEADER_BYTES = 44

    /**
     * Steady-state clip size (~10 s). Firmware max is ~12 s / 384 KiB;
     * stay under the safe 380 KiB app limit.
     */
    const val CHUNK_PCM_BYTES = SAMPLE_RATE * BYTES_PER_SAMPLE * 10

    /** Tiny first clip so sound starts almost immediately. */
    const val FIRST_CHUNK_PCM_BYTES = SAMPLE_RATE * BYTES_PER_SAMPLE * 2

    const val MAX_POST_BYTES = 380 * 1024

    /**
     * Send the next clip this many ms before the current clip is estimated
     * to finish, so the robot always has ~1 clip queued (reduces gaps).
     */
    const val PRELOAD_LEAD_MS = 1_800

    fun durationMsForPcmBytes(pcmBytes: Int): Int {
        if (pcmBytes <= 0) return 0
        return ((pcmBytes.toLong() * 1000L) / (SAMPLE_RATE * BYTES_PER_SAMPLE)).toInt()
    }

    fun pcmBytesForDurationMs(durationMs: Int): Int {
        val samples = (SAMPLE_RATE.toLong() * durationMs.coerceAtLeast(0) / 1000L).toInt()
        return samples * BYTES_PER_SAMPLE
    }

    fun wrapPcm(pcm: ByteArray): ByteArray {
        val len = pcm.size and 1.inv()
        val wav = ByteArray(WAV_HEADER_BYTES + len)
        writeHeader(wav, len)
        System.arraycopy(pcm, 0, wav, WAV_HEADER_BYTES, len)
        require(wav.size <= MAX_POST_BYTES) { "WAV chunk too large (${wav.size})" }
        return wav
    }

    fun wrapPcmSlice(pcm: ByteArray, offset: Int, length: Int): ByteArray {
        val start = offset.coerceIn(0, pcm.size)
        val len = length.coerceIn(0, pcm.size - start) and 1.inv()
        val slice = pcm.copyOfRange(start, start + len)
        return wrapPcm(slice)
    }

    private fun writeHeader(out: ByteArray, dataBytes: Int) {
        val byteRate = SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE
        val blockAlign = CHANNELS * BYTES_PER_SAMPLE
        val buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(36 + dataBytes)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)
        buf.putShort(1) // PCM
        buf.putShort(CHANNELS.toShort())
        buf.putInt(SAMPLE_RATE)
        buf.putInt(byteRate)
        buf.putShort(blockAlign.toShort())
        buf.putShort(BITS_PER_SAMPLE.toShort())
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataBytes)
    }
}
