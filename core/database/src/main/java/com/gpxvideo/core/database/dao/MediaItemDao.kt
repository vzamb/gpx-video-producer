package com.gpxvideo.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gpxvideo.core.database.entity.MediaItemEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface MediaItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mediaItem: MediaItemEntity)

    @Update
    suspend fun update(mediaItem: MediaItemEntity)

    @Delete
    suspend fun delete(mediaItem: MediaItemEntity)

    @Query("SELECT * FROM media_items WHERE project_id = :projectId ORDER BY created_at ASC")
    fun getByProjectId(projectId: UUID): Flow<List<MediaItemEntity>>
}
