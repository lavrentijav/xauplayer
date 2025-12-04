package ru.fire_core.xauplayer.domain.usecase.notes

import ru.fire_core.xauplayer.domain.repo.NotesRepository
import javax.inject.Inject

class NotesUseCases @Inject constructor(private val repo: NotesRepository) {
    suspend fun syncNotes(bookId: Long) = repo.syncNotes(bookId)
    suspend fun getNotes(bookId: Long) = repo.getLocal(bookId)
    suspend fun createNote(bookId: Long, text: String, timestamp: Long) = repo.createNote(bookId, text, timestamp)
}

