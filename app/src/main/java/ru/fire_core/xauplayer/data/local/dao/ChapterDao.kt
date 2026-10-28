package ru.fire_core.xauplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import ru.fire_core.xauplayer.data.local.entities.Chapter

@Dao
interface ChapterDao {
    @Upsert
    suspend fun upsertAll(items: List<Chapter>)
    
    @Upsert
    suspend fun upsert(chapter: Chapter)

    @Query("DELETE FROM Chapter WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** Кэш глав: очищается вместе с библиотекой (скачанные главы не затрагиваются — они в downloaded_chapters). */
    @Query("DELETE FROM Chapter")
    suspend fun clear()

    @Query("SELECT * FROM Chapter WHERE bookId = :bookId ORDER BY COALESCE(realOrder, chapterOrder)")
    suspend fun getByBook(bookId: Long): List<Chapter>
    
    @Query("SELECT * FROM Chapter WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Chapter?
}

