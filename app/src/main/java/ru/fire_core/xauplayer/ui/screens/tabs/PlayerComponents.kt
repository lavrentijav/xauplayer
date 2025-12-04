package ru.fire_core.xauplayer.ui.screens.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlin.math.roundToInt
import ru.fire_core.xauplayer.data.local.entities.Book
import ru.fire_core.xauplayer.ui.viewmodel.PlayerUiState
import ru.fire_core.xauplayer.ui.viewmodel.PlayerViewModel

@Composable
fun rememberBookCoverUrl(book: Book, vm: PlayerViewModel): String {
    var coverUrl by remember(book.id) { mutableStateOf<String>("") }
    LaunchedEffect(book.id) {
        coverUrl = vm.getBookCoverUrl(book.id)
    }
    return coverUrl
}

@Composable
internal fun CurrentBookCard(
    book: Book,
    state: PlayerUiState,
    onPlayClick: () -> Unit,
    onShareClick: () -> Unit,
    onMoreClick: () -> Unit,
    vm: PlayerViewModel
) {
    val coverUrl = rememberBookCoverUrl(book, vm)
    // Вычисляем прогресс книги (от общей длительности всех глав с учетом скорости)
    val progress = remember(
        state.currentPosition,
        state.duration,
        state.chapters,
        state.selectedChapter,
        state.playbackSpeed
    ) {
        if (state.chapters.isNotEmpty()) {
            // Общая длительность всех глав с учетом скорости (реальное время прослушивания)
            val speed = state.playbackSpeed.coerceAtLeast(0.1f)
            val totalDuration = state.chapters.sumOf { (it.duration / speed).toLong() }
            if (totalDuration > 0) {
                val selectedChapter = state.selectedChapter
                if (selectedChapter != null) {
                    // Находим индекс текущей главы
                    val currentIndex = state.chapters.indexOfFirst { it.id == selectedChapter.id }
                    if (currentIndex >= 0) {
                        // Время прослушанных глав (до текущей) с учетом скорости
                        val listenedChaptersDuration = state.chapters.subList(0, currentIndex)
                            .sumOf { (it.duration / speed).toLong() }
                        // Текущая позиция в текущей главе с учетом скорости
                        val currentChapterProgress =
                            (state.currentPosition.coerceIn(0, state.duration) / speed).toLong()
                        // Общее прослушанное время
                        val listenedDuration = listenedChaptersDuration + currentChapterProgress
                        // Процент
                        (listenedDuration.toFloat() / totalDuration.toFloat() * 100).coerceIn(
                            0f,
                            100f
                        )
                    } else {
                        0f
                    }
                } else {
                    0f
                }
            } else {
                0f
            }
        } else {
            0f
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlayClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Заголовок с кнопками
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    book.author?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    IconButton(onClick = onShareClick) {
                        Icon(Icons.Default.Share, contentDescription = "Поделиться")
                    }
                    IconButton(onClick = onMoreClick) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Еще")
                    }
                }
            }

            // Обложка
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit
            )

            // Прогресс-бар
            Column {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = "${progress.roundToInt()}% от всей книги",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Кнопка "Слушать"
            Button(
                onClick = onPlayClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Слушать")
            }
        }
    }
}

@Composable
internal fun CurrentBookCardSimple(
    book: Book,
    vm: PlayerViewModel,
    onPlayClick: () -> Unit
) {
    val coverUrl = rememberBookCoverUrl(book, vm)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlayClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Обложка
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit
            )

            // Название и автор
            Column {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.author ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Кнопка "Слушать"
            Button(
                onClick = { onPlayClick() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Слушать")
            }
        }
    }
}

@Composable
internal fun BookCoverItem(
    book: Book,
    vm: PlayerViewModel,
    onClick: () -> Unit
) {
    val coverUrl = rememberBookCoverUrl(book, vm)
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit
            )
            // TODO: Добавить бейдж с прогрессом (например, "100%")
        }
        Text(
            text = book.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

