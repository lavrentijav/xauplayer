package ru.fire_core.xauplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.fire_core.xauplayer.data.local.entities.BufferedProgress

@Dao
interface BufferedProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: BufferedProgress): Long

    @Query("SELECT * FROM progress_buffer WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsynced(): List<BufferedProgress>

    @Query("SELECT * FROM progress_buffer WHERE bookId = :bookId AND synced = 0 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestUnsyncedForBook(bookId: Long): BufferedProgress?

    @Query("UPDATE progress_buffer SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Query("DELETE FROM progress_buffer WHERE synced = 1 AND timestamp < :before")
    suspend fun cleanupOldSynced(before: Long)

    @Query("DELETE FROM progress_buffer WHERE bookId = :bookId AND chapterId = :chapterId AND synced = 0")
    suspend fun deleteUnsyncedForChapter(bookId: Long, chapterId: Long)
}

