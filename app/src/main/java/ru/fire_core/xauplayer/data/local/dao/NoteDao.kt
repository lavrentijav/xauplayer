package ru.fire_core.xauplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.fire_core.xauplayer.data.local.entities.Note

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: Note)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(notes: List<Note>)

    @Query("SELECT * FROM Note WHERE bookId = :bookId ORDER BY timestamp DESC")
    suspend fun getByBook(bookId: Long): List<Note>
    
    @Query("DELETE FROM Note WHERE id = :id")
    suspend fun delete(id: Long)
}

