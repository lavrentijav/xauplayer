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
import ru.fire_core.xauplayer.data.network.BookDto
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
    /** Главы со скачанной копией, изменившиеся на сервере; очищается при чтении. */
    fun takePendingDownloadUpdates(bookId: Long): List<Chapter>
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
    private fun BookDto.toEntity() = Book(
        id = id,
        title = title,
        author = author,
        narrator = narrator,
        coverUrl = cover_url,
        description = description,
        seriesId = series_id,
        seriesOrder = series_order,
        totalSizeBytes = total_size_bytes,
        uploadedAt = uploaded_at
    )

    /** Скачивает постранично весь список книг. Любая ошибка — исключение: кэш при этом не трогаем. */
    private suspend fun fetchAllBooks(): List<Book> {
        val pageSize = ru.fire_core.xauplayer.core.config.AppConfig.API_MAX_LIMIT
        val result = LinkedHashMap<Long, Book>()
        var offset = 0
        while (true) {
            val page = api.getBooks(offset = offset, limit = pageSize)
            page.forEach { result[it.id] = it.toEntity() }
            if (page.size < pageSize) break
            offset += page.size
        }
        return result.values.toList()
    }

    /**
     * Обновляет кэш библиотеки с сервера. Схема «запрос → успех → перезапись кэша»:
     * весь список скачивается целиком, и только после успеха кэш заменяется (вместе с удалением того,
     * чего на сервере больше нет). При ошибке сети кэш остаётся как был.
     * Книги со скачанными главами не удаляются, даже если пропали с сервера.
     * [force] = false — пропускаем, если синхронизировали недавно (BOOKS_SYNC_INTERVAL_MS).
     */
    override suspend fun syncBooks(force: Boolean, loadAll: Boolean): Boolean {
        return try {
            val lastSync = settingsStore.getLastBooksSync()
            val now = java.time.Instant.now().toEpochMilli() // UTC время
            if (!force && (now - lastSync) <= ru.fire_core.xauplayer.core.config.AppConfig.BOOKS_SYNC_INTERVAL_MS) {
                logger.info("BooksRepository", "Skipping books sync (synced ${ru.fire_core.xauplayer.core.config.AppConfig.msToSeconds(now - lastSync)}s ago)")
                return false
            }
            logger.info("BooksRepository", "Syncing books from server (force=$force, lastSync=${now - lastSync}ms ago)")

            val remote = fetchAllBooks()

            // Книги из статус-списков (на случай, если они скрыты из общего списка)
            val byId = remote.associateBy { it.id }.toMutableMap()
            listOf(
                ApiConstants.STATUS_WANTED,
                ApiConstants.STATUS_LISTENING,
                ApiConstants.STATUS_COMPLETED,
                ApiConstants.STATUS_DROPPED
            ).forEach { status ->
                try {
                    api.getBooksByStatus(status, 0, ru.fire_core.xauplayer.core.config.AppConfig.API_MAX_LIMIT)
                        .forEach { byId[it.id] = it.toEntity() }
                } catch (e: Exception) {
                    logger.warn("BooksRepository", "Failed to sync books from status: $status", e)
                }
            }

            db.bookDao().upsertAll(byId.values.toList())

            // Удаляем из кэша то, чего нет на сервере (кроме книг со скачанными главами)
            val keep = byId.keys + db.downloadedChapterDao().getBookIdsWithDownloads().toSet()
            val stale = db.bookDao().getAllIds().filter { it !in keep }
            stale.chunked(500).forEach { db.bookDao().deleteByIds(it) }

            settingsStore.setLastBooksSync(now)
            logger.info("BooksRepository", "Books synced: ${byId.size} on server, ${stale.size} removed from cache")

            // Обложки — в фоне
            repositoryScope.launch {
                byId.values.forEach { book ->
                    try {
                        coverManager.downloadBookCover(book.id)
                    } catch (e: Exception) {
                        logger.warn("BooksRepository", "Failed to download cover for book ${book.id}", e)
                    }
                }
            }
            true
        } catch (e: Exception) {
            logger.error("BooksRepository", "Failed to sync books (cache left untouched)", e)
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

    /** Главы, которые появились/изменились на сервере у книги со скачанными главами (ждут решения пользователя). */
    private val pendingDownloadUpdates = java.util.concurrent.ConcurrentHashMap<Long, List<Chapter>>()

    override fun takePendingDownloadUpdates(bookId: Long): List<Chapter> =
        pendingDownloadUpdates.remove(bookId) ?: emptyList()

    /**
     * Обновляет кэш глав книги с сервера (запрос → успех → перезапись кэша).
     * Если у книги есть скачанные главы и на сервере появились новые или изменились (другой размер файла),
     * они запоминаются — UI спрашивает, обновить ли локальную копию ([takePendingDownloadUpdates]).
     */
    override suspend fun syncChapters(bookId: Long, force: Boolean): Boolean {
        return try {
            val downloaded = db.downloadedChapterDao().getByBookId(bookId)
                .filter { java.io.File(it.localPath).exists() }

            // Если ничего не скачано и не принудительная синхронизация - не кэшируем
            if (!force && downloaded.isEmpty()) {
                logger.info("BooksRepository", "Skipping chapters sync for book $bookId (no downloaded chapters)")
                return false
            }

            val lastSync = settingsStore.getLastChaptersSync(bookId)
            val now = java.time.Instant.now().toEpochMilli() // UTC время
            if (!force && (now - lastSync) <= ru.fire_core.xauplayer.core.config.AppConfig.CHAPTERS_SYNC_INTERVAL_MS) {
                logger.info("BooksRepository", "Skipping chapters sync for book $bookId (synced ${ru.fire_core.xauplayer.core.config.AppConfig.msToSeconds(now - lastSync)}s ago)")
                return false
            }

            logger.info("BooksRepository", "Syncing chapters for book $bookId from server (force=$force, lastSync=${now - lastSync}ms ago)")
            val remote = api.getChapters(bookId)
            val mapped = remote.map {
                Chapter(
                    id = it.id,
                    bookId = bookId,
                    title = it.title,
                    duration = ((it.duration ?: 0.0) * 1000).toLong(),
                    chapterOrder = it.order,
                    realOrder = it.real_order,
                    fileSizeBytes = it.file_size_bytes,
                    downloadUrl = it.download_url
                )
            }

            val knownBefore = db.chapterDao().getByBook(bookId).map { it.id }.toSet()
            db.chapterDao().upsertAll(mapped)

            // Удаляем из кэша главы, которых нет на сервере (скачанные оставляем)
            val keep = mapped.map { it.id }.toSet() + downloaded.map { it.chapterId }.toSet()
            val stale = knownBefore.filter { it !in keep }
            stale.chunked(500).forEach { db.chapterDao().deleteByIds(it) }

            // Что предложить обновить в локальной копии
            if (downloaded.isNotEmpty()) {
                val downloadedById = downloaded.associateBy { it.chapterId }
                val updates = mapped.filter { ch ->
                    val local = downloadedById[ch.id]
                    if (local != null) {
                        ch.fileSizeBytes != null && local.fileSize != ch.fileSizeBytes
                    } else {
                        ch.id !in knownBefore && knownBefore.isNotEmpty()
                    }
                }
                if (updates.isNotEmpty()) pendingDownloadUpdates[bookId] = updates else pendingDownloadUpdates.remove(bookId)
            }

            settingsStore.setLastChaptersSync(now)
            logger.info("BooksRepository", "Chapters synced for book $bookId: ${mapped.size} chapters, ${stale.size} removed from cache")
            true
        } catch (e: Exception) {
            logger.error("BooksRepository", "Failed to sync chapters for book $bookId (cache left untouched)", e)
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


