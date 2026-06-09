package ru.fire_core.xauplayer.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import ru.fire_core.xauplayer.data.local.entities.Book
import ru.fire_core.xauplayer.data.local.entities.Chapter
import ru.fire_core.xauplayer.data.local.entities.Progress
import ru.fire_core.xauplayer.domain.usecase.books.GetBooksUseCase
import ru.fire_core.xauplayer.domain.usecase.books.RefreshBooksUseCase
import ru.fire_core.xauplayer.domain.usecase.books.SyncBooksUseCase
import ru.fire_core.xauplayer.domain.usecase.books.ShouldSyncBooksUseCase
import ru.fire_core.xauplayer.domain.usecase.books.LoadBooksByIdsUseCase
import ru.fire_core.xauplayer.domain.usecase.books.GetChaptersUseCase
import ru.fire_core.xauplayer.domain.usecase.books.RefreshChaptersUseCase
import ru.fire_core.xauplayer.domain.usecase.books.SyncChaptersUseCase
import ru.fire_core.xauplayer.domain.usecase.books.ShouldSyncChaptersUseCase
import ru.fire_core.xauplayer.domain.usecase.progress.GetProgressUseCase
import ru.fire_core.xauplayer.domain.usecase.progress.GetAllProgressUseCase
import ru.fire_core.xauplayer.domain.usecase.progress.SaveProgressUseCase
import ru.fire_core.xauplayer.domain.usecase.progress.SyncProgressUseCase
import ru.fire_core.xauplayer.domain.usecase.progress.SyncProgressFromServerUseCase
import ru.fire_core.xauplayer.domain.usecase.series.SeriesUseCases
import ru.fire_core.xauplayer.domain.usecase.status.StatusUseCases
import ru.fire_core.xauplayer.data.cache.CoverManager
import ru.fire_core.xauplayer.player.PlayerHolder
import ru.fire_core.xauplayer.player.PlayerService
import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.data.datastore.SettingsStore
import ru.fire_core.xauplayer.data.network.StreamUrlResolver
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

data class PlayerUiState(
    val loading: Boolean = false,
    val books: List<Book> = emptyList(), // Только книги с прогрессом
    val selectedBook: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    val selectedChapter: Chapter? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false, // Буферизация аудио
    val hasError: Boolean = false, // Ошибка воспроизведения
    val errorMessage: String? = null,
    val currentProgress: Progress? = null,
    val currentPosition: Long = 0L, // Текущая позиция в миллисекундах
    val duration: Long = 0L, // Длительность в миллисекундах
    val playbackSpeed: Float = 1.0f // Текущая скорость воспроизведения
)

@UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val holder: PlayerHolder,
    @ApplicationContext private val context: Context,
    private val getBooks: GetBooksUseCase,
    private val refreshBooks: RefreshBooksUseCase,
    private val syncBooks: SyncBooksUseCase,
    private val shouldSyncBooks: ShouldSyncBooksUseCase,
    private val loadBooksByIds: LoadBooksByIdsUseCase,
    private val getChapters: GetChaptersUseCase,
    private val refreshChapters: RefreshChaptersUseCase,
    private val syncChapters: SyncChaptersUseCase,
    private val shouldSyncChapters: ShouldSyncChaptersUseCase,
    private val getProgress: GetProgressUseCase,
    private val getAllProgress: GetAllProgressUseCase,
    private val saveProgress: SaveProgressUseCase,
    private val syncProgress: SyncProgressUseCase,
    private val syncProgressFromServer: SyncProgressFromServerUseCase,
    private val offlineProgressManager: ru.fire_core.xauplayer.download.OfflineProgressManager,
    private val downloadManager: ru.fire_core.xauplayer.download.DownloadManager,
    private val seriesUseCases: SeriesUseCases,
    private val statusUseCases: StatusUseCases,
    private val coverManager: CoverManager,
    private val streamUrlResolver: StreamUrlResolver,
    private val logger: AppLogger,
    private val settingsStore: SettingsStore,
    val equalizerManager: ru.fire_core.xauplayer.player.EqualizerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState(loading = true))
    val uiState: StateFlow<PlayerUiState> = _uiState
    private var progressSyncJob: Job? = null
    private var progressBufferJob: Job? = null
    private var lastSyncedChapterId: Long? = null
    private var isPlayingChapter = false // Флаг для предотвращения множественных запусков
    private var lastBufferedProgress: Progress? = null
    
    // Кэш для списков книг
    private var cachedListeningBooks: List<Book>? = null
    private var cachedWantedBooks: List<Book>? = null
    private var cachedCompletedBooks: List<Book>? = null
    private var cachedDroppedBooks: List<Book>? = null
    private var cachedDownloadedBooks: List<Book>? = null
    private var cachedAllMyBooks: List<Book>? = null
    private var cachedAllSeries: List<SeriesWithBooks>? = null
    private var cacheInvalidationTime: Long = 0
    private val cacheValidityDuration = ru.fire_core.xauplayer.core.config.AppConfig.BOOKS_LIST_CACHE_VALIDITY_DURATION_MS

    init {
        loadBooks()
        startProgressTracking()
        viewModelScope.launch {
            syncProgressFromServer() // Теперь возвращает Map<String, Int> (activity), но мы его не используем
        }
        // Отслеживаем состояние плеера
        setupPlayerListener()
        
        // Отслеживаем завершение загрузки книги для автоматического обновления статуса
        viewModelScope.launch {
            downloadManager.downloadProgress.collect { progressMap ->
                // Проверяем все книги на полную загрузку
                val bookIds = progressMap.values.map { it.bookId }.distinct()
                for (bookId in bookIds) {
                    val bookProgresses = progressMap.values.filter { it.bookId == bookId }
                    val allCompleted = bookProgresses.all { 
                        it.state == ru.fire_core.xauplayer.download.DownloadState.COMPLETED 
                    }
                    val hasDownloading = bookProgresses.any { 
                        it.state == ru.fire_core.xauplayer.download.DownloadState.DOWNLOADING ||
                        it.state == ru.fire_core.xauplayer.download.DownloadState.QUEUED
                    }
                    
                    // Если все главы завершены и нет активных загрузок, проверяем статус
                    if (allCompleted && !hasDownloading && bookProgresses.isNotEmpty()) {
                        val chapters = _uiState.value.chapters
                        if (chapters.isNotEmpty() && chapters.all { ch ->
                            val chapterProgress = progressMap[ch.id]
                            chapterProgress?.state == ru.fire_core.xauplayer.download.DownloadState.COMPLETED
                        }) {
                            // Все главы скачаны - проверяем статус книги
                            try {
                                val isFullyDownloaded = isBookFullyDownloaded(bookId)
                                if (isFullyDownloaded) {
                                    logger.info("PlayerViewModel", "Book $bookId is fully downloaded, status check completed")
                                }
                            } catch (e: Exception) {
                                logger.error("PlayerViewModel", "Failed to check book status for $bookId", e)
                            }
                        }
                    }
                }
            }
        }
    }

    private var playerListenerSetup = false
    private var positionUpdateJob: Job? = null
    
    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        val player = holder.player()
        positionUpdateJob = viewModelScope.launch {
            // Получаем интервал обновления из настроек
            val updateInterval = settingsStore.positionUpdateInterval.first()
            while (true) {
                try {
                    // Обновляем только если играет - экономия батареи
                    if (!player.isPlaying) {
                        delay(updateInterval.toLong()) // Используем интервал из настроек
                        continue
                    }
                    delay(updateInterval.toLong()) // Используем интервал из настроек
                    val currentPos = player.currentPosition
                    val dur = player.duration
                    if (dur != C.TIME_UNSET && currentPos != C.TIME_UNSET) {
                        _uiState.value = _uiState.value.copy(
                            currentPosition = currentPos,
                            duration = dur
                        )
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Job был отменен (например, ViewModel уничтожен) - это нормально
                    break
                } catch (e: Exception) {
                    logger.error("PlayerViewModel", "Error updating position", e)
                    break // Выходим из цикла при ошибке
                }
            }
        }
    }
    
    private fun setupPlayerListener() {
        if (playerListenerSetup) return // Настраиваем только один раз
        playerListenerSetup = true
        
        val player = holder.player()
        
        // Запускаем обновления позиции (будет обновлять только когда играет)
        startPositionUpdates()
        
        player.addListener(object : Player.Listener {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _uiState.value = _uiState.value.copy(
                            isBuffering = true,
                            hasError = false,
                            errorMessage = null
                        )
                    }
                    Player.STATE_READY -> {
                        val currentPos = player.currentPosition
                        val dur = player.duration
                        _uiState.value = _uiState.value.copy(
                            isBuffering = false,
                            hasError = false,
                            errorMessage = null,
                            currentPosition = if (currentPos != C.TIME_UNSET) currentPos else 0L,
                            duration = if (dur != C.TIME_UNSET) dur else 0L
                        )
                        // Прикрепляем эквалайзер когда плеер готов
                        equalizerManager.attachToPlayer(player)
                    }
                    Player.STATE_IDLE -> {
                        // Если плеер в состоянии IDLE, но не воспроизводится, это нормально
                        // Но если была ошибка, покажем её
                        if (!playWhenReady) {
                            _uiState.value = _uiState.value.copy(isBuffering = false)
                        }
                    }
                    Player.STATE_ENDED -> {
                        _uiState.value = _uiState.value.copy(
                            isBuffering = false,
                            isPlaying = false
                        )
                        // Автоматический переход на следующую главу
                        viewModelScope.launch {
                            autoPlayNextChapter()
                        }
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // Логируем ошибку
                logger.error(
                    tag = "PlayerViewModel",
                    message = "Player error: ${error.message}",
                    throwable = error
                )
                // ExoPlayer автоматически повторяет попытки, но показываем состояние
                _uiState.value = _uiState.value.copy(
                    isBuffering = true, // Показываем что идет загрузка/повтор
                    hasError = true,
                    errorMessage = "Загрузка аудио... Продолжаем попытки подключения"
                )
                // Продолжаем попытки - ExoPlayer делает это автоматически
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                _uiState.value = _uiState.value.copy(isPlaying = playWhenReady)
                // Перезапускаем обновления позиции при изменении состояния воспроизведения
                if (playWhenReady) {
                    startPositionUpdates()
                }
            }
            
            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                // Обновляем скорость в UI при изменении
                _uiState.value = _uiState.value.copy(playbackSpeed = playbackParameters.speed)
            }
        })
    }

    private fun startProgressTracking() {
        // Буферизация прогресса (только когда играет) для экономии батареи
        progressBufferJob = viewModelScope.launch {
            // Получаем интервал обновления позиции из настроек (используем тот же для прогресса)
            val updateInterval = settingsStore.positionUpdateInterval.first()
            while (true) {
                delay(updateInterval.toLong()) // Используем интервал из настроек
                val player = holder.player()
                // Обновляем прогресс только если играет
                if (!player.isPlaying) {
                    continue
                }
                val currentBook = _uiState.value.selectedBook
                val currentChapter = _uiState.value.selectedChapter
                if (currentBook != null && currentChapter != null) {
                    val progress = Progress(
                        bookId = currentBook.id,
                        chapterId = currentChapter.id,
                        positionMs = player.currentPosition,
                        speed = player.playbackParameters.speed,
                        lastUpdate = java.time.Instant.now().toEpochMilli()
                    )
                    // Сохраняем локально и буферизуем
                    saveProgress(progress)
                    offlineProgressManager.bufferProgress(progress)
                    lastBufferedProgress = progress
                    _uiState.value = _uiState.value.copy(currentProgress = progress, currentPosition = player.currentPosition)
                }
            }
        }
        
        // Периодическая синхронизация (интервал из настроек)
        progressSyncJob = viewModelScope.launch {
            // Получаем интервал синхронизации из настроек
            val syncInterval = settingsStore.progressSyncInterval.first()
            while (true) {
                delay(syncInterval.toLong()) // Используем интервал из настроек
                // Синхронизируем только если есть прогресс и играет
                val player = holder.player()
                if (lastBufferedProgress != null && player.isPlaying) {
                    try {
                        syncProgress(lastBufferedProgress!!)
                        // Помечаем как синхронизированное в буфере
                        offlineProgressManager.syncBufferedProgress()
                    } catch (e: Exception) {
                        // Ошибка синхронизации - прогресс остается в буфере
                        logger.warn("PlayerViewModel", "Failed to sync progress: ${e.message}")
                    }
                }
            }
        }
    }

    private suspend fun saveAndSyncProgress(player: Player, urgent: Boolean = false) {
        val currentBook = _uiState.value.selectedBook ?: return
        val currentChapter = _uiState.value.selectedChapter ?: return
        
        val progress = Progress(
            bookId = currentBook.id,
            chapterId = currentChapter.id,
            positionMs = player.currentPosition,
            speed = player.playbackParameters.speed,
            lastUpdate = java.time.Instant.now().toEpochMilli() // UTC время
        )
        
        saveProgress(progress)
        _uiState.value = _uiState.value.copy(currentProgress = progress)
        
        // Вне очереди при смене главы или если urgent
        val isNewChapter = lastSyncedChapterId != currentChapter.id
        if (isNewChapter || urgent) {
            // Принудительная синхронизация с буферизацией для offline
            offlineProgressManager.forceSyncProgress(progress)
            lastSyncedChapterId = currentChapter.id
        } else {
            // Обычная синхронизация в фоне с буферизацией
            viewModelScope.launch {
                try {
                    syncProgress(progress)
                } catch (e: Exception) {
                    // Если не удалось синхронизировать - буферизуем
                    offlineProgressManager.bufferProgress(progress)
                }
            }
        }
    }

    fun loadBooks() = viewModelScope.launch {
        invalidateCache() // Инвалидируем кэш при загрузке книг
        try {
            _uiState.value = _uiState.value.copy(loading = true)
            logger.info("PlayerViewModel", "Starting loadBooks()")
            
            // Синхронизируем прогресс с сервера (если есть интернет)
            logger.info("PlayerViewModel", "Syncing progress from server...")
            try {
                syncProgressFromServer()
            } catch (e: Exception) {
                logger.warn("PlayerViewModel", "Failed to sync progress from server (offline mode?), using local data", e)
            }
            
            // Загружаем книги из статусов (слушаю, в планах, прослушано, брошено)
            // В офлайн режиме используем локальные данные
            logger.info("PlayerViewModel", "Loading books from statuses...")
            try {
                // Загружаем книги по статусам (в офлайн режиме вернет локальные данные)
                val listeningBooks = statusUseCases.getBooksByStatus("listening")
                val wantedBooks = statusUseCases.getBooksByStatus("wanted")
                val completedBooks = statusUseCases.getBooksByStatus("completed")
                val droppedBooks = statusUseCases.getBooksByStatus("dropped")
                
                val statusBookIds = (listeningBooks + wantedBooks + completedBooks + droppedBooks)
                    .map { it.id }
                    .distinct()
                    .toSet()
                
                logger.info("PlayerViewModel", "Found ${statusBookIds.size} books with statuses: listening=${listeningBooks.size}, wanted=${wantedBooks.size}, completed=${completedBooks.size}, dropped=${droppedBooks.size}")
                
                // Загружаем недостающие книги из статусов (только если есть интернет)
                val localBooks = getBooks()
                val localBookIds = localBooks.map { it.id }.toSet()
                val missingStatusBookIds = statusBookIds.filter { it !in localBookIds }
                
                if (missingStatusBookIds.isNotEmpty()) {
                    logger.info("PlayerViewModel", "Loading ${missingStatusBookIds.size} missing books from statuses: $missingStatusBookIds")
                    try {
                        loadBooksByIds(missingStatusBookIds.toList())
                        logger.info("PlayerViewModel", "Loaded ${missingStatusBookIds.size} missing books from statuses")
                    } catch (e: Exception) {
                        logger.warn("PlayerViewModel", "Failed to load missing books from statuses (offline mode?), continuing with local data", e)
                    }
                }
            } catch (e: Exception) {
                logger.warn("PlayerViewModel", "Failed to load books from statuses (offline mode?), using local data", e)
            }
            
            // Получаем список прогресса (книги, которые были начаты пользователем)
            val progressList = getAllProgress()
            val bookIdsWithProgress = progressList.map { it.bookId }.distinct()
            logger.info("PlayerViewModel", "Found ${progressList.size} progress entries for ${bookIdsWithProgress.size} books: $bookIdsWithProgress")
            
            // Получаем локальные книги
            val localBooks = getBooks()
            val localBookIds = localBooks.map { it.id }.toSet()
            logger.info("PlayerViewModel", "Found ${localBooks.size} local books: $localBookIds")
            
            // Находим книги, которые есть в прогрессе, но отсутствуют локально
            val missingBookIds = bookIdsWithProgress.filter { it !in localBookIds }
            logger.info("PlayerViewModel", "Missing books: $missingBookIds")
            
            // Загружаем недостающие книги с сервера (только если есть интернет)
            if (missingBookIds.isNotEmpty()) {
                logger.info("PlayerViewModel", "Loading ${missingBookIds.size} missing books from server: $missingBookIds")
                try {
                    loadBooksByIds(missingBookIds)
                    logger.info("PlayerViewModel", "Loaded ${missingBookIds.size} missing books")
                } catch (e: Exception) {
                    logger.warn("PlayerViewModel", "Failed to load missing books (offline mode?), continuing with local data", e)
                }
            }
            
            // Получаем обновленный список книг после загрузки недостающих
            val allBooks = getBooks()
            val finalProgressList = getAllProgress()
            
            // Получаем список загруженных книг (для офлайн режима)
            val downloadedBooks = getDownloadedBooks()
            val downloadedBookIds = downloadedBooks.map { it.id }.toSet()
            logger.info("PlayerViewModel", "Found ${downloadedBooks.size} downloaded books: $downloadedBookIds")
            
            // Показываем книги с прогрессом ИЛИ загруженные книги (для офлайн режима)
            // Это гарантирует, что в плеере отображаются книги, которые пользователь начал слушать
            // или которые полностью загружены на устройство
            val booksWithProgress = allBooks.filter { book ->
                finalProgressList.any { it.bookId == book.id } || downloadedBookIds.contains(book.id)
            }
            
            logger.info("PlayerViewModel", "Displaying ${booksWithProgress.size} books (with progress or downloaded) out of ${allBooks.size} total books")
            logger.info("PlayerViewModel", "Books with progress IDs: ${booksWithProgress.map { it.id }}")
            logger.info("PlayerViewModel", "Progress entries: ${finalProgressList.map { "bookId=${it.bookId}, chapterId=${it.chapterId}" }}")
            _uiState.value = _uiState.value.copy(
                loading = false,
                books = booksWithProgress
            )
        } catch (e: Exception) {
            logger.error("PlayerViewModel", "Failed to load books", e)
            // В случае ошибки используем только локальные данные
            val allBooks = try { getBooks() } catch (ex: Exception) {
                logger.error("PlayerViewModel", "Failed to get books from cache", ex)
                emptyList()
            }
            val progressList = try { getAllProgress() } catch (ex: Exception) {
                logger.error("PlayerViewModel", "Failed to get progress from cache", ex)
                emptyList()
            }
            // Также включаем загруженные книги в офлайн режиме
            val downloadedBooks = try { getDownloadedBooks() } catch (ex: Exception) {
                logger.error("PlayerViewModel", "Failed to get downloaded books", ex)
                emptyList()
            }
            val downloadedBookIds = downloadedBooks.map { it.id }.toSet()
            val booksWithProgress = allBooks.filter { book ->
                progressList.any { it.bookId == book.id } || downloadedBookIds.contains(book.id)
            }
            _uiState.value = _uiState.value.copy(
                loading = false,
                books = booksWithProgress
            )
        }
    }

    fun selectBook(book: Book) = viewModelScope.launch {
        val currentBook = _uiState.value.selectedBook
        val isCurrentBook = currentBook?.id == book.id
        
        _uiState.value = _uiState.value.copy(selectedBook = book, loading = true)
        try {
            // Если выбирается другая книга (не текущая) - синхронизируем прогресс с сервера
            if (!isCurrentBook) {
                logger.info("PlayerViewModel", "Selecting different book (${book.id}), syncing progress from server")
                syncProgressFromServer()
                // Сбрасываем скорость при смене книги - будет восстановлена из сохраненного прогресса или установлена по умолчанию
                val defaultSpeed = settingsStore.defaultSpeed.first()
                holder.player().setPlaybackSpeed(defaultSpeed)
            } else {
                logger.info("PlayerViewModel", "Selecting current book (${book.id}), using local progress")
            }
            
            // Сначала проверяем есть ли главы локально
            var chapters = getChapters(book.id)
            
            // Если глав нет локально - пытаемся загрузить их (только если есть интернет)
            if (chapters.isEmpty()) {
                logger.info("PlayerViewModel", "No chapters found locally for book ${book.id}, attempting to load from server")
                try {
                    syncChapters(book.id, force = true)
                    chapters = getChapters(book.id)
                    logger.info("PlayerViewModel", "Loaded ${chapters.size} chapters for book ${book.id}")
                } catch (e: Exception) {
                    logger.warn("PlayerViewModel", "Failed to load chapters for book ${book.id} (offline mode?), continuing with empty list", e)
                    // В офлайн режиме продолжаем с пустым списком глав
                    // Но если есть загруженные главы, они будут доступны через DownloadedChapter
                }
            } else {
                // Если главы есть локально, проверяем нужно ли синхронизировать (только если есть интернет)
                val needsSync = shouldSyncChapters(book.id)
                if (needsSync) {
                    logger.info("PlayerViewModel", "Syncing chapters for book ${book.id} from server (shouldSync=true)")
                    try {
                        syncChapters(book.id, force = false)
                        chapters = getChapters(book.id) // Обновляем список после синхронизации
                    } catch (e: Exception) {
                        logger.warn("PlayerViewModel", "Failed to sync chapters for book ${book.id} (offline mode?), using existing chapters", e)
                        // Используем существующие главы при ошибке (офлайн режим)
                    }
                } else {
                    logger.info("PlayerViewModel", "Skipping chapters sync for book ${book.id} (shouldSync=false)")
                }
            }
            
            _uiState.value = _uiState.value.copy(chapters = chapters, loading = false)
            
            // Сохраняем последнюю выбранную книгу
            settingsStore.setLastBookId(book.id)
            
            // Автоматически загружаем последнюю позицию для этой книги
            val savedProgress = getProgress(book.id)
            val currentChapter = _uiState.value.selectedChapter
            val currentDuration = _uiState.value.duration
            
            // Проверяем, нужно ли подготовить плеер для уже выбранной главы
            val needsPreparation = currentChapter != null && 
                                   currentChapter.id in chapters.map { it.id } && 
                                   currentDuration == 0L
            
            if (savedProgress != null && savedProgress.positionMs > 0) {
                // Находим главу с сохраненным прогрессом
                val chapter = chapters.find { it.id == savedProgress.chapterId }
                if (chapter != null) {
                    // Восстанавливаем скорость воспроизведения из сохраненного прогресса
                    if (savedProgress.speed > 0) {
                        holder.player().setPlaybackSpeed(savedProgress.speed)
                    }
                    
                    // Обновляем выбранную главу
                    _uiState.value = _uiState.value.copy(
                        selectedChapter = chapter,
                        currentProgress = savedProgress,
                        playbackSpeed = savedProgress.speed
                    )
                    
                    // Подготавливаем плеер для текущей главы, но НЕ начинаем воспроизведение автоматически
                    // Воспроизведение начнется только при нажатии кнопки "продолжить прослушивание"
                    prepareChapterForPlayback(chapter, book, savedProgress, autoPlay = false)
                } else if (chapters.isNotEmpty()) {
                    // Если глава не найдена, но есть главы, выбираем первую
                    val firstChapter = chapters.first()
                    _uiState.value = _uiState.value.copy(selectedChapter = firstChapter)
                    // Подготавливаем плеер для первой главы
                    prepareChapterForPlayback(firstChapter, book, null)
                }
            } else if (needsPreparation && currentChapter != null) {
                // Если глава уже выбрана, но плеер не подготовлен (duration = 0) - подготавливаем
                logger.info("PlayerViewModel", "Chapter ${currentChapter.id} is selected but player not prepared, preparing...")
                prepareChapterForPlayback(currentChapter, book, savedProgress, autoPlay = false)
            } else if (chapters.isNotEmpty()) {
                // Если нет сохраненного прогресса, автоматически выбираем первую главу
                val firstChapter = chapters.first()
                _uiState.value = _uiState.value.copy(selectedChapter = firstChapter)
                // Подготавливаем плеер для первой главы
                prepareChapterForPlayback(firstChapter, book, null)
            }
        } catch (e: Exception) {
            logger.error("PlayerViewModel", "Failed to refresh chapters for book ${book.id}", e)
            val chapters = try { getChapters(book.id) } catch (ex: Exception) {
                logger.error("PlayerViewModel", "Failed to get chapters from cache", ex)
                emptyList()
            }
            _uiState.value = _uiState.value.copy(chapters = chapters, loading = false)
            
            // Пытаемся загрузить последнюю позицию даже при ошибке (но не начинаем воспроизведение)
            try {
                val savedProgress = getProgress(book.id)
                if (savedProgress != null && savedProgress.positionMs > 0) {
                    val chapter = chapters.find { it.id == savedProgress.chapterId }
                    if (chapter != null) {
                        // Восстанавливаем скорость воспроизведения
                        if (savedProgress.speed > 0) {
                            holder.player().setPlaybackSpeed(savedProgress.speed)
                        }
                        
                        _uiState.value = _uiState.value.copy(
                            selectedChapter = chapter,
                            currentProgress = savedProgress,
                            playbackSpeed = savedProgress.speed
                        )
                        // НЕ начинаем воспроизведение автоматически
                    }
                }
            } catch (ex: Exception) {
                logger.error("PlayerViewModel", "Failed to load progress for book ${book.id}", ex)
            }
        }
    }

    /**
     * Подготавливает плеер для воспроизведения главы, но не начинает воспроизведение
     * Используется при загрузке текущей главы при старте приложения
     */
    private suspend fun prepareChapterForPlayback(
        chapter: Chapter,
        book: Book,
        savedProgress: Progress?,
        autoPlay: Boolean = false
    ) {
        try {
            // Проверяем наличие локальной копии
            var downloadedChapter = downloadManager.getDownloadedChapter(chapter.id)
            
            // Если глава загружается, ждем завершения загрузки (максимум 5 секунд для подготовки)
            val downloadProgress = downloadManager.downloadProgress.value[chapter.id]
            if (downloadProgress?.state == ru.fire_core.xauplayer.download.DownloadState.DOWNLOADING) {
                logger.info("PlayerViewModel", "Chapter ${chapter.id} is downloading, waiting for completion...")
                var attempts = 0
                val maxAttempts = ru.fire_core.xauplayer.core.config.AppConfig.CHAPTER_DOWNLOAD_PREPARE_MAX_ATTEMPTS
                while (attempts < maxAttempts) {
                    delay(ru.fire_core.xauplayer.core.config.AppConfig.MEDIUM_DELAY_MS)
                    attempts++
                    val updatedProgress = downloadManager.downloadProgress.value[chapter.id]
                    if (updatedProgress?.state == ru.fire_core.xauplayer.download.DownloadState.COMPLETED) {
                        downloadedChapter = downloadManager.getDownloadedChapter(chapter.id)
                        if (downloadedChapter != null && java.io.File(downloadedChapter.localPath).exists()) {
                            logger.info("PlayerViewModel", "Download completed, using local file")
                            break
                        }
                    } else if (updatedProgress?.state == ru.fire_core.xauplayer.download.DownloadState.ERROR || 
                               updatedProgress?.state == ru.fire_core.xauplayer.download.DownloadState.CANCELLED) {
                        break
                    }
                }
            }
            
            val audioUrl = if (downloadedChapter != null && java.io.File(downloadedChapter.localPath).exists()) {
                val file = java.io.File(downloadedChapter.localPath)
                logger.info("PlayerViewModel", "Preparing downloaded chapter: ${file.absolutePath}")
                android.net.Uri.fromFile(file).toString()
            } else {
                val streamUrl = streamUrlResolver.resolve(book.id, chapter.id)
                logger.info("PlayerViewModel", "Preparing stream URL: $streamUrl")
                streamUrl
            }

            val player = holder.player()

            val serviceIntent = Intent(context, PlayerService::class.java)
            context.startForegroundService(serviceIntent)

            if (savedProgress != null && savedProgress.speed > 0) {
                player.setPlaybackSpeed(savedProgress.speed)
            } else {
                val defaultSpeed = settingsStore.defaultSpeed.first()
                player.setPlaybackSpeed(defaultSpeed)
            }
            
            // Получаем URL обложки (в офлайн режиме используем кэшированную или локальную)
            val coverUrl = try {
                // Пытаемся получить кэшированную обложку
                coverManager.getBookCoverUrlForDisplay(book.id)
            } catch (e: Exception) {
                // Если не удалось, используем URL (но в офлайн режиме может не загрузиться)
                logger.warn("PlayerViewModel", "Failed to get cached cover, using URL", e)
                coverManager.getBookCoverUrl(book.id)
            }

            holder.prepare(
                url = audioUrl,
                title = chapter.title,
                artist = "${book.title} - ${book.author}",
                coverUrl = coverUrl
            )
            
            // Перематываем на сохраненную позицию, если есть
            val startPosition = if (savedProgress != null && savedProgress.chapterId == chapter.id) {
                savedProgress.positionMs
            } else {
                0L
            }
            
            // Ждем готовности плеера и перематываем на позицию (но не начинаем воспроизведение)
            try {
                var attempts = 0
                val maxAttempts = ru.fire_core.xauplayer.core.config.AppConfig.PLAYER_PREPARE_MAX_ATTEMPTS
                while (player.playbackState != Player.STATE_READY && 
                       player.playbackState != Player.STATE_ENDED &&
                       attempts < maxAttempts) {
                    delay(ru.fire_core.xauplayer.core.config.AppConfig.PLAYER_READY_CHECK_INTERVAL_MS)
                    attempts++
                }
                
                if (player.playbackState == Player.STATE_READY) {
                    if (startPosition > 0) {
                        logger.info("PlayerViewModel", "Player ready, seeking to saved position: $startPosition")
                        player.seekTo(startPosition)
                    }
                    // Если autoPlay = true, начинаем воспроизведение
                    if (autoPlay) {
                        player.playWhenReady = true
                        _uiState.value = _uiState.value.copy(
                            currentPosition = startPosition,
                            isBuffering = false,
                            isPlaying = true
                        )
                        logger.info("PlayerViewModel", "Auto-playing chapter")
                    } else {
                        _uiState.value = _uiState.value.copy(
                            currentPosition = startPosition,
                            isBuffering = false,
                            isPlaying = false
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(isBuffering = false, isPlaying = false)
                }
            } catch (e: Exception) {
                logger.error("PlayerViewModel", "Error preparing chapter", e)
                _uiState.value = _uiState.value.copy(isBuffering = false)
            }
            
            logger.info("PlayerViewModel", "Chapter prepared for playback (not started)")
        } catch (e: Exception) {
            logger.error("PlayerViewModel", "Failed to prepare chapter for playback", e)
        }
    }

    fun playChapter(chapter: Chapter, book: Book, preferredSpeed: Float? = null) = viewModelScope.launch {
        // Предотвращаем множественные запуски
        if (isPlayingChapter) {
            logger.warn("PlayerViewModel", "Already playing a chapter, ignoring request")
            return@launch
        }
        isPlayingChapter = true
        
        try {
            // Проверяем наличие локальной копии
            var downloadedChapter = downloadManager.getDownloadedChapter(chapter.id)
            
            // Если глава загружается, ждем завершения загрузки
            val downloadProgress = downloadManager.downloadProgress.value[chapter.id]
            if (downloadProgress?.state == ru.fire_core.xauplayer.download.DownloadState.DOWNLOADING) {
                logger.info("PlayerViewModel", "Chapter ${chapter.id} is downloading, waiting for completion...")
                var attempts = 0
                val maxAttempts = ru.fire_core.xauplayer.core.config.AppConfig.CHAPTER_DOWNLOAD_WAIT_MAX_ATTEMPTS
                while (attempts < maxAttempts) {
                    delay(ru.fire_core.xauplayer.core.config.AppConfig.MEDIUM_DELAY_MS)
                    attempts++
                    val updatedProgress = downloadManager.downloadProgress.value[chapter.id]
                    if (updatedProgress?.state == ru.fire_core.xauplayer.download.DownloadState.COMPLETED) {
                        // Загрузка завершена, проверяем наличие файла
                        downloadedChapter = downloadManager.getDownloadedChapter(chapter.id)
                        if (downloadedChapter != null && java.io.File(downloadedChapter.localPath).exists()) {
                            logger.info("PlayerViewModel", "Download completed, switching to local file")
                            break
                        }
                    } else if (updatedProgress?.state == ru.fire_core.xauplayer.download.DownloadState.ERROR || 
                               updatedProgress?.state == ru.fire_core.xauplayer.download.DownloadState.CANCELLED) {
                        // Загрузка завершилась с ошибкой или отменена
                        logger.info("PlayerViewModel", "Download failed or cancelled, using stream")
                        break
                    }
                }
            }
            
            val audioUrl = if (downloadedChapter != null && java.io.File(downloadedChapter.localPath).exists()) {
                // Используем локальный файл
                val file = java.io.File(downloadedChapter.localPath)
                logger.info("PlayerViewModel", "Using downloaded chapter: ${file.absolutePath}, size: ${file.length()} bytes")
                // Используем file:// URI для локальных файлов
                android.net.Uri.fromFile(file).toString()
            } else {
                val streamUrl = streamUrlResolver.resolve(book.id, chapter.id)
                logger.info("PlayerViewModel", "Using stream URL: $streamUrl")
                streamUrl
            }
            
            // Получаем плеер до запуска сервиса, чтобы избежать блокировок
            val player = holder.player()
            
            // Запускаем сервис для уведомлений (после получения плеера)
            val serviceIntent = Intent(context, PlayerService::class.java)
            context.startForegroundService(serviceIntent)
            
            // Загружаем сохраненный прогресс
            val savedProgress = getProgress(book.id)
            val speedToUse = preferredSpeed
                ?: savedProgress?.speed?.takeIf { it > 0 && savedProgress.chapterId == chapter.id }
            if (speedToUse != null && speedToUse > 0) {
                player.setPlaybackSpeed(speedToUse)
                _uiState.value = _uiState.value.copy(playbackSpeed = speedToUse)
            }
            val startPosition = if (savedProgress?.chapterId == chapter.id) {
                savedProgress.positionMs
            } else {
                0L
            }
            
            // Получаем URL обложки (в офлайн режиме используем кэшированную или локальную)
            val coverUrl = try {
                // Пытаемся получить кэшированную обложку
                coverManager.getBookCoverUrlForDisplay(book.id)
            } catch (e: Exception) {
                // Если не удалось, используем URL (но в офлайн режиме может не загрузиться)
                logger.warn("PlayerViewModel", "Failed to get cached cover, using URL", e)
                coverManager.getBookCoverUrl(book.id)
            }

            holder.prepare(
                url = audioUrl,
                title = chapter.title,
                artist = "${book.title} - ${book.author}",
                coverUrl = coverUrl
            )

            _uiState.value = _uiState.value.copy(
                selectedChapter = chapter,
                isBuffering = true, // Начинаем буферизацию
                hasError = false,
                errorMessage = null
            )
            
            // Ждем готовности плеера перед перемоткой и воспроизведением
            viewModelScope.launch {
                try {
                    logger.info("PlayerViewModel", "Waiting for player to be ready, current state: ${player.playbackState}")
                    // Ждем пока плеер будет готов
                    var attempts = 0
                    val maxAttempts = ru.fire_core.xauplayer.core.config.AppConfig.PLAYER_READY_MAX_ATTEMPTS
                    while (player.playbackState != Player.STATE_READY && 
                           player.playbackState != Player.STATE_ENDED &&
                           attempts < maxAttempts) {
                        delay(ru.fire_core.xauplayer.core.config.AppConfig.PLAYER_READY_CHECK_INTERVAL_MS)
                        attempts++
                        // Логируем состояние каждые 2 секунды
                        if (attempts % 20 == 0) {
                            logger.info("PlayerViewModel", "Player state after ${attempts * 100}ms: ${player.playbackState}")
                        }
                    }
                    
                    logger.info("PlayerViewModel", "Player final state: ${player.playbackState} after ${attempts * 100}ms")
                    
                    // Проверяем наличие ошибки
                    val playerError = player.playerError
                    if (playerError != null) {
                        logger.error("PlayerViewModel", "Player error: ${playerError.message}", playerError)
                        _uiState.value = _uiState.value.copy(
                            hasError = true,
                            errorMessage = "Ошибка воспроизведения: ${playerError.message}",
                            isBuffering = false
                        )
                        isPlayingChapter = false
                        return@launch
                    }
                    
                    // Если плеер готов, перематываем и воспроизводим
                    if (player.playbackState == Player.STATE_READY) {
                        logger.info("PlayerViewModel", "Player is ready, seeking to position: $startPosition")
                        // Перематываем на сохраненную позицию
                        if (startPosition > 0) {
                            player.seekTo(startPosition)
                            _uiState.value = _uiState.value.copy(currentPosition = startPosition)
                        }
                        
                        // Начинаем воспроизведение
                        player.playWhenReady = true
                        _uiState.value = _uiState.value.copy(
                            isPlaying = true,
                            isBuffering = false
                        )
                        logger.info("PlayerViewModel", "Playback started successfully")
                    } else {
                        // Если плеер не готов, все равно пытаемся воспроизвести
                        logger.warn("PlayerViewModel", "Player not ready after timeout (state: ${player.playbackState}), starting anyway")
                        player.playWhenReady = true
                        _uiState.value = _uiState.value.copy(
                            isPlaying = true,
                            isBuffering = false
                        )
                    }
                } catch (e: Exception) {
                    logger.error("PlayerViewModel", "Error waiting for player ready", e)
                    // В случае ошибки все равно пытаемся воспроизвести
                    try {
                        player.playWhenReady = true
                        _uiState.value = _uiState.value.copy(
                            isPlaying = true,
                            isBuffering = false
                        )
                    } catch (ex: Exception) {
                        logger.error("PlayerViewModel", "Error starting playback", ex)
                        _uiState.value = _uiState.value.copy(
                            hasError = true,
                            errorMessage = "Ошибка воспроизведения: ${ex.message}",
                            isBuffering = false
                        )
                        isPlayingChapter = false
                    }
                }
            }
            
            // Вне очереди синхронизируем при смене главы
            if (lastSyncedChapterId != chapter.id) {
                saveAndSyncProgress(player, urgent = true)
            }
        } catch (e: Exception) {
            logger.error("PlayerViewModel", "Error playing chapter", e)
            isPlayingChapter = false
            _uiState.value = _uiState.value.copy(
                hasError = true,
                errorMessage = "Ошибка воспроизведения: ${e.message}",
                isBuffering = false
            )
        } finally {
            // Сбрасываем флаг после небольшой задержки
            viewModelScope.launch {
                delay(ru.fire_core.xauplayer.core.config.AppConfig.PLAYBACK_FLAG_RESET_DELAY_MS)
                isPlayingChapter = false
            }
        }
    }

    fun pause() {
        val player = holder.player()
        player.playWhenReady = false
        // ПРИНУДИТЕЛЬНО синхронизируем прогресс при паузе
        viewModelScope.launch {
            saveAndSyncProgress(player, urgent = true)
        }
        _uiState.value = _uiState.value.copy(isPlaying = false)
    }
    
    fun resume() {
        holder.player().playWhenReady = true
        _uiState.value = _uiState.value.copy(isPlaying = true)
    }
    
    override fun onCleared() {
        super.onCleared()
        progressSyncJob?.cancel()
        progressBufferJob?.cancel()
        positionUpdateJob?.cancel()
        // Отправляем буферизованный прогресс при закрытии
        viewModelScope.launch {
            try {
                offlineProgressManager.syncBufferedProgress()
            } catch (e: Exception) {
                logger.warn("PlayerViewModel", "Failed to sync buffered progress on clear: ${e.message}")
            }
        }
    }

    fun setSpeed(speed: Float) {
        val p = holder.player()
        p.setPlaybackSpeed(speed)
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
        // Скорость будет синхронизирована только при паузе/остановке
    }
    
    private suspend fun autoPlayNextChapter() {
        val currentBook = _uiState.value.selectedBook ?: return
        val currentChapter = _uiState.value.selectedChapter ?: return
        val chapters = _uiState.value.chapters
        val currentSpeed = _uiState.value.playbackSpeed

        val currentIndex = chapters.indexOfFirst { it.id == currentChapter.id }
        if (currentIndex >= 0 && currentIndex < chapters.size - 1) {
            val nextChapter = chapters[currentIndex + 1]
            logger.info("PlayerViewModel", "Auto-playing next chapter: ${nextChapter.title}")
            playChapter(nextChapter, currentBook)
            return
        }

        if (!settingsStore.autoPlayNextSeriesBook.first()) return
        val seriesId = currentBook.seriesId ?: return

        val seriesBooks = getBooks()
            .filter { it.seriesId == seriesId }
            .sortedBy { it.seriesOrder ?: Int.MAX_VALUE }
        val bookIndex = seriesBooks.indexOfFirst { it.id == currentBook.id }
        if (bookIndex < 0 || bookIndex >= seriesBooks.size - 1) return

        val nextBook = seriesBooks[bookIndex + 1]
        var nextChapters = getChapters(nextBook.id)
        if (nextChapters.isEmpty()) {
            try {
                syncChapters(nextBook.id, force = true)
                nextChapters = getChapters(nextBook.id)
            } catch (e: Exception) {
                logger.warn("PlayerViewModel", "Failed to load chapters for next series book ${nextBook.id}", e)
                return
            }
        }
        if (nextChapters.isEmpty()) return

        val firstChapter = nextChapters.first()
        logger.info("PlayerViewModel", "Auto-playing next series book: ${nextBook.title}")

        _uiState.value = _uiState.value.copy(
            selectedBook = nextBook,
            chapters = nextChapters,
            selectedChapter = firstChapter,
            playbackSpeed = currentSpeed
        )
        settingsStore.setLastBookId(nextBook.id)
        playChapter(firstChapter, nextBook, preferredSpeed = currentSpeed)
    }

    fun seekTo(positionMs: Long) {
        val player = holder.player()
        player.seekTo(positionMs.coerceIn(0, player.duration.takeIf { it != C.TIME_UNSET } ?: 0))
        _uiState.value = _uiState.value.copy(currentPosition = positionMs)
    }
    
    fun rewind() = viewModelScope.launch {
        val rewindSeconds = settingsStore.rewindSeconds.first()
        val player = holder.player()
        val currentPosition = player.currentPosition
        val newPosition = currentPosition - rewindSeconds * ru.fire_core.xauplayer.core.config.AppConfig.MILLISECONDS_PER_SECOND
        
        // Если новая позиция меньше 0, переходим на предыдущую главу
        if (newPosition < 0) {
            val currentBook = _uiState.value.selectedBook
            val currentChapter = _uiState.value.selectedChapter
            val chapters = _uiState.value.chapters
            
            if (currentBook != null && currentChapter != null && chapters.isNotEmpty()) {
                val currentIndex = chapters.indexOfFirst { it.id == currentChapter.id }
                if (currentIndex > 0) {
                    // Переходим на предыдущую главу и доделываем шаг перехода
                    val prevChapter = chapters[currentIndex - 1]
                    val prevChapterDuration = prevChapter.duration
                    val remainingSeconds = (-newPosition) / ru.fire_core.xauplayer.core.config.AppConfig.MILLISECONDS_PER_SECOND
                    val positionInPrevChapter = (prevChapterDuration - remainingSeconds * ru.fire_core.xauplayer.core.config.AppConfig.MILLISECONDS_PER_SECOND).coerceAtLeast(0L)
                    playChapter(prevChapter, currentBook)
                    // Ждем готовности плеера и перематываем
                    delay(ru.fire_core.xauplayer.core.config.AppConfig.CHAPTER_SEEK_DELAY_MS)
                    player.seekTo(positionInPrevChapter)
                    _uiState.value = _uiState.value.copy(currentPosition = positionInPrevChapter)
                    return@launch
                }
            }
            // Если нет предыдущей главы, просто ставим на начало
            player.seekTo(0L)
            _uiState.value = _uiState.value.copy(currentPosition = 0L)
        } else {
            player.seekTo(newPosition)
            _uiState.value = _uiState.value.copy(currentPosition = newPosition)
        }
    }
    
    fun forward() = viewModelScope.launch {
        val forwardSeconds = settingsStore.forwardSeconds.first()
        val player = holder.player()
        val currentPosition = player.currentPosition
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        val newPosition = currentPosition + forwardSeconds * ru.fire_core.xauplayer.core.config.AppConfig.MILLISECONDS_PER_SECOND
        
        // Если новая позиция больше длительности, переходим на следующую главу
        if (newPosition > duration) {
            val currentBook = _uiState.value.selectedBook
            val currentChapter = _uiState.value.selectedChapter
            val chapters = _uiState.value.chapters
            
            if (currentBook != null && currentChapter != null && chapters.isNotEmpty()) {
                val currentIndex = chapters.indexOfFirst { it.id == currentChapter.id }
                if (currentIndex < chapters.size - 1) {
                    // Переходим на следующую главу и доделываем шаг перехода
                    val nextChapter = chapters[currentIndex + 1]
                    val remainingSeconds = (newPosition - duration) / ru.fire_core.xauplayer.core.config.AppConfig.MILLISECONDS_PER_SECOND
                    val positionInNextChapter = (remainingSeconds * ru.fire_core.xauplayer.core.config.AppConfig.MILLISECONDS_PER_SECOND).coerceAtMost(nextChapter.duration)
                    playChapter(nextChapter, currentBook)
                    // Ждем готовности плеера и перематываем
                    delay(ru.fire_core.xauplayer.core.config.AppConfig.CHAPTER_SEEK_DELAY_MS)
                    player.seekTo(positionInNextChapter)
                    _uiState.value = _uiState.value.copy(currentPosition = positionInNextChapter)
                    return@launch
                }
            }
            // Если нет следующей главы, просто ставим на конец
            player.seekTo(duration)
            _uiState.value = _uiState.value.copy(currentPosition = duration)
        } else {
            player.seekTo(newPosition)
            _uiState.value = _uiState.value.copy(currentPosition = newPosition)
        }
    }
    
    @Deprecated("Use rewind() instead", ReplaceWith("rewind()"))
    fun seekBackward(seconds: Int = 30) {
        val player = holder.player()
        val newPosition = (player.currentPosition - seconds * ru.fire_core.xauplayer.core.config.AppConfig.MILLISECONDS_PER_SECOND).coerceAtLeast(0L)
        player.seekTo(newPosition)
        _uiState.value = _uiState.value.copy(currentPosition = newPosition)
    }
    
    @Deprecated("Use forward() instead", ReplaceWith("forward()"))
    fun seekForward(seconds: Int = 15) {
        val player = holder.player()
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        val newPosition = (player.currentPosition + seconds * ru.fire_core.xauplayer.core.config.AppConfig.MILLISECONDS_PER_SECOND).coerceAtMost(duration)
        player.seekTo(newPosition)
        _uiState.value = _uiState.value.copy(currentPosition = newPosition)
    }

    fun loadCurrentBook() = viewModelScope.launch {
        try {
            // Даем небольшое время для завершения loadBooks(), если он еще выполняется
            delay(100)
            
            // Сначала синхронизируем прогресс с сервера
            syncProgressFromServer()
            
            // Пытаемся загрузить последнюю книгу из настроек
            val lastBookId = settingsStore.getLastBookId()
            var book = if (lastBookId > 0) {
                getBooks().find { it.id == lastBookId }
            } else {
                null
            }
            
            // Если книга не найдена локально, но есть ID - пытаемся загрузить её
            if (book == null && lastBookId > 0) {
                logger.info("PlayerViewModel", "Book $lastBookId not found locally, attempting to load from server")
                try {
                    loadBooksByIds(listOf(lastBookId))
                    book = getBooks().find { it.id == lastBookId }
                    if (book != null) {
                        logger.info("PlayerViewModel", "Successfully loaded book $lastBookId from server")
                    }
                } catch (e: Exception) {
                    logger.warn("PlayerViewModel", "Failed to load book $lastBookId from server (offline mode?)", e)
                }
            }
            
            val selectedBook = if (book != null) {
                book
            } else {
                // Если последней книги нет, берем из прогресса
                val allProgress = getAllProgress()
                if (allProgress.isNotEmpty()) {
                    // Берем последний прогресс (самый свежий)
                    val latestProgress = allProgress.maxByOrNull { it.lastUpdate }
                    if (latestProgress == null) {
                        logger.info("PlayerViewModel", "No progress found, cannot load current book")
                        _uiState.value = _uiState.value.copy(loading = false)
                        return@launch
                    }
                    
                    var progressBook = getBooks().find { it.id == latestProgress.bookId }
                    
                    // Если книга из прогресса не найдена локально - пытаемся загрузить её
                    if (progressBook == null) {
                        logger.info("PlayerViewModel", "Book ${latestProgress.bookId} from progress not found locally, attempting to load from server")
                        try {
                            loadBooksByIds(listOf(latestProgress.bookId))
                            progressBook = getBooks().find { it.id == latestProgress.bookId }
                            if (progressBook != null) {
                                logger.info("PlayerViewModel", "Successfully loaded book ${latestProgress.bookId} from server")
                            }
                        } catch (e: Exception) {
                            logger.warn("PlayerViewModel", "Failed to load book ${latestProgress.bookId} from server (offline mode?)", e)
                        }
                    }
                    
                    if (progressBook == null) {
                        logger.warn("PlayerViewModel", "Book ${latestProgress.bookId} from progress not found and could not be loaded")
                        _uiState.value = _uiState.value.copy(loading = false)
                        return@launch
                    }
                    
                    progressBook
                } else {
                    logger.info("PlayerViewModel", "No progress found, cannot load current book")
                    _uiState.value = _uiState.value.copy(loading = false)
                    return@launch
                }
            }
                
            // Загружаем главы (синхронизируем если нужно, но работаем и в офлайн режиме)
            var chapters = getChapters(selectedBook.id)
            try {
                // Если глав нет локально - пытаемся загрузить их (только если есть интернет)
                if (chapters.isEmpty()) {
                    logger.info("PlayerViewModel", "No chapters found locally for book ${selectedBook.id}, attempting to load from server")
                    try {
                        syncChapters(selectedBook.id, force = true)
                        chapters = getChapters(selectedBook.id)
                        logger.info("PlayerViewModel", "Loaded ${chapters.size} chapters for book ${selectedBook.id}")
                    } catch (e: Exception) {
                        logger.warn("PlayerViewModel", "Failed to load chapters for book ${selectedBook.id} (offline mode?), continuing with empty list", e)
                        // В офлайн режиме продолжаем с пустым списком глав
                        // Но если есть загруженные главы, они будут доступны через DownloadedChapter
                    }
                } else {
                    // Если главы есть локально, проверяем нужно ли синхронизировать (только если есть интернет)
                    val needsSync = shouldSyncChapters(selectedBook.id)
                    if (needsSync) {
                        logger.info("PlayerViewModel", "Syncing chapters for book ${selectedBook.id} from server (shouldSync=true)")
                        try {
                            syncChapters(selectedBook.id, force = false)
                            chapters = getChapters(selectedBook.id) // Обновляем список после синхронизации
                        } catch (e: Exception) {
                            logger.warn("PlayerViewModel", "Failed to sync chapters for book ${selectedBook.id} (offline mode?), using existing chapters", e)
                            // Используем существующие главы при ошибке (офлайн режим)
                        }
                    } else {
                        logger.info("PlayerViewModel", "Skipping chapters sync for book ${selectedBook.id} (shouldSync=false)")
                    }
                }
            } catch (e: Exception) {
                logger.warn("PlayerViewModel", "Failed to sync chapters (offline mode?), using existing chapters", e)
                // Используем существующие главы при ошибке (офлайн режим)
                chapters = try { getChapters(selectedBook.id) } catch (ex: Exception) {
                    logger.error("PlayerViewModel", "Failed to get chapters from cache", ex)
                    emptyList()
                }
            }
            
            // Загружаем прогресс для выбранной книги
            val savedProgress = getProgress(selectedBook.id)
            var chapter = if (savedProgress != null) {
                chapters.find { it.id == savedProgress.chapterId }
            } else {
                null
            }
            
            // Если глава не найдена, но есть главы - берем первую
            if (chapter == null && chapters.isNotEmpty()) {
                chapter = chapters.first()
                logger.info("PlayerViewModel", "Chapter not found, using first chapter")
            }
            
            // ВАЖНО: Сначала обновляем selectedBook и chapters, затем selectedChapter
            // Это гарантирует, что UI получит все данные
            _uiState.value = _uiState.value.copy(
                selectedBook = selectedBook,
                chapters = chapters,
                loading = false
            )
            
            // Затем обновляем selectedChapter отдельно, чтобы гарантировать обновление
            if (chapter != null) {
                _uiState.value = _uiState.value.copy(selectedChapter = chapter)
                
                // Подготавливаем плеер для текущей главы, но НЕ начинаем воспроизведение автоматически
                // Воспроизведение начнется только при нажатии кнопки "продолжить прослушивание"
                prepareChapterForPlayback(chapter, selectedBook, savedProgress, autoPlay = false)
            }
        } catch (e: Exception) {
            logger.error("PlayerViewModel", "Failed to load current book", e)
            _uiState.value = _uiState.value.copy(loading = false)
        }
    }

    /**
     * Продолжает прослушивание текущей книги с сохраненной позиции
     * Вызывается при нажатии кнопки "продолжить прослушивание" во вкладке "моё"
     */
    fun continueListening() = viewModelScope.launch {
        val selectedBook = _uiState.value.selectedBook
        val selectedChapter = _uiState.value.selectedChapter
        
        if (selectedBook == null || selectedChapter == null) {
            logger.warn("PlayerViewModel", "Cannot continue listening: no book or chapter selected")
            return@launch
        }
        
        try {
            // Загружаем сохраненный прогресс
            val savedProgress = getProgress(selectedBook.id)
            
            // Подготавливаем плеер и начинаем воспроизведение
            prepareChapterForPlayback(selectedChapter, selectedBook, savedProgress, autoPlay = true)
            logger.info("PlayerViewModel", "Continuing playback of book ${selectedBook.id}, chapter ${selectedChapter.id}")
        } catch (e: Exception) {
            logger.error("PlayerViewModel", "Failed to continue listening", e)
        }
    }

    fun playDemo() {
        // Demo: play test stream if backend not available
        val demo = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
        
        // Запускаем сервис для уведомлений
        val serviceIntent = Intent(context, PlayerService::class.java)
        context.startForegroundService(serviceIntent)
        
        holder.prepare(demo)
        holder.player().playWhenReady = true
        _uiState.value = _uiState.value.copy(isPlaying = true)
    }

    // === Методы для загрузки глав ===
    
    fun downloadChapter(chapter: Chapter, bookId: Long) {
        try {
            logger.info("PlayerViewModel", "Starting download of chapter ${chapter.id}")
            downloadManager.downloadChapter(chapter, bookId)
        } catch (e: Exception) {
            logger.error("PlayerViewModel", "Failed to download chapter ${chapter.id}", e)
        }
    }

    fun cancelDownload(chapterId: Long) {
        try {
            downloadManager.cancelDownload(chapterId)
            logger.info("PlayerViewModel", "Cancelled download for chapter $chapterId")
        } catch (e: Exception) {
            logger.error("PlayerViewModel", "Failed to cancel download $chapterId", e)
        }
    }
    
    fun cancelBookDownload(bookId: Long) = viewModelScope.launch {
        try {
            val chapters = _uiState.value.chapters
            if (chapters.isNotEmpty()) {
                downloadManager.cancelBookDownload(bookId, chapters)
                logger.info("PlayerViewModel", "Cancelled book download for book $bookId")
            }
        } catch (e: Exception) {
            logger.error("PlayerViewModel", "Failed to cancel book download $bookId", e)
        }
    }

    fun deleteDownloadedChapter(chapterId: Long) = viewModelScope.launch {
        try {
            downloadManager.deleteChapter(chapterId)
            logger.info("PlayerViewModel", "Deleted chapter $chapterId")
        } catch (e: Exception) {
            logger.error("PlayerViewModel", "Failed to delete chapter $chapterId", e)
        }
    }

    fun downloadBook(bookId: Long) = viewModelScope.launch {
        try {
            var chapters = if (_uiState.value.selectedBook?.id == bookId) {
                _uiState.value.chapters
            } else {
                getChapters(bookId)
            }
            if (chapters.isEmpty()) {
                syncChapters(bookId, force = true)
                chapters = getChapters(bookId)
            }
            if (chapters.isEmpty()) {
                logger.warn("PlayerViewModel", "No chapters to download for book $bookId")
                return@launch
            }
            downloadManager.downloadBook(chapters, bookId)
            logger.info("PlayerViewModel", "Started downloading book $bookId with ${chapters.size} chapters")
        } catch (e: Exception) {
            logger.error("PlayerViewModel", "Failed to download book $bookId", e)
        }
    }

    fun downloadSeries(seriesId: Long) = viewModelScope.launch {
        try {
            val seriesBooks = getBooks()
                .filter { it.seriesId == seriesId }
                .sortedBy { it.seriesOrder ?: Int.MAX_VALUE }
            if (seriesBooks.isEmpty()) {
                logger.warn("PlayerViewModel", "No books found for series $seriesId")
                return@launch
            }
            for (book in seriesBooks) {
                var chapters = getChapters(book.id)
                if (chapters.isEmpty()) {
                    try {
                        syncChapters(book.id, force = true)
                        chapters = getChapters(book.id)
                    } catch (e: Exception) {
                        logger.warn("PlayerViewModel", "Failed to sync chapters for book ${book.id} in series $seriesId", e)
                        continue
                    }
                }
                if (chapters.isNotEmpty()) {
                    downloadManager.downloadBook(chapters, book.id)
                }
            }
            logger.info("PlayerViewModel", "Started downloading series $seriesId (${seriesBooks.size} books)")
        } catch (e: Exception) {
            logger.error("PlayerViewModel", "Failed to download series $seriesId", e)
        }
    }

    fun deleteBook(bookId: Long) = viewModelScope.launch {
        try {
            downloadManager.deleteBook(bookId)
            logger.info("PlayerViewModel", "Deleted book $bookId")
        } catch (e: Exception) {
            logger.error("PlayerViewModel", "Failed to delete book $bookId", e)
        }
    }

    suspend fun isChapterDownloaded(chapterId: Long): Boolean {
        val downloaded = downloadManager.getDownloadedChapter(chapterId)
        return downloaded != null && java.io.File(downloaded.localPath).exists()
    }

    fun getDownloadProgress(chapterId: Long): ru.fire_core.xauplayer.download.DownloadProgress? {
        return downloadManager.downloadProgress.value[chapterId]
    }
    
    fun isBookDownloading(bookId: Long): kotlinx.coroutines.flow.StateFlow<Boolean> {
        return kotlinx.coroutines.flow.combine(
            downloadManager.downloadProgress,
            _uiState
        ) { progress, state ->
            val chapters = state.chapters
            if (chapters.isEmpty() || state.selectedBook?.id != bookId) return@combine false
            chapters.any { chapter ->
                val chapterProgress = progress[chapter.id]
                chapterProgress?.state == ru.fire_core.xauplayer.download.DownloadState.DOWNLOADING
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ru.fire_core.xauplayer.core.config.AppConfig.STATE_FLOW_SUBSCRIPTION_TIMEOUT_MS),
            initialValue = false
        )
    }
    
    val downloadProgress: kotlinx.coroutines.flow.StateFlow<Map<Long, ru.fire_core.xauplayer.download.DownloadProgress>>
        get() = downloadManager.downloadProgress
    
    fun cancelChapterDownload(chapterId: Long) {
        downloadManager.cancelDownload(chapterId)
        logger.info("PlayerViewModel", "Cancelled download for chapter $chapterId")
    }
    
    fun removeChapterFromQueue(chapterId: Long) {
        downloadManager.removeFromQueue(chapterId)
        logger.info("PlayerViewModel", "Removed chapter $chapterId from download queue")
    }

    suspend fun isBookFullyDownloaded(bookId: Long): Boolean {
        val chapters = _uiState.value.chapters
        return if (chapters.isEmpty()) false else downloadManager.isBookFullyDownloaded(chapters)
    }
    
    suspend fun isBookDownloaded(bookId: Long): Boolean {
        val chapters = _uiState.value.chapters
        if (chapters.isEmpty()) return false
        // Проверяем, что все главы скачаны
        return chapters.all { chapter ->
            val downloaded = downloadManager.getDownloadedChapter(chapter.id)
            downloaded != null && File(downloaded.localPath).exists()
        }
    }
    
    suspend fun getBookStatus(bookId: Long): String? {
        return statusUseCases.getBookStatus(bookId)?.status
    }
    
    fun toggleBookStatus(bookId: Long) = viewModelScope.launch {
        try {
            val currentStatus = statusUseCases.getBookStatus(bookId)
            if (currentStatus != null) {
                // Если статус есть, удаляем его (убираем галочку)
                statusUseCases.deleteBookStatus(bookId)
            } else {
                // Если статуса нет, ставим статус "listening"
                statusUseCases.setBookStatus(bookId, "listening")
            }
            invalidateCache() // Инвалидируем кэш при изменении статуса
        } catch (e: Exception) {
            logger.error("PlayerViewModel", "Failed to toggle book status", e)
        }
    }
    
    suspend fun getSeries(seriesId: Long?) = if (seriesId != null) seriesUseCases.getSeries(seriesId) else null
    
    suspend fun getBookCoverUrl(bookId: Long): String {
        // Используем метод с кэшированием
        return coverManager.getBookCoverUrlForDisplay(bookId)
    }
    
    suspend fun getSeriesCoverUrl(seriesId: Long, booksInSeries: List<Book>? = null): String {
        return coverManager.getSeriesCoverUrlForDisplay(seriesId, booksInSeries)
    }
    
    private fun invalidateCache() {
        cacheInvalidationTime = System.currentTimeMillis()
        cachedListeningBooks = null
        cachedWantedBooks = null
        cachedCompletedBooks = null
        cachedDroppedBooks = null
        cachedDownloadedBooks = null
        cachedAllMyBooks = null
        cachedAllSeries = null
    }
    
    private fun isCacheValid(): Boolean {
        return System.currentTimeMillis() - cacheInvalidationTime < cacheValidityDuration
    }
    
    suspend fun getCompletedBooks(): List<Book> {
        if (cachedCompletedBooks != null && isCacheValid()) {
            return cachedCompletedBooks!!
        }
        val books = statusUseCases.getBooksByStatus("completed")
        cachedCompletedBooks = books
        return books
    }
    
    suspend fun getDownloadedBooks(): List<Book> {
        if (cachedDownloadedBooks != null && isCacheValid()) {
            return cachedDownloadedBooks!!
        }
        // Получаем все локальные книги (работает в офлайн режиме)
        val allBooks = try {
            getBooks()
        } catch (e: Exception) {
            logger.warn("PlayerViewModel", "Failed to get books for downloaded books check, using empty list", e)
            emptyList()
        }
        val downloadedBooks = mutableListOf<Book>()
        for (book in allBooks) {
            try {
                // Получаем главы из локальной БД (работает в офлайн режиме)
                val chapters = getChapters(book.id)
                if (chapters.isNotEmpty() && downloadManager.isBookFullyDownloaded(chapters)) {
                    downloadedBooks.add(book)
                }
            } catch (e: Exception) {
                logger.warn("PlayerViewModel", "Failed to check if book ${book.id} is downloaded", e)
                // Продолжаем проверку других книг
            }
        }
        cachedDownloadedBooks = downloadedBooks
        logger.info("PlayerViewModel", "Found ${downloadedBooks.size} downloaded books: ${downloadedBooks.map { it.id }}")
        return downloadedBooks
    }
    
    suspend fun getAllMyBooks(): List<Book> {
        if (cachedAllMyBooks != null && isCacheValid()) {
            return cachedAllMyBooks!!
        }
        // Все книги кроме законченных и брошенных
        val completedIds = statusUseCases.getBooksByStatus("completed").map { it.id }.toSet()
        val droppedIds = statusUseCases.getBooksByStatus("dropped").map { it.id }.toSet()
        val excludedIds = completedIds + droppedIds
        val allBooks = getBooks()
        val books = allBooks.filter { it.id !in excludedIds }
        cachedAllMyBooks = books
        return books
    }
    
    suspend fun getStartedAndWantedBooks(): List<Book> {
        // Книги, которые начаты (listening) или в планах (wanted)
        val listening = getListeningBooks()
        val wanted = getWantedBooks()
        val allBooks = getBooks()
        val bookIds = (listening + wanted).map { it.id }.toSet()
        val filteredBooks = allBooks.filter { it.id in bookIds }
        
        // Сортируем по последнему времени прослушивания (по lastUpdate из прогресса)
        val progressList = getAllProgress()
        val progressMap = progressList.associateBy { it.bookId }
        
        return filteredBooks.sortedByDescending { book ->
            progressMap[book.id]?.lastUpdate ?: 0L
        }
    }
    
    suspend fun getListeningBooks(): List<Book> {
        if (cachedListeningBooks != null && isCacheValid()) {
            return cachedListeningBooks!!
        }
        // Книги со статусом "listening"
        val listening = statusUseCases.getBooksByStatus("listening")
        val allBooks = getBooks()
        val bookIds = listening.map { it.id }.toSet()
        val filteredBooks = allBooks.filter { it.id in bookIds }
        
        // Сортируем по последнему времени прослушивания
        val progressList = getAllProgress()
        val progressMap = progressList.associateBy { it.bookId }
        
        val books = filteredBooks.sortedByDescending { book ->
            progressMap[book.id]?.lastUpdate ?: 0L
        }
        cachedListeningBooks = books
        return books
    }
    
    suspend fun getWantedBooks(): List<Book> {
        if (cachedWantedBooks != null && isCacheValid()) {
            return cachedWantedBooks!!
        }
        // Книги со статусом "wanted"
        val wanted = statusUseCases.getBooksByStatus("wanted")
        val allBooks = getBooks()
        val bookIds = wanted.map { it.id }.toSet()
        val books = allBooks.filter { it.id in bookIds }
        cachedWantedBooks = books
        return books
    }
    
    suspend fun getDroppedBooks(): List<Book> {
        if (cachedDroppedBooks != null && isCacheValid()) {
            return cachedDroppedBooks!!
        }
        // Книги со статусом "dropped"
        val dropped = statusUseCases.getBooksByStatus("dropped")
        val allBooks = getBooks()
        val bookIds = dropped.map { it.id }.toSet()
        val books = allBooks.filter { it.id in bookIds }
        cachedDroppedBooks = books
        return books
    }
    
    suspend fun getAllSeries(): List<ru.fire_core.xauplayer.ui.viewmodel.SeriesWithBooks> {
        if (cachedAllSeries != null && isCacheValid()) {
            return cachedAllSeries!!
        }
        val allSeries = seriesUseCases.getSeries()
        val allBooks = getBooks()
        val seriesMap = allSeries.associateBy { it.id }
        val booksBySeries = allBooks.filter { it.seriesId != null }
            .groupBy { it.seriesId!! }
        
        val series = booksBySeries.mapNotNull { (seriesId, seriesBooks) ->
            seriesMap[seriesId]?.let { s ->
                ru.fire_core.xauplayer.ui.viewmodel.SeriesWithBooks(
                    series = s,
                    books = seriesBooks.sortedBy { it.seriesOrder ?: Int.MAX_VALUE }
                )
            }
        }.sortedBy { it.series.name }
        cachedAllSeries = series
        return series
    }
    
    fun addBook(book: Book) = viewModelScope.launch {
        try {
            // Загружаем главы для книги
            var chapters = getChapters(book.id)
            if (chapters.isEmpty()) {
                syncChapters(book.id, force = true)
                chapters = getChapters(book.id)
            }
            
            if (chapters.isNotEmpty()) {
                // Создаем нулевой прогресс для первой главы
                val firstChapter = chapters.first()
                val progress = Progress(
                    bookId = book.id,
                    chapterId = firstChapter.id,
                    positionMs = 0L,
                    speed = 1.0f,
                    lastUpdate = java.time.Instant.now().toEpochMilli()
                )
                saveProgress(progress)
                logger.info("PlayerViewModel", "Added book ${book.id} with zero progress")
                
                // Перезагружаем книги чтобы показать новую
                loadBooks()
                
                // Выбираем книгу только если сейчас не играет другая
                val currentBook = _uiState.value.selectedBook
                val isCurrentlyPlaying = _uiState.value.isPlaying
                if (!isCurrentlyPlaying || currentBook == null) {
                    // Автоматически выбираем книгу и первую главу, но не начинаем воспроизведение
                    selectBook(book)
                } else {
                    // Если играет другая книга, просто обновляем состояние без начала воспроизведения
                    _uiState.value = _uiState.value.copy(
                        selectedBook = book,
                        chapters = chapters,
                        selectedChapter = firstChapter
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("PlayerViewModel", "Failed to add book ${book.id}", e)
        }
    }
    
    private var sleepTimerJob: Job? = null
    
    fun setSleepTimer(durationMs: Long) {
        sleepTimerJob?.cancel()
        if (durationMs > 0) {
            sleepTimerJob = viewModelScope.launch {
                delay(durationMs)
                pause()
                logger.info("PlayerViewModel", "Sleep timer ended, playback paused")
            }
        }
    }
}


