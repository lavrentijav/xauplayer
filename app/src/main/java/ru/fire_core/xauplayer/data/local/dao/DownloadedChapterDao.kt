package ru.fire_core.xauplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.fire_core.xauplayer.data.local.entities.DownloadedChapter

@Dao
interface DownloadedChapterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(chapter: DownloadedChapter)

    @Query("SELECT * FROM downloaded_chapters WHERE chapterId = :chapterId LIMIT 1")
    suspend fun getByChapterId(chapterId: Long): DownloadedChapter?

    @Query("SELECT * FROM downloaded_chapters WHERE bookId = :bookId")
    suspend fun getByBookId(bookId: Long): List<DownloadedChapter>

    @Query("SELECT COUNT(*) FROM downloaded_chapters WHERE bookId = :bookId")
    suspend fun countByBookId(bookId: Long): Int

    @Query("DELETE FROM downloaded_chapters WHERE chapterId = :chapterId")
    suspend fun delete(chapterId: Long)

    @Query("DELETE FROM downloaded_chapters WHERE bookId = :bookId")
    suspend fun deleteByBookId(bookId: Long)
}

