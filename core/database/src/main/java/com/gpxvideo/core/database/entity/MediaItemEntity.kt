package com.gpxvideo.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "media_items",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("project_id")]
)
data class MediaItemEntity(
    @PrimaryKey
    val id: UUID = UUID.randomUUID(),
    @ColumnInfo(name = "project_id")
    val projectId: UUID,
    val type: String,
    @ColumnInfo(name = "source_path")
    val sourcePath: String,
    @ColumnInfo(name = "local_copy_path")
    val localCopyPath: String,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long? = null,
    val width: Int,
    val height: Int,
    val rotation: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),
    val codec: String? = null,
    val bitrate: Long? = null,
    @ColumnInfo(name = "file_size")
    val fileSize: Long = 0,
    @ColumnInfo(name = "has_audio")
    val hasAudio: Boolean = false,
    @ColumnInfo(name = "audio_codec")
    val audioCodec: String? = null,
    @ColumnInfo(name = "gps_latitude")
    val gpsLatitude: Double? = null,
    @ColumnInfo(name = "gps_longitude")
    val gpsLongitude: Double? = null
)
