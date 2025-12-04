package ru.fire_core.xauplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.fire_core.xauplayer.data.local.entities.Note
import ru.fire_core.xauplayer.domain.repo.NotesRepository
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val repo: NotesRepository
) : ViewModel() {
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes

    fun load(bookId: Long) = viewModelScope.launch {
        // Синхронизируем заметки с сервера
        repo.syncNotes(bookId)
        _notes.value = repo.getLocal(bookId)
    }

    fun add(bookId: Long, text: String, timestamp: Long = System.currentTimeMillis()) = viewModelScope.launch {
        // Создаем заметку (автоматически синхронизируется с сервером)
        repo.createNote(bookId, text, timestamp)
        _notes.value = repo.getLocal(bookId)
    }
}


