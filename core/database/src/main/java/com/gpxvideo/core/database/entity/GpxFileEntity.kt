package com.gpxvideo.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "gpx_files",
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
data class GpxFileEntity(
    @PrimaryKey
    val id: UUID = UUID.randomUUID(),
    @ColumnInfo(name = "project_id")
    val projectId: UUID,
    val name: String,
    @ColumnInfo(name = "file_path")
    val filePath: String,
    @ColumnInfo(name = "parsed_data_json")
    val parsedDataJson: String? = null
)
