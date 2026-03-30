package com.gpxvideo.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gpxvideo.core.database.entity.TimelineClipEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface TimelineClipDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(clip: TimelineClipEntity)

    @Update
    suspend fun update(clip: TimelineClipEntity)

    @Delete
    suspend fun delete(clip: TimelineClipEntity)

    @Query("SELECT * FROM timeline_clips WHERE id = :id")
    suspend fun getById(id: UUID): TimelineClipEntity?

    @Query("DELETE FROM timeline_clips WHERE id = :id")
    suspend fun deleteById(id: UUID)

    @Query("DELETE FROM timeline_clips WHERE track_id = :trackId")
    suspend fun deleteByTrackId(trackId: UUID)

    @Query("SELECT * FROM timeline_clips WHERE track_id = :trackId ORDER BY start_time_ms ASC")
    fun getByTrackId(trackId: UUID): Flow<List<TimelineClipEntity>>
}
