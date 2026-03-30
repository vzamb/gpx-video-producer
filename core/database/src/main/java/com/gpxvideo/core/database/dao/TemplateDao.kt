package com.gpxvideo.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gpxvideo.core.database.entity.TemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: TemplateEntity)

    @Update
    suspend fun update(template: TemplateEntity)

    @Delete
    suspend fun delete(template: TemplateEntity)

    @Query("SELECT * FROM templates ORDER BY created_at DESC")
    fun getAll(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE is_built_in = 1 ORDER BY name ASC")
    fun getBuiltIn(): Flow<List<TemplateEntity>>
}
