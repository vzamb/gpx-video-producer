package com.gpxvideo.core.model

import java.time.Instant
import java.util.UUID

data class MediaItem(
    val id: UUID = UUID.randomUUID(),
    val projectId: UUID,
    val type: MediaType,
    val sourcePath: String,
    val localCopyPath: String,
    val durationMs: Long? = null,
    val width: Int,
    val height: Int,
    val rotation: Int = 0,
    val createdAt: Instant = Instant.now(),
    val metadata: MediaMetadata = MediaMetadata()
)

enum class MediaType { VIDEO, IMAGE }

data class MediaMetadata(
    val codec: String? = null,
    val bitrate: Long? = null,
    val fileSize: Long = 0,
    val hasAudio: Boolean = false,
    val audioCodec: String? = null,
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null
)
