package ru.fire_core.xauplayer.ui.screens.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import ru.fire_core.xauplayer.data.local.entities.Book
import ru.fire_core.xauplayer.data.local.entities.Series
import ru.fire_core.xauplayer.data.local.entities.Tag
import ru.fire_core.xauplayer.ui.viewmodel.BooksViewModel

@Composable
fun TagChip(tag: Tag) {
    val defaultColor = MaterialTheme.colorScheme.primary
    val tagColor = remember(tag.color) {
        if (tag.color != null) {
            try {
                Color(android.graphics.Color.parseColor(tag.color))
            } catch (e: Exception) {
                defaultColor
            }
        } else {
            defaultColor
        }
    }
    
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = tagColor.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, tagColor.copy(alpha = 0.5f))
    ) {
        Text(
            text = tag.name,
            style = MaterialTheme.typography.labelSmall,
            color = tagColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun TagSelectionDialog(
    book: Book?,
    series: Series?,
    allTags: List<Tag>,
    vm: BooksViewModel,
    onDismiss: () -> Unit,
    onTagsUpdated: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val uiState by vm.uiState.collectAsState()
    
    // Используем теги из состояния, если они обновились
    val currentAllTags = if (uiState.allTags.isNotEmpty()) uiState.allTags else allTags
    
    // Загружаем актуальные теги при открытии диалога
    LaunchedEffect(book?.id, series?.id) {
        if (book != null) {
            vm.getBookTags(book.id)
        } else if (series != null) {
            vm.getSeriesTags(series.id)
        }
    }
    
    val currentTags = remember(book?.id, series?.id, uiState.bookTags, uiState.seriesTags) {
        if (book != null) {
            uiState.bookTags[book.id] ?: emptyList()
        } else if (series != null) {
            uiState.seriesTags[series.id] ?: emptyList()
        } else {
            emptyList()
        }
    }
    
    var selectedTagIds by remember(book?.id, series?.id) { 
        mutableStateOf(currentTags.map { it.id }.toSet()) 
    }
    
    // Обновляем выбранные теги при изменении текущих тегов
    LaunchedEffect(currentTags) {
        selectedTagIds = currentTags.map { it.id }.toSet()
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Заголовок
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (book != null) "Теги: ${book.title}" else "Теги: ${series?.name ?: ""}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                
                // Список тегов
                if (currentAllTags.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Нет доступных тегов",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    vm.refreshTags()
                                    // Обновляем состояние после обновления тегов
                                    vm.load()
                                }
                            }
                        ) {
                            Text("Обновить список тегов")
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(currentAllTags) { tag ->
                        val isSelected = selectedTagIds.contains(tag.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedTagIds = if (isSelected) {
                                        selectedTagIds - tag.id
                                    } else {
                                        selectedTagIds + tag.id
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedTagIds = if (checked) {
                                        selectedTagIds + tag.id
                                    } else {
                                        selectedTagIds - tag.id
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                }
                
                // Кнопка сохранения
                Button(
                    onClick = {
                        scope.launch {
                            if (book != null) {
                                // Удаляем теги, которые были сняты
                                currentTags.forEach { tag ->
                                    if (!selectedTagIds.contains(tag.id)) {
                                        vm.removeTagFromBook(book.id, tag.id)
                                    }
                                }
                                // Добавляем новые теги
                                selectedTagIds.forEach { tagId ->
                                    if (!currentTags.map { it.id }.contains(tagId)) {
                                        vm.addTagToBook(book.id, tagId)
                                    }
                                }
                            } else if (series != null) {
                                // Удаляем теги, которые были сняты
                                currentTags.forEach { tag ->
                                    if (!selectedTagIds.contains(tag.id)) {
                                        vm.removeTagFromSeries(series.id, tag.id)
                                    }
                                }
                                // Добавляем новые теги
                                selectedTagIds.forEach { tagId ->
                                    if (!currentTags.map { it.id }.contains(tagId)) {
                                        vm.addTagToSeries(series.id, tagId)
                                    }
                                }
                            }
                            onTagsUpdated()
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text("Сохранить")
                }
            }
        }
    }
}

