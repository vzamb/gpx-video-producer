package com.gpxvideo.feature.timeline

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

object AudioWaveformExtractor {

    private val cache = ConcurrentHashMap<String, List<Float>>()

    suspend fun extractWaveform(
        context: Context,
        uri: Uri,
        samplesCount: Int = 200
    ): List<Float> {
        val cacheKey = "${uri}:$samplesCount"
        cache[cacheKey]?.let { return it }

        return withContext(Dispatchers.Default) {
            val result = extractWaveformInternal(context, uri, samplesCount)
            cache[cacheKey] = result
            result
        }
    }

    fun clearCache() {
        cache.clear()
    }

    private fun extractWaveformInternal(
        context: Context,
        uri: Uri,
        samplesCount: Int
    ): List<Float> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)

            val audioTrackIndex = findAudioTrack(extractor) ?: return emptyList()
            extractor.selectTrack(audioTrackIndex)

            val format = extractor.getTrackFormat(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return emptyList()
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }

            if (durationUs <= 0) return List(samplesCount) { 0f }

            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(format, null, null, 0)
                codec.start()

                val pcmSamples = decodePcmSamples(codec, extractor, durationUs)
                return computeWaveform(pcmSamples, samplesCount, channelCount)
            } finally {
                codec.stop()
                codec.release()
            }
        } catch (_: Exception) {
            return List(samplesCount) { 0f }
        } finally {
            extractor.release()
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i)
                .getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return null
    }

    private fun decodePcmSamples(
        codec: MediaCodec,
        extractor: MediaExtractor,
        durationUs: Long
    ): ShortArray {
        val bufferInfo = MediaCodec.BufferInfo()
        val output = mutableListOf<Short>()
        var inputDone = false
        var outputDone = false
        val timeoutUs = 10_000L
        // Limit decoded samples to avoid OOM for very long audio
        val maxSamples = 48_000 * 120 // ~120 seconds at 48kHz mono

        while (!outputDone && output.size < maxSamples) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(timeoutUs)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(
                            inputIndex, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(
                            inputIndex, 0, sampleSize,
                            extractor.sampleTime, 0
                        )
                        extractor.advance()
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex) ?: continue
                val shortBuffer = outputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                val sampleCount = shortBuffer.remaining()
                for (i in 0 until sampleCount) {
                    output.add(shortBuffer.get())
                }
                codec.releaseOutputBuffer(outputIndex, false)

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputDone = true
                }
            }
        }

        return output.toShortArray()
    }

    private fun computeWaveform(
        pcmSamples: ShortArray,
        samplesCount: Int,
        channelCount: Int
    ): List<Float> {
        if (pcmSamples.isEmpty()) return List(samplesCount) { 0f }

        // Work with mono data by averaging channels
        val monoSamples = if (channelCount > 1) {
            ShortArray(pcmSamples.size / channelCount) { i ->
                var sum = 0L
                for (ch in 0 until channelCount) {
                    sum += pcmSamples[i * channelCount + ch]
                }
                (sum / channelCount).toShort()
            }
        } else {
            pcmSamples
        }

        val chunkSize = (monoSamples.size / samplesCount).coerceAtLeast(1)
        val rmsValues = FloatArray(samplesCount)

        for (i in 0 until samplesCount) {
            val start = i * chunkSize
            val end = ((i + 1) * chunkSize).coerceAtMost(monoSamples.size)
            if (start >= monoSamples.size) break

            var sumSquares = 0.0
            for (j in start until end) {
                val normalized = monoSamples[j].toFloat() / Short.MAX_VALUE
                sumSquares += normalized * normalized
            }
            rmsValues[i] = sqrt(sumSquares / (end - start)).toFloat()
        }

        // Normalize to 0.0-1.0
        val maxRms = rmsValues.maxOrNull() ?: 0f
        return if (maxRms > 0f) {
            rmsValues.map { it / maxRms }
        } else {
            rmsValues.toList()
        }
    }
}
