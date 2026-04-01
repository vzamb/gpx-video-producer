package com.gpxvideo.feature.preview

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.View
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs

@Composable
fun VideoPreview(
    previewEngine: PreviewEngine,
    overlayContent: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val videoAspectRatio by previewEngine.videoAspectRatio.collectAsStateWithLifecycle()
    val displayTransform by previewEngine.activeDisplayTransform.collectAsStateWithLifecycle()

    // Keep a reference to the TextureView so we can apply transforms from LaunchedEffect
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var surfaceAvailable by remember { mutableStateOf(false) }

    // Use rememberUpdatedState so the SurfaceTextureListener callbacks always see fresh values
    val currentAR by rememberUpdatedState(videoAspectRatio)
    val currentTransform by rememberUpdatedState(displayTransform)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> previewEngine.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Re-apply transform whenever aspect ratio or display transform changes
    LaunchedEffect(videoAspectRatio, displayTransform, surfaceAvailable) {
        val tv = textureViewRef
        if (tv != null && surfaceAvailable && tv.width > 0 && tv.height > 0) {
            tv.applyVideoTransform(videoAspectRatio, displayTransform)
            tv.applyVideoColorAdjustments(displayTransform)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { context ->
                TextureView(context).apply {
                    isOpaque = false
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            surface: SurfaceTexture,
                            width: Int,
                            height: Int
                        ) {
                            previewEngine.bindToTextureView(this@apply)
                            surfaceAvailable = true
                            applyVideoTransform(currentAR, currentTransform)
                            applyVideoColorAdjustments(currentTransform)
                        }

                        override fun onSurfaceTextureSizeChanged(
                            surface: SurfaceTexture,
                            width: Int,
                            height: Int
                        ) {
                            applyVideoTransform(currentAR, currentTransform)
                            applyVideoColorAdjustments(currentTransform)
                        }

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            surfaceAvailable = false
                            previewEngine.unbindTextureView(this@apply)
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                    }
                    textureViewRef = this
                    if (isAvailable) {
                        previewEngine.bindToTextureView(this)
                        surfaceAvailable = true
                    }
                }
            },
            update = { textureView ->
                textureViewRef = textureView
                if (textureView.isAvailable) {
                    previewEngine.bindToTextureView(textureView)
                    textureView.applyVideoTransform(videoAspectRatio, displayTransform)
                    textureView.applyVideoColorAdjustments(displayTransform)
                }
            },
            modifier = Modifier.matchParentSize()
        )

        overlayContent()
    }
}

/**
 * Computes and applies a transform matrix so the video is correctly framed inside the
 * TextureView (which is sized to the project canvas AR).
 *
 * ExoPlayer renders raw video frames into the SurfaceTexture. The TextureView then
 * stretches those frames to fill its own bounds. Our matrix undoes that stretch and
 * applies the desired framing (fit / fill / crop) plus any user rotation / pan / zoom.
 */
private fun TextureView.applyVideoTransform(
    videoAspectRatio: Float?,
    displayTransform: PreviewDisplayTransform
) {
    if (width == 0 || height == 0) return

    // Prefer the per-clip AR computed from media metadata at import time.
    // Fall back to ExoPlayer's runtime callback, then to the view AR (identity).
    val effectiveVideoAR = when {
        displayTransform.sourceVideoAspectRatio > 0f -> displayTransform.sourceVideoAspectRatio
        videoAspectRatio != null && videoAspectRatio > 0f -> videoAspectRatio
        else -> width.toFloat() / height.toFloat()
    }

    val normalizedRotation = ((displayTransform.rotationDegrees % 360f) + 360f) % 360f
    val rotatedVideoAR = if (
        abs(normalizedRotation - 90f) < 1f || abs(normalizedRotation - 270f) < 1f
    ) {
        1f / effectiveVideoAR
    } else {
        effectiveVideoAR
    }

    val viewAR = width.toFloat() / height.toFloat()
    val matrix = Matrix()

    val (baseScaleX, baseScaleY) = when (displayTransform.contentMode) {
        com.gpxvideo.feature.timeline.ClipContentMode.FIT -> {
            // Letterbox / pillarbox — shrink to fit entirely inside the canvas
            if (viewAR > rotatedVideoAR) {
                // Canvas wider than video → pillarbox (black bars left/right)
                rotatedVideoAR / viewAR to 1f
            } else {
                // Video wider than canvas → letterbox (black bars top/bottom)
                1f to viewAR / rotatedVideoAR
            }
        }
        com.gpxvideo.feature.timeline.ClipContentMode.FILL,
        com.gpxvideo.feature.timeline.ClipContentMode.CROP -> {
            // Fill the entire canvas, cropping the excess
            if (viewAR > rotatedVideoAR) {
                1f to viewAR / rotatedVideoAR
            } else {
                rotatedVideoAR / viewAR to 1f
            }
        }
    }

    // User scale is always applied on top of the base framing.
    val userScale = displayTransform.scale.coerceAtLeast(0.1f)
    val scaleX = baseScaleX * userScale
    val scaleY = baseScaleY * userScale

    matrix.setScale(scaleX, scaleY, width / 2f, height / 2f)

    if (displayTransform.rotationDegrees != 0f) {
        matrix.postRotate(displayTransform.rotationDegrees, width / 2f, height / 2f)
    }

    // Position: positionX/Y ∈ [0,1], 0.5 = centred.
    // When the scaled video is SMALLER than the view (FIT) the user slides the video
    // within the black-bar area.  When it is LARGER (FILL / zoomed) the user pans the
    // cropped region.  The same formula handles both cases because the sign of
    // "space" flips automatically.
    val scaledW = width * scaleX
    val scaledH = height * scaleY
    val spaceX = width - scaledW
    val spaceY = height - scaledH
    val translateX = (displayTransform.positionX - 0.5f) * spaceX
    val translateY = (displayTransform.positionY - 0.5f) * spaceY
    if (translateX != 0f || translateY != 0f) {
        matrix.postTranslate(translateX, translateY)
    }

    setTransform(matrix)
}

private fun TextureView.applyVideoColorAdjustments(displayTransform: PreviewDisplayTransform) {
    val isIdentity = displayTransform.brightness == 0f &&
        displayTransform.contrast == 1f &&
        displayTransform.saturation == 1f

    if (isIdentity) {
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        return
    }

    val contrast = displayTransform.contrast.coerceIn(0.5f, 1.8f)
    val brightness = displayTransform.brightness.coerceIn(-0.4f, 0.4f) * 255f
    val translate = (1f - contrast) * 128f + brightness

    val colorMatrix = ColorMatrix().apply {
        setSaturation(displayTransform.saturation.coerceIn(0f, 1.8f))
        postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    val paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(colorMatrix)
    }
    setLayerType(View.LAYER_TYPE_HARDWARE, paint)
}
