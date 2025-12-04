package ru.fire_core.xauplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.fire_core.xauplayer.data.local.entities.Book

@Dao
interface BookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Book>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: Book)

    @Query("SELECT * FROM Book ORDER BY title")
    suspend fun getAll(): List<Book>
    
    @Query("SELECT * FROM Book WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Book?
    
    @Query("SELECT * FROM Book WHERE seriesId = :seriesId ORDER BY seriesOrder")
    suspend fun getBySeries(seriesId: Long): List<Book>

    @Query("""
        SELECT * FROM Book 
        WHERE title LIKE '%' || :query || '%' 
           OR author LIKE '%' || :query || '%' 
           OR narrator LIKE '%' || :query || '%' 
           OR description LIKE '%' || :query || '%'
        ORDER BY title
    """)
    suspend fun search(query: String): List<Book>

    @Query("DELETE FROM Book")
    suspend fun clear()
}

