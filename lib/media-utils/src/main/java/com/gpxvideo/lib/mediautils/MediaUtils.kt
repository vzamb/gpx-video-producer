package com.gpxvideo.lib.mediautils

import android.content.ContentResolver
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
    val fileSize: Long,
    val videoCreatedAt: java.time.Instant? = null
)

object MediaProber {

    suspend fun probeMedia(context: Context, uri: Uri): MediaInfo = withContext(Dispatchers.IO) {
        val fileSize = when (uri.scheme) {
            null, ContentResolver.SCHEME_FILE -> uri.path?.let(::File)?.takeIf(File::exists)?.length()
            else -> context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        } ?: 0L

        // Try MediaMetadataRetriever first (works for video/audio; may fail for images)
        val retrieverInfo = try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setSafeDataSource(context, uri)
                val durationMs = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull()?.takeIf { it > 0L }
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
                val dateStr = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DATE
                )
                val videoCreatedAt = dateStr?.let { parseVideoDate(it) }

                MediaInfo(
                    durationMs = durationMs,
                    width = videoWidth,
                    height = videoHeight,
                    rotation = rotation,
                    codec = codec,
                    hasAudio = hasAudio,
                    audioCodec = null,
                    fileSize = fileSize,
                    videoCreatedAt = videoCreatedAt
                )
            } finally {
                retriever.release()
            }
        } catch (_: Exception) {
            null
        }

        // If retriever succeeded with valid dimensions, use it
        if (retrieverInfo != null && retrieverInfo.width > 0 && retrieverInfo.height > 0) {
            return@withContext retrieverInfo
        }

        // Fall back to BitmapFactory for dimensions (handles images and cases where
        // MediaMetadataRetriever failed or returned 0×0)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        val imgWidth = options.outWidth.coerceAtLeast(0)
        val imgHeight = options.outHeight.coerceAtLeast(0)

        if (retrieverInfo != null) {
            // Retriever worked but had 0×0 dimensions — patch with BitmapFactory sizes
            retrieverInfo.copy(width = imgWidth, height = imgHeight)
        } else {
            // Pure image fallback — no video metadata available
            val mimeType = context.contentResolver.getType(uri)
            MediaInfo(
                durationMs = null,
                width = imgWidth,
                height = imgHeight,
                rotation = 0,
                codec = mimeType,
                hasAudio = false,
                audioCodec = null,
                fileSize = fileSize,
                videoCreatedAt = null
            )
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
            retriever.setSafeDataSource(context, uri)
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

private fun MediaMetadataRetriever.setSafeDataSource(context: Context, uri: Uri) {
    when (uri.scheme) {
        null, ContentResolver.SCHEME_FILE -> setDataSource(uri.path ?: uri.toString())
        else -> setDataSource(context, uri)
    }
}

/** Parse video creation date from MediaMetadataRetriever.METADATA_KEY_DATE. */
private fun parseVideoDate(dateStr: String): java.time.Instant? {
    // Android METADATA_KEY_DATE returns various formats:
    // "2024-03-15T14:30:22Z", "20240315T143022.000Z", "20240315T143022.000+0000"
    // Some devices also return: "2024-03-15 14:30:22", "Mar 15, 2024 14:30:22"
    return try {
        java.time.Instant.parse(dateStr)
    } catch (_: Exception) {
        try {
            // Handle offset formats like "20240315T143022.000+0000"
            java.time.OffsetDateTime.parse(
                dateStr,
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSSZ")
            ).toInstant()
        } catch (_: Exception) {
            try {
                java.time.OffsetDateTime.parse(
                    dateStr,
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssZ")
                ).toInstant()
            } catch (_: Exception) {
                try {
                    val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'")
                    java.time.LocalDateTime.parse(dateStr, formatter)
                        .atZone(java.time.ZoneOffset.UTC).toInstant()
                } catch (_: Exception) {
                    try {
                        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                        java.time.LocalDateTime.parse(dateStr, formatter)
                            .atZone(java.time.ZoneOffset.UTC).toInstant()
                    } catch (_: Exception) {
                        try {
                            // "2024-03-15 14:30:22" (space separator)
                            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            java.time.LocalDateTime.parse(dateStr, formatter)
                                .atZone(java.time.ZoneOffset.UTC).toInstant()
                        } catch (_: Exception) {
                            try {
                                // "yyyy-MM-dd'T'HH:mm:ss+ZZZZ" (ISO with offset)
                                java.time.OffsetDateTime.parse(dateStr).toInstant()
                            } catch (_: Exception) {
                                android.util.Log.w("MediaUtils", "Could not parse video date: $dateStr")
                                null
                            }
                        }
                    }
                }
            }
        }
    }
}
