package com.gpxvideo.lib.ffmpeg

data class FfmpegInput(
    val path: String,
    val options: Map<String, String>
)

data class FfmpegCommand(
    val arguments: List<String>,
    val description: String
) {
    fun toCommandString(): String = arguments.joinToString(" ")
}

class FfmpegCommandBuilder {
    private val inputs = mutableListOf<FfmpegInput>()
    private val filters = mutableListOf<String>()
    private var filterComplex: String? = null
    private val outputOptions = mutableListOf<String>()
    private var outputPath: String? = null
    private var overwriteOutput = false
    private var description: String = ""

    fun addInput(path: String, options: Map<String, String> = emptyMap()): FfmpegCommandBuilder {
        inputs.add(FfmpegInput(path, options))
        return this
    }

    fun addImageSequenceInput(pattern: String, framerate: Int): FfmpegCommandBuilder {
        inputs.add(
            FfmpegInput(
                path = pattern,
                options = mapOf(
                    "framerate" to framerate.toString(),
                    "f" to "image2"
                )
            )
        )
        return this
    }

    fun addFilter(filter: String): FfmpegCommandBuilder {
        filters.add(filter)
        return this
    }

    fun addFilterComplex(filterGraph: String): FfmpegCommandBuilder {
        filterComplex = filterGraph
        return this
    }

    fun setVideoCodec(codec: String): FfmpegCommandBuilder {
        outputOptions.addAll(listOf("-c:v", codec))
        return this
    }

    fun setAudioCodec(codec: String): FfmpegCommandBuilder {
        outputOptions.addAll(listOf("-c:a", codec))
        return this
    }

    fun setResolution(width: Int, height: Int): FfmpegCommandBuilder {
        outputOptions.addAll(listOf("-s", "${width}x${height}"))
        return this
    }

    fun setFrameRate(fps: Int): FfmpegCommandBuilder {
        outputOptions.addAll(listOf("-r", fps.toString()))
        return this
    }

    fun setBitrate(bitrate: Long): FfmpegCommandBuilder {
        outputOptions.addAll(listOf("-b:v", bitrate.toString()))
        return this
    }

    fun setCrf(crf: Int): FfmpegCommandBuilder {
        outputOptions.addAll(listOf("-crf", crf.toString()))
        return this
    }

    fun setPreset(preset: String): FfmpegCommandBuilder {
        outputOptions.addAll(listOf("-preset", preset))
        return this
    }

    fun setPixelFormat(format: String): FfmpegCommandBuilder {
        outputOptions.addAll(listOf("-pix_fmt", format))
        return this
    }

    fun setMovFlags(flags: String): FfmpegCommandBuilder {
        outputOptions.addAll(listOf("-movflags", flags))
        return this
    }

    fun addOutputOption(key: String, value: String): FfmpegCommandBuilder {
        outputOptions.addAll(listOf(key, value))
        return this
    }

    fun setOutput(path: String): FfmpegCommandBuilder {
        outputPath = path
        return this
    }

    fun overwrite(): FfmpegCommandBuilder {
        overwriteOutput = true
        return this
    }

    fun setDescription(desc: String): FfmpegCommandBuilder {
        description = desc
        return this
    }

    fun build(): FfmpegCommand {
        val args = mutableListOf<String>()

        if (overwriteOutput) {
            args.add("-y")
        }

        for (input in inputs) {
            for ((key, value) in input.options) {
                args.add("-$key")
                args.add(value)
            }
            args.add("-i")
            args.add(input.path)
        }

        if (filterComplex != null) {
            args.add("-filter_complex")
            args.add(filterComplex!!)
        } else if (filters.isNotEmpty()) {
            args.add("-vf")
            args.add(filters.joinToString(","))
        }

        args.addAll(outputOptions)

        val output = requireNotNull(outputPath) { "Output path must be set" }
        args.add(output)

        return FfmpegCommand(
            arguments = args,
            description = description.ifEmpty { "FFmpeg command with ${inputs.size} input(s)" }
        )
    }
}
