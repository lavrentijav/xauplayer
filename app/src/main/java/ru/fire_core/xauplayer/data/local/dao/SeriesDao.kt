package ru.fire_core.xauplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import ru.fire_core.xauplayer.data.local.entities.Series

@Dao
interface SeriesDao {
    @Upsert
    suspend fun upsert(series: Series)
    
    @Upsert
    suspend fun upsertAll(series: List<Series>)

    @Query("SELECT id FROM Series")
    suspend fun getAllIds(): List<Long>

    @Query("DELETE FROM Series WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT * FROM Series ORDER BY name")
    suspend fun getAll(): List<Series>
    
    @Query("SELECT * FROM Series WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Series?
    
    @Query("DELETE FROM Series WHERE id = :id")
    suspend fun delete(id: Long)
    
    @Query("DELETE FROM Series")
    suspend fun clear()
}

