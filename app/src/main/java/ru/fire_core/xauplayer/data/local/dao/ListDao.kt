package ru.fire_core.xauplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.fire_core.xauplayer.data.local.entities.BookList

@Dao
interface ListDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<BookList>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(list: BookList)

    @Query("SELECT * FROM BookList ORDER BY name")
    suspend fun getAll(): List<BookList>
    
    @Query("SELECT * FROM BookList WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): BookList?

    @Query("DELETE FROM BookList WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM BookList")
    suspend fun clear()
}

