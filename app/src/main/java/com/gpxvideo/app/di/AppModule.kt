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

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE projects ADD COLUMN activity_title TEXT NOT NULL DEFAULT ''"
            )
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE timeline_clips ADD COLUMN gpx_point_index INTEGER NOT NULL DEFAULT -1")
            database.execSQL("ALTER TABLE timeline_clips ADD COLUMN gpx_distance_meters REAL NOT NULL DEFAULT 0.0")
            database.execSQL("ALTER TABLE timeline_clips ADD COLUMN is_synced INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE media_items ADD COLUMN video_created_at INTEGER")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE timeline_clips ADD COLUMN is_auto_synced INTEGER NOT NULL DEFAULT 0")
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .build()
    }
}
