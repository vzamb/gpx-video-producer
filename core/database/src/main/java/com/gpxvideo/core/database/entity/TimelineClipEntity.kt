package com.gpxvideo.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "timeline_clips",
    foreignKeys = [
        ForeignKey(
            entity = TimelineTrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("track_id")]
)
data class TimelineClipEntity(
    @PrimaryKey
    val id: UUID = UUID.randomUUID(),
    @ColumnInfo(name = "track_id")
    val trackId: UUID,
    @ColumnInfo(name = "media_item_id")
    val mediaItemId: UUID? = null,
    @ColumnInfo(name = "start_time_ms")
    val startTimeMs: Long,
    @ColumnInfo(name = "end_time_ms")
    val endTimeMs: Long,
    @ColumnInfo(name = "trim_start_ms")
    val trimStartMs: Long = 0,
    @ColumnInfo(name = "trim_end_ms")
    val trimEndMs: Long = 0,
    @ColumnInfo(name = "entry_transition_type")
    val entryTransitionType: String? = null,
    @ColumnInfo(name = "entry_transition_duration_ms")
    val entryTransitionDurationMs: Long? = null,
    @ColumnInfo(name = "exit_transition_type")
    val exitTransitionType: String? = null,
    @ColumnInfo(name = "exit_transition_duration_ms")
    val exitTransitionDurationMs: Long? = null,
    val volume: Float = 1.0f,
    val speed: Float = 1.0f,
    val brightness: Float = 0.0f,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val rotation: Float = 0.0f,
    val scale: Float = 1.0f,
    @ColumnInfo(name = "position_x")
    val positionX: Float = 0.5f,
    @ColumnInfo(name = "position_y")
    val positionY: Float = 0.5f,
    val opacity: Float = 1.0f
)
