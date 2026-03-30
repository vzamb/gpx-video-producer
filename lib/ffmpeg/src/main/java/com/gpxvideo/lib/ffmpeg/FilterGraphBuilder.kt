package com.gpxvideo.lib.ffmpeg

class FilterGraphBuilder {
    private val filters = mutableListOf<String>()

    fun addVideoConcat(inputCount: Int): FilterGraphBuilder {
        val inputs = (0 until inputCount).joinToString("") { "[v$it]" }
        filters.add("${inputs}concat=n=$inputCount:v=1:a=0[vout]")
        return this
    }

    fun addAudioVideoConcat(inputCount: Int): FilterGraphBuilder {
        val inputs = (0 until inputCount).joinToString("") { "[v$it][a$it]" }
        filters.add("${inputs}concat=n=$inputCount:v=1:a=1[vout][aout]")
        return this
    }

    fun addOverlay(
        baseStream: String,
        overlayStream: String,
        x: Int,
        y: Int,
        outputLabel: String
    ): FilterGraphBuilder {
        filters.add("[$baseStream][$overlayStream]overlay=$x:$y[$outputLabel]")
        return this
    }

    fun addOverlayWithEnable(
        baseStream: String,
        overlayStream: String,
        x: Int,
        y: Int,
        enableStart: Double,
        enableEnd: Double,
        outputLabel: String
    ): FilterGraphBuilder {
        val enable = "enable='between(t,${"%.3f".format(enableStart)},${"%.3f".format(enableEnd)})'"
        filters.add("[$baseStream][$overlayStream]overlay=$x:$y:$enable[$outputLabel]")
        return this
    }

    fun addTrim(
        stream: String,
        startMs: Long,
        endMs: Long,
        outputLabel: String
    ): FilterGraphBuilder {
        val startSec = startMs / 1000.0
        val endSec = endMs / 1000.0
        filters.add("[$stream]trim=start=${"%.3f".format(startSec)}:end=${"%.3f".format(endSec)},setpts=PTS-STARTPTS[$outputLabel]")
        return this
    }

    fun addSetPts(
        stream: String,
        expression: String,
        outputLabel: String
    ): FilterGraphBuilder {
        filters.add("[$stream]setpts=$expression[$outputLabel]")
        return this
    }

    fun addFade(
        stream: String,
        type: String,
        startMs: Long,
        durationMs: Long,
        outputLabel: String
    ): FilterGraphBuilder {
        val startSec = startMs / 1000.0
        val durSec = durationMs / 1000.0
        filters.add("[$stream]fade=t=$type:st=${"%.3f".format(startSec)}:d=${"%.3f".format(durSec)}[$outputLabel]")
        return this
    }

    fun addScale(
        stream: String,
        width: Int,
        height: Int,
        outputLabel: String
    ): FilterGraphBuilder {
        filters.add("[$stream]scale=${width}:${height}:force_original_aspect_ratio=decrease,pad=${width}:${height}:(ow-iw)/2:(oh-ih)/2[$outputLabel]")
        return this
    }

    fun addSpeed(
        stream: String,
        speed: Float,
        outputLabel: String
    ): FilterGraphBuilder {
        val pts = 1.0f / speed
        filters.add("[$stream]setpts=${"%.4f".format(pts)}*PTS[$outputLabel]")
        return this
    }

    fun addAudioSpeed(
        stream: String,
        speed: Float,
        outputLabel: String
    ): FilterGraphBuilder {
        filters.add("[$stream]atempo=${"%.4f".format(speed.coerceIn(0.5f, 2.0f))}[$outputLabel]")
        return this
    }

    fun addVolume(
        stream: String,
        volume: Float,
        outputLabel: String
    ): FilterGraphBuilder {
        filters.add("[$stream]volume=${"%.2f".format(volume)}[$outputLabel]")
        return this
    }

    fun addAudioMix(
        streams: List<String>,
        outputLabel: String
    ): FilterGraphBuilder {
        val inputs = streams.joinToString("") { "[$it]" }
        filters.add("${inputs}amix=inputs=${streams.size}:duration=longest[$outputLabel]")
        return this
    }

    fun addCustomFilter(filter: String): FilterGraphBuilder {
        filters.add(filter)
        return this
    }

    fun build(): String = filters.joinToString(";")
}
