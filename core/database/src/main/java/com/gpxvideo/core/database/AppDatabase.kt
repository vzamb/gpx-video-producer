package com.gpxvideo.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.gpxvideo.core.database.converter.Converters
import com.gpxvideo.core.database.dao.GpxFileDao
import com.gpxvideo.core.database.dao.MediaItemDao
import com.gpxvideo.core.database.dao.OverlayDao
import com.gpxvideo.core.database.dao.ProjectDao
import com.gpxvideo.core.database.dao.TemplateDao
import com.gpxvideo.core.database.dao.TimelineClipDao
import com.gpxvideo.core.database.dao.TimelineTrackDao
import com.gpxvideo.core.database.entity.GpxFileEntity
import com.gpxvideo.core.database.entity.MediaItemEntity
import com.gpxvideo.core.database.entity.OverlayEntity
import com.gpxvideo.core.database.entity.ProjectEntity
import com.gpxvideo.core.database.entity.TemplateEntity
import com.gpxvideo.core.database.entity.TimelineClipEntity
import com.gpxvideo.core.database.entity.TimelineTrackEntity

@Database(
    entities = [
        ProjectEntity::class,
        MediaItemEntity::class,
        GpxFileEntity::class,
        TimelineTrackEntity::class,
        TimelineClipEntity::class,
        OverlayEntity::class,
        TemplateEntity::class
    ],
    version = 10,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun gpxFileDao(): GpxFileDao
    abstract fun timelineTrackDao(): TimelineTrackDao
    abstract fun timelineClipDao(): TimelineClipDao
    abstract fun overlayDao(): OverlayDao
    abstract fun templateDao(): TemplateDao
}
