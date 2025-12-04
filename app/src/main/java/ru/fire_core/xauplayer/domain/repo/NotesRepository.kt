package ru.fire_core.xauplayer.domain.repo

import ru.fire_core.xauplayer.data.local.AppDatabase
import ru.fire_core.xauplayer.data.local.entities.Note
import ru.fire_core.xauplayer.data.network.ApiService
import ru.fire_core.xauplayer.data.network.NoteRequest
import ru.fire_core.xauplayer.core.logger.AppLogger
import javax.inject.Inject

interface NotesRepository {
    suspend fun syncNotes(bookId: Long)
    suspend fun getLocal(bookId: Long): List<Note>
    suspend fun createNote(bookId: Long, text: String, timestamp: Long)
}

class NotesRepositoryImpl @Inject constructor(
    private val api: ApiService,
    private val db: AppDatabase,
    private val logger: AppLogger
) : NotesRepository {
    override suspend fun syncNotes(bookId: Long) {
        try {
            val remote = api.getNotes(bookId)
            val mapped = remote.map {
                Note(
                    id = it.id,
                    bookId = bookId,
                    text = it.text,
                    timestamp = it.timestamp,
                    createdAt = it.created_at,
                    updatedAt = it.updated_at
                )
            }
            db.noteDao().upsertAll(mapped)
        } catch (e: Exception) {
            logger.warn("NotesRepository", "Failed to sync notes for book $bookId", e)
        }
    }
    
    override suspend fun getLocal(bookId: Long) = db.noteDao().getByBook(bookId)
    
    override suspend fun createNote(bookId: Long, text: String, timestamp: Long) {
        try {
            val note = api.createNote(bookId, NoteRequest(text, timestamp))
            db.noteDao().upsert(
                Note(
                    id = note.id,
                    bookId = bookId,
                    text = note.text,
                    timestamp = note.timestamp,
                    createdAt = note.created_at,
                    updatedAt = note.updated_at
                )
            )
        } catch (e: Exception) {
            logger.warn("NotesRepository", "Failed to create note for book $bookId", e)
        }
    }
}
