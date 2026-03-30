package com.gpxvideo.app.di

import com.gpxvideo.core.database.AppDatabase
import com.gpxvideo.core.database.dao.GpxFileDao
import com.gpxvideo.core.database.dao.MediaItemDao
import com.gpxvideo.core.database.dao.OverlayDao
import com.gpxvideo.core.database.dao.ProjectDao
import com.gpxvideo.core.database.dao.TemplateDao
import com.gpxvideo.core.database.dao.TimelineClipDao
import com.gpxvideo.core.database.dao.TimelineTrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    fun provideProjectDao(database: AppDatabase): ProjectDao = database.projectDao()

    @Provides
    fun provideMediaItemDao(database: AppDatabase): MediaItemDao = database.mediaItemDao()

    @Provides
    fun provideGpxFileDao(database: AppDatabase): GpxFileDao = database.gpxFileDao()

    @Provides
    fun provideTimelineTrackDao(database: AppDatabase): TimelineTrackDao = database.timelineTrackDao()

    @Provides
    fun provideTimelineClipDao(database: AppDatabase): TimelineClipDao = database.timelineClipDao()

    @Provides
    fun provideOverlayDao(database: AppDatabase): OverlayDao = database.overlayDao()

    @Provides
    fun provideTemplateDao(database: AppDatabase): TemplateDao = database.templateDao()
}
