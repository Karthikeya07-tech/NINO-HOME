package com.example.nino_home.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Decodes phone audio to 16 kHz mono 16-bit PCM incrementally so the first
 * clip can be sent without waiting for the whole song.
 */
class StreamingPcmDecoder(
    context: Context,
    uri: Uri,
) : Closeable {
    private val appContext = context.applicationContext
    private val extractor = MediaExtractor()
    private val codec: MediaCodec
    private val info = MediaCodec.BufferInfo()

    private var inputDone = false
    private var outputDone = false
    private var sampleRate = WavPcm.SAMPLE_RATE
    private var channelCount = 1
    private var pcmEncoding = android.media.AudioFormat.ENCODING_PCM_16BIT
    private var closed = false

    private var pendingSrc = ShortArray(0)
    private var resamplePos = 0.0
    private val outBuffer = ByteArrayOutputStream(WavPcm.FIRST_CHUNK_PCM_BYTES * 2)

    val durationMs: Int = readDurationMs(appContext, uri)

    init {
        extractor.setDataSource(appContext, uri, null)
        val track = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        } ?: throw IllegalStateException("No audio track in this file")

        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val mime = format.getString(MediaFormat.KEY_MIME)
            ?: throw IllegalStateException("Unknown audio format")
        if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        }
        if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        }

        codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()
    }

    fun seekToPcmByte(pcmByte: Int) {
        check(!closed)
        val us = if (pcmByte <= 0) {
            0L
        } else {
            (pcmByte.toLong() / WavPcm.BYTES_PER_SAMPLE) * 1_000_000L / WavPcm.SAMPLE_RATE
        }
        codec.flush()
        extractor.seekTo(us, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        inputDone = false
        outputDone = false
        pendingSrc = ShortArray(0)
        resamplePos = 0.0
        outBuffer.reset()
    }

    /** Read up to [maxBytes] of converted PCM (even). Empty = end of stream. */
    fun read(maxBytes: Int): ByteArray {
        check(!closed)
        val want = maxBytes.coerceAtLeast(0) and 1.inv()
        if (want <= 0) return ByteArray(0)

        var spins = 0
        while (outBuffer.size() < want && !outputDone && spins < 400) {
            val progressed = pumpOnce()
            if (!progressed) spins++ else spins = 0
        }
        // Flush resampler leftovers when decoder ended.
        if (outputDone && pendingSrc.isNotEmpty()) {
            flushResampler()
        }

        val available = outBuffer.size() and 1.inv()
        if (available <= 0) return ByteArray(0)

        val all = outBuffer.toByteArray()
        val take = min(want, available)
        val result = all.copyOfRange(0, take)
        outBuffer.reset()
        if (take < all.size) {
            outBuffer.write(all, take, all.size - take)
        }
        return result
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { codec.stop() }
        codec.release()
        extractor.release()
    }

    private fun pumpOnce(): Boolean {
        var progressed = false
        if (!inputDone) {
            val inIndex = codec.dequeueInputBuffer(1_000)
            if (inIndex >= 0) {
                val buffer = codec.getInputBuffer(inIndex)
                if (buffer == null) {
                    codec.queueInputBuffer(inIndex, 0, 0, 0, 0)
                } else {
                    buffer.clear()
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(
                            inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(
                            inIndex, 0, sampleSize, extractor.sampleTime.coerceAtLeast(0L), 0,
                        )
                        extractor.advance()
                    }
                }
                progressed = true
            }
        }

        when (val outIndex = codec.dequeueOutputBuffer(info, 1_000)) {
            MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                val outFormat = codec.outputFormat
                if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }
                if (outFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    channelCount = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                }
                if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    pcmEncoding = outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                }
                progressed = true
            }
            MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> progressed = true
            else -> if (outIndex >= 0) {
                val buffer = codec.getOutputBuffer(outIndex)
                if (buffer != null && info.size > 0) {
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val chunk = ByteArray(info.size)
                    buffer.get(chunk)
                    appendConverted(chunk)
                    progressed = true
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputDone = true
                    progressed = true
                }
            }
        }
        return progressed
    }

    private fun appendConverted(raw: ByteArray) {
        val mono = when (pcmEncoding) {
            android.media.AudioFormat.ENCODING_PCM_FLOAT -> floatToMono16(raw, channelCount)
            android.media.AudioFormat.ENCODING_PCM_8BIT -> pcm8ToMono16(raw, channelCount)
            else -> pcm16ToMono(raw, channelCount)
        }
        if (mono.isEmpty()) return

        if (sampleRate == WavPcm.SAMPLE_RATE) {
            outBuffer.write(shortsToLeBytes(mono))
            return
        }

        val combined = if (pendingSrc.isEmpty()) {
            mono
        } else {
            ShortArray(pendingSrc.size + mono.size).also {
                System.arraycopy(pendingSrc, 0, it, 0, pendingSrc.size)
                System.arraycopy(mono, 0, it, pendingSrc.size, mono.size)
            }
        }
        val (converted, leftover, newPos) = resampleChunk(
            input = combined,
            srcRate = sampleRate,
            dstRate = WavPcm.SAMPLE_RATE,
            startPos = resamplePos,
        )
        pendingSrc = leftover
        resamplePos = newPos
        if (converted.isNotEmpty()) {
            outBuffer.write(shortsToLeBytes(converted))
        }
    }

    private fun flushResampler() {
        if (pendingSrc.isEmpty()) return
        // Emit last sample held for interpolation.
        if (pendingSrc.size == 1) {
            outBuffer.write(shortsToLeBytes(pendingSrc))
        } else {
            val (converted, _, _) = resampleChunk(
                input = pendingSrc,
                srcRate = sampleRate,
                dstRate = WavPcm.SAMPLE_RATE,
                startPos = 0.0,
            )
            if (converted.isNotEmpty()) outBuffer.write(shortsToLeBytes(converted))
        }
        pendingSrc = ShortArray(0)
        resamplePos = 0.0
    }

    companion object {
        fun readDurationMs(context: Context, uri: Uri): Int {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toIntOrNull()
                    ?.coerceAtLeast(0)
                    ?: 0
            } catch (_: Exception) {
                0
            } finally {
                runCatching { retriever.release() }
            }
        }

        private fun pcm16ToMono(bytes: ByteArray, channels: Int): ShortArray {
            val frames = bytes.size / (2 * channels)
            if (frames <= 0) return ShortArray(0)
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val out = ShortArray(frames)
            if (channels == 1) {
                for (i in 0 until frames) out[i] = bb.short
                return out
            }
            for (i in 0 until frames) {
                var acc = 0
                for (c in 0 until channels) acc += bb.short.toInt()
                out[i] = (acc / channels).toShort()
            }
            return out
        }

        private fun pcm8ToMono16(bytes: ByteArray, channels: Int): ShortArray {
            val frames = bytes.size / channels
            if (frames <= 0) return ShortArray(0)
            val out = ShortArray(frames)
            var i = 0
            for (f in 0 until frames) {
                var acc = 0
                for (c in 0 until channels) {
                    acc += ((bytes[i].toInt() and 0xFF) - 128) shl 8
                    i++
                }
                out[f] = (acc / channels).toShort()
            }
            return out
        }

        private fun floatToMono16(bytes: ByteArray, channels: Int): ShortArray {
            val frames = bytes.size / (4 * channels)
            if (frames <= 0) return ShortArray(0)
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val out = ShortArray(frames)
            for (i in 0 until frames) {
                var acc = 0f
                for (c in 0 until channels) acc += bb.float
                out[i] = ((acc / channels).coerceIn(-1f, 1f) * Short.MAX_VALUE)
                    .roundToInt().toShort()
            }
            return out
        }

        private fun resampleChunk(
            input: ShortArray,
            srcRate: Int,
            dstRate: Int,
            startPos: Double,
        ): Triple<ShortArray, ShortArray, Double> {
            if (input.isEmpty() || srcRate <= 0) {
                return Triple(ShortArray(0), input, startPos)
            }
            val step = srcRate.toDouble() / dstRate
            val out = ArrayList<Short>((input.size.toLong() * dstRate / srcRate).toInt() + 8)
            var pos = startPos
            while (pos + 1.0 < input.size) {
                val index = pos.toInt()
                val frac = pos - index
                val a = input[index].toInt()
                val b = input[index + 1].toInt()
                out.add((a + ((b - a) * frac)).roundToInt().toShort())
                pos += step
            }
            val consumed = pos.toInt().coerceIn(0, input.size)
            val leftover = if (consumed < input.size) {
                input.copyOfRange(consumed, input.size)
            } else {
                ShortArray(0)
            }
            return Triple(out.toShortArray(), leftover, pos - consumed)
        }

        private fun shortsToLeBytes(samples: ShortArray): ByteArray {
            val out = ByteArray(samples.size * 2)
            val bb = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
            for (s in samples) bb.putShort(s)
            return out
        }
    }
}

data class DecodedPcm(
    val pcm: ByteArray,
    val durationMs: Int,
)

object AudioPcmDecoder {
    fun decodeTo16kMonoPcm(context: Context, uri: Uri): DecodedPcm {
        StreamingPcmDecoder(context, uri).use { decoder ->
            val chunks = ArrayList<ByteArray>()
            var total = 0
            while (true) {
                val slice = decoder.read(WavPcm.CHUNK_PCM_BYTES)
                if (slice.isEmpty()) break
                chunks.add(slice)
                total += slice.size
            }
            if (total == 0) throw IllegalStateException("Decoded audio was empty")
            val pcm = ByteArray(total)
            var at = 0
            for (c in chunks) {
                System.arraycopy(c, 0, pcm, at, c.size)
                at += c.size
            }
            val durationMs = decoder.durationMs.takeIf { it > 0 }
                ?: WavPcm.durationMsForPcmBytes(pcm.size)
            return DecodedPcm(pcm = pcm, durationMs = durationMs)
        }
    }
}
