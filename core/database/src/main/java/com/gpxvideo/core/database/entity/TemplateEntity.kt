package com.gpxvideo.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val description: String? = null,
    @ColumnInfo(name = "sport_type")
    val sportType: String? = null,
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String? = null,
    @ColumnInfo(name = "is_built_in")
    val isBuiltIn: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),
    @ColumnInfo(name = "track_layout_json")
    val trackLayoutJson: String = "[]",
    @ColumnInfo(name = "overlay_presets_json")
    val overlayPresetsJson: String = "[]",
    @ColumnInfo(name = "output_settings_json")
    val outputSettingsJson: String = "{}",
    @ColumnInfo(name = "style_preset_json")
    val stylePresetJson: String = "{}"
)
