package ru.fire_core.xauplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.fire_core.xauplayer.data.local.entities.BookStatus

@Dao
interface BookStatusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(status: BookStatus)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(statuses: List<BookStatus>)

    @Query("SELECT * FROM BookStatus WHERE bookId = :bookId LIMIT 1")
    suspend fun getByBook(bookId: Long): BookStatus?
    
    @Query("SELECT * FROM BookStatus WHERE status = :status")
    suspend fun getByStatus(status: String): List<BookStatus>
    
    @Query("SELECT * FROM BookStatus")
    suspend fun getAll(): List<BookStatus>
    
    @Query("DELETE FROM BookStatus WHERE bookId = :bookId")
    suspend fun delete(bookId: Long)
    
    @Query("DELETE FROM BookStatus")
    suspend fun clear()
}

