package com.gpxvideo.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gpxvideo.core.database.entity.OverlayEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface OverlayDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(overlay: OverlayEntity)

    @Update
    suspend fun update(overlay: OverlayEntity)

    @Delete
    suspend fun delete(overlay: OverlayEntity)

    @Query("DELETE FROM overlays WHERE id = :id")
    suspend fun deleteById(id: UUID)

    @Query("SELECT * FROM overlays WHERE id = :id")
    suspend fun getById(id: UUID): OverlayEntity?

    @Query("SELECT * FROM overlays WHERE project_id = :projectId")
    fun getByProjectId(projectId: UUID): Flow<List<OverlayEntity>>

    @Query("SELECT * FROM overlays WHERE timeline_clip_id = :timelineClipId LIMIT 1")
    suspend fun getByTimelineClipId(timelineClipId: UUID): OverlayEntity?

    @Query("DELETE FROM overlays WHERE timeline_clip_id = :timelineClipId")
    suspend fun deleteByTimelineClipId(timelineClipId: UUID)
}
