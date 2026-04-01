package com.gpxvideo.feature.export

import android.content.Context
import com.gpxvideo.core.model.ExportFormat
import com.gpxvideo.core.model.OverlayConfig
import com.gpxvideo.core.model.TransitionType
import com.gpxvideo.lib.ffmpeg.FfmpegCommand
import com.gpxvideo.lib.ffmpeg.FfmpegCommandBuilder
import com.gpxvideo.lib.ffmpeg.FfmpegExecutor
import com.gpxvideo.lib.ffmpeg.FfmpegResult
import com.gpxvideo.lib.ffmpeg.FilterGraphBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private data class RenderedOverlayInput(
    val path: String,
    val isSequence: Boolean
)

class ExportPipeline @Inject constructor(
    private val ffmpegExecutor: FfmpegExecutor,
    private val overlayFrameRenderer: OverlayFrameRenderer,
    @ApplicationContext private val context: Context
) {

    suspend fun export(
        config: ExportConfig,
        onPhaseChanged: (ExportPhase) -> Unit,
        onProgress: (Float) -> Unit
    ): FfmpegResult = withContext(Dispatchers.IO) {

        onPhaseChanged(ExportPhase.PREPARING)
        onProgress(0f)

        val tempDir = File(context.cacheDir, "export_${config.projectId}")
        tempDir.mkdirs()

        if (config.clips.isEmpty()) {
            return@withContext FfmpegResult.Error("No clips to export", -1, "")
        }

        val dynamicOverlays = config.overlays.filter { isDynamic(it.overlayConfig) }
        val stillOverlays = config.overlays.filterNot { isDynamic(it.overlayConfig) }
        val renderedInputs = linkedMapOf<ExportOverlay, RenderedOverlayInput>()
        val totalOverlayCount = (dynamicOverlays.size + stillOverlays.size).coerceAtLeast(1)

        if (config.overlays.isNotEmpty()) {
            onPhaseChanged(ExportPhase.RENDERING_OVERLAYS)
            val outputWidth = config.outputSettings.resolution.width
            val outputHeight = config.outputSettings.resolution.height
            var completed = 0

            if (dynamicOverlays.isNotEmpty() && config.gpxData != null && config.syncEngine != null) {
                for (overlay in dynamicOverlays) {
                    val overlayWidth = (overlay.overlayConfig.size.width * outputWidth).toInt().coerceAtLeast(1)
                    val overlayHeight = (overlay.overlayConfig.size.height * outputHeight).toInt().coerceAtLeast(1)

                    val pattern = overlayFrameRenderer.renderOverlayFrames(
                        overlay = overlay,
                        gpxData = config.gpxData,
                        syncEngine = config.syncEngine,
                        outputDir = tempDir,
                        frameRate = config.outputSettings.frameRate,
                        width = overlayWidth,
                        height = overlayHeight
                    ) { frameProgress ->
                        val doneWeight = completed.toFloat() / totalOverlayCount
                        val currentWeight = frameProgress / totalOverlayCount
                        onProgress(0.3f * (doneWeight + currentWeight))
                    }
                    renderedInputs[overlay] = RenderedOverlayInput(
                        path = File(tempDir, pattern).absolutePath,
                        isSequence = true
                    )
                    completed += 1
                    onProgress(0.3f * (completed.toFloat() / totalOverlayCount))
                }
            }

            for (overlay in stillOverlays) {
                val overlayWidth = (overlay.overlayConfig.size.width * outputWidth).toInt().coerceAtLeast(1)
                val overlayHeight = (overlay.overlayConfig.size.height * outputHeight).toInt().coerceAtLeast(1)
                val path = overlayFrameRenderer.renderStillOverlay(
                    overlay = overlay,
                    gpxData = config.gpxData,
                    gpxStats = config.gpxStats,
                    outputDir = tempDir,
                    width = overlayWidth,
                    height = overlayHeight
                )
                if (path != null) {
                    renderedInputs[overlay] = RenderedOverlayInput(path = path, isSequence = false)
                }
                completed += 1
                onProgress(0.3f * (completed.toFloat() / totalOverlayCount))
            }
        }

        onPhaseChanged(ExportPhase.ENCODING)

        val command = buildFfmpegCommand(config, renderedInputs)
        val result = ffmpegExecutor.execute(command) { progress ->
            val encodingBase = if (renderedInputs.isNotEmpty()) 0.3f else 0f
            val encodingWeight = 0.6f
            onProgress(encodingBase + encodingWeight * progress.percentage)
        }

        if (result is FfmpegResult.Error || result is FfmpegResult.Cancelled) {
            cleanupTempDir(tempDir)
            return@withContext result
        }

        onPhaseChanged(ExportPhase.MIXING_AUDIO)
        onProgress(0.9f)

        onPhaseChanged(ExportPhase.FINALIZING)
        cleanupTempDir(tempDir)
        onProgress(1f)

        result
    }

    private fun buildFfmpegCommand(
        config: ExportConfig,
        renderedInputs: Map<ExportOverlay, RenderedOverlayInput>
    ): FfmpegCommand {
        val builder = FfmpegCommandBuilder().overwrite()
        val settings = config.outputSettings
        val filterGraph = FilterGraphBuilder()
        val width = settings.resolution.width
        val height = settings.resolution.height

        for (clip in config.clips) {
            val inputOptions = mutableMapOf<String, String>()
            if (clip.trimStartMs > 0) {
                inputOptions["ss"] = "%.3f".format(clip.trimStartMs / 1000.0)
            }
            if (clip.trimEndMs > 0) {
                inputOptions["to"] = "%.3f".format(clip.trimEndMs / 1000.0)
            }
            builder.addInput(clip.filePath, inputOptions)
        }

        val overlayEntries = renderedInputs.entries.toList()
        val overlayInputIndex = config.clips.size
        overlayEntries.forEach { (overlay, rendered) ->
            val startSeconds = "%.3f".format(overlay.startTimeMs / 1000.0)
            if (rendered.isSequence) {
                builder.addImageSequenceInput(
                    rendered.path,
                    settings.frameRate,
                    options = linkedMapOf("itsoffset" to startSeconds)
                )
            } else {
                builder.addInput(
                    rendered.path,
                    linkedMapOf(
                        "loop" to "1",
                        "itsoffset" to startSeconds
                    )
                )
            }
        }

        if (config.clips.size == 1 && overlayEntries.isEmpty()) {
            val clip = config.clips.first()
            if (clip.speed != 1.0f) {
                filterGraph.addSpeed("0:v", clip.speed, "v0")
                if (clip.volume > 0f) {
                    filterGraph.addAudioSpeed("0:a", clip.speed, "a0_speed")
                    filterGraph.addVolume("a0_speed", clip.volume, "a0")
                }
            } else if (clip.volume != 1.0f) {
                filterGraph.addVolume("0:a", clip.volume, "a0")
            }

            filterGraph.addScale("v0".takeIf { clip.speed != 1.0f } ?: "0:v", width, height, "vout")
        } else {
            for ((idx, clip) in config.clips.withIndex()) {
                val inputLabel = "$idx:v"
                var currentLabel = inputLabel

                if (clip.speed != 1.0f) {
                    val speedLabel = "v${idx}_speed"
                    filterGraph.addSpeed(currentLabel, clip.speed, speedLabel)
                    currentLabel = speedLabel
                }

                val scaleLabel = "v${idx}_scaled"
                filterGraph.addScale(currentLabel, width, height, scaleLabel)
                currentLabel = scaleLabel

                if (clip.transition != null && clip.transition.type != TransitionType.CUT) {
                    val fadeLabel = "v${idx}_fade"
                    filterGraph.addFade(
                        currentLabel, "in", 0,
                        clip.transition.durationMs, fadeLabel
                    )
                    currentLabel = fadeLabel
                }

                if (currentLabel != "v$idx") {
                    filterGraph.addSetPts(currentLabel, "PTS-STARTPTS", "v$idx")
                }

                val audioInput = "$idx:a"
                var currentAudioLabel = audioInput
                if (clip.speed != 1.0f) {
                    val audioSpeedLabel = "a${idx}_speed"
                    filterGraph.addAudioSpeed(currentAudioLabel, clip.speed, audioSpeedLabel)
                    currentAudioLabel = audioSpeedLabel
                }
                if (clip.volume != 1.0f) {
                    val volLabel = "a${idx}_vol"
                    filterGraph.addVolume(currentAudioLabel, clip.volume, volLabel)
                    currentAudioLabel = volLabel
                }
                if (currentAudioLabel != "a$idx") {
                    filterGraph.addCustomFilter("[$currentAudioLabel]asetpts=PTS-STARTPTS[a$idx]")
                }
            }

            if (config.clips.size > 1) {
                filterGraph.addAudioVideoConcat(config.clips.size)
            } else {
                filterGraph.addCustomFilter("[v0]copy[vout]")
                filterGraph.addCustomFilter("[a0]acopy[aout]")
            }

            var currentBase = "vout"
            overlayEntries.forEachIndexed { idx, (overlay, _) ->
                val overlayStreamIndex = overlayInputIndex + idx
                val x = (overlay.overlayConfig.position.x * width).toInt()
                val y = (overlay.overlayConfig.position.y * height).toInt()
                val outLabel = if (idx == overlayEntries.lastIndex) "vfinal" else "vov$idx"
                filterGraph.addOverlayWithEnable(
                    baseStream = currentBase,
                    overlayStream = "$overlayStreamIndex:v",
                    x = x,
                    y = y,
                    enableStart = overlay.startTimeMs / 1000.0,
                    enableEnd = overlay.endTimeMs / 1000.0,
                    outputLabel = outLabel
                )
                currentBase = outLabel
            }
        }

        val filterString = filterGraph.build()
        if (filterString.isNotEmpty()) {
            builder.addFilterComplex(filterString)
        }

        builder.setVideoCodec(settings.videoCodecName())
        builder.setAudioCodec(settings.audioCodecName())
        builder.setFrameRate(settings.frameRate)
        builder.setBitrate(settings.bitrateBps)
        builder.setCrf(crfForFormat(settings.format))
        builder.setPixelFormat("yuv420p")

        if (settings.format == ExportFormat.MP4_H264 || settings.format == ExportFormat.MP4_H265) {
            builder.setPreset("medium")
            builder.setMovFlags("+faststart")
        }

        if (filterString.isNotEmpty()) {
            val videoOut = if (overlayEntries.isNotEmpty()) "vfinal" else "vout"
            builder.addOutputOption("-map", "[$videoOut]")
            if (filterString.contains("[aout]")) {
                builder.addOutputOption("-map", "[aout]")
            } else if (config.clips.size == 1) {
                builder.addOutputOption("-map", "0:a?")
            }
        }

        builder.setOutput(config.outputPath)
        builder.setDescription("Export project ${config.projectId}")

        return builder.build()
    }

    private fun crfForFormat(format: ExportFormat): Int = when (format) {
        ExportFormat.MP4_H264 -> 23
        ExportFormat.MP4_H265 -> 28
        ExportFormat.WEBM_VP9 -> 31
    }

    private fun isDynamic(config: OverlayConfig): Boolean = when (config) {
        is OverlayConfig.DynamicAltitudeProfile,
        is OverlayConfig.DynamicMap,
        is OverlayConfig.DynamicStat -> true
        else -> false
    }

    private suspend fun cleanupTempDir(dir: File) {
        withContext(Dispatchers.IO) {
            dir.deleteRecursively()
        }
    }

    fun cancel() {
        ffmpegExecutor.cancel()
    }
}
