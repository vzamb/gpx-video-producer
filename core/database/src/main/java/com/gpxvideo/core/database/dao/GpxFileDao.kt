package com.gpxvideo.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gpxvideo.core.database.entity.GpxFileEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface GpxFileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(gpxFile: GpxFileEntity)

    @Delete
    suspend fun delete(gpxFile: GpxFileEntity)

    @Query("SELECT * FROM gpx_files WHERE project_id = :projectId")
    fun getByProjectId(projectId: UUID): Flow<List<GpxFileEntity>>
}
