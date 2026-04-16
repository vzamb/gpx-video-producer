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

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE projects ADD COLUMN accent_color INTEGER NOT NULL DEFAULT -12010753")
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE projects ADD COLUMN show_elevation_chart INTEGER NOT NULL DEFAULT 1")
            database.execSQL("ALTER TABLE projects ADD COLUMN show_route_map INTEGER NOT NULL DEFAULT 1")
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE projects ADD COLUMN metric_config TEXT DEFAULT NULL")
        }
    }

    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Replace boolean show_elevation_chart with string chart_type
            // Must recreate the table to drop the old column (Room validates exact column match)
            database.execSQL("ALTER TABLE projects ADD COLUMN chart_type TEXT DEFAULT 'ELEVATION'")
            database.execSQL("UPDATE projects SET chart_type = NULL WHERE show_elevation_chart = 0")

            database.execSQL("""
                CREATE TABLE projects_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    description TEXT,
                    sport_type TEXT NOT NULL DEFAULT 'OTHER',
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    thumbnail_path TEXT,
                    resolution_width INTEGER NOT NULL,
                    resolution_height INTEGER NOT NULL,
                    frame_rate INTEGER NOT NULL,
                    export_format TEXT NOT NULL,
                    bitrate_bps INTEGER NOT NULL,
                    audio_codec TEXT NOT NULL,
                    template_id TEXT,
                    story_mode TEXT NOT NULL DEFAULT 'HYPER_LAPSE',
                    story_template TEXT NOT NULL DEFAULT 'CINEMATIC',
                    activity_title TEXT NOT NULL DEFAULT '',
                    accent_color INTEGER NOT NULL DEFAULT -12010753,
                    chart_type TEXT DEFAULT 'ELEVATION',
                    show_route_map INTEGER NOT NULL DEFAULT 1,
                    metric_config TEXT DEFAULT NULL
                )
            """.trimIndent())

            database.execSQL("""
                INSERT INTO projects_new (
                    id, name, description, sport_type, created_at, updated_at,
                    thumbnail_path, resolution_width, resolution_height, frame_rate,
                    export_format, bitrate_bps, audio_codec, template_id,
                    story_mode, story_template, activity_title, accent_color,
                    chart_type, show_route_map, metric_config
                )
                SELECT
                    id, name, description, sport_type, created_at, updated_at,
                    thumbnail_path, resolution_width, resolution_height, frame_rate,
                    export_format, bitrate_bps, audio_codec, template_id,
                    story_mode, story_template, activity_title, accent_color,
                    chart_type, show_route_map, metric_config
                FROM projects
            """.trimIndent())

            database.execSQL("DROP TABLE projects")
            database.execSQL("ALTER TABLE projects_new RENAME TO projects")
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
            .build()
    }
}
