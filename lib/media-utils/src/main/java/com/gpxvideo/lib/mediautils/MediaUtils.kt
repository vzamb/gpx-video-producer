package com.gpxvideo.lib.mediautils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class MediaInfo(
    val durationMs: Long?,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val codec: String?,
    val hasAudio: Boolean,
    val audioCodec: String?,
    val fileSize: Long
)

object MediaProber {

    suspend fun probeMedia(context: Context, uri: Uri): MediaInfo = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)

            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull()
            val videoWidth = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull() ?: 0
            val videoHeight = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
            )?.toIntOrNull() ?: 0
            val codec = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_MIMETYPE
            )
            val hasAudio = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO
            ) == "yes"

            val fileSize = context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0L

            // For images, video width/height may be 0; fall back to BitmapFactory
            val (finalWidth, finalHeight) = if (videoWidth == 0 || videoHeight == 0) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
                Pair(options.outWidth.coerceAtLeast(0), options.outHeight.coerceAtLeast(0))
            } else {
                Pair(videoWidth, videoHeight)
            }

            MediaInfo(
                durationMs = durationMs,
                width = finalWidth,
                height = finalHeight,
                rotation = rotation,
                codec = codec,
                hasAudio = hasAudio,
                audioCodec = null,
                fileSize = fileSize
            )
        } finally {
            retriever.release()
        }
    }
}

object ThumbnailGenerator {

    suspend fun generateVideoThumbnail(
        context: Context,
        uri: Uri,
        timeMs: Long = 0
    ): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(
                timeMs * 1000,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    suspend fun generateImageThumbnail(
        context: Context,
        uri: Uri,
        maxSize: Int = 512
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }

            var inSampleSize = 1
            while (bounds.outWidth / inSampleSize > maxSize ||
                bounds.outHeight / inSampleSize > maxSize
            ) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveThumbnail(bitmap: Bitmap, outputFile: File): String =
        withContext(Dispatchers.IO) {
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            outputFile.absolutePath
        }
}
