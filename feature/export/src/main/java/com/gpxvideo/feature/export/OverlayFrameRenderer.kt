package com.gpxvideo.feature.export

import android.graphics.Bitmap
import com.gpxvideo.core.model.GpxData
import com.gpxvideo.core.model.OverlayConfig
import com.gpxvideo.feature.overlays.DynamicOverlayRenderer
import com.gpxvideo.feature.overlays.GpxTimeSyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class OverlayFrameRenderer @Inject constructor() {

    /**
     * Pre-renders dynamic overlay frames as PNGs to [outputDir].
     *
     * @return the file name pattern for FFmpeg (e.g. "overlay_000001_%06d.png")
     */
    suspend fun renderOverlayFrames(
        overlay: ExportOverlay,
        gpxData: GpxData,
        syncEngine: GpxTimeSyncEngine,
        outputDir: File,
        frameRate: Int,
        width: Int,
        height: Int,
        onProgress: (Float) -> Unit
    ): String = withContext(Dispatchers.Default) {
        val config = overlay.overlayConfig
        val durationMs = overlay.endTimeMs - overlay.startTimeMs
        val totalFrames = ((durationMs / 1000.0) * frameRate).toInt().coerceAtLeast(1)
        val prefix = "overlay_${config.id.toString().take(8)}"
        val pattern = "${prefix}_%06d.png"

        for (frame in 0 until totalFrames) {
            val videoTimeMs = overlay.startTimeMs + (frame * 1000L / frameRate)
            val point = syncEngine.getPointAtVideoTime(videoTimeMs)

            val bitmap = when (config) {
                is OverlayConfig.DynamicAltitudeProfile ->
                    DynamicOverlayRenderer.renderDynamicAltitudeProfile(
                        config, gpxData, point, width, height
                    )
                is OverlayConfig.DynamicMap ->
                    DynamicOverlayRenderer.renderDynamicMap(
                        config, gpxData, point, width, height
                    )
                is OverlayConfig.DynamicStat ->
                    DynamicOverlayRenderer.renderDynamicStat(
                        config, point, width, height
                    )
                else -> null
            }

            if (bitmap != null) {
                val file = File(outputDir, "${prefix}_${String.format("%06d", frame + 1)}.png")
                saveBitmapAsPng(bitmap, file)
                bitmap.recycle()
            }

            onProgress((frame + 1).toFloat() / totalFrames)
        }

        pattern
    }

    private fun saveBitmapAsPng(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
