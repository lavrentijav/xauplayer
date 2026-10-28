package ru.fire_core.xauplayer.ui.screens.tabs

import androidx.compose.foundation.clickable
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
import ru.fire_core.xauplayer.ui.viewmodel.SeriesWithBooks

/**
 * Страница серии: иконка серии, описание и все входящие книги.
 * На фоне — тематический эффект по названию серии. Нажатие на книгу
 * открывает просмотр книги.
 */
@Composable
fun SeriesDetailDialog(
    seriesWithBooks: SeriesWithBooks,
    vm: BooksViewModel,
    playerVm: ru.fire_core.xauplayer.ui.viewmodel.PlayerViewModel,
    onOpenBook: (Book) -> Unit,
    onDismiss: () -> Unit
) {
    val series = seriesWithBooks.series
    var coverUrl by remember(series.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(series.id) { coverUrl = vm.getSeriesCoverUrl(series.id, seriesWithBooks.books) }

    val effect = remember(series.name) { FunPhrases.bookEffect(series.name) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        coverUrl?.let {
                            AsyncImage(
                                model = it,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                series.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${seriesWithBooks.books.size} книг",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            effect?.let {
                                Text(it.emoji, style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                    }

                    series.description?.takeIf { it.isNotBlank() }?.let { desc ->
                        Spacer(Modifier.height(12.dp))
                        Text(desc, style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Книги серии",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))

                    seriesWithBooks.books.forEach { book ->
                        SeriesBookRow(
                            book = book,
                            vm = vm,
                            playerVm = playerVm,
                            onClick = { onOpenBook(book) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesBookRow(
    book: Book,
    vm: BooksViewModel,
    playerVm: ru.fire_core.xauplayer.ui.viewmodel.PlayerViewModel,
    onClick: () -> Unit
) {
    var coverUrl by remember(book.id) { mutableStateOf("") }
    LaunchedEffect(book.id) { coverUrl = vm.getBookCoverUrl(book.id) }
    val effect = remember(book.title) { FunPhrases.bookEffect(book.title) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                contentScale = ContentScale.Fit
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            effect?.let { Text(it.emoji, style = MaterialTheme.typography.titleMedium) }
            // Скачать / загрузка / удалить (если книга скачана)
            BookDownloadIconButton(bookId = book.id, playerVm = playerVm)
        }
    }
}
