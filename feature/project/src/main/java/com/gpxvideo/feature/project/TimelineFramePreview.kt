package com.gpxvideo.feature.project

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.gpxvideo.core.database.entity.MediaItemEntity
import com.gpxvideo.core.model.TrackType
import com.gpxvideo.feature.timeline.TimelineClipState
import com.gpxvideo.feature.timeline.TimelineState
import com.gpxvideo.lib.mediautils.ThumbnailGenerator
import java.io.File
import java.util.UUID

@Composable
fun TimelineFramePreview(
    timelineState: TimelineState,
    mediaItems: List<MediaItemEntity>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mediaMap = mediaItems.associateBy { it.id }
    val currentVideoClip = findCurrentVideoClip(timelineState)
    val currentMedia = currentVideoClip?.mediaItemId?.let(mediaMap::get)
    val frameTimeMs = currentVideoClip?.let { clip ->
        (clip.trimStartMs + (timelineState.playheadPositionMs - clip.startTimeMs))
            .coerceAtLeast(0L)
    } ?: 0L
    val colorFilter = currentVideoClip?.let { clip ->
        ColorFilter.colorMatrix(
            ColorMatrix(
                buildPreviewColorMatrix(
                    brightness = clip.brightness,
                    contrast = clip.contrast,
                    saturation = clip.saturation
                )
            )
        )
    }

    val bitmap by rememberPreviewFrame(
        context = context,
        mediaItem = currentMedia,
        frameTimeMs = frameTimeMs
    )

    Box(
        modifier = modifier
            .background(Color.Black)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun rememberPreviewFrame(
    context: Context,
    mediaItem: MediaItemEntity?,
    frameTimeMs: Long
) = produceState<ImageBitmap?>(initialValue = null, context, mediaItem?.id, frameTimeMs) {
    if (mediaItem == null) {
        value = null
        return@produceState
    }

    val uri = Uri.fromFile(File(mediaItem.localCopyPath))
    value = if (mediaItem.type == "IMAGE") {
        ThumbnailGenerator.generateImageThumbnail(context, uri)?.asImageBitmap()
    } else {
        ThumbnailGenerator.generateVideoThumbnail(context, uri, frameTimeMs)?.asImageBitmap()
    }
}

private fun findCurrentVideoClip(timelineState: TimelineState): TimelineClipState? {
    val playhead = timelineState.playheadPositionMs
    return timelineState.tracks
        .filter { it.type == TrackType.VIDEO || it.type == TrackType.IMAGE }
        .flatMap { it.clips }
        .sortedBy { it.startTimeMs }
        .firstOrNull { clip ->
            playhead in clip.startTimeMs until clip.endTimeMs.coerceAtLeast(clip.startTimeMs + 1)
        }
        ?: timelineState.tracks
            .filter { it.type == TrackType.VIDEO || it.type == TrackType.IMAGE }
            .flatMap { it.clips }
            .sortedBy { it.startTimeMs }
            .firstOrNull()
}

private fun buildPreviewColorMatrix(
    brightness: Float,
    contrast: Float,
    saturation: Float
): FloatArray {
    val safeContrast = contrast.coerceIn(0.5f, 1.8f)
    val safeBrightness = brightness.coerceIn(-0.4f, 0.4f) * 255f
    val translate = (1f - safeContrast) * 128f + safeBrightness

    val saturationMatrix = android.graphics.ColorMatrix().apply {
        setSaturation(saturation.coerceIn(0f, 1.8f))
    }
    saturationMatrix.postConcat(
        android.graphics.ColorMatrix(
            floatArrayOf(
                safeContrast, 0f, 0f, 0f, translate,
                0f, safeContrast, 0f, 0f, translate,
                0f, 0f, safeContrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
    return saturationMatrix.array
}
