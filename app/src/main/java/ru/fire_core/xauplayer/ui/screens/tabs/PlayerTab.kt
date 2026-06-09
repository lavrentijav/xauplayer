package ru.fire_core.xauplayer.ui.screens.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.fire_core.xauplayer.data.local.entities.Book
import ru.fire_core.xauplayer.data.local.entities.Chapter
import ru.fire_core.xauplayer.ui.viewmodel.PlayerUiState
import ru.fire_core.xauplayer.ui.viewmodel.PlayerViewModel
import kotlin.math.roundToInt

@Composable
fun PlayerTab(
    modifier: Modifier = Modifier,
    vm: PlayerViewModel = hiltViewModel(),
    booksVm: ru.fire_core.xauplayer.ui.viewmodel.BooksViewModel = hiltViewModel(),
    initialBook: Book? = null,
    showPlayerModal: Boolean = false,
    onShowPlayerModalChange: (Boolean) -> Unit = {}
) {
    val state by vm.uiState.collectAsState()

    // Загружаем книги с прогрессом при открытии плеера
    LaunchedEffect(Unit) {
        vm.loadBooks()
    }

    // Если передан начальная книга, выбираем её, иначе загружаем текущую книгу
    LaunchedEffect(initialBook) {
        if (initialBook != null) {
            vm.selectBook(initialBook)
        } else {
            vm.loadCurrentBook()
        }
    }

    // Состояния для модальных окон
    var showDownloadedModal by remember { mutableStateOf(false) }
    var showCompletedModal by remember { mutableStateOf(false) }
    var showDroppedModal by remember { mutableStateOf(false) }
    var showAllMyBooksModal by remember { mutableStateOf(false) }
    var showSeriesModal by remember { mutableStateOf(false) }

    // Загружаем книги по категориям
    var listeningBooks by remember { mutableStateOf<List<Book>>(emptyList()) }
    var wantedBooks by remember { mutableStateOf<List<Book>>(emptyList()) }
    var completedBooks by remember { mutableStateOf<List<Book>>(emptyList()) }
    var droppedBooks by remember { mutableStateOf<List<Book>>(emptyList()) }
    var downloadedBooks by remember { mutableStateOf<List<Book>>(emptyList()) }
    var allMyBooks by remember { mutableStateOf<List<Book>>(emptyList()) }
    var allSeries by remember {
        mutableStateOf<List<ru.fire_core.xauplayer.ui.viewmodel.SeriesWithBooks>>(
            emptyList()
        )
    }

    // Обновляем книги при изменении состояния с debounce для оптимизации
    LaunchedEffect(state.books, state.selectedBook) {
        kotlinx.coroutines.delay(ru.fire_core.xauplayer.core.config.AppConfig.UI_DEBOUNCE_DELAY_MS)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                listeningBooks = vm.getListeningBooks()
            } catch (e: Exception) {
                // В офлайн режиме продолжаем с пустым списком
            }
            try {
                wantedBooks = vm.getWantedBooks()
            } catch (e: Exception) {
                // В офлайн режиме продолжаем с пустым списком
            }
            try {
                completedBooks = vm.getCompletedBooks()
            } catch (e: Exception) {
                // В офлайн режиме продолжаем с пустым списком
            }
            try {
                droppedBooks = vm.getDroppedBooks()
            } catch (e: Exception) {
                // В офлайн режиме продолжаем с пустым списком
            }
            // Загруженные книги - критично для офлайн режима, поэтому обрабатываем отдельно
            try {
                downloadedBooks = vm.getDownloadedBooks()
            } catch (e: Exception) {
                // В офлайн режиме пытаемся получить загруженные книги из локальной БД
                downloadedBooks = emptyList()
            }
            try {
                allMyBooks = vm.getAllMyBooks()
            } catch (e: Exception) {
                // В офлайн режиме продолжаем с пустым списком
            }
            try {
                allSeries = vm.getAllSeries()
            } catch (e: Exception) {
                // В офлайн режиме продолжаем с пустым списком
            }
        }
    }
    
    // Периодически обновляем список загруженных книг (важно для офлайн режима)
    LaunchedEffect(Unit) {
        while (isActive) { // Проверяем isActive для правильной отмены корутины
            kotlinx.coroutines.delay(ru.fire_core.xauplayer.core.config.AppConfig.PERIODIC_UPDATE_INTERVAL_MS)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    if (isActive) { // Дополнительная проверка перед операцией
                        downloadedBooks = vm.getDownloadedBooks()
                    }
                } catch (e: Exception) {
                    // Игнорируем ошибки при периодическом обновлении
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Карточка текущей книги
            item {
                val currentBook = state.selectedBook
                if (currentBook != null) {
                    CurrentBookCardSimple(
                        book = currentBook,
                        vm = vm,
                        onPlayClick = { 
                            // Продолжаем прослушивание и открываем модальное окно плеера
                            vm.continueListening()
                            onShowPlayerModalChange(true) 
                        }
                    )
                }
            }

            // Секция "Моё" с книгами по статусам - всегда показываем все категории
            item {
                Text(
                    text = "Моё",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
            }

            // Слушаю
            item {
                Text(
                    text = "Слушаю",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
                )
            }
            item {
                val listeningBooksWithoutCurrent =
                    listeningBooks.filter { it.id != state.selectedBook?.id }
                if (listeningBooksWithoutCurrent.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        items(listeningBooksWithoutCurrent) { book ->
                            BookCoverItem(
                                book = book,
                                vm = vm,
                                onClick = { vm.selectBook(book) }
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Нет книг",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )
                }
            }

            // В планах
            item {
                Text(
                    text = "В планах",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
                )
            }
            item {
                if (wantedBooks.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        items(wantedBooks) { book ->
                            BookCoverItem(
                                book = book,
                                vm = vm,
                                onClick = { vm.selectBook(book) }
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Нет книг",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )
                }
            }

            // Прослушано
            item {
                Text(
                    text = "Прослушано",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
                )
            }
            item {
                if (completedBooks.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        items(completedBooks) { book ->
                            BookCoverItem(
                                book = book,
                                vm = vm,
                                onClick = { vm.selectBook(book) }
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Нет книг",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )
                }
            }

            // Брошено
            item {
                Text(
                    text = "Брошено",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
                )
            }
            item {
                if (droppedBooks.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        items(droppedBooks) { book ->
                            BookCoverItem(
                                book = book,
                                vm = vm,
                                onClick = { vm.selectBook(book) }
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Нет книг",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )
                }
            }

            //Spacer(modifier = Modifier.height(16.dp))

            // Скачанные
            if (downloadedBooks.isNotEmpty()) {
                item {
                    OutlinedButton(
                        onClick = { showDownloadedModal = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Скачанные (${downloadedBooks.size})")
                    }
                }
            }

            // Все мои
            if (allMyBooks.isNotEmpty()) {
                item {
                    OutlinedButton(
                        onClick = { showAllMyBooksModal = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Все мои (${allMyBooks.size})")
                    }
                }
            }

            // Мои серии
            if (allSeries.isNotEmpty()) {
                item {
                    OutlinedButton(
                        onClick = { showSeriesModal = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CollectionsBookmark, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Мои серии (${allSeries.size})")
                    }
                }
            }
        }
    }

    // Модальное окно для скачанных книг
    if (showDownloadedModal) {
        BooksListModal(
            title = "Скачанные",
            books = downloadedBooks,
            vm = vm,
            booksVm = booksVm,
            state = state,
            onDismiss = { showDownloadedModal = false },
            onBookSelected = { book ->
                vm.selectBook(book)
                showDownloadedModal = false
            }
        )
    }

    // Модальное окно для прослушанных книг
    if (showCompletedModal) {
        BooksListModal(
            title = "Прослушано",
            books = completedBooks,
            vm = vm,
            booksVm = booksVm,
            state = state,
            onDismiss = { showCompletedModal = false },
            onBookSelected = { book ->
                vm.selectBook(book)
                showCompletedModal = false
            }
        )
    }

    // Модальное окно для брошенных книг
    if (showDroppedModal) {
        BooksListModal(
            title = "Брошено",
            books = droppedBooks,
            vm = vm,
            booksVm = booksVm,
            state = state,
            onDismiss = { showDroppedModal = false },
            onBookSelected = { book ->
                vm.selectBook(book)
                showDroppedModal = false
            }
        )
    }

    // Модальное окно для всех моих книг
    if (showAllMyBooksModal) {
        BooksListModal(
            title = "Все мои",
            books = allMyBooks.filter { it.id != state.selectedBook?.id },
            vm = vm,
            booksVm = booksVm,
            state = state,
            onDismiss = { showAllMyBooksModal = false },
            onBookSelected = { book ->
                vm.selectBook(book)
                showAllMyBooksModal = false
            }
        )
    }

    // Модальное окно для серий
    if (showSeriesModal) {
        SeriesListModal(
            title = "Мои серии",
            series = allSeries,
            vm = vm,
            state = state,
            onDismiss = { showSeriesModal = false },
            onBookSelected = { book ->
                vm.selectBook(book)
                showSeriesModal = false
            }
        )
    }
}

@Composable
private fun LibraryCategoriesList(
    onCategoryClick: (String) -> Unit
) {
    val categories = listOf(
        "Добавленные" to (Icons.Default.Bookmark to 54),
        "Серии" to (Icons.Default.CollectionsBookmark to 14),
        "Подписки на авторов" to (Icons.Default.Person to 0),
        "Впечатления" to (Icons.Default.FavoriteBorder to 2),
        "Цитаты" to (Icons.Default.Star to 0),
        "Законченные" to (Icons.Default.CheckCircle to 109),
        "На устройстве" to (Icons.Default.CloudDownload to 20),
        "Все книги" to (Icons.AutoMirrored.Filled.MenuBook to 163)
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        categories.forEach { (name, iconCount) ->
            val (icon, count) = iconCount
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCategoryClick(name) }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FullScreenPlayerModal(
    vm: PlayerViewModel,
    state: PlayerUiState,
    onDismiss: () -> Unit,
    onSpeedClick: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        FullScreenPlayerContent(
            vm = vm,
            state = state,
            onDismiss = onDismiss,
            onSpeedClick = onSpeedClick
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullScreenPlayerContent(
    vm: PlayerViewModel,
    state: PlayerUiState,
    onDismiss: () -> Unit,
    onSpeedClick: () -> Unit
) {
    // Определяем порог высоты экрана (в dp)
    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp
    val compactHeightThreshold = 600 // порог, например, 600dp
    val isCompact = screenHeightDp < compactHeightThreshold

    // Вычисляем размеры кнопок в зависимости от высоты экрана
    val buttonLargeSize = if (isCompact) 36.dp else 48.dp
    val buttonMediumSize = if (isCompact) 32.dp else 40.dp
    val iconLargeSize = if (isCompact) 24.dp else 32.dp
    val iconMediumSize = if (isCompact) 20.dp else 24.dp
    val spacerSize = if (isCompact) 8.dp else 12.dp
    val fabSize = if (isCompact) 64.dp else 72.dp
    val fabIconSize = if (isCompact) 32.dp else 36.dp

    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showChaptersList by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val equalizerManager = vm.equalizerManager

    val selectedBook = state.selectedBook
    val selectedChapter = state.selectedChapter

    // Загружаем главы при открытии большого плеера, если они не загружены
    // И подготавливаем плеер, если глава выбрана, но плеер не готов
    LaunchedEffect(selectedBook?.id, selectedChapter?.id, state.duration) {
        if (selectedBook != null) {
            if (state.chapters.isEmpty()) {
                // Если книга выбрана, но главы не загружены - загружаем их
                vm.selectBook(selectedBook)
            } else if (selectedChapter != null && state.duration == 0L) {
                // Если глава выбрана, но плеер не подготовлен (duration = 0) - подготавливаем
                // Вызываем selectBook снова, чтобы он подготовил плеер для текущей главы
                // selectBook проверит, что главы уже загружены, и вызовет prepareChapterForPlayback
                vm.selectBook(selectedBook)
            }
        }
    }

    // Отслеживаем статус загрузки (должно быть на верхнем уровне)
    val downloadProgress by vm.downloadProgress.collectAsState()

    // Проверяем, скачана ли книга (обновляется при завершении/отмене загрузки)
    var isBookDownloaded by remember { mutableStateOf(false) }
    LaunchedEffect(selectedBook?.id, state.chapters, downloadProgress) {
        isBookDownloaded = if (selectedBook != null && state.chapters.isNotEmpty()) {
            vm.isBookDownloaded(selectedBook.id)
        } else {
            false
        }
    }

    // Получаем серию, если есть
    var series by remember { mutableStateOf<ru.fire_core.xauplayer.data.local.entities.Series?>(null) }
    LaunchedEffect(selectedBook?.seriesId) {
        series = if (selectedBook?.seriesId != null) {
            vm.getSeries(selectedBook.seriesId)
        } else {
            null
        }
    }

    // Вычисляем оставшееся время книги с учетом скорости (до конца ВСЕЙ книги)
    val remainingTime = remember(
        state.currentPosition,
        state.duration,
        state.chapters,
        selectedChapter,
        state.playbackSpeed
    ) {
        if (selectedChapter != null && state.chapters.isNotEmpty()) {
            val speed = state.playbackSpeed.coerceAtLeast(0.1f)
            val currentIndex = state.chapters.indexOfFirst { it.id == selectedChapter.id }
            if (currentIndex >= 0) {
                // Оставшееся время текущей главы
                val currentChapterRemaining = if (state.duration > 0) {
                    ((state.duration - state.currentPosition.coerceAtLeast(0L)).coerceAtLeast(0L) / speed).toLong()
                } else {
                    // Если длительность главы еще неизвестна, используем длительность из списка глав
                    ((selectedChapter.duration - state.currentPosition.coerceAtLeast(0L)).coerceAtLeast(0L) / speed).toLong()
                }
                
                // Длительность всех оставшихся глав (после текущей)
                val remainingChapters = state.chapters.subList(currentIndex + 1, state.chapters.size)
                val remainingChaptersDuration = remainingChapters.sumOf { (it.duration / speed).toLong() }
                
                currentChapterRemaining + remainingChaptersDuration
            } else {
                0L
            }
        } else {
            0L
        }
    }

    // Вычисляем, загружается ли книга
    val isBookDownloading = remember(selectedBook?.id, state.chapters, downloadProgress) {
        if (selectedBook == null || state.chapters.isEmpty()) false
        else state.chapters.any { ch ->
            val progress = downloadProgress[ch.id]
            progress?.state == ru.fire_core.xauplayer.download.DownloadState.DOWNLOADING
        }
    }
    
    val isBookQueued = remember(selectedBook?.id, state.chapters, downloadProgress) {
        if (selectedBook == null || state.chapters.isEmpty()) false
        else {
            val hasDownloading = state.chapters.any { ch ->
                val progress = downloadProgress[ch.id]
                progress?.state == ru.fire_core.xauplayer.download.DownloadState.DOWNLOADING
            }
            // Показываем QUEUED только если нет активных загрузок, но есть главы в очереди
            !hasDownloading && state.chapters.any { ch ->
                val progress = downloadProgress[ch.id]
                progress?.state == ru.fire_core.xauplayer.download.DownloadState.QUEUED
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            // Заголовок с кнопками (фиксированный сверху)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(align = Alignment.CenterVertically)
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Кнопка назад (стрелка вниз)
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Назад",
                        tint = Color.White
                    )
                }

                // Название книги и автор
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = selectedBook?.title ?: "Выберите книгу",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    selectedBook?.author?.let { author ->
                        Text(
                            text = author,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Кнопки справа: скачать/удалить (одна кнопка, меняет иконку), три точки
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Кнопка скачать/удалить (одна кнопка, меняет иконку в зависимости от статуса)
                    if (selectedBook != null) {
                        when {
                            isBookDownloading || isBookQueued -> {
                                val progressData = remember(downloadProgress, state.chapters, selectedBook.id, selectedBook.totalSizeBytes) {
                                    if (state.chapters.isEmpty()) {
                                        Triple(0f, 0L, 0L)
                                    } else {
                                        val totalDownloaded = state.chapters.sumOf { ch ->
                                            val p = downloadProgress[ch.id]
                                            when (p?.state) {
                                                ru.fire_core.xauplayer.download.DownloadState.COMPLETED ->
                                                    p.totalBytes.takeIf { it > 0 } ?: (ch.fileSizeBytes ?: 0L)
                                                else -> p?.downloadedBytes ?: 0L
                                            }
                                        }
                                        var totalSize = selectedBook.totalSizeBytes ?: 0L
                                        if (totalSize == 0L) {
                                            totalSize = state.chapters.sumOf { ch ->
                                                downloadProgress[ch.id]?.totalBytes?.takeIf { it > 0 }
                                                    ?: (ch.fileSizeBytes ?: 0L)
                                            }
                                        }
                                        val progress = if (totalSize > 0) {
                                            (totalDownloaded.toFloat() / totalSize.toFloat()).coerceIn(0f, 1f)
                                        } else 0f
                                        Triple(progress, totalDownloaded, totalSize)
                                    }
                                }
                                val (progress, totalDownloaded, totalSize) = progressData
                                val downloadedMB = (totalDownloaded.toDouble() / ru.fire_core.xauplayer.core.config.AppConfig.BYTES_PER_MB).toInt()
                                val totalMB = (totalSize.toDouble() / ru.fire_core.xauplayer.core.config.AppConfig.BYTES_PER_MB).toInt()
                                val label = when {
                                    isBookDownloading && totalMB > 0 -> "$downloadedMB/$totalMB Мб"
                                    isBookDownloading -> "Загрузка..."
                                    totalMB > 0 -> "$totalMB Мб"
                                    else -> "В очереди"
                                }
                                DownloadProgressControl(
                                    label = label,
                                    progress = if (isBookDownloading && totalSize > 0) progress else null,
                                    onCancel = {
                                        scope.launch { vm.cancelBookDownload(selectedBook.id) }
                                    },
                                    labelColor = Color.White,
                                    ringColor = Color.White,
                                    iconTint = Color.White,
                                    ringSize = 36.dp,
                                    buttonSize = 28.dp
                                )
                            }
                            else -> {
                                IconButton(
                                    onClick = {
                                        if (isBookDownloaded) {
                                            scope.launch {
                                                vm.deleteBook(selectedBook.id)
                                                isBookDownloaded = vm.isBookDownloaded(selectedBook.id)
                                            }
                                        } else {
                                            vm.downloadBook(selectedBook.id)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isBookDownloaded) Icons.Default.Delete else Icons.Default.Download,
                                        contentDescription = if (isBookDownloaded) "Удалить с устройства" else "Скачать на устройство",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }

                    // Три точки (меню)
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Еще",
                                tint = Color.White
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            if (selectedBook?.seriesId != null) {
                                DropdownMenuItem(
                                    text = { Text("Скачать всю серию") },
                                    onClick = {
                                        showMoreMenu = false
                                        vm.downloadSeries(selectedBook.seriesId!!)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Эквалайзер") },
                                onClick = {
                                    showMoreMenu = false
                                    showEqualizerDialog = true
                                }
                            )
                        }
                    }
                }
            }

            // Центральная область с обложкой и кнопками управления
            if (selectedBook != null) {
                val bookCoverUrl = rememberBookCoverUrl(selectedBook, vm)
                
                // Вычисляем, нужно ли размещать кнопки поверх обложки
                // Используем onSizeChanged для определения доступного пространства
                val density = LocalDensity.current
                var availableHeight by remember { mutableStateOf(0) }
                var needsOverlayLayout by remember { mutableStateOf(false) }
                
                Box(
                    modifier = Modifier
                        .weight(2f) // 2/3 экрана для содержимого
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            availableHeight = size.height
                            // Проверяем, влезают ли все элементы с учетом отступа снизу (80dp)
                            val bottomPaddingDp = 80.dp
                            val bottomPaddingPx = with(density) { bottomPaddingDp.toPx().toInt() }
                            val controlsHeight = 200 // Примерная высота всех элементов управления
                            val additionalControlsHeight = 100 // Высота дополнительных элементов
                            needsOverlayLayout = (availableHeight - bottomPaddingPx) < (controlsHeight + additionalControlsHeight)
                        }
                ) {
                    if (needsOverlayLayout) {
                        // Компактный layout: кнопки поверх обложки
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 80.dp), // Отступ снизу для удобной прокрутки
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Обложка
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = bookCoverUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight()
                                        .clip(MaterialTheme.shapes.medium),
                                    contentScale = ContentScale.Fit
                                )
                                
                                // Кнопки управления поверх обложки
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .background(Color.Black.copy(alpha = 0.3f), MaterialTheme.shapes.medium)
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                    // --- Обернуть каждую кнопку ---
                    // Переход к предыдущей главе (изогнутая стрелка назад)
                    Box { // Обернули
                        IconButton(
                            onClick = {
                                if (selectedChapter != null && state.chapters.isNotEmpty()) {
                                    val currentIndex =
                                        state.chapters.indexOfFirst { it.id == selectedChapter.id }
                                    if (currentIndex > 0) {
                                        val prevChapter = state.chapters[currentIndex - 1]
                                        vm.playChapter(prevChapter, selectedBook)
                                    }
                                }
                            },
                            modifier = Modifier.size(buttonLargeSize) // Используем динамический размер
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Предыдущая глава",
                                tint = Color.White,
                                modifier = Modifier.size(iconLargeSize) // Используем динамический размер
                            )
                        }
                    } // /Обернули
                    Spacer(modifier = Modifier.width(spacerSize)) // Используем динамический размер
                    // Перемотка назад
                    Box { // Обернули
                        IconButton(
                            onClick = { vm.rewind() },
                            modifier = Modifier.size(buttonMediumSize) // Используем динамический размер
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Replay,
                                contentDescription = "Перемотка назад",
                                tint = Color.White,
                                modifier = Modifier.size(iconMediumSize) // Используем динамический размер
                            )
                        }
                    } // /Обернули
                    Spacer(modifier = Modifier.width(if (screenHeightDp < compactHeightThreshold) 12.dp else 16.dp)) // Центральная зазор может быть чуть больше
                    // Кнопка Play/Pause (большая по центру)
                    Box { // Обернули
                        FloatingActionButton(
                            onClick = {
                                if (state.isPlaying) {
                                    vm.pause()
                                } else {
                                    vm.resume()
                                }
                            },
                            modifier = Modifier.size(fabSize), // Используем динамический размер
                            containerColor = Color.White.copy(alpha = 0.2f)
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state.isPlaying) "Пауза" else "Воспроизведение",
                                tint = Color.White,
                                modifier = Modifier.size(fabIconSize) // Используем динамический размер
                            )
                        }
                    } // /Обернули
                    Spacer(modifier = Modifier.width(if (screenHeightDp < compactHeightThreshold) 12.dp else 16.dp)) // Центральная зазор может быть чуть больше
                    // Перемотка вперед
                    Box { // Обернули
                        IconButton(
                            onClick = { vm.forward() },
                            modifier = Modifier.size(buttonMediumSize) // Используем динамический размер
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Replay,
                                contentDescription = "Перемотка вперед",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(iconMediumSize) // Используем динамический размер
                                    .scale(-1F, 1F)
                            )
                        }
                    } // /Обернули
                    Spacer(modifier = Modifier.width(spacerSize)) // Используем динамический размер
                    // Переход к следующей главе (изогнутая стрелка вперед)
                    Box { // Обернули
                        IconButton(
                            onClick = {
                                if (selectedChapter != null && state.chapters.isNotEmpty()) {
                                    val currentIndex =
                                        state.chapters.indexOfFirst { it.id == selectedChapter.id }
                                    if (currentIndex < state.chapters.size - 1) {
                                        val nextChapter = state.chapters[currentIndex + 1]
                                        vm.playChapter(nextChapter, selectedBook)
                                    }
                                }
                            },
                            modifier = Modifier.size(buttonLargeSize) // Используем динамический размер
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Следующая глава",
                                tint = Color.White,
                                modifier = Modifier.size(iconLargeSize) // Используем динамический размер
                            )
                        }
                    } // /Обернули
                                }
                                
                                // Дополнительные элементы под обложкой (только в компактном режиме)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Таймер сна (слева)
                                        IconButton(
                                            onClick = { showSleepTimerDialog = true },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Timer,
                                                contentDescription = "Таймер сна",
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        // Выбор главы по центру — не растягивается на всю ширину!
                                        if (selectedChapter != null && state.chapters.isNotEmpty()) {
                                            OutlinedButton(
                                                onClick = { showChaptersList = true },
                                                modifier = Modifier
                                                    .weight(1f) // ← растягивается только на свободное пространство
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                                    brush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.5f))
                                                )
                                            ) {
                                                Text(
                                                    text = selectedChapter.title,
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "Открыть список глав",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f)) // если глав нет — оставляем пустое место
                                        }

                                        // Скорость (справа)
                                        Text(
                                            text = "${"%.2f".format(state.playbackSpeed)}x",
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier
                                                .combinedClickable(
                                                    onClick = { /* ... */ },
                                                    onLongClick = { showSpeedDialog = true }
                                                )
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                    
                                    // Прогресс-бар под обложкой
                                    if (state.duration > 0) {
                                        val currentPos = state.currentPosition.coerceIn(0, state.duration)
                                        val progress = if (state.duration > 0) currentPos.toFloat() / state.duration.toFloat() else 0f
                                        val speed = state.playbackSpeed.coerceAtLeast(0.1f)
                                        val realCurrentTime = (currentPos / speed).toLong()
                                        val remaining = ((state.duration - currentPos) / speed).toLong()

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = formatTime(realCurrentTime),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "-${formatTime(remaining)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White
                                            )
                                        }

                                        Slider(
                                            value = progress,
                                            onValueChange = { newProgress ->
                                                val newPosition = (newProgress * state.duration).toLong()
                                                vm.seekTo(newPosition)
                                            },
                                            valueRange = 0f..1f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = Color.White,
                                                activeTrackColor = Color.White,
                                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Обычный layout: все элементы по порядку
                        Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = 80.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Обложка
                                AsyncImage(
                                    model = bookCoverUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(0.6f)
                                        .wrapContentHeight(Alignment.CenterVertically)
                                        .clip(MaterialTheme.shapes.medium),
                                    contentScale = ContentScale.Fit
                                )

                                // Оставшееся время для всей книги
                                if (remainingTime > 0) {
                                    Text(
                                        text = formatRemainingTime(remainingTime),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                                
                                // Кнопки управления в стиле Яндекс Книги
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight(align = Alignment.CenterVertically)
                                        .padding(vertical = 16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Центральные кнопки управления
                                    // Глава назад
                                    if (selectedChapter != null && state.chapters.isNotEmpty()) {
                                        val currentIndex = state.chapters.indexOfFirst { it.id == selectedChapter.id }
                                        if (currentIndex > 0) {
                                            IconButton(
                                                onClick = {
                                                    val prevChapter = state.chapters[currentIndex - 1]
                                                    vm.playChapter(prevChapter, selectedBook)
                                                },
                                                modifier = Modifier.size(buttonMediumSize)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.SkipPrevious,
                                                    contentDescription = "Предыдущая глава",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(iconMediumSize)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(if (screenHeightDp < compactHeightThreshold) 8.dp else 12.dp))
                                        }
                                    }
                                    
                                    // Перемотка назад 15 сек
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = { vm.rewind() },
                                            modifier = Modifier.size(buttonMediumSize)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Replay,
                                                contentDescription = "Перемотка назад",
                                                tint = Color.White,
                                                modifier = Modifier.size(iconMediumSize)
                                            )
                                        }
                                        Text(
                                            text = "15",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(if (screenHeightDp < compactHeightThreshold) 12.dp else 16.dp))
                                    
                                    // Кнопка Play/Pause
                                    FloatingActionButton(
                                        onClick = {
                                            if (state.isPlaying) vm.pause() else vm.resume()
                                        },
                                        modifier = Modifier.size(fabSize),
                                        containerColor = Color.White.copy(alpha = 0.2f)
                                    ) {
                                        Icon(
                                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (state.isPlaying) "Пауза" else "Воспроизведение",
                                            tint = Color.White,
                                            modifier = Modifier.size(fabIconSize)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(if (screenHeightDp < compactHeightThreshold) 12.dp else 16.dp))
                                    
                                    // Перемотка вперед 30 сек
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = { vm.forward() },
                                            modifier = Modifier.size(buttonMediumSize)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Replay,
                                                contentDescription = "Перемотка вперед",
                                                tint = Color.White,
                                                modifier = Modifier.size(iconMediumSize).scale(-1F, 1F)
                                            )
                                        }
                                        Text(
                                            text = "30",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                    
                                    // Глава вперед
                                    if (selectedChapter != null && state.chapters.isNotEmpty()) {
                                        val currentIndex = state.chapters.indexOfFirst { it.id == selectedChapter.id }
                                        if (currentIndex < state.chapters.size - 1) {
                                            Spacer(modifier = Modifier.width(if (screenHeightDp < compactHeightThreshold) 8.dp else 12.dp))
                                            IconButton(
                                                onClick = {
                                                    val nextChapter = state.chapters[currentIndex + 1]
                                                    vm.playChapter(nextChapter, selectedBook)
                                                },
                                                modifier = Modifier.size(buttonMediumSize)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.SkipNext,
                                                    contentDescription = "Следующая глава",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(iconMediumSize)
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                // Кнопки скорости и таймера сна под управлением
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Таймер сна (первым)
                                    IconButton(
                                        onClick = { showSleepTimerDialog = true },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Timer,
                                            contentDescription = "Таймер сна",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(32.dp))
                                    
                                    // Информация о главе (кнопка для открытия меню)
                                if (selectedChapter != null && state.chapters.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { showChaptersList = true },
                                        modifier = Modifier
                                            .weight(1f) // Используем weight вместо fillMaxWidth для правильного распределения пространства
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color.White
                                        ),
                                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                            brush = androidx.compose.ui.graphics.SolidColor(
                                                Color.White.copy(alpha = 0.5f)
                                            )
                                        )
                                    ) {
                                        Text(
                                            text = selectedChapter.title,
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Открыть список глав",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f)) // Если глав нет — оставляем пустое место
                                }
                                    
                                    Spacer(modifier = Modifier.width(32.dp))
                                    
                                    // Скорость (последний)
                                    TextButton(
                                        onClick = {
                                            val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                                            val exactIndex = speeds.indexOf(state.playbackSpeed)
                                            val nextSpeed = if (exactIndex >= 0) {
                                                speeds[(exactIndex + 1) % speeds.size]
                                            } else {
                                                val nextIndex = speeds.indexOfFirst { it > state.playbackSpeed }
                                                if (nextIndex >= 0) speeds[nextIndex] else speeds[0]
                                            }
                                            vm.setSpeed(nextSpeed)
                                        }
                                    ) {
                                        Text(
                                            text = "${"%.2f".format(state.playbackSpeed)}x",
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.combinedClickable(
                                                onClick = {},
                                                onLongClick = { showSpeedDialog = true }
                                            )
                                        )
                                    }
                                }
                                
                                
                                
                                // Прогресс-бар
                                if (state.duration > 0) {
                                    val currentPos = state.currentPosition.coerceIn(0, state.duration)
                                    val progress = if (state.duration > 0) currentPos.toFloat() / state.duration.toFloat() else 0f
                                    val speed = state.playbackSpeed.coerceAtLeast(0.1f)
                                    val realCurrentTime = (currentPos / speed).toLong()
                                    val remaining = ((state.duration - currentPos) / speed).toLong()
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(formatTime(realCurrentTime), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                        Text("-${formatTime(remaining)}", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                    }
                                    Slider(
                                        value = progress,
                                        onValueChange = { vm.seekTo((it * state.duration).toLong()) },
                                        valueRange = 0f..1f,
                                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(2f))
                    Text(
                        text = "Выберите книгу для воспроизведения",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.weight(1f)) // 1/3 пустоты снизу
                }
            }
            // Пустота снизу (1/3 экрана)
            Spacer(modifier = Modifier.height(1.dp))
        }

        // Диалог таймера сна
        if (showSleepTimerDialog) {
            SleepTimerDialog(
                onDismiss = { showSleepTimerDialog = false },
                onSetTimer = { durationMs ->
                    vm.setSleepTimer(durationMs)
                    showSleepTimerDialog = false
                }
            )
        }

        // Список глав
        val currentSelectedBook = state.selectedBook
        if (showChaptersList && currentSelectedBook != null) {
            ChaptersListDialog(
                chapters = state.chapters,
                selectedChapter = state.selectedChapter,
                bookId = currentSelectedBook.id,
                playbackSpeed = state.playbackSpeed,
                vm = vm,
                onDismiss = { showChaptersList = false },
                onChapterSelected = { chapter ->
                    vm.playChapter(chapter, currentSelectedBook)
                    showChaptersList = false
                }
            )
        }

        // Диалог выбора скорости с ползунком
        if (showSpeedDialog) {
            SpeedSliderDialog(
                currentSpeed = state.playbackSpeed,
                onSpeedSelected = { speed ->
                    vm.setSpeed(speed)
                    showSpeedDialog = false
                },
                onDismiss = { showSpeedDialog = false }
            )
        }
        
        // Диалог эквалайзера
        if (showEqualizerDialog) {
            EqualizerDialog(
                equalizerManager = equalizerManager,
                vm = vm,
                onDismiss = { showEqualizerDialog = false }
            )
        }
    }

@Composable
fun SpeedSliderDialog(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var speed by remember(currentSpeed) { mutableStateOf(currentSpeed) }
    val minSpeed = 0.5f
    val maxSpeed = 3.0f
    val step = 0.05f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите скорость воспроизведения") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Отображаем текущую скорость
                Text(
                    text = "${"%.2f".format(speed)}x",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                // Ползунок
                Slider(
                    value = speed,
                    onValueChange = { speed = it },
                    valueRange = minSpeed..maxSpeed,
                    steps = ((maxSpeed - minSpeed) / step).toInt() - 1,
                    modifier = Modifier.fillMaxWidth()
                )

                // Быстрые значения
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f).forEach { presetSpeed ->
                        TextButton(
                            onClick = { speed = presetSpeed },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (speed == presetSpeed) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        ) {
                            Text("${"%.2f".format(presetSpeed)}x")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSpeedSelected(speed) }) {
                Text("Применить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun EqualizerDialog(
    equalizerManager: ru.fire_core.xauplayer.player.EqualizerManager,
    vm: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val numberOfBands = remember { equalizerManager.getNumberOfBands() }
    val bandLevelRange = remember { equalizerManager.getBandLevelRange() }
    val isEnabled = remember { equalizerManager.isEnabled() }
    var enabled by remember(isEnabled) { mutableStateOf(isEnabled) }
    
    val bandLevels = remember(numberOfBands) {
        (0 until numberOfBands).map { band ->
            mutableStateOf(equalizerManager.getBandLevel(band))
        }
    }
    
    LaunchedEffect(Unit) {
        // Загружаем текущие значения при открытии
        for (i in 0 until numberOfBands) {
            bandLevels[i].value = equalizerManager.getBandLevel(i)
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Эквалайзер") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Переключатель включения/выключения
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Включить эквалайзер")
                    Switch(
                        checked = enabled,
                        onCheckedChange = { newValue ->
                            enabled = newValue
                            equalizerManager.setEnabled(newValue)
                            scope.launch {
                                equalizerManager.saveSettings()
                            }
                        }
                    )
                }
                
                if (numberOfBands > 0 && enabled) {
                    HorizontalDivider()
                    
                    // Полосы эквалайзера
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        items(numberOfBands) { band ->
                            Column(
                                modifier = Modifier
                                    .width(60.dp)
                                    .fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Частота
                                val centerFreq = equalizerManager.getCenterFreq(band)
                                val freqText = when {
                                    centerFreq >= ru.fire_core.xauplayer.core.config.AppConfig.EQUALIZER_MIN_FREQ_DISPLAY -> "${centerFreq / ru.fire_core.xauplayer.core.config.AppConfig.MILLISECONDS_PER_SECOND.toInt()}k"
                                    else -> "${centerFreq}"
                                }
                                Text(
                                    text = freqText,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.height(20.dp)
                                )
                                
                                // Вертикальный слайдер
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                ) {
                                    Slider(
                                        value = bandLevels[band].value.toFloat(),
                                        onValueChange = { newValue ->
                                            val intValue = newValue.toInt()
                                            bandLevels[band].value = intValue
                                            equalizerManager.setBandLevel(band, intValue)
                                        },
                                        valueRange = bandLevelRange.first.toFloat()..bandLevelRange.second.toFloat(),
                                        modifier = Modifier
                                            .rotate(-90f)
                                            .fillMaxHeight()
                                            .width(200.dp),
                                        colors = SliderDefaults.colors()
                                    )
                                }
                                
                                // Значение в дБ
                                Text(
                                    text = "${bandLevels[band].value / 100}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.height(20.dp)
                                )
                            }
                        }
                    }
                    
                    HorizontalDivider()
                    
                    // Кнопка сброса
                    TextButton(
                        onClick = {
                            for (i in 0 until numberOfBands) {
                                equalizerManager.setBandLevel(i, 0)
                                bandLevels[i].value = 0
                            }
                            scope.launch {
                                equalizerManager.saveSettings()
                            }
                        }
                    ) {
                        Text("Сбросить")
                    }
                } else if (!enabled) {
                    Text(
                        text = "Включите эквалайзер для настройки",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Эквалайзер недоступен",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    equalizerManager.saveSettings()
                }
                onDismiss()
            }) {
                Text("Готово")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun ChapterItemInDialog(
    chapter: Chapter,
    selectedChapter: Chapter?,
    bookId: Long,
    playbackSpeed: Float,
    vm: PlayerViewModel,
    onChapterSelected: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isChapterDownloaded by remember { mutableStateOf(false) }
    val downloadProgress = vm.downloadProgress.collectAsState()
    val chapterProgress = downloadProgress.value[chapter.id]
    val isChapterDownloading = chapterProgress?.state == ru.fire_core.xauplayer.download.DownloadState.DOWNLOADING
    val isChapterQueued = chapterProgress?.state == ru.fire_core.xauplayer.download.DownloadState.QUEUED
    
    LaunchedEffect(chapter.id, chapterProgress?.state) {
        isChapterDownloaded = vm.isChapterDownloaded(chapter.id)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChapterSelected() },
        colors = CardDefaults.cardColors(
            containerColor = if (selectedChapter?.id == chapter.id) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selectedChapter?.id == chapter.id) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = formatTime((chapter.duration / playbackSpeed.coerceAtLeast(0.1f)).toLong()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isChapterDownloaded && isChapterQueued) {
                    DownloadProgressControl(
                        label = "В очереди",
                        progress = null,
                        onCancel = {
                            scope.launch { vm.removeChapterFromQueue(chapter.id) }
                        },
                        ringSize = 32.dp,
                        buttonSize = 24.dp,
                        strokeWidth = 2.dp
                    )
                } else if (!isChapterDownloaded && isChapterDownloading) {
                    val progress = remember(chapterProgress) {
                        if (chapterProgress != null && chapterProgress.totalBytes > 0) {
                            (chapterProgress.downloadedBytes.toFloat() / chapterProgress.totalBytes.toFloat())
                                .coerceIn(0f, 1f)
                        } else null
                    }
                    val label = remember(chapterProgress) {
                        if (chapterProgress != null && chapterProgress.totalBytes > 0) {
                            val downloadedMB = ru.fire_core.xauplayer.core.config.AppConfig.bytesToMB(chapterProgress.downloadedBytes)
                            val totalMB = ru.fire_core.xauplayer.core.config.AppConfig.bytesToMB(chapterProgress.totalBytes)
                            "${String.format("%.1f", downloadedMB)}/${String.format("%.1f", totalMB)} Мб"
                        } else {
                            "Загрузка..."
                        }
                    }
                    DownloadProgressControl(
                        label = label,
                        progress = progress,
                        onCancel = {
                            scope.launch { vm.cancelChapterDownload(chapter.id) }
                        },
                        ringSize = 32.dp,
                        buttonSize = 24.dp,
                        strokeWidth = 2.dp
                    )
                } else {
                    // Кнопка скачать/удалить (отдельная кнопка, не вызывает переход)
                    IconButton(
                        onClick = {
                            scope.launch {
                                if (isChapterDownloaded) {
                                    vm.deleteDownloadedChapter(chapter.id)
                                    // Обновляем статус после удаления
                                    kotlinx.coroutines.delay(ru.fire_core.xauplayer.core.config.AppConfig.ANIMATION_DELAY_MS)
                                    isChapterDownloaded = vm.isChapterDownloaded(chapter.id)
                                } else {
                                    vm.downloadChapter(chapter, bookId)
                                    // Обновляем статус после начала загрузки
                                    kotlinx.coroutines.delay(ru.fire_core.xauplayer.core.config.AppConfig.MEDIUM_DELAY_MS)
                                    isChapterDownloaded = vm.isChapterDownloaded(chapter.id)
                                }
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isChapterDownloaded) Icons.Default.Delete else Icons.Default.Download,
                            contentDescription = if (isChapterDownloaded) "Удалить с устройства" else "Скачать на устройство",
                            tint = if (isChapterDownloaded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Индикатор текущей главы
                if (selectedChapter?.id == chapter.id) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Текущая глава",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChaptersListDialog(
    chapters: List<Chapter>,
    selectedChapter: Chapter?,
    bookId: Long,
    playbackSpeed: Float,
    vm: PlayerViewModel,
    onDismiss: () -> Unit,
    onChapterSelected: (Chapter) -> Unit
) {
    val currentIndex = remember(selectedChapter, chapters) {
        if (selectedChapter != null) {
            chapters.indexOfFirst { it.id == selectedChapter.id }
        } else {
            -1
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Главы")
                // Кнопки переключения глав
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (currentIndex > 0) {
                                onChapterSelected(chapters[currentIndex - 1])
                            }
                        },
                        enabled = currentIndex > 0
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Предыдущая глава"
                        )
                    }
                    IconButton(
                        onClick = {
                            if (currentIndex >= 0 && currentIndex < chapters.size - 1) {
                                onChapterSelected(chapters[currentIndex + 1])
                            }
                        },
                        enabled = currentIndex >= 0 && currentIndex < chapters.size - 1
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Следующая глава"
                        )
                    }
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chapters) { chapter ->
                    ChapterItemInDialog(
                        chapter = chapter,
                        selectedChapter = selectedChapter,
                        bookId = bookId,
                        playbackSpeed = playbackSpeed,
                        vm = vm,
                        onChapterSelected = { onChapterSelected(chapter) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

@Composable
private fun SleepTimerDialog(
    onDismiss: () -> Unit,
    onSetTimer: (Long) -> Unit
) {
    var customHours by remember { mutableStateOf(0) }
    var customMinutes by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Таймер сна") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Быстрый выбор:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                // Используем простой подход с Row и weight для автоматического переноса
                // Если кнопки не помещаются, они автоматически переносятся
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15, 30, 60, 120).forEach { minutes ->
                        OutlinedButton(
                            onClick = {
                                onSetTimer(ru.fire_core.xauplayer.core.config.AppConfig.secondsToMs((minutes * ru.fire_core.xauplayer.core.config.AppConfig.SECONDS_PER_MINUTE).toLong()))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = when (minutes) {
                                    15 -> "15 мин"
                                    30 -> "30 мин"
                                    60 -> "1 час"
                                    120 -> "2 часа"
                                    else -> "${minutes} мин"
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Свое время:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                // Барабаны для часов и минут
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Барабан для часов
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Часы",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        WheelPicker(
                            items = (0..23).toList(),
                            selectedItem = customHours,
                            onItemSelected = { customHours = it },
                            modifier = Modifier.height(120.dp)
                        )
                    }

                    Text(
                        text = ":",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Барабан для минут
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Минуты",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        WheelPicker(
                            items = (0..59).toList(),
                            selectedItem = customMinutes,
                            onItemSelected = { customMinutes = it },
                            modifier = Modifier.height(120.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val totalMinutes = customHours * 60L + customMinutes
                    if (totalMinutes > 0) {
                        onSetTimer(ru.fire_core.xauplayer.core.config.AppConfig.secondsToMs(totalMinutes * ru.fire_core.xauplayer.core.config.AppConfig.SECONDS_PER_MINUTE))
                    }
                }
            ) {
                Text("Установить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ru.fire_core.xauplayer.core.config.AppConfig.msToSeconds(ms)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

private fun formatRemainingTime(ms: Long): String {
    val totalSeconds = ru.fire_core.xauplayer.core.config.AppConfig.msToSeconds(ms)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) {
        "$hours ч $minutes мин до конца книги"
    } else {
        "$minutes мин до конца книги"
    }
}

// Компонент барабана для выбора времени
@Composable
private fun WheelPicker(
    items: List<Int>,
    selectedItem: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = items.indexOf(selectedItem).coerceAtLeast(0)
    )

    // Прокрутка к выбранному элементу при изменении
    LaunchedEffect(selectedItem) {
        val index = items.indexOf(selectedItem)
        if (index >= 0 && index < items.size) {
            listState.scrollToItem(index)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 40.dp)
        ) {
            items(items.size) { index ->
                val item = items[index]
                val isSelected = item == selectedItem

                Text(
                    text = item.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    },
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onItemSelected(item) }
                        .padding(vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Индикатор выбранного элемента
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.Center)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    RoundedCornerShape(4.dp)
                )
        )
    }
}

@Composable
private fun BooksListModal(
    title: String,
    books: List<Book>,
    vm: PlayerViewModel,
    booksVm: ru.fire_core.xauplayer.ui.viewmodel.BooksViewModel? = null,
    state: PlayerUiState,
    onDismiss: () -> Unit,
    onBookSelected: (Book) -> Unit
) {
    // Загружаем теги для всех книг при открытии модального окна
    LaunchedEffect(books, booksVm) {
        if (booksVm != null) {
            books.forEach { book ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        booksVm.getBookTags(book.id)
                    } catch (e: Exception) {
                        // Игнорируем ошибки
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Заголовок с кнопкой закрытия
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }

                HorizontalDivider()

                // Список книг
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(books) { book ->
                        BookRowModal(
                            book = book,
                            isSelected = state.selectedBook?.id == book.id,
                            vm = vm,
                            booksVm = booksVm,
                            onClick = { onBookSelected(book) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookRowModal(
    book: Book,
    isSelected: Boolean,
    vm: PlayerViewModel,
    booksVm: ru.fire_core.xauplayer.ui.viewmodel.BooksViewModel? = null,
    onClick: () -> Unit
) {
    val coverUrl = rememberBookCoverUrl(book, vm)

    // Загружаем теги при первом отображении
    var bookTags by remember {
        mutableStateOf<List<ru.fire_core.xauplayer.data.local.entities.Tag>>(
            emptyList()
        )
    }
    LaunchedEffect(book.id, booksVm) {
        if (booksVm != null) {
            bookTags = booksVm.getBookTags(book.id)
        }
    }

    // Также получаем теги из состояния, если они уже загружены
    val booksState by booksVm?.uiState?.collectAsState() ?: remember {
        kotlinx.coroutines.flow.MutableStateFlow(ru.fire_core.xauplayer.ui.viewmodel.BooksUiState())
    }.collectAsState()

    val tagsFromState = remember(book.id, booksState.bookTags) {
        booksVm?.let { booksState.bookTags[book.id] ?: emptyList() } ?: emptyList()
    }

    // Используем теги из состояния, если они есть, иначе используем загруженные
    val finalTags = if (tagsFromState.isNotEmpty()) tagsFromState else bookTags

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                contentScale = ContentScale.Fit
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                book.author?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Отображение тегов
                if (finalTags.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        finalTags.take(2).forEach { tag ->
                            TagChip(tag = tag)
                        }
                        if (finalTags.size > 2) {
                            Text(
                                text = "+${finalTags.size - 2}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesListModal(
    title: String,
    series: List<ru.fire_core.xauplayer.ui.viewmodel.SeriesWithBooks>,
    vm: PlayerViewModel,
    state: PlayerUiState,
    onDismiss: () -> Unit,
    onBookSelected: (Book) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Заголовок с кнопкой закрытия
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }

                HorizontalDivider()

                // Список серий
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(series) { seriesWithBooks ->
                        SeriesRowModal(
                            seriesWithBooks = seriesWithBooks,
                            vm = vm,
                            state = state,
                            onBookSelected = onBookSelected
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesRowModal(
    seriesWithBooks: ru.fire_core.xauplayer.ui.viewmodel.SeriesWithBooks,
    vm: PlayerViewModel,
    state: PlayerUiState,
    onBookSelected: (Book) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var seriesCoverUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(seriesWithBooks.series.id, seriesWithBooks.books) {
        seriesCoverUrl = vm.getSeriesCoverUrl(seriesWithBooks.series.id, seriesWithBooks.books)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            // Заголовок серии
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    seriesCoverUrl?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Column {
                        Text(
                            text = seriesWithBooks.series.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        seriesWithBooks.series.description?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${seriesWithBooks.books.size} книг",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { vm.downloadSeries(seriesWithBooks.series.id) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Скачать всю серию"
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Свернуть" else "Развернуть"
                    )
                }
            }

            // Книги серии
            if (isExpanded) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    seriesWithBooks.books.forEach { book ->
                        BookRowModal(
                            book = book,
                            isSelected = state.selectedBook?.id == book.id,
                            vm = vm,
                            onClick = { onBookSelected(book) }
                        )
                    }
                }
            }
        }
    }
}

