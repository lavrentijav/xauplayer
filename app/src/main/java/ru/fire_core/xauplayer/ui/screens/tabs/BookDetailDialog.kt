package ru.fire_core.xauplayer.ui.screens.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import ru.fire_core.xauplayer.core.humor.FunPhrases
import ru.fire_core.xauplayer.data.local.entities.Book
import ru.fire_core.xauplayer.ui.components.EffectOverlay
import ru.fire_core.xauplayer.ui.viewmodel.BooksViewModel

/**
 * Страница просмотра книги: обложка, автор, чтец, описание, статус
 * и кнопка воспроизведения. На фоне — тематический эффект (матрица,
 * искры вверх, звёзды) в зависимости от названия книги.
 */
@Composable
fun BookDetailDialog(
    book: Book,
    vm: BooksViewModel,
    playerVm: ru.fire_core.xauplayer.ui.viewmodel.PlayerViewModel,
    bookStatus: String?,
    onPlay: () -> Unit,
    onDismiss: () -> Unit
) {
    var coverUrl by remember(book.id) { mutableStateOf("") }
    LaunchedEffect(book.id) { coverUrl = vm.getBookCoverUrl(book.id) }

    val effect = remember(book.title) { FunPhrases.bookEffect(book.title) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Фоновый эффект
                effect?.let {
                    EffectOverlay(
                        kind = it.kind,
                        colorHex = it.colorHex,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = onDismiss) {
                            Text("✕", style = MaterialTheme.typography.headlineMedium)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .height(220.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                        effect?.let {
                            Text(
                                it.emoji,
                                style = MaterialTheme.typography.headlineLarge,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        book.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        book.author,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    book.narrator?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            "Читает: $it",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    bookStatus?.let { st ->
                        Spacer(Modifier.height(8.dp))
                        AssistChip(
                            onClick = {},
                            label = { Text(statusLabelRu(st)) }
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { onPlay(); onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Слушать")
                    }

                    Spacer(Modifier.height(8.dp))

                    // Добавить в список воспроизведения и управление загрузкой (скачать/удалить)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { playerVm.addBook(book) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Добавить")
                        }
                        BookDownloadButton(
                            bookId = book.id,
                            playerVm = playerVm,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    val desc = book.description?.takeIf { it.isNotBlank() }
                    if (desc != null) {
                        Text(
                            "Описание",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(desc, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            "Описание отсутствует",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun statusLabelRu(status: String): String = when (status) {
    "listening" -> "Слушаю 🎧"
    "wanted" -> "В планах 📚"
    "completed" -> "Прослушано ✅"
    "dropped" -> "Брошено 🥀"
    else -> status
}
