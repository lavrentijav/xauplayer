package ru.fire_core.xauplayer.domain.repo

import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.data.cache.CoverManager
import ru.fire_core.xauplayer.data.datastore.SettingsStore
import ru.fire_core.xauplayer.data.local.AppDatabase
import ru.fire_core.xauplayer.data.local.entities.Book
import ru.fire_core.xauplayer.data.local.entities.Chapter
import ru.fire_core.xauplayer.data.local.entities.Progress
import ru.fire_core.xauplayer.data.network.ApiService
import ru.fire_core.xauplayer.data.network.ApiConstants
import javax.inject.Inject

interface BooksRepository {
    suspend fun syncBooks(force: Boolean = false, loadAll: Boolean = false): Boolean
    suspend fun loadBooksByIds(bookIds: List<Long>): List<Book>
    suspend fun refreshBooks()
    suspend fun getBooks(): List<Book>
    suspend fun searchBooks(search: String): List<Book>
    suspend fun syncChapters(bookId: Long, force: Boolean = false): Boolean
    suspend fun refreshChapters(bookId: Long)
    suspend fun getChapters(bookId: Long): List<Chapter>
    suspend fun shouldSyncBooks(): Boolean
    suspend fun shouldSyncChapters(bookId: Long): Boolean
}

class BooksRepositoryImpl @Inject constructor(
    private val api: ApiService,
    private val db: AppDatabase,
    private val settingsStore: SettingsStore,
    private val coverManager: CoverManager,
    private val logger: AppLogger
) : BooksRepository {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    /**
     * Синхронизирует книги с сервера, если нужно
     * Проверяет время последней синхронизации и обновляет только если нужно
     * Оптимизированная загрузка: сначала первые 10 книг, потом остальные
     */
    override suspend fun syncBooks(force: Boolean, loadAll: Boolean): Boolean {
        return try {
            val lastSync = settingsStore.getLastBooksSync()
            val now = java.time.Instant.now().toEpochMilli() // UTC время
            
            // Если принудительная синхронизация или прошло больше интервала синхронизации
            if (force || (now - lastSync) > ru.fire_core.xauplayer.core.config.AppConfig.BOOKS_SYNC_INTERVAL_MS) {
                logger.info("BooksRepository", "Syncing books from server (force=$force, loadAll=$loadAll, lastSync=${now - lastSync}ms ago)")
                
                // Сначала загружаем первые книги для быстрого отображения
                val initialRemote = api.getBooks(offset = 0, limit = ru.fire_core.xauplayer.core.config.AppConfig.API_INITIAL_BOOKS_LIMIT)
                val initialMapped = initialRemote.map {
                    Book(
                        id = it.id,
                        title = it.title,
                        author = it.author,
                        narrator = it.narrator,
                        coverUrl = it.cover_url,
                        description = it.description,
                        seriesId = it.series_id,
                        seriesOrder = it.series_order,
                        totalSizeBytes = it.total_size_bytes,
                        uploadedAt = it.uploaded_at
                    )
                }
                // Сохраняем первые 10 книг сразу
                db.bookDao().upsertAll(initialMapped)
                logger.info("BooksRepository", "Loaded initial ${initialMapped.size} books")
                
                // Если нужно загрузить все книги, загружаем остальные в фоне
                if (loadAll || initialRemote.size >= ru.fire_core.xauplayer.core.config.AppConfig.API_INITIAL_BOOKS_LIMIT) {
                    repositoryScope.launch {
                        try {
                            var offset = ru.fire_core.xauplayer.core.config.AppConfig.API_INITIAL_BOOKS_LIMIT
                            var hasMore = true
                            
                            while (hasMore) {
                                val batch = api.getBooks(offset = offset, limit = ru.fire_core.xauplayer.core.config.AppConfig.API_DEFAULT_LIMIT)
                                if (batch.isEmpty()) {
                                    hasMore = false
                                } else {
                                    val batchMapped = batch.map {
                                        Book(
                                            id = it.id,
                                            title = it.title,
                                            author = it.author,
                                            narrator = it.narrator,
                                            coverUrl = it.cover_url,
                                            description = it.description,
                                            seriesId = it.series_id,
                                            seriesOrder = it.series_order,
                                            totalSizeBytes = it.total_size_bytes,
                                            uploadedAt = it.uploaded_at
                                        )
                                    }
                                    db.bookDao().upsertAll(batchMapped)
                                    logger.info("BooksRepository", "Loaded batch of ${batchMapped.size} books (offset=$offset)")
                                    offset += batch.size
                                    
                                    // Если получили меньше чем запрашивали, значит это последняя партия
                                    if (batch.size < ru.fire_core.xauplayer.core.config.AppConfig.API_DEFAULT_LIMIT) {
                                        hasMore = false
                                    }
                                }
                            }
                            logger.info("BooksRepository", "Finished loading all books")
                        } catch (e: Exception) {
                            logger.error("BooksRepository", "Error loading remaining books", e)
                        }
                    }
                }
                
                val mapped = initialMapped // Используем начальные книги для дальнейшей обработки
                
                // Также загружаем книги из статусов (wanted, listening, completed, dropped)
                try {
                    logger.info("BooksRepository", "Syncing books from status lists")
                    val statusLists = listOf(
                        ApiConstants.STATUS_WANTED,
                        ApiConstants.STATUS_LISTENING,
                        ApiConstants.STATUS_COMPLETED,
                        ApiConstants.STATUS_DROPPED
                    )
                    statusLists.forEach { status ->
                        try {
                            // Используем новый единый endpoint
                            val statusBooks = api.getBooksByStatus(status, 0, ru.fire_core.xauplayer.core.config.AppConfig.API_MAX_LIMIT)
                            val statusMapped = statusBooks.map {
                                Book(
                                    id = it.id,
                                    title = it.title,
                                    author = it.author,
                                    narrator = it.narrator,
                                    coverUrl = it.cover_url,
                                    description = it.description,
                                    seriesId = it.series_id,
                                    seriesOrder = it.series_order,
                                    totalSizeBytes = it.total_size_bytes,
                                    uploadedAt = it.uploaded_at
                                )
                            }
                            if (statusMapped.isNotEmpty()) {
                                db.bookDao().upsertAll(statusMapped)
                                logger.info("BooksRepository", "Synced ${statusMapped.size} books from status: $status")
                            }
                        } catch (e: Exception) {
                            logger.warn("BooksRepository", "Failed to sync books from status: $status", e)
                            // Пробуем использовать старые методы для обратной совместимости
                            try {
                                val statusBooks = when (status) {
                                    "wanted" -> api.getWantedBooks()
                                    "listening" -> api.getListeningBooks()
                                    "completed" -> api.getCompletedBooks()
                                    "dropped" -> api.getDroppedBooks()
                                    else -> emptyList()
                                }
                                val statusMapped = statusBooks.map {
                                    Book(
                                        id = it.id,
                                        title = it.title,
                                        author = it.author,
                                        narrator = it.narrator,
                                        coverUrl = it.cover_url,
                                        description = it.description,
                                        seriesId = it.series_id,
                                        seriesOrder = it.series_order,
                                        uploadedAt = it.uploaded_at
                                    )
                                }
                                if (statusMapped.isNotEmpty()) {
                                    db.bookDao().upsertAll(statusMapped)
                                    logger.info("BooksRepository", "Synced ${statusMapped.size} books from status: $status (fallback)")
                                }
                            } catch (e2: Exception) {
                                logger.warn("BooksRepository", "Failed to sync books from status: $status (fallback)", e2)
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.warn("BooksRepository", "Failed to sync books from status lists", e)
                }
                
                settingsStore.setLastBooksSync(now)
                logger.info("BooksRepository", "Books synced successfully, ${mapped.size} books")
                
                // Загружаем обложки в фоне для всех книг
                repositoryScope.launch {
                    mapped.forEach { book ->
                        try {
                            coverManager.downloadBookCover(book.id)
                        } catch (e: Exception) {
                            logger.warn("BooksRepository", "Failed to download cover for book ${book.id}", e)
                        }
                    }
                }
                
                true
            } else {
                logger.info("BooksRepository", "Skipping books sync (synced ${ru.fire_core.xauplayer.core.config.AppConfig.msToSeconds(now - lastSync)}s ago)")
                false
            }
        } catch (e: Exception) {
            logger.error("BooksRepository", "Failed to sync books", e)
            false
        }
    }

    /**
     * Загружает конкретные книги по их ID с сервера
     * Используется для загрузки книг, которые есть в прогрессе, но отсутствуют локально
     */
    override suspend fun loadBooksByIds(bookIds: List<Long>): List<Book> {
        return try {
            if (bookIds.isEmpty()) return emptyList()
            
            logger.info("BooksRepository", "Loading ${bookIds.size} books by IDs from server")
            // Получаем все книги с сервера и фильтруем по нужным ID
            val remote = api.getBooks()
            val mapped = remote.filter { it.id in bookIds }.map {
                Book(
                    id = it.id,
                    title = it.title,
                    author = it.author,
                    narrator = it.narrator,
                    coverUrl = it.cover_url,
                    description = it.description,
                    seriesId = it.series_id,
                    seriesOrder = it.series_order,
                    totalSizeBytes = it.total_size_bytes,
                    uploadedAt = it.uploaded_at
                )
            }
            // Сохраняем найденные книги в базу
            if (mapped.isNotEmpty()) {
                db.bookDao().upsertAll(mapped)
                logger.info("BooksRepository", "Loaded ${mapped.size} books by IDs")
                
                // Загружаем обложки в фоне для всех загруженных книг
                // Даже если загружена только 1 глава, сохраняем обложку
                repositoryScope.launch {
                    mapped.forEach { book ->
                        try {
                            coverManager.downloadBookCover(book.id)
                        } catch (e: Exception) {
                            logger.warn("BooksRepository", "Failed to download cover for book ${book.id}", e)
                        }
                    }
                }
            } else {
                logger.warn("BooksRepository", "No books found for requested IDs: $bookIds")
            }
            mapped
        } catch (e: Exception) {
            logger.error("BooksRepository", "Failed to load books by IDs", e)
            emptyList()
        }
    }

    override suspend fun refreshBooks() {
        syncBooks(force = true)
    }

    override suspend fun getBooks(): List<Book> = db.bookDao().getAll()
    
    override suspend fun searchBooks(search: String): List<Book> {
        return try {
            // Сначала пробуем поискать на сервере
            val remote = api.getBooks(search = search)
            val mapped = remote.map {
                Book(
                    id = it.id,
                    title = it.title,
                    author = it.author,
                    narrator = it.narrator,
                    coverUrl = it.cover_url,
                    description = it.description,
                    seriesId = it.series_id,
                    seriesOrder = it.series_order,
                    totalSizeBytes = it.total_size_bytes,
                    uploadedAt = it.uploaded_at
                )
            }
            db.bookDao().upsertAll(mapped)
            mapped
        } catch (e: Exception) {
            // Если нет сети, ищем локально через SQL LIKE запрос
            val query = search.lowercase()
            db.bookDao().search(query)
        }
    }

    /**
     * Синхронизирует главы для книги с сервера, если нужно
     * Проверяет время последней синхронизации и обновляет только если нужно
     */
    override suspend fun syncChapters(bookId: Long, force: Boolean): Boolean {
        return try {
            // Проверяем, есть ли скачанные главы для этой книги
            val downloadedChapters = db.downloadedChapterDao().getByBookId(bookId)
            val hasDownloadedChapters = downloadedChapters.isNotEmpty() && downloadedChapters.any { 
                java.io.File(it.localPath).exists() 
            }
            
            // Если ничего не скачано и не принудительная синхронизация - не кэшируем
            if (!force && !hasDownloadedChapters) {
                logger.info("BooksRepository", "Skipping chapters sync for book $bookId (no downloaded chapters)")
                return false
            }
            
            val lastSync = settingsStore.getLastChaptersSync(bookId)
            val now = java.time.Instant.now().toEpochMilli() // UTC время
            
            // Если принудительная синхронизация или прошло больше интервала синхронизации
            if (force || (now - lastSync) > ru.fire_core.xauplayer.core.config.AppConfig.CHAPTERS_SYNC_INTERVAL_MS) {
                logger.info("BooksRepository", "Syncing chapters for book $bookId from server (force=$force, lastSync=${now - lastSync}ms ago)")
                val remote = api.getChapters(bookId)
                val mapped = remote.map { 
                    Chapter(
                        id = it.id, 
                        bookId = bookId, 
                        title = it.title, 
                        duration = (it.duration * 1000).toLong(), // Конвертируем секунды в миллисекунды
                        chapterOrder = it.order,
                        realOrder = it.real_order,
                        fileSizeBytes = it.file_size_bytes,
                        downloadUrl = it.download_url
                    ) 
                }
                db.chapterDao().upsertAll(mapped)
                settingsStore.setLastChaptersSync(now)
                logger.info("BooksRepository", "Chapters synced successfully for book $bookId, ${mapped.size} chapters")
                true
            } else {
                logger.info("BooksRepository", "Skipping chapters sync for book $bookId (synced ${ru.fire_core.xauplayer.core.config.AppConfig.msToSeconds(now - lastSync)}s ago)")
                false
            }
        } catch (e: Exception) {
            logger.error("BooksRepository", "Failed to sync chapters for book $bookId", e)
            false
        }
    }

    override suspend fun refreshChapters(bookId: Long) {
        syncChapters(bookId, force = true)
    }

    override suspend fun getChapters(bookId: Long): List<Chapter> = db.chapterDao().getByBook(bookId)
    
    /**
     * Проверяет нужно ли обновить данные на основе времени последнего использования
     * Если последнее обновление на сервере новее чем локальное использование - обновляем
     * Логика: если последняя синхронизация была раньше чем последнее использование, 
     * значит могли быть изменения на сервере, которые нужно получить
     */
    override suspend fun shouldSyncBooks(): Boolean {
        val lastSync = settingsStore.getLastBooksSync()
        val now = java.time.Instant.now().toEpochMilli() // UTC время
        
        // Если никогда не синхронизировали - нужно синхронизировать
        if (lastSync == 0L) {
            return true
        }
        
        // Проверяем время последнего использования (время последнего прогресса)
        // lastUpdate уже в UTC (из Instant.parse)
        val lastProgress = db.progressDao().getAll().maxOfOrNull { it.lastUpdate } ?: 0L
        
        // Если последняя синхронизация была раньше чем последнее использование - нужно синхронизировать
        // Это означает, что после последней синхронизации были локальные изменения, 
        // и возможно на сервере тоже были изменения
        if (lastSync < lastProgress) {
            return true
        }
        
        // Также синхронизируем если прошло больше интервала синхронизации
        // Это гарантирует периодическое обновление данных
        if ((now - lastSync) > ru.fire_core.xauplayer.core.config.AppConfig.BOOKS_SYNC_INTERVAL_MS) {
            return true
        }
        
        return false
    }
    
    override suspend fun shouldSyncChapters(bookId: Long): Boolean {
        val lastSync = settingsStore.getLastChaptersSync(bookId)
        val now = java.time.Instant.now().toEpochMilli() // UTC время
        
        // Если никогда не синхронизировали - нужно синхронизировать
        if (lastSync == 0L) {
            return true
        }
        
        // Проверяем время последнего использования для этой книги
        // lastUpdate уже в UTC (из Instant.parse)
        val progress = db.progressDao().get(bookId)
        val lastProgress = progress?.lastUpdate ?: 0L
        
        // Если последняя синхронизация была раньше чем последнее использование - нужно синхронизировать
        if (lastSync < lastProgress) {
            return true
        }
        
        // Также синхронизируем если прошло больше интервала синхронизации
        if ((now - lastSync) > ru.fire_core.xauplayer.core.config.AppConfig.CHAPTERS_SYNC_INTERVAL_MS) {
            return true
        }
        
        return false
    }
}


