package com.gpxvideo.app.di

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gpxvideo.core.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE timeline_clips ADD COLUMN content_mode TEXT NOT NULL DEFAULT 'FIT'"
            )
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE projects ADD COLUMN story_mode TEXT NOT NULL DEFAULT 'HYPER_LAPSE'"
            )
            database.execSQL(
                "ALTER TABLE projects ADD COLUMN story_template TEXT NOT NULL DEFAULT 'CINEMATIC'"
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "gpx_video_producer.db"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }
}
