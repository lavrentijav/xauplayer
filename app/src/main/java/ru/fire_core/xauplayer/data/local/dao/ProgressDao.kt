package ru.fire_core.xauplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.fire_core.xauplayer.data.local.entities.Progress

@Dao
interface ProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: Progress)

    @Query("SELECT * FROM Progress WHERE bookId = :bookId LIMIT 1")
    suspend fun get(bookId: Long): Progress?
    
    @Query("SELECT * FROM Progress")
    suspend fun getAll(): List<Progress>
}

