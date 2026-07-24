package ru.fire_core.xauplayer.ui.screens.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import ru.fire_core.xauplayer.data.local.entities.Book
import ru.fire_core.xauplayer.data.local.entities.Series
import ru.fire_core.xauplayer.data.local.entities.Tag
import ru.fire_core.xauplayer.ui.viewmodel.BooksViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BooksTab(
    modifier: Modifier = Modifier, 
    vm: BooksViewModel = hiltViewModel(),
    playerVm: ru.fire_core.xauplayer.ui.viewmodel.PlayerViewModel = hiltViewModel(),
    onBookSelected: (Book) -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val playerState by playerVm.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { vm.load() }
    
    // Состояние для модального окна тегов
    var showTagDialog by remember { mutableStateOf(false) }
    var selectedBookForTags by remember { mutableStateOf<Book?>(null) }
    var selectedSeriesForTags by remember { mutableStateOf<Series?>(null) }

    // Поиск и страницы просмотра книги/серии
    var searchQuery by remember { mutableStateOf("") }
    var bookForDetail by remember { mutableStateOf<Book?>(null) }
    var seriesForDetail by remember { mutableStateOf<ru.fire_core.xauplayer.ui.viewmodel.SeriesWithBooks?>(null) }

    // Получаем список ID книг с прогрессом
    val booksWithProgress = playerState.books.map { it.id }.toSet()

    // Фильтрация по поисковому запросу
    val q = searchQuery.trim().lowercase()
    val mySeriesFiltered = state.mySeries.filter { seriesMatches(it, q) }
    val librarySeriesFiltered = state.librarySeries.filter { seriesMatches(it, q) }
    val booksWithoutSeriesFiltered = state.booksWithoutSeries.filter { bookMatches(it, q) }
    val nothingFound = mySeriesFiltered.isEmpty() && librarySeriesFiltered.isEmpty() && booksWithoutSeriesFiltered.isEmpty()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // Поиск по книгам и сериям
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Поиск книг и серий") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Очистить")
                    }
                }
            }
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Показываем ошибку если есть
        if (state.hasNetworkError && !state.loading) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = state.errorMessage ?: "Нет доступа к серверу",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Button(
                        onClick = { vm.load() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Попробовать снова")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Список серий и книг
        Box(modifier = Modifier.weight(1f)) {
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (nothingFound && !state.hasNetworkError) {
                Text(
                    text = if (q.isNotEmpty()) "Ничего не найдено" else "Нет книг",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Секция "Мои серии"
                    if (mySeriesFiltered.isNotEmpty()) {
                        item {
                            Text(
                                text = "Мои серии",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(
                                    top = 16.dp,
                                    bottom = 8.dp
                                )
                            )
                        }
                        items(mySeriesFiltered) { seriesWithBooks ->
                            SeriesItem(
                                seriesWithBooks = seriesWithBooks,
                                selectedBookId = state.selectedBook?.id,
                                booksWithProgress = booksWithProgress,
                                bookStatuses = state.bookStatuses,
                                seriesTags = state.seriesTags[seriesWithBooks.series.id] ?: emptyList(),
                                bookTags = state.bookTags,
                                vm = vm,
                                onOpenSeries = { seriesForDetail = it },
                                onBookSelected = { book ->
                                    bookForDetail = book
                                },
                                onAddBook = { book ->
                                    playerVm.addBook(book)
                                },
                                onLongPress = { series ->
                                    selectedSeriesForTags = series
                                    showTagDialog = true
                                },
                                onBookLongPress = { book ->
                                    selectedBookForTags = book
                                    showTagDialog = true
                                },
                                playerVm = playerVm
                            )
                        }
                    }

                    // Секция "Библиотека"
                    if (librarySeriesFiltered.isNotEmpty()) {
                        item {
                            Text(
                                text = "Библиотека",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(
                                    top = 16.dp,
                                    bottom = 8.dp
                                )
                            )
                        }
                        items(librarySeriesFiltered) { seriesWithBooks ->
                            SeriesItem(
                                seriesWithBooks = seriesWithBooks,
                                selectedBookId = state.selectedBook?.id,
                                booksWithProgress = booksWithProgress,
                                bookStatuses = state.bookStatuses,
                                seriesTags = state.seriesTags[seriesWithBooks.series.id] ?: emptyList(),
                                bookTags = state.bookTags,
                                vm = vm,
                                onOpenSeries = { seriesForDetail = it },
                                onBookSelected = { book ->
                                    bookForDetail = book
                                },
                                onAddBook = { book ->
                                    playerVm.addBook(book)
                                },
                                onLongPress = { series ->
                                    selectedSeriesForTags = series
                                    showTagDialog = true
                                },
                                onBookLongPress = { book ->
                                    selectedBookForTags = book
                                    showTagDialog = true
                                },
                                playerVm = playerVm
                            )
                        }
                    }

                    // Секция "Книги вне серий"
                    if (booksWithoutSeriesFiltered.isNotEmpty()) {
                        item {
                            Text(
                                text = "Книги вне серий",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                            )
                        }
                        items(booksWithoutSeriesFiltered) { book ->
                            BookRowWithStatus(
                                book = book,
                                isSelected = state.selectedBook?.id == book.id,
                                hasProgress = booksWithProgress.contains(book.id),
                                tags = state.bookTags[book.id] ?: emptyList(),
                                vm = vm,
                                onClick = {
                                    bookForDetail = book
                                },
                                onAddBook = {
                                    playerVm.addBook(book)
                                },
                                onLongPress = {
                                    selectedBookForTags = book
                                    showTagDialog = true
                                },
                                status = null,
                                bookStatuses = state.bookStatuses
                            ) 
                        }
                    }
                }
            }
        }
    }
    
    // Модальное окно выбора тегов
    if (showTagDialog) {
        ru.fire_core.xauplayer.ui.screens.tabs.TagSelectionDialog(
            book = selectedBookForTags,
            series = selectedSeriesForTags,
            allTags = state.allTags,
            vm = vm,
            onDismiss = {
                showTagDialog = false
                selectedBookForTags = null
                selectedSeriesForTags = null
            },
            onTagsUpdated = {
                scope.launch {
                    vm.load()
                }
            }
        )
    }

    // Страница просмотра книги
    bookForDetail?.let { b ->
        BookDetailDialog(
            book = b,
            vm = vm,
            playerVm = playerVm,
            bookStatus = state.bookStatuses[b.id],
            onPlay = {
                playerVm.addBook(b)
                vm.selectBook(b)
                onBookSelected(b)
            },
            onDismiss = { bookForDetail = null }
        )
    }

    // Страница серии
    seriesForDetail?.let { s ->
        SeriesDetailDialog(
            seriesWithBooks = s,
            vm = vm,
            playerVm = playerVm,
            onOpenBook = { bookForDetail = it },
            onDismiss = { seriesForDetail = null }
        )
    }
}

