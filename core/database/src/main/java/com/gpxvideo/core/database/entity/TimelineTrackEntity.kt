package com.gpxvideo.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "timeline_tracks",
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
data class TimelineTrackEntity(
    @PrimaryKey
    val id: UUID = UUID.randomUUID(),
    @ColumnInfo(name = "project_id")
    val projectId: UUID,
    val type: String,
    @ColumnInfo(name = "track_order")
    val order: Int,
    @ColumnInfo(name = "is_locked")
    val isLocked: Boolean = false,
    @ColumnInfo(name = "is_visible")
    val isVisible: Boolean = true
)
