package com.gpxvideo.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val description: String? = null,
    @ColumnInfo(name = "sport_type")
    val sportType: String = "OTHER",
    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now(),
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String? = null,
    @ColumnInfo(name = "resolution_width")
    val resolutionWidth: Int = 1920,
    @ColumnInfo(name = "resolution_height")
    val resolutionHeight: Int = 1080,
    @ColumnInfo(name = "frame_rate")
    val frameRate: Int = 30,
    @ColumnInfo(name = "export_format")
    val exportFormat: String = "MP4_H264",
    @ColumnInfo(name = "bitrate_bps")
    val bitrateBps: Long = 10_000_000L,
    @ColumnInfo(name = "audio_codec")
    val audioCodec: String = "AAC",
    @ColumnInfo(name = "template_id")
    val templateId: UUID? = null,
    @ColumnInfo(name = "story_mode", defaultValue = "HYPER_LAPSE")
    val storyMode: String = "HYPER_LAPSE",
    @ColumnInfo(name = "story_template", defaultValue = "CINEMATIC")
    val storyTemplate: String = "CINEMATIC"
)
