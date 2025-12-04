package ru.fire_core.xauplayer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.fire_core.xauplayer.ui.viewmodel.NotesViewModel

@Composable
fun NotesBox(bookId: Long, modifier: Modifier = Modifier, vm: NotesViewModel = hiltViewModel()) {
    var note by remember { mutableStateOf("") }
    val notes by vm.notes.collectAsState()
    LaunchedEffect(bookId) { vm.load(bookId) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.weight(1f), label = { Text("Заметка") })
            Button(onClick = { if (note.isNotBlank()) { vm.add(bookId, note); note = "" } }) { Text("Сохранить") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(notes) { n -> Text("• ${'$'}{n.text}") }
        }
    }
}


