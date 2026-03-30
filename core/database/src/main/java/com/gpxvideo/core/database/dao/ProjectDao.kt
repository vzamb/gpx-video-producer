package com.gpxvideo.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gpxvideo.core.database.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface ProjectDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity)

    @Update
    suspend fun update(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: UUID)

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: UUID): ProjectEntity?

    @Query("SELECT * FROM projects ORDER BY updated_at DESC")
    fun getAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE name LIKE '%' || :query || '%' ORDER BY updated_at DESC")
    fun searchByName(query: String): Flow<List<ProjectEntity>>
}
