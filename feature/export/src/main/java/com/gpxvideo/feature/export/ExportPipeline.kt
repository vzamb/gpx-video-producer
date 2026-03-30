package com.gpxvideo.feature.export

import android.content.Context
import com.gpxvideo.core.model.ExportFormat
import com.gpxvideo.core.model.OverlayConfig
import com.gpxvideo.core.model.TransitionType
import com.gpxvideo.lib.ffmpeg.FfmpegCommandBuilder
import com.gpxvideo.lib.ffmpeg.FfmpegExecutor
import com.gpxvideo.lib.ffmpeg.FfmpegResult
import com.gpxvideo.lib.ffmpeg.FilterGraphBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

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

        // Phase 1: PREPARING
        onPhaseChanged(ExportPhase.PREPARING)
        onProgress(0f)

        val tempDir = File(context.cacheDir, "export_${config.projectId}")
        tempDir.mkdirs()

        if (config.clips.isEmpty()) {
            return@withContext FfmpegResult.Error("No clips to export", -1, "")
        }

        // Phase 2: RENDERING_OVERLAYS
        val dynamicOverlays = config.overlays.filter { isDynamic(it.overlayConfig) }
        val overlayPatterns = mutableMapOf<ExportOverlay, String>()

        if (dynamicOverlays.isNotEmpty() && config.gpxData != null && config.syncEngine != null) {
            onPhaseChanged(ExportPhase.RENDERING_OVERLAYS)
            val width = config.outputSettings.resolution.width
            val height = config.outputSettings.resolution.height

            for ((idx, overlay) in dynamicOverlays.withIndex()) {
                val overlayWidth = (overlay.overlayConfig.size.width * width).toInt().coerceAtLeast(1)
                val overlayHeight = (overlay.overlayConfig.size.height * height).toInt().coerceAtLeast(1)

                val pattern = overlayFrameRenderer.renderOverlayFrames(
                    overlay = overlay,
                    gpxData = config.gpxData,
                    syncEngine = config.syncEngine,
                    outputDir = tempDir,
                    frameRate = config.outputSettings.frameRate,
                    width = overlayWidth,
                    height = overlayHeight
                ) { frameProgress ->
                    val overlayWeight = 0.3f
                    val base = overlayWeight * idx / dynamicOverlays.size
                    val contribution = overlayWeight * frameProgress / dynamicOverlays.size
                    onProgress(base + contribution)
                }
                overlayPatterns[overlay] = pattern
            }
        }

        // Phase 3: ENCODING
        onPhaseChanged(ExportPhase.ENCODING)

        val command = buildFfmpegCommand(config, overlayPatterns, tempDir)
        val result = ffmpegExecutor.execute(command) { progress ->
            val encodingBase = if (dynamicOverlays.isNotEmpty()) 0.3f else 0f
            val encodingWeight = 0.6f
            onProgress(encodingBase + encodingWeight * progress.percentage)
        }

        if (result is FfmpegResult.Error || result is FfmpegResult.Cancelled) {
            cleanupTempDir(tempDir)
            return@withContext result
        }

        // Phase 4: MIXING_AUDIO (handled in the main command via filter_complex)
        onPhaseChanged(ExportPhase.MIXING_AUDIO)
        onProgress(0.9f)

        // Phase 5: FINALIZING
        onPhaseChanged(ExportPhase.FINALIZING)
        cleanupTempDir(tempDir)
        onProgress(1f)

        result
    }

    private fun buildFfmpegCommand(
        config: ExportConfig,
        overlayPatterns: Map<ExportOverlay, String>,
        tempDir: File
    ): com.gpxvideo.lib.ffmpeg.FfmpegCommand {
        val builder = FfmpegCommandBuilder().overwrite()
        val settings = config.outputSettings
        val filterGraph = FilterGraphBuilder()
        val width = settings.resolution.width
        val height = settings.resolution.height

        // Add video clip inputs
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

        // Add overlay image sequence inputs
        var overlayInputIndex = config.clips.size
        for ((overlay, pattern) in overlayPatterns) {
            builder.addImageSequenceInput(
                File(tempDir, pattern).absolutePath,
                settings.frameRate
            )
        }

        // Build filter graph
        if (config.clips.size == 1 && overlayPatterns.isEmpty()) {
            // Simple case: single clip, no overlays
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
            // Complex case: multiple clips and/or overlays
            for ((idx, clip) in config.clips.withIndex()) {
                val inputLabel = "$idx:v"
                var currentLabel = inputLabel

                // Apply speed
                if (clip.speed != 1.0f) {
                    val speedLabel = "v${idx}_speed"
                    filterGraph.addSpeed(currentLabel, clip.speed, speedLabel)
                    currentLabel = speedLabel
                }

                // Scale to output resolution
                val scaleLabel = "v${idx}_scaled"
                filterGraph.addScale(currentLabel, width, height, scaleLabel)
                currentLabel = scaleLabel

                // Apply transition fades
                if (clip.transition != null && clip.transition.type != TransitionType.CUT) {
                    val fadeLabel = "v${idx}_fade"
                    filterGraph.addFade(
                        currentLabel, "in", 0,
                        clip.transition.durationMs, fadeLabel
                    )
                    currentLabel = fadeLabel
                }

                // Rename final label for concat
                if (currentLabel != "v$idx") {
                    filterGraph.addSetPts(currentLabel, "PTS-STARTPTS", "v$idx")
                }

                // Audio processing
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

            // Concat all clips
            if (config.clips.size > 1) {
                filterGraph.addAudioVideoConcat(config.clips.size)
            } else {
                // Single clip, just rename
                filterGraph.addCustomFilter("[v0]copy[vout]")
                filterGraph.addCustomFilter("[a0]acopy[aout]")
            }

            // Apply overlays
            var currentBase = "vout"
            for ((idx, entry) in overlayPatterns.entries.withIndex()) {
                val (overlay, _) = entry
                val overlayIdx = overlayInputIndex + idx
                val oConfig = overlay.overlayConfig
                val x = (oConfig.position.x * width).toInt()
                val y = (oConfig.position.y * height).toInt()
                val outLabel = if (idx == overlayPatterns.size - 1) "vfinal" else "vov$idx"

                filterGraph.addOverlay(currentBase, "$overlayIdx:v", x, y, outLabel)
                currentBase = outLabel
            }

            if (overlayPatterns.isNotEmpty()) {
                // Final output uses vfinal
            }
        }

        val filterString = filterGraph.build()
        if (filterString.isNotEmpty()) {
            builder.addFilterComplex(filterString)
        }

        // Output options
        builder.setVideoCodec(settings.videoCodecName())
        builder.setAudioCodec(settings.audioCodecName())
        builder.setFrameRate(settings.frameRate)
        builder.setCrf(crfForFormat(settings.format))
        builder.setPixelFormat("yuv420p")

        if (settings.format == ExportFormat.MP4_H264 || settings.format == ExportFormat.MP4_H265) {
            builder.setPreset("medium")
            builder.setMovFlags("+faststart")
        }

        // Map outputs
        if (filterString.isNotEmpty()) {
            val hasOverlays = overlayPatterns.isNotEmpty()
            val videoOut = if (hasOverlays) "vfinal" else "vout"
            builder.addOutputOption("-map", "[$videoOut]")
            if (filterString.contains("[aout]")) {
                builder.addOutputOption("-map", "[aout]")
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
