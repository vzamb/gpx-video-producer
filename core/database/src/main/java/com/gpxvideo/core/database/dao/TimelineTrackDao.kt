package com.gpxvideo.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gpxvideo.core.database.entity.TimelineTrackEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface TimelineTrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(track: TimelineTrackEntity)

    @Update
    suspend fun update(track: TimelineTrackEntity)

    @Delete
    suspend fun delete(track: TimelineTrackEntity)

    @Query("SELECT * FROM timeline_tracks WHERE project_id = :projectId ORDER BY track_order ASC")
    fun getByProjectId(projectId: UUID): Flow<List<TimelineTrackEntity>>
}
