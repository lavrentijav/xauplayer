package ru.fire_core.xauplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.fire_core.xauplayer.data.local.entities.Tag

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Tag>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tag: Tag)

    @Query("SELECT * FROM Tag ORDER BY name")
    suspend fun getAll(): List<Tag>
    
    @Query("SELECT * FROM Tag WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Tag?

    @Query("DELETE FROM Tag WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM Tag")
    suspend fun clear()
}

