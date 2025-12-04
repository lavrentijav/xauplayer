package ru.fire_core.xauplayer.ui.screens.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import ru.fire_core.xauplayer.data.local.entities.Book
import ru.fire_core.xauplayer.data.local.entities.Chapter
import ru.fire_core.xauplayer.ui.viewmodel.PlayerUiState
import ru.fire_core.xauplayer.ui.viewmodel.PlayerViewModel
import ru.fire_core.xauplayer.ui.screens.tabs.rememberBookCoverUrl

@Composable
fun MiniPlayer(
    book: Book,
    chapter: Chapter,
    state: PlayerUiState,
    vm: PlayerViewModel,
    onClick: () -> Unit,
    onChapterClick: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Отслеживаем статус загрузки книги
    val downloadProgress by vm.downloadProgress.collectAsState()
    val isBookDownloading = remember(book.id, state.chapters, downloadProgress) {
        if (state.chapters.isEmpty()) {
            false
        } else {
            state.chapters.any { ch ->
                val progress = downloadProgress[ch.id]
                progress?.state == ru.fire_core.xauplayer.download.DownloadState.DOWNLOADING
            }
        }
    }

    // Получаем URL обложки
    val coverUrl = rememberBookCoverUrl(book, vm)

    // Индекс текущей главы
    val chapterIndex = state.chapters.indexOfFirst { it.id == chapter.id } + 1

    Card(
        modifier = modifier
            .wrapContentHeight()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(enabled = true) { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Обложка книги слева
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Fit
            )

            // Информация о книге и главе
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        onClick = { onChapterClick() },
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Название книги
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Автор
                book.author?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Глава
                Text(
                    text = "Глава $chapterIndex",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Кнопки управления справа
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(
                    onClick = { /* Предотвращаем всплытие клика на карточку */ },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                )
            ) {
                // Иконка загрузки с анимацией и кнопкой отмены
                if (isBookDownloading) {
                    Box(
                        modifier = Modifier.size(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        IconButton(
                            onClick = {
                                vm.cancelBookDownload(book.id)
                            },
                            modifier = Modifier.size(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Отменить загрузку",
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                // Кнопка назад
                IconButton(
                    onClick = {
                        if (state.chapters.isNotEmpty() && chapterIndex > 1) {
                            val prevChapter = state.chapters[chapterIndex - 2]
                            vm.playChapter(prevChapter, book)
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Предыдущая глава",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Кнопка Play/Pause
                IconButton(
                    onClick = { onPlayPause() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Пауза" else "Воспроизведение",
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Кнопка вперед
                IconButton(
                    onClick = {
                        if (state.chapters.isNotEmpty() && chapterIndex < state.chapters.size) {
                            val nextChapter = state.chapters[chapterIndex]
                            vm.playChapter(nextChapter, book)
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Следующая глава",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