/** Совпадает ли серия (или её книги) с поисковым запросом. */
private fun seriesMatches(s: ru.fire_core.xauplayer.ui.viewmodel.SeriesWithBooks, q: String): Boolean {
    if (q.isEmpty()) return true
    if (s.series.name.lowercase().contains(q)) return true
    if (s.series.description?.lowercase()?.contains(q) == true) return true
    return s.books.any { bookMatches(it, q) }
}

/** Совпадает ли книга с поисковым запросом. */
private fun bookMatches(b: Book, q: String): Boolean {
    if (q.isEmpty()) return true
    return b.title.lowercase().contains(q) || b.author.lowercase().contains(q)
}

/**
 * Получает URL обложки серии для отображения через API через CoverManager
 * Если у серии нет обложки, использует обложку из первой доступной книги
 */
@Composable
private fun rememberSeriesCoverUrl(series: Series, seriesWithBooks: ru.fire_core.xauplayer.ui.viewmodel.SeriesWithBooks, vm: BooksViewModel): String? {
    var coverUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(series.id, seriesWithBooks.books) {
        coverUrl = vm.getSeriesCoverUrl(series.id, seriesWithBooks.books)
    }
    return coverUrl
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesItem(
    seriesWithBooks: ru.fire_core.xauplayer.ui.viewmodel.SeriesWithBooks,
    selectedBookId: Long?,
    booksWithProgress: Set<Long>,
    bookStatuses: Map<Long, String>,
    seriesTags: List<Tag>,
    bookTags: Map<Long, List<Tag>>,
    vm: BooksViewModel,
    onOpenSeries: (ru.fire_core.xauplayer.ui.viewmodel.SeriesWithBooks) -> Unit = {},
    onBookSelected: (Book) -> Unit,
    onAddBook: (Book) -> Unit,
    onLongPress: (Series) -> Unit = {},
    onBookLongPress: (Book) -> Unit = {},
    playerVm: ru.fire_core.xauplayer.ui.viewmodel.PlayerViewModel? = null
) {
    var isExpanded by remember { mutableStateOf(false) }
    val seriesCoverUrl = rememberSeriesCoverUrl(seriesWithBooks.series, seriesWithBooks, vm)
    
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
                    .combinedClickable(
                        onClick = { onOpenSeries(seriesWithBooks) },
                        onLongClick = { onLongPress(seriesWithBooks.series) }
                    )
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
                                maxLines = 1
                            )
                        }
                        Text(
                            text = "${seriesWithBooks.books.size} книг",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Отображение тегов
                        if (seriesTags.isNotEmpty()) {
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                seriesTags.take(3).forEach { tag ->
                                    ru.fire_core.xauplayer.ui.screens.tabs.TagChip(tag = tag)
                                }
                                if (seriesTags.size > 3) {
                                    Text(
                                        text = "+${seriesTags.size - 3}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                IconButton(onClick = { isExpanded = !isExpanded }) {
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
                        BookRowWithStatus(
                            book = book,
                            isSelected = selectedBookId == book.id,
                            hasProgress = booksWithProgress.contains(book.id),
                            tags = bookTags[book.id] ?: emptyList(),
                            vm = vm,
                            onClick = { onBookSelected(book) },
                            onAddBook = { onAddBook(book) },
                            onLongPress = { onBookLongPress(book) },
                            isInSeries = true,
                            status = null,
                            bookStatuses = bookStatuses,
                            playerVm = playerVm
                        )
                    }
                }
            }
        }
    }
}

