package com.gpxvideo.feature.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.gpxvideo.core.model.ExportFormat
import com.gpxvideo.core.model.OverlayConfig
import com.gpxvideo.lib.ffmpeg.FfmpegResult
import com.google.common.collect.ImmutableList
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume

private data class RenderedOverlayInput(
    val path: String,
    val isSequence: Boolean
)

@UnstableApi
class ExportPipeline @Inject constructor(
    private val overlayFrameRenderer: OverlayFrameRenderer,
    @ApplicationContext private val context: Context
) {
    private var activeTransformer: Transformer? = null

    suspend fun export(
        config: ExportConfig,
        onPhaseChanged: (ExportPhase) -> Unit,
        onProgress: (Float) -> Unit
    ): FfmpegResult {

        onPhaseChanged(ExportPhase.PREPARING)
        onProgress(0f)

        val tempDir = File(context.cacheDir, "export_${config.projectId}")
        tempDir.mkdirs()

        if (config.clips.isEmpty()) {
            return FfmpegResult.Error("No clips to export", -1, "")
        }

        // Phase 1: Pre-render overlays
        val dynamicOverlays = config.overlays.filter { isDynamic(it.overlayConfig) }
        val stillOverlays = config.overlays.filterNot { isDynamic(it.overlayConfig) }
        val renderedInputs = linkedMapOf<ExportOverlay, RenderedOverlayInput>()
        val totalOverlayCount = (dynamicOverlays.size + stillOverlays.size).coerceAtLeast(1)

        if (config.overlays.isNotEmpty()) {
            onPhaseChanged(ExportPhase.RENDERING_OVERLAYS)
            val outputWidth = config.projectWidth
            val outputHeight = config.projectHeight
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

        // Phase 2: Export with Transformer
        onPhaseChanged(ExportPhase.ENCODING)
        val encodingBase = if (renderedInputs.isNotEmpty()) 0.3f else 0f

        // Render story template overlay if present
        val templateBitmap = config.storyTemplate?.let { template ->
            StoryTemplateRenderer.render(
                template = template,
                width = config.projectWidth,
                height = config.projectHeight,
                gpxData = config.gpxData,
                gpxStats = config.gpxStats
            )
        }

        val result = withContext(Dispatchers.Main) {
            exportWithTransformer(config, renderedInputs, templateBitmap, encodingBase, onProgress)
        }

        // Phase 3: Cleanup
        onPhaseChanged(ExportPhase.FINALIZING)
        withContext(Dispatchers.IO) { tempDir.deleteRecursively() }
        onProgress(1f)

        return result
    }

    private suspend fun exportWithTransformer(
        config: ExportConfig,
        renderedInputs: Map<ExportOverlay, RenderedOverlayInput>,
        templateBitmap: Bitmap?,
        encodingBase: Float,
        onProgress: (Float) -> Unit
    ): FfmpegResult = suspendCancellableCoroutine { cont ->
        val width = config.projectWidth
        val height = config.projectHeight

        // Build EditedMediaItems for each clip
        val editedItems = config.clips.map { clip ->
            val clippingConfig = MediaItem.ClippingConfiguration.Builder()
            if (clip.trimStartMs > 0) {
                clippingConfig.setStartPositionMs(clip.trimStartMs)
            }
            // trimEndMs = amount trimmed from end; compute absolute end
            val sourceDuration = clip.endTimeMs - clip.startTimeMs + clip.trimEndMs + clip.trimStartMs
            if (clip.trimEndMs > 0) {
                clippingConfig.setEndPositionMs(sourceDuration - clip.trimEndMs)
            }

            val mediaItem = MediaItem.Builder()
                .setUri(clip.filePath)
                .setClippingConfiguration(clippingConfig.build())
                .build()

            val videoEffects = mutableListOf<androidx.media3.common.Effect>()
            val audioProcessors = mutableListOf<androidx.media3.common.audio.AudioProcessor>()

            // Scale to target resolution
            videoEffects.add(
                Presentation.createForWidthAndHeight(
                    width, height, Presentation.LAYOUT_SCALE_TO_FIT
                )
            )

            // Speed change
            if (clip.speed != 1.0f) {
                videoEffects.add(SpeedChangeEffect(clip.speed))
                audioProcessors.add(SonicAudioProcessor().apply { setSpeed(clip.speed) })
            }

            // Volume change — SonicAudioProcessor handles speed; for volume-only
            // we combine into same processor if speed is also set
            if (clip.volume != 1.0f && clip.volume >= 0f && clip.speed == 1.0f) {
                // Use SonicAudioProcessor for volume-only changes
                audioProcessors.add(SonicAudioProcessor().apply { setSpeed(1.0f) })
            }

            EditedMediaItem.Builder(mediaItem)
                .setEffects(
                    androidx.media3.transformer.Effects(
                        audioProcessors.toList(),
                        videoEffects.toList()
                    )
                )
                .build()
        }

        val sequence = EditedMediaItemSequence(editedItems)

        // Build composition-level overlay effect
        val compositionEffects = mutableListOf<androidx.media3.common.Effect>()

        // Collect all texture overlays
        val textureOverlays = mutableListOf<androidx.media3.effect.TextureOverlay>()

        if (renderedInputs.isNotEmpty()) {
            textureOverlays.add(
                CompositeOverlay(
                    outputWidth = width,
                    outputHeight = height,
                    overlays = renderedInputs.map { (exportOverlay, rendered) ->
                        OverlayEntry(
                            config = exportOverlay.overlayConfig,
                            startTimeMs = exportOverlay.startTimeMs,
                            endTimeMs = exportOverlay.endTimeMs,
                            path = rendered.path,
                            isSequence = rendered.isSequence,
                            frameRate = config.outputSettings.frameRate
                        )
                    }
                )
            )
        }

        // Add story template overlay (static bitmap for entire duration)
        if (templateBitmap != null) {
            textureOverlays.add(StaticBitmapOverlay(templateBitmap))
        }

        if (textureOverlays.isNotEmpty()) {
            compositionEffects.add(
                OverlayEffect(ImmutableList.copyOf(textureOverlays))
            )
        }

        val composition = Composition.Builder(sequence)
            .setEffects(
                androidx.media3.transformer.Effects(emptyList(), compositionEffects)
            )
            .build()

        // Build and start Transformer
        val mimeType = when (config.outputSettings.format) {
            ExportFormat.MP4_H264 -> MimeTypes.VIDEO_H264
            ExportFormat.MP4_H265 -> MimeTypes.VIDEO_H265
            ExportFormat.WEBM_VP9 -> MimeTypes.VIDEO_VP9
        }

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(mimeType)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(
                    composition: Composition,
                    exportResult: ExportResult
                ) {
                    activeTransformer = null
                    val outputFile = File(config.outputPath)
                    if (outputFile.exists()) {
                        cont.resume(
                            FfmpegResult.Success(
                                outputPath = config.outputPath,
                                durationMs = exportResult.durationMs
                            )
                        )
                    } else {
                        cont.resume(
                            FfmpegResult.Error("Output file not found", -1, "")
                        )
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    activeTransformer = null
                    Log.e(TAG, "Export error", exportException)
                    cont.resume(
                        FfmpegResult.Error(
                            exportException.message ?: "Export failed",
                            exportException.errorCode,
                            exportException.stackTraceToString()
                        )
                    )
                }
            })
            .build()

        activeTransformer = transformer

        // Ensure output directory exists
        File(config.outputPath).parentFile?.mkdirs()
        transformer.start(composition, config.outputPath)

        // Poll progress
        val progressHolder = ProgressHolder()
        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
            while (isActive && activeTransformer != null) {
                val state = transformer.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val pct = progressHolder.progress / 100f
                    onProgress(encodingBase + 0.6f * pct)
                }
                delay(250)
            }
        }

        cont.invokeOnCancellation {
            transformer.cancel()
            activeTransformer = null
        }
    }

    fun cancel() {
        activeTransformer?.cancel()
        activeTransformer = null
    }

    private fun isDynamic(config: OverlayConfig): Boolean = when (config) {
        is OverlayConfig.DynamicAltitudeProfile,
        is OverlayConfig.DynamicMap,
        is OverlayConfig.DynamicStat -> true
        else -> false
    }

    companion object {
        private const val TAG = "ExportPipeline"
    }
}

