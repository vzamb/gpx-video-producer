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
import com.gpxvideo.core.model.GpxData
import com.gpxvideo.core.model.OverlayConfig
import com.gpxvideo.core.overlayrenderer.OverlayTemplateRenderer
import com.gpxvideo.core.overlayrenderer.UnifiedTemplate
import com.gpxvideo.core.overlayrenderer.OverlayFrameData
import com.gpxvideo.lib.ffmpeg.FfmpegResult
import com.gpxvideo.lib.gpxparser.GpxStats
import com.google.common.collect.ImmutableList
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import com.gpxvideo.core.common.FormatUtils
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

        // Calculate total video duration for story template progress
        val totalDurationMs = config.clips.maxOfOrNull { it.endTimeMs } ?: 0L

        val result = withContext(Dispatchers.Main) {
            exportWithTransformer(config, renderedInputs, totalDurationMs, encodingBase, onProgress)
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
        totalDurationMs: Long,
        encodingBase: Float,
        onProgress: (Float) -> Unit
    ): FfmpegResult = suspendCancellableCoroutine { cont ->
        val width = config.projectWidth
        val height = config.projectHeight

        // Build EditedMediaItems for each clip
        val editedItems = config.clips.map { clip ->
            val videoEffects = mutableListOf<androidx.media3.common.Effect>()

            // Scale to target resolution
            videoEffects.add(
                Presentation.createForWidthAndHeight(
                    width, height, Presentation.LAYOUT_SCALE_TO_FIT
                )
            )

            if (clip.isImage) {
                val mediaItem = MediaItem.Builder()
                    .setUri(clip.filePath)
                    .setImageDurationMs(clip.endTimeMs - clip.startTimeMs)
                    .build()

                EditedMediaItem.Builder(mediaItem)
                    .setEffects(
                        androidx.media3.transformer.Effects(
                            emptyList(),
                            videoEffects.toList()
                        )
                    )
                    .build()
            } else {
                val audioProcessors = mutableListOf<androidx.media3.common.audio.AudioProcessor>()
                val clippingConfig = MediaItem.ClippingConfiguration.Builder()
                if (clip.trimStartMs > 0) {
                    clippingConfig.setStartPositionMs(clip.trimStartMs)
                }
                val sourceDuration = clip.endTimeMs - clip.startTimeMs + clip.trimEndMs + clip.trimStartMs
                if (clip.trimEndMs > 0) {
                    clippingConfig.setEndPositionMs(sourceDuration - clip.trimEndMs)
                }

                val mediaItem = MediaItem.Builder()
                    .setUri(clip.filePath)
                    .setClippingConfiguration(clippingConfig.build())
                    .build()

                if (clip.speed != 1.0f) {
                    videoEffects.add(SpeedChangeEffect(clip.speed))
                    audioProcessors.add(SonicAudioProcessor().apply { setSpeed(clip.speed) })
                }

                if (clip.volume != 1.0f && clip.volume >= 0f && clip.speed == 1.0f) {
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

        // Add dynamic story template overlay (renders per-frame with live GPX data)
        if (config.storyTemplate != null) {
            textureOverlays.add(
                DynamicStoryTemplateOverlay(
                    context = context,
                    template = config.storyTemplate,
                    width = width,
                    height = height,
                    gpxData = config.gpxData,
                    gpxStats = config.gpxStats,
                    syncEngine = config.syncEngine,
                    totalDurationMs = totalDurationMs,
                    activityTitle = config.activityTitle,
                    storyMode = config.storyMode,
                    exportClips = config.clips
                )
            )
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
 * A [BitmapOverlay] that renders the story template overlay per-frame using
 * the unified OverlayTemplateRenderer — same renderer as the preview.
 */
@UnstableApi
private class DynamicStoryTemplateOverlay(
    private val context: Context,
    private val template: String,
    private val width: Int,
    private val height: Int,
    private val gpxData: GpxData?,
    private val gpxStats: GpxStats?,
    private val syncEngine: com.gpxvideo.feature.overlays.GpxTimeSyncEngine?,
    private val totalDurationMs: Long,
    private val activityTitle: String = "",
    private val storyMode: String = "FAST_FORWARD",
    private val exportClips: List<ExportClip> = emptyList()
) : BitmapOverlay() {

    private val templateRenderer = OverlayTemplateRenderer(context)
    private val loadedTemplate: UnifiedTemplate? by lazy {
        templateRenderer.loadSync(template, width, height)
    }

    // Pre-compute GPX point data for fast indexed lookups
    private val allPoints by lazy {
        gpxData?.tracks?.flatMap { it.segments }?.flatMap { it.points } ?: emptyList()
    }
    private val cumulativeDistances by lazy { computeCumulativeDistances() }
    private val cumulativeElevGain by lazy { computeCumulativeElevGain() }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val tmpl = loadedTemplate ?: return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val timeMs = presentationTimeUs / 1000L
        val progress = if (totalDurationMs > 0) (timeMs.toFloat() / totalDurationMs).coerceIn(0f, 1f) else 0f

        val frameData = when (storyMode) {
            "STATIC" -> buildStaticFrame(progress)
            "LIVE_SYNC" -> buildLiveSyncFrame(progress, timeMs)
            else -> buildAnimatedFrame(progress, timeMs)
        }

        return templateRenderer.render(
            template = tmpl,
            width = width,
            height = height,
            frameData = frameData,
            gpxData = gpxData,
            activityTitle = activityTitle
        )
    }

    private fun buildStaticFrame(progress: Float): OverlayFrameData {
        val stats = gpxStats
        val avgSpeed = stats?.avgSpeed
            ?: if (gpxData != null && gpxData.totalDuration.seconds > 0)
                gpxData.totalDistance / gpxData.totalDuration.seconds.toDouble() else 0.0
        val paceStr = formatPace(avgSpeed)
        return OverlayFrameData(
            distance = gpxData?.totalDistance ?: 0.0,
            elevation = stats?.totalElevationGain ?: gpxData?.totalElevationGain ?: 0.0,
            elevationGain = stats?.totalElevationGain ?: gpxData?.totalElevationGain ?: 0.0,
            speed = avgSpeed,
            pace = paceStr,
            heartRate = allPoints.mapNotNull { it.heartRate }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            progress = progress,
            elapsedTime = (stats?.movingDuration ?: gpxData?.totalDuration)?.toMillis() ?: 0L
        )
    }

    private fun buildAnimatedFrame(progress: Float, timeMs: Long): OverlayFrameData {
        if (allPoints.size < 2) return OverlayFrameData(progress = progress, elapsedTime = timeMs)

        val fractionalIdx = (progress * (allPoints.size - 1)).coerceIn(0f, allPoints.lastIndex.toFloat())
        val loIdx = fractionalIdx.toInt().coerceIn(0, allPoints.lastIndex)
        val hiIdx = (loIdx + 1).coerceAtMost(allPoints.lastIndex)
        val frac = fractionalIdx - loIdx

        val pointLo = allPoints[loIdx]
        val pointHi = allPoints[hiIdx]

        val distance = if (loIdx == hiIdx) cumulativeDistances.getOrElse(loIdx) { 0.0 }
            else {
                val dLo = cumulativeDistances.getOrElse(loIdx) { 0.0 }
                val dHi = cumulativeDistances.getOrElse(hiIdx) { 0.0 }
                dLo + frac * (dHi - dLo)
            }
        val elevGain = if (loIdx == hiIdx) cumulativeElevGain.getOrElse(loIdx) { 0.0 }
            else {
                val gLo = cumulativeElevGain.getOrElse(loIdx) { 0.0 }
                val gHi = cumulativeElevGain.getOrElse(hiIdx) { 0.0 }
                gLo + frac * (gHi - gLo)
            }

        // Windowed speed — widen progressively if speed is too low (avoids "—" pace at edge points)
        val windowSize = (allPoints.size / 50).coerceIn(3, 15)
        val speed = run {
            var currentSpeed = 0.0
            var currentWindowSize = windowSize
            while (currentWindowSize <= windowSize * 4) {
                val ws = (loIdx - currentWindowSize).coerceAtLeast(0)
                val we = (loIdx + currentWindowSize).coerceAtMost(allPoints.lastIndex)
                val t0 = allPoints[ws].time
                val t1 = allPoints[we].time
                if (t0 != null && t1 != null) {
                    val dtSec = (t1.toEpochMilli() - t0.toEpochMilli()) / 1000.0
                    if (dtSec > 1.0) {
                        val d = (cumulativeDistances.getOrElse(we) { 0.0 }) -
                                (cumulativeDistances.getOrElse(ws) { 0.0 })
                        currentSpeed = d / dtSec
                        if (currentSpeed >= 0.3) break
                    }
                } else {
                    currentSpeed = gpxData?.let { if (it.totalDuration.seconds > 0) it.totalDistance / it.totalDuration.seconds.toDouble() else 0.0 } ?: 0.0
                    break
                }
                currentWindowSize *= 2
            }
            currentSpeed
        }

        val grade = if (loIdx > 0 && hiIdx < allPoints.lastIndex) {
            val d = com.gpxvideo.lib.gpxparser.GpxStatistics.computeDistance(
                allPoints[loIdx - 1].latitude, allPoints[loIdx - 1].longitude,
                allPoints[hiIdx + 1].latitude, allPoints[hiIdx + 1].longitude
            )
            if (d > 1.0) ((allPoints[hiIdx + 1].elevation ?: 0.0) - (allPoints[loIdx - 1].elevation ?: 0.0)) / d * 100.0
            else 0.0
        } else 0.0

        val elapsedTime = if (pointLo.time != null && pointHi.time != null && allPoints.first().time != null) {
            val loMs = java.time.Duration.between(allPoints.first().time, pointLo.time).toMillis()
            val hiMs = java.time.Duration.between(allPoints.first().time, pointHi.time).toMillis()
            (loMs + frac * (hiMs - loMs)).toLong()
        } else if (allPoints.first().time != null && pointLo.time != null) {
            java.time.Duration.between(allPoints.first().time, pointLo.time).toMillis()
        } else {
            (gpxData?.totalDuration?.toMillis()?.times(progress))?.toLong() ?: timeMs
        }

        val lat = pointLo.latitude + frac * (pointHi.latitude - pointLo.latitude)
        val lng = pointLo.longitude + frac * (pointHi.longitude - pointLo.longitude)
        val nearestPoint = if (frac < 0.5f) pointLo else pointHi

        return OverlayFrameData(
            distance = distance,
            elevation = nearestPoint.elevation ?: 0.0,
            elevationGain = elevGain,
            speed = speed,
            pace = formatPace(speed),
            heartRate = nearestPoint.heartRate,
            cadence = nearestPoint.cadence,
            power = nearestPoint.power,
            temperature = nearestPoint.temperature,
            grade = grade,
            progress = progress,
            elapsedTime = elapsedTime,
            latitude = lat,
            longitude = lng
        )
    }

    private fun formatPace(speedMs: Double) = FormatUtils.formatPaceFromSpeed(speedMs)

    /** LIVE_SYNC: map current time to the correct GPX point index based on clip sync points */
    private fun buildLiveSyncFrame(progress: Float, timeMs: Long): OverlayFrameData {
        if (allPoints.size < 2) return OverlayFrameData(progress = progress, elapsedTime = timeMs)

        val syncedClips = exportClips.filter { it.isSynced && it.gpxPointIndex != null }
        if (syncedClips.isEmpty()) {
            // No sync data — fall back to animated (proportional)
            return buildAnimatedFrame(progress, timeMs)
        }

        // Determine which clip is active at this timeMs
        var cumulativeMs = 0L
        var activeClipIdx = -1
        var clipStartMs = 0L
        for (i in exportClips.indices) {
            val clipDuration = exportClips[i].trimEndMs - exportClips[i].trimStartMs
            if (timeMs < cumulativeMs + clipDuration) {
                activeClipIdx = i
                clipStartMs = cumulativeMs
                break
            }
            cumulativeMs += clipDuration
        }
        if (activeClipIdx < 0) activeClipIdx = exportClips.lastIndex

        val activeClip = exportClips[activeClipIdx]
        val clipDuration = (activeClip.trimEndMs - activeClip.trimStartMs).coerceAtLeast(1L)
        val positionWithinClip = (timeMs - clipStartMs).coerceAtLeast(0L)
        val clipProgress = (positionWithinClip.toFloat() / clipDuration).coerceIn(0f, 1f)

        val syncGpxIdx = activeClip.gpxPointIndex
        if (syncGpxIdx == null || !activeClip.isSynced) {
            return buildAnimatedFrame(progress, timeMs)
        }

        val startIdx = syncGpxIdx.coerceIn(0, allPoints.lastIndex)

        // Determine how many GPX points this clip spans using timestamps
        val startTime = allPoints[startIdx].time
        val endIdx: Int
        if (startTime != null && clipDuration > 0) {
            val clipEndTime = startTime.plusMillis(clipDuration)
            var ei = startIdx
            for (i in startIdx until allPoints.size) {
                if (allPoints[i].time != null && allPoints[i].time!! <= clipEndTime) {
                    ei = i
                } else break
            }
            if (ei <= startIdx) ei = (startIdx + 1).coerceAtMost(allPoints.lastIndex)
            endIdx = ei
        } else {
            endIdx = (startIdx + (allPoints.size / exportClips.size.coerceAtLeast(1)))
                .coerceAtMost(allPoints.lastIndex)
        }

        val fractionalIdx = (startIdx + clipProgress * (endIdx - startIdx))
            .coerceIn(0f, allPoints.lastIndex.toFloat())

        return buildAnimatedFrame(fractionalIdx / (allPoints.size - 1).coerceAtLeast(1).toFloat(), timeMs)
    }

    private fun computeCumulativeDistances(): List<Double> {
        if (allPoints.size < 2) return if (allPoints.isNotEmpty()) listOf(0.0) else emptyList()
        val dists = ArrayList<Double>(allPoints.size)
        dists.add(0.0)
        for (i in 1 until allPoints.size) {
            dists.add(dists.last() + com.gpxvideo.lib.gpxparser.GpxStatistics.computeDistance(
                allPoints[i - 1].latitude, allPoints[i - 1].longitude,
                allPoints[i].latitude, allPoints[i].longitude
            ))
        }
        return dists
    }

    private fun computeCumulativeElevGain(): List<Double> {
        if (allPoints.isEmpty()) return emptyList()
        val gains = ArrayList<Double>(allPoints.size)
        gains.add(0.0)
        for (i in 1 until allPoints.size) {
            val diff = (allPoints[i].elevation ?: 0.0) - (allPoints[i - 1].elevation ?: 0.0)
            gains.add(gains.last() + if (diff > 0) diff else 0.0)
        }
        return gains
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        return OverlaySettings.Builder().build()
    }
}
