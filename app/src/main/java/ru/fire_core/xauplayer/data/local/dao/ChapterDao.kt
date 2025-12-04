package ru.fire_core.xauplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.fire_core.xauplayer.data.local.entities.Chapter

@Dao
interface ChapterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Chapter>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(chapter: Chapter)

    @Query("SELECT * FROM Chapter WHERE bookId = :bookId ORDER BY COALESCE(realOrder, chapterOrder)")
    suspend fun getByBook(bookId: Long): List<Chapter>
    
    @Query("SELECT * FROM Chapter WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Chapter?
}