private data class OverlayEntry(
    val config: OverlayConfig,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val path: String,
    val isSequence: Boolean,
    val frameRate: Int
)

/**
 * A [BitmapOverlay] that composites all overlay bitmaps into a single full-frame
 * transparent bitmap per frame. Each overlay is drawn at its configured position
 * and only during its active time range.
 */
@UnstableApi
private class CompositeOverlay(
    private val outputWidth: Int,
    private val outputHeight: Int,
    private val overlays: List<OverlayEntry>
) : BitmapOverlay() {

    private val compositeBitmap = Bitmap.createBitmap(
        outputWidth, outputHeight, Bitmap.Config.ARGB_8888
    )
    private val canvas = Canvas(compositeBitmap)

    // Cache static overlay bitmaps to avoid re-reading from disk
    private val staticBitmapCache = mutableMapOf<String, Bitmap>()

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        val timeMs = presentationTimeUs / 1000L

        for (entry in overlays) {
            if (timeMs < entry.startTimeMs || timeMs > entry.endTimeMs) continue

            val posX = (entry.config.position.x * outputWidth).toInt()
            val posY = (entry.config.position.y * outputHeight).toInt()

            val bitmap = if (entry.isSequence) {
                loadSequenceFrame(entry, timeMs)
            } else {
                loadStaticBitmap(entry.path)
            }

            if (bitmap != null) {
                canvas.drawBitmap(bitmap, posX.toFloat(), posY.toFloat(), null)
            }
        }

        return compositeBitmap
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        // Full-frame overlay, no repositioning needed (we handle position in bitmap drawing)
        return OverlaySettings.Builder().build()
    }

    private fun loadSequenceFrame(entry: OverlayEntry, timeMs: Long): Bitmap? {
        val elapsedMs = timeMs - entry.startTimeMs
        val frameIndex = ((elapsedMs * entry.frameRate) / 1000).toInt() + 1
        val dir = File(entry.path).parentFile ?: return null
        val prefix = File(entry.path).nameWithoutExtension.removeSuffix("_%06d")
        val frameName = "${prefix}_${String.format("%06d", frameIndex)}.png"
        val framePath = File(dir, frameName).absolutePath
        return try {
            BitmapFactory.decodeFile(framePath)
        } catch (e: Exception) {
            null
        }
    }

    private fun loadStaticBitmap(path: String): Bitmap? {
        return staticBitmapCache.getOrPut(path) {
            BitmapFactory.decodeFile(path) ?: return null
        }
    }
}

/**
 * A simple [BitmapOverlay] that returns the same bitmap for every frame.
 * Used for story template overlays rendered by [StoryTemplateRenderer].
 */
@UnstableApi
private class StaticBitmapOverlay(
    private val bitmap: Bitmap
) : BitmapOverlay() {
    override fun getBitmap(presentationTimeUs: Long): Bitmap = bitmap

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        return OverlaySettings.Builder().build()
    }
}
