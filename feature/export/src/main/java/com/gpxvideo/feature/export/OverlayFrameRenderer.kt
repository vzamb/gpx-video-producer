package com.gpxvideo.feature.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.gpxvideo.core.model.GpxData
import com.gpxvideo.core.model.OverlayConfig
import com.gpxvideo.feature.overlays.DynamicOverlayRenderer
import com.gpxvideo.feature.overlays.GpxTimeSyncEngine
import com.gpxvideo.feature.overlays.OverlayRenderer
import com.gpxvideo.lib.gpxparser.GpxStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlin.math.max

class OverlayFrameRenderer @Inject constructor() {

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

    suspend fun renderStillOverlay(
        overlay: ExportOverlay,
        gpxData: GpxData?,
        gpxStats: GpxStats?,
        outputDir: File,
        width: Int,
        height: Int
    ): String? = withContext(Dispatchers.Default) {
        val bitmap = when (val config = overlay.overlayConfig) {
            is OverlayConfig.StaticAltitudeProfile ->
                gpxData?.let { OverlayRenderer.renderStaticAltitudeProfile(config, it, width, height) }

            is OverlayConfig.StaticMap ->
                gpxData?.let { OverlayRenderer.renderStaticMap(config, it, width, height) }

            is OverlayConfig.StaticStats ->
                gpxStats?.let { OverlayRenderer.renderStaticStats(config, it, width, height) }

            is OverlayConfig.TextLabel -> renderTextLabel(config, width, height)
            else -> null
        } ?: return@withContext null

        val file = File(outputDir, "overlay_${overlay.overlayConfig.id.toString().take(8)}.png")
        saveBitmapAsPng(bitmap, file)
        bitmap.recycle()
        file.absolutePath
    }

    private fun renderTextLabel(
        overlay: OverlayConfig.TextLabel,
        width: Int,
        height: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val alpha = (overlay.style.opacity.coerceIn(0f, 1f) * 255).toInt()

        val bgColor = overlay.style.backgroundColor?.toInt() ?: 0x33000000
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            this.alpha = alpha
        }
        canvas.drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            overlay.style.cornerRadius,
            overlay.style.cornerRadius,
            bgPaint
        )

        overlay.style.borderColor?.let { color ->
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = max(1f, overlay.style.borderWidth)
                this.color = color.toInt()
                this.alpha = alpha
            }
            val inset = borderPaint.strokeWidth / 2f
            canvas.drawRoundRect(
                RectF(inset, inset, width - inset, height - inset),
                overlay.style.cornerRadius,
                overlay.style.cornerRadius,
                borderPaint
            )
        }

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = overlay.style.fontColor.toInt()
            textSize = max(overlay.style.fontSize * 1.4f, height * 0.24f)
            this.alpha = alpha
            textAlign = when (overlay.textAlignment.uppercase()) {
                "START", "LEFT" -> Paint.Align.LEFT
                "END", "RIGHT" -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            if (overlay.style.shadowEnabled) {
                setShadowLayer(
                    overlay.style.shadowRadius,
                    2f,
                    2f,
                    overlay.style.shadowColor.toInt()
                )
            }
        }

        val horizontalPadding = (width * 0.08f).toInt().coerceAtLeast(12)
        val availableWidth = (width - horizontalPadding * 2).coerceAtLeast(1)
        val alignment = when (overlay.textAlignment.uppercase()) {
            "START", "LEFT" -> Layout.Alignment.ALIGN_NORMAL
            "END", "RIGHT" -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_CENTER
        }
        val textLayout = StaticLayout.Builder
            .obtain(overlay.text, 0, overlay.text.length, textPaint, availableWidth)
            .setAlignment(alignment)
            .setIncludePad(false)
            .build()

        canvas.save()
        val textTop = ((height - textLayout.height) / 2f).coerceAtLeast(0f)
        canvas.translate(horizontalPadding.toFloat(), textTop)
        textLayout.draw(canvas)
        canvas.restore()

        return bitmap
    }

    private fun saveBitmapAsPng(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
