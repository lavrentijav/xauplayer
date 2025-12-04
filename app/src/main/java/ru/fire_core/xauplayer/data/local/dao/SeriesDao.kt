package ru.fire_core.xauplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.fire_core.xauplayer.data.local.entities.Series

@Dao
interface SeriesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(series: Series)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(series: List<Series>)

    @Query("SELECT * FROM Series ORDER BY name")
    suspend fun getAll(): List<Series>
    
    @Query("SELECT * FROM Series WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Series?
    
    @Query("DELETE FROM Series WHERE id = :id")
    suspend fun delete(id: Long)
    
    @Query("DELETE FROM Series")
    suspend fun clear()
}

