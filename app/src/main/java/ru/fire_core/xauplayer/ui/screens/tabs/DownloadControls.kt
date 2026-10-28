package ru.fire_core.xauplayer.ui.screens.tabs

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.fire_core.xauplayer.download.DownloadState
import ru.fire_core.xauplayer.ui.viewmodel.PlayerViewModel

/** Состояние загрузки книги для отображения соответствующей кнопки. */
private enum class BookDownloadUi { NOT_DOWNLOADED, DOWNLOADING, DOWNLOADED }

@Composable
private fun rememberBookDownloadState(
    bookId: Long,
    playerVm: PlayerViewModel,
    refreshKey: Int
): BookDownloadUi {
    val progressMap by playerVm.downloadProgress.collectAsState()
    val bookProgress = progressMap.values.filter { it.bookId == bookId }
    val isDownloading = bookProgress.any {
        it.state == DownloadState.DOWNLOADING || it.state == DownloadState.QUEUED
    }
    var downloadedCount by remember(bookId) { mutableStateOf(0) }
    // Обновляем число скачанных глав при открытии, при старте/завершении загрузки и
    // после явных действий (удаление), не дёргая БД на каждое обновление прогресса.
    LaunchedEffect(bookId, isDownloading, refreshKey) {
        downloadedCount = playerVm.downloadedChapterCount(bookId)
    }
    return when {
        isDownloading -> BookDownloadUi.DOWNLOADING
        downloadedCount > 0 -> BookDownloadUi.DOWNLOADED
        else -> BookDownloadUi.NOT_DOWNLOADED
    }
}

/**
 * Кнопка управления загрузкой книги: «Скачать» → индикатор загрузки с отменой →
 * «Удалить» (когда есть скачанные главы). Используется в браузере одной книги.
 */
@Composable
fun BookDownloadButton(
    bookId: Long,
    playerVm: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var refreshKey by remember(bookId) { mutableStateOf(0) }
    when (rememberBookDownloadState(bookId, playerVm, refreshKey)) {
        BookDownloadUi.DOWNLOADING -> OutlinedButton(
            onClick = { playerVm.cancelBookDownloadByProgress(bookId) },
            modifier = modifier
        ) {
            Icon(Icons.Default.Close, contentDescription = "Отменить", modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Загрузка…", style = MaterialTheme.typography.labelLarge)
        }
        BookDownloadUi.DOWNLOADED -> OutlinedButton(
            onClick = { scope.launch { playerVm.deleteBook(bookId).join(); refreshKey++ } },
            modifier = modifier
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Удалить загрузку", modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Удалить", style = MaterialTheme.typography.labelLarge)
        }
        BookDownloadUi.NOT_DOWNLOADED -> Button(
            onClick = { playerVm.downloadBook(bookId) },
            modifier = modifier
        ) {
            Icon(Icons.Default.Download, contentDescription = "Скачать", modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Скачать", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Компактная иконка управления загрузкой книги для списков (например, книги
 * серии при развороте): скачать / индикатор загрузки / удалить.
 */
@Composable
fun BookDownloadIconButton(
    bookId: Long,
    playerVm: PlayerViewModel
) {
    val scope = rememberCoroutineScope()
    var refreshKey by remember(bookId) { mutableStateOf(0) }
    when (rememberBookDownloadState(bookId, playerVm, refreshKey)) {
        BookDownloadUi.DOWNLOADING -> IconButton(
            onClick = { playerVm.cancelBookDownloadByProgress(bookId) }
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        BookDownloadUi.DOWNLOADED -> IconButton(
            onClick = { scope.launch { playerVm.deleteBook(bookId).join(); refreshKey++ } }
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Удалить загрузку",
                tint = MaterialTheme.colorScheme.error
            )
        }
        BookDownloadUi.NOT_DOWNLOADED -> IconButton(
            onClick = { playerVm.downloadBook(bookId) }
        ) {
            Icon(Icons.Default.Download, contentDescription = "Скачать")
        }
    }
}
