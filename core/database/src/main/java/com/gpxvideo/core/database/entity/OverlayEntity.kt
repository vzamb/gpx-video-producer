package com.gpxvideo.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "overlays",
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
data class OverlayEntity(
    @PrimaryKey
    val id: UUID = UUID.randomUUID(),
    @ColumnInfo(name = "project_id")
    val projectId: UUID,
    @ColumnInfo(name = "timeline_clip_id")
    val timelineClipId: UUID,
    val name: String,
    @ColumnInfo(name = "overlay_type")
    val overlayType: String,
    @ColumnInfo(name = "position_x")
    val positionX: Float = 0.0f,
    @ColumnInfo(name = "position_y")
    val positionY: Float = 0.0f,
    val anchor: String = "TOP_LEFT",
    @ColumnInfo(name = "size_width")
    val sizeWidth: Float = 0.2f,
    @ColumnInfo(name = "size_height")
    val sizeHeight: Float = 0.2f,
    @ColumnInfo(name = "style_json")
    val styleJson: String = "{}",
    @ColumnInfo(name = "config_json")
    val configJson: String = "{}"
)
