package ru.fire_core.xauplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.fire_core.xauplayer.data.local.entities.Book
import ru.fire_core.xauplayer.data.local.entities.Series
import ru.fire_core.xauplayer.domain.usecase.books.GetBooksUseCase
import ru.fire_core.xauplayer.domain.usecase.books.RefreshBooksUseCase
import ru.fire_core.xauplayer.domain.usecase.books.SyncBooksUseCase
import ru.fire_core.xauplayer.domain.usecase.books.ShouldSyncBooksUseCase
import ru.fire_core.xauplayer.domain.usecase.series.SeriesUseCases
import ru.fire_core.xauplayer.domain.usecase.status.StatusUseCases
import ru.fire_core.xauplayer.data.cache.CoverManager
import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.domain.repo.TagRepository
import ru.fire_core.xauplayer.data.local.entities.Tag
import javax.inject.Inject

data class SeriesWithBooks(
    val series: Series,
    val books: List<Book>
)

data class BooksUiState(
    val loading: Boolean = false, 
    val books: List<Book> = emptyList(),
    val series: List<SeriesWithBooks> = emptyList(),
    val mySeries: List<SeriesWithBooks> = emptyList(), // Серии со статусом
    val librarySeries: List<SeriesWithBooks> = emptyList(), // Все серии
    val booksWithoutSeries: List<Book> = emptyList(),
    val wantedBooks: List<Book> = emptyList(),
    val listeningBooks: List<Book> = emptyList(),
    val completedBooks: List<Book> = emptyList(),
    val droppedBooks: List<Book> = emptyList(),
    val bookStatuses: Map<Long, String> = emptyMap(), // Map bookId -> status
    val seriesStatuses: Map<Long, String> = emptyMap(), // Map seriesId -> status
    val bookTags: Map<Long, List<Tag>> = emptyMap(), // Map bookId -> tags
    val seriesTags: Map<Long, List<Tag>> = emptyMap(), // Map seriesId -> tags
    val allTags: List<Tag> = emptyList(),
    val selectedBook: Book? = null,
    val hasNetworkError: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class BooksViewModel @Inject constructor(
    private val refreshBooks: RefreshBooksUseCase,
    private val syncBooks: SyncBooksUseCase,
    private val shouldSyncBooks: ShouldSyncBooksUseCase,
    private val getBooks: GetBooksUseCase,
    private val seriesUseCases: SeriesUseCases,
    private val statusUseCases: StatusUseCases,
    private val tagRepository: TagRepository,
    private val coverManager: CoverManager,
    private val logger: AppLogger
) : ViewModel() {
    private val _uiState = MutableStateFlow(BooksUiState(loading = true))
    val uiState: StateFlow<BooksUiState> = _uiState

    private fun groupBooksBySeries(books: List<Book>, allSeries: List<Series>): Pair<List<SeriesWithBooks>, List<Book>> {
        val seriesMap = allSeries.associateBy { it.id }
        val booksBySeries = books.filter { it.seriesId != null }
            .groupBy { it.seriesId!! }
        
        val seriesWithBooks = booksBySeries.mapNotNull { (seriesId, seriesBooks) ->
            seriesMap[seriesId]?.let { series ->
                SeriesWithBooks(
                    series = series,
                    books = seriesBooks.sortedWith(compareBy(
                        { it.seriesOrder ?: Int.MAX_VALUE }, // Сначала по seriesOrder
                        { it.id } // Затем по id если seriesOrder одинаковый
                    ))
                )
            }
        }.sortedBy { it.series.name }
        
        val booksWithoutSeries = books.filter { it.seriesId == null }
        
        return Pair(seriesWithBooks, booksWithoutSeries)
    }

    fun load() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(loading = true, hasNetworkError = false, errorMessage = null)
        try {
            // Сначала загружаем кэшированные данные для быстрого отображения
            val cachedBooks = getBooks()
            val cachedSeries = seriesUseCases.getSeries()
            val (cachedSeriesWithBooks, cachedBooksWithoutSeries) = groupBooksBySeries(cachedBooks, cachedSeries)
            
            // Показываем кэшированные данные сразу
            if (cachedBooks.isNotEmpty() || cachedSeries.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    books = cachedBooks,
                    series = cachedSeriesWithBooks,
                    librarySeries = cachedSeriesWithBooks,
                    booksWithoutSeries = cachedBooksWithoutSeries
                )
            }
            
            // Затем синхронизируем актуальные данные в фоне
            try {
                seriesUseCases.syncSeries()
            } catch (e: Exception) {
                logger.warn("BooksViewModel", "Failed to sync series", e)
            }
            
            // Проверяем нужно ли синхронизировать книги
            val needsSync = shouldSyncBooks()
            if (needsSync) {
                logger.info("BooksViewModel", "Syncing books from server (shouldSync=true)")
                // Загружаем все книги (первые 10 сразу, остальные в фоне)
                syncBooks(force = false, loadAll = true)
            } else {
                logger.info("BooksViewModel", "Skipping books sync (shouldSync=false)")
            }
            
            val books = getBooks()
            val allSeries = seriesUseCases.getSeries()
            val (seriesWithBooks, booksWithoutSeries) = groupBooksBySeries(books, allSeries)
            
            // Загружаем статусы серий
            val seriesStatuses = try {
                val allSeriesStatuses = statusUseCases.getSeriesByStatus("wanted") +
                        statusUseCases.getSeriesByStatus("listening") +
                        statusUseCases.getSeriesByStatus("completed") +
                        statusUseCases.getSeriesByStatus("dropped")
                val statusMap = mutableMapOf<Long, String>()
                allSeriesStatuses.forEach { series ->
                    try {
                        val status = statusUseCases.getSeriesStatus(series.id)
                        status?.let { statusMap[series.id] = it.status }
                    } catch (e: Exception) {
                        // Игнорируем ошибки
                    }
                }
                statusMap
            } catch (e: Exception) {
                logger.warn("BooksViewModel", "Failed to get series statuses", e)
                emptyMap()
            }
            
            // Разделяем серии на "Мои" (со статусом) и "Библиотека" (все)
            val mySeries = seriesWithBooks.filter { seriesStatuses.containsKey(it.series.id) }
            val librarySeries = seriesWithBooks
            
            // Синхронизируем теги
            try {
                tagRepository.syncTags()
            } catch (e: Exception) {
                logger.warn("BooksViewModel", "Failed to sync tags", e)
            }
            
            // Загружаем все теги
            val allTags = try {
                tagRepository.getTags()
            } catch (e: Exception) {
                logger.warn("BooksViewModel", "Failed to get tags", e)
                emptyList()
            }
            
            // Загружаем теги для книг
            val bookTags = try {
                books.associateWith { book ->
                    try {
                        tagRepository.getBookTags(book.id)
                    } catch (e: Exception) {
                        emptyList()
                    }
                }.mapKeys { it.key.id }
            } catch (e: Exception) {
                logger.warn("BooksViewModel", "Failed to get book tags", e)
                emptyMap()
            }
            
            // Загружаем теги для серий
            val seriesTags = try {
                allSeries.associateWith { series ->
                    try {
                        tagRepository.getSeriesTags(series.id)
                    } catch (e: Exception) {
                        emptyList()
                    }
                }.mapKeys { it.key.id }
            } catch (e: Exception) {
                logger.warn("BooksViewModel", "Failed to get series tags", e)
                emptyMap()
            }
            
            // Загружаем книги по статусам
            val wantedBooks = try {
                statusUseCases.getBooksByStatus("wanted")
            } catch (e: Exception) {
                logger.warn("BooksViewModel", "Failed to get wanted books", e)
                emptyList()
            }
            val listeningBooks = try {
                statusUseCases.getBooksByStatus("listening")
            } catch (e: Exception) {
                logger.warn("BooksViewModel", "Failed to get listening books", e)
                emptyList()
            }
            val completedBooks = try {
                statusUseCases.getBooksByStatus("completed")
            } catch (e: Exception) {
                logger.warn("BooksViewModel", "Failed to get completed books", e)
                emptyList()
            }
            val droppedBooks = try {
                statusUseCases.getBooksByStatus("dropped")
            } catch (e: Exception) {
                logger.warn("BooksViewModel", "Failed to get dropped books", e)
                emptyList()
            }
            
            // Загружаем все статусы книг для отображения в библиотеке
            val bookStatuses = try {
                val allStatuses = statusUseCases.getAllBookStatuses()
                allStatuses.associate { it.bookId to it.status }
            } catch (e: Exception) {
                logger.warn("BooksViewModel", "Failed to get all book statuses", e)
                emptyMap()
            }
            
            _uiState.value = BooksUiState(
                loading = false, 
                books = books,
                series = seriesWithBooks,
                mySeries = mySeries,
                librarySeries = librarySeries,
                booksWithoutSeries = booksWithoutSeries,
                wantedBooks = wantedBooks,
                listeningBooks = listeningBooks,
                completedBooks = completedBooks,
                droppedBooks = droppedBooks,
                bookStatuses = bookStatuses,
                seriesStatuses = seriesStatuses,
                bookTags = bookTags,
                seriesTags = seriesTags,
                allTags = allTags,
                hasNetworkError = false,
                errorMessage = null
            )
        } catch (e: Exception) {
            val appError = ru.fire_core.xauplayer.core.error.AppError.fromException(e)
            logger.error("BooksViewModel", "Failed to load books: ${appError.message}", e)
            // Пытаемся показать кэшированные данные
            _uiState.value = _uiState.value.copy(
                hasNetworkError = appError is ru.fire_core.xauplayer.core.error.AppError.NetworkError,
                errorMessage = appError.message
            )
            val books = try { getBooks() } catch (ex: Exception) {
                logger.error("BooksViewModel", "Failed to get books from cache", ex)
                emptyList()
            }
            
            // Группируем кэшированные книги
            val allSeries = try { seriesUseCases.getSeries() } catch (ex: Exception) {
                logger.warn("BooksViewModel", "Failed to get series from cache", ex)
                emptyList()
            }
            
            val (seriesWithBooks, booksWithoutSeries) = groupBooksBySeries(books, allSeries)
            
            // Загружаем статусы серий из кэша
            val seriesStatuses = try {
                val allSeriesStatuses = statusUseCases.getSeriesByStatus("wanted") +
                        statusUseCases.getSeriesByStatus("listening") +
                        statusUseCases.getSeriesByStatus("completed") +
                        statusUseCases.getSeriesByStatus("dropped")
                val statusMap = mutableMapOf<Long, String>()
                allSeriesStatuses.forEach { series ->
                    try {
                        val status = statusUseCases.getSeriesStatus(series.id)
                        status?.let { statusMap[series.id] = it.status }
                    } catch (e: Exception) {
                        // Игнорируем ошибки
                    }
                }
                statusMap
            } catch (e: Exception) {
                emptyMap()
            }
            
            // Разделяем серии на "Мои" (со статусом) и "Библиотека" (все)
            val mySeries = seriesWithBooks.filter { seriesStatuses.containsKey(it.series.id) }
            val librarySeries = seriesWithBooks
            
            // Загружаем теги из кэша
            val allTags = try {
                tagRepository.getTags()
            } catch (e: Exception) {
                emptyList()
            }
            
            val bookTags = try {
                books.associateWith { book ->
                    try {
                        tagRepository.getBookTags(book.id)
                    } catch (e: Exception) {
                        emptyList()
                    }
                }.mapKeys { it.key.id }
            } catch (e: Exception) {
                emptyMap()
            }
            
            val seriesTags = try {
                allSeries.associateWith { series ->
                    try {
                        tagRepository.getSeriesTags(series.id)
                    } catch (e: Exception) {
                        emptyList()
                    }
                }.mapKeys { it.key.id }
            } catch (e: Exception) {
                emptyMap()
            }
            
            // Загружаем книги по статусам из кэша
            val wantedBooks = try {
                statusUseCases.getBooksByStatus("wanted")
            } catch (e: Exception) {
                emptyList()
            }
            val listeningBooks = try {
                statusUseCases.getBooksByStatus("listening")
            } catch (e: Exception) {
                emptyList()
            }
            val completedBooks = try {
                statusUseCases.getBooksByStatus("completed")
            } catch (e: Exception) {
                emptyList()
            }
            val droppedBooks = try {
                statusUseCases.getBooksByStatus("dropped")
            } catch (e: Exception) {
                emptyList()
            }
            
            // Загружаем все статусы книг для отображения в библиотеке
            val bookStatuses = try {
                val allStatuses = statusUseCases.getAllBookStatuses()
                allStatuses.associate { it.bookId to it.status }
            } catch (e: Exception) {
                emptyMap()
            }
            
            val errorMsg = if (books.isEmpty()) {
                "Нет доступа к серверу"
            } else {
                "Нет доступа к серверу. Показаны кэшированные данные"
            }
            _uiState.value = BooksUiState(
                loading = false, 
                books = books,
                series = seriesWithBooks,
                mySeries = mySeries,
                librarySeries = librarySeries,
                booksWithoutSeries = booksWithoutSeries,
                wantedBooks = wantedBooks,
                listeningBooks = listeningBooks,
                completedBooks = completedBooks,
                droppedBooks = droppedBooks,
                bookStatuses = bookStatuses,
                seriesStatuses = seriesStatuses,
                bookTags = bookTags,
                seriesTags = seriesTags,
                allTags = allTags,
                hasNetworkError = true,
                errorMessage = errorMsg
            )
        }
    }
    
    fun selectBook(book: Book) {
        _uiState.value = _uiState.value.copy(selectedBook = book)
    }
    
    suspend fun getBookCoverUrl(bookId: Long): String {
        // Используем метод с кэшированием
        return coverManager.getBookCoverUrlForDisplay(bookId)
    }
    
    suspend fun getSeriesCoverUrl(seriesId: Long, booksInSeries: List<Book>? = null): String {
        // Используем метод с кэшированием и fallback на обложку книги
        return coverManager.getSeriesCoverUrlForDisplay(seriesId, booksInSeries)
    }
    
    suspend fun getBookStatus(bookId: Long): String? {
        return try {
            statusUseCases.getBookStatus(bookId)?.status
        } catch (e: Exception) {
            logger.warn("BooksViewModel", "Failed to get book status for book $bookId", e)
            null
        }
    }
    
    suspend fun getBookTags(bookId: Long): List<Tag> {
        return try {
            tagRepository.getBookTags(bookId)
        } catch (e: Exception) {
            logger.warn("BooksViewModel", "Failed to get book tags for book $bookId", e)
            emptyList()
        }
    }
    
    suspend fun getSeriesTags(seriesId: Long): List<Tag> {
        return try {
            tagRepository.getSeriesTags(seriesId)
        } catch (e: Exception) {
            logger.warn("BooksViewModel", "Failed to get series tags for series $seriesId", e)
            emptyList()
        }
    }
    
    suspend fun addTagToBook(bookId: Long, tagId: Long): Boolean {
        return try {
            tagRepository.addTagToBook(bookId, tagId)
        } catch (e: Exception) {
            logger.error("BooksViewModel", "Failed to add tag to book", e)
            false
        }
    }
    
    suspend fun removeTagFromBook(bookId: Long, tagId: Long): Boolean {
        return try {
            tagRepository.removeTagFromBook(bookId, tagId)
        } catch (e: Exception) {
            logger.error("BooksViewModel", "Failed to remove tag from book", e)
            false
        }
    }
    
    suspend fun addTagToSeries(seriesId: Long, tagId: Long): Boolean {
        return try {
            tagRepository.addTagToSeries(seriesId, tagId)
        } catch (e: Exception) {
            logger.error("BooksViewModel", "Failed to add tag to series", e)
            false
        }
    }
    
    suspend fun removeTagFromSeries(seriesId: Long, tagId: Long): Boolean {
        return try {
            tagRepository.removeTagFromSeries(seriesId, tagId)
        } catch (e: Exception) {
            logger.error("BooksViewModel", "Failed to remove tag from series", e)
            false
        }
    }
    
    fun refreshTags() = viewModelScope.launch {
        try {
            tagRepository.syncTags()
            val allTags = tagRepository.getTags()
            _uiState.value = _uiState.value.copy(allTags = allTags)
        } catch (e: Exception) {
            logger.error("BooksViewModel", "Failed to refresh tags", e)
        }
    }
}