/**
 * Получает URL обложки книги для отображения через API через CoverManager
 */
@Composable
private fun rememberBookCoverUrl(book: Book, vm: BooksViewModel): String {
    var coverUrl by remember { mutableStateOf<String>("") }
    LaunchedEffect(book.id) {
        coverUrl = vm.getBookCoverUrl(book.id)
    }
    return coverUrl
}

@Composable
private fun BookRowWithStatus(
    book: Book,
    isSelected: Boolean = false,
    hasProgress: Boolean = false,
    tags: List<Tag> = emptyList(),
    vm: BooksViewModel,
    onClick: () -> Unit = {},
    onAddBook: () -> Unit = {},
    onLongPress: () -> Unit = {},
    isInSeries: Boolean = false,
    status: String? = null,
    bookStatuses: Map<Long, String> = emptyMap(),
    playerVm: ru.fire_core.xauplayer.ui.viewmodel.PlayerViewModel? = null
) {
    // Получаем статус книги из переданной Map или используем явно переданный статус
    val bookStatus = status ?: bookStatuses[book.id]
    
    BookRow(
        b = book,
        isSelected = isSelected,
        hasProgress = hasProgress,
        tags = tags,
        vm = vm,
        onClick = onClick,
        onAddBook = onAddBook,
        onLongPress = onLongPress,
        isInSeries = isInSeries,
        bookStatus = bookStatus,
        playerVm = playerVm
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookRow(
    b: Book, 
    isSelected: Boolean = false,
    hasProgress: Boolean = false,
    tags: List<Tag> = emptyList(),
    vm: BooksViewModel,
    onClick: () -> Unit = {},
    onAddBook: () -> Unit = {},
    onLongPress: () -> Unit = {},
    isInSeries: Boolean = false,
    bookStatus: String? = null,
    playerVm: ru.fire_core.xauplayer.ui.viewmodel.PlayerViewModel? = null
) {
    val coverUrl = rememberBookCoverUrl(b, vm)
    
    // Определяем цвет метки статуса
    val statusColor = when (bookStatus) {
        "listening" -> Color(0xFF4CAF50) // Зеленый
        "wanted" -> Color(0xFF2196F3) // Синий
        "completed" -> Color(0xFF9C27B0) // Фиолетовый
        "dropped" -> Color(0xFFF44336) // Красный
        else -> null
    }
    
    val statusLabel = when (bookStatus) {
        "listening" -> "Слушаю 🎧"
        "wanted" -> "В планах 📚"
        "completed" -> "Прослушано ✅"
        "dropped" -> "Брошено 🥀"
        else -> null
    }

    // Эффект для некоторых книг по названию (эмодзи-бейдж + цветная рамка обложки)
    val bookEffect = ru.fire_core.xauplayer.core.humor.FunPhrases.bookEffect(b.title)
    val effectColor = remember(bookEffect) {
        bookEffect?.let {
            try { Color(android.graphics.Color.parseColor(it.colorHex)) } catch (e: Exception) { null }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(if (isInSeries) 8.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Обложка с полупрозрачной надписью статуса
            Box(
                modifier = Modifier
                    .size(if (isInSeries) 48.dp else 64.dp)
                    .then(
                        if (effectColor != null)
                            Modifier.border(2.dp, effectColor, RoundedCornerShape(4.dp))
                        else Modifier
                    )
            ) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                // Эмодзи-бейдж эффекта в углу обложки
                bookEffect?.let { eff ->
                    Text(
                        eff.emoji,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(2.dp)
                    )
                }
                // Полупрозрачная надпись статуса поверх обложки снизу
                if (statusLabel != null && statusColor != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                statusColor.copy(alpha = 0.8f),
                                RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                            )
                    ) {
                        Text(
                            statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(b.title, style = if (isInSeries) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge)
                Text(b.author, style = MaterialTheme.typography.bodySmall)
                // Отображение тегов
                if (tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tags.take(2).forEach { tag ->
                            ru.fire_core.xauplayer.ui.screens.tabs.TagChip(tag = tag)
                        }
                        if (tags.size > 2) {
                            Text(
                                text = "+${tags.size - 2}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (!hasProgress) {
                Button(onClick = onAddBook, modifier = Modifier.height(36.dp)) {
                    Text("Добавить", style = MaterialTheme.typography.labelSmall)
                }
            } else if (hasProgress && playerVm != null) {
                // Для книг в "Моих" показываем кнопку загрузки
                val scope = rememberCoroutineScope()
                Button(
                    onClick = {
                        scope.launch {
                            playerVm.downloadBook(b.id)
                        }
                    },
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Download,
                        contentDescription = "Скачать",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Скачать", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}


