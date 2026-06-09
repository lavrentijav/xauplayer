package ru.fire_core.xauplayer.domain.repo

import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.data.local.AppDatabase
import ru.fire_core.xauplayer.data.local.entities.BookStatus
import ru.fire_core.xauplayer.data.local.entities.SeriesStatus
import ru.fire_core.xauplayer.data.network.ApiService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Deferred
import javax.inject.Inject

interface StatusRepository {
    suspend fun getBookStatus(bookId: Long): BookStatus?
    suspend fun setBookStatus(bookId: Long, status: String): BookStatus?
    suspend fun updateBookStatus(bookId: Long, status: String): BookStatus?
    suspend fun deleteBookStatus(bookId: Long)
    suspend fun getBooksByStatus(status: String, offset: Int = 0, limit: Int = 100): List<ru.fire_core.xauplayer.data.local.entities.Book>
    suspend fun getAllBookStatuses(): List<BookStatus>
    suspend fun getSeriesStatus(seriesId: Long): SeriesStatus?
    suspend fun setSeriesStatus(seriesId: Long, status: String): SeriesStatus?
    suspend fun updateSeriesStatus(seriesId: Long, status: String): SeriesStatus?
    suspend fun deleteSeriesStatus(seriesId: Long)
    suspend fun getSeriesByStatus(status: String, offset: Int = 0, limit: Int = 100): List<ru.fire_core.xauplayer.data.local.entities.Series>
}

class StatusRepositoryImpl @Inject constructor(
    private val api: ApiService,
    private val db: AppDatabase,
    private val logger: AppLogger
) : StatusRepository {
    // Кэш для статусов книг
    private val statusCache = mutableMapOf<Long, Pair<BookStatus, Long>>()
    private val cacheValidityDuration = ru.fire_core.xauplayer.core.config.AppConfig.STATUS_CACHE_VALIDITY_DURATION_MS
    
    // Ограничение на количество параллельных запросов статусов
    private val maxConcurrentStatusRequests = ru.fire_core.xauplayer.core.config.AppConfig.MAX_CONCURRENT_STATUS_REQUESTS
    // Book Status
    override suspend fun getBookStatus(bookId: Long): BookStatus? {
        // Проверяем кэш
        val cached = statusCache[bookId]
        val now = System.currentTimeMillis()
        if (cached != null && (now - cached.second) < cacheValidityDuration) {
            logger.debug("StatusRepository", "Using cached status for book $bookId")
            return cached.first
        }
        
        return try {
            val status = api.getBookStatus(bookId)
            if (status.status.isNullOrBlank()) {
                db.bookStatusDao().delete(bookId)
                statusCache.remove(bookId)
                return null
            }
            val bookStatus = BookStatus(
                bookId = status.book_id,
                status = status.status,
                createdAt = status.created_at,
                updatedAt = status.updated_at
            )
            db.bookStatusDao().upsert(bookStatus)
            statusCache[bookId] = Pair(bookStatus, now)
            bookStatus
        } catch (e: Exception) {
            logger.warn("StatusRepository", "Failed to get book status from API for bookId: $bookId, using cached data", e)
            try {
                val dbStatus = db.bookStatusDao().getByBook(bookId)
                // Обновляем кэш из БД
                if (dbStatus != null) {
                    statusCache[bookId] = Pair(dbStatus, now)
                }
                dbStatus
            } catch (dbException: Exception) {
                logger.error("StatusRepository", "Failed to get book status from database", dbException)
                null
            }
        }
    }
    
    override suspend fun setBookStatus(bookId: Long, status: String): BookStatus? {
        return try {
            // PUT создает статус если не существует, или обновляет если существует
            val result = api.setBookStatus(
                bookId,
                ru.fire_core.xauplayer.data.network.StatusRequest(status)
            )
            if (result.status.isNullOrBlank()) return null
            val bookStatus = BookStatus(
                bookId = result.book_id,
                status = result.status,
                createdAt = result.created_at,
                updatedAt = result.updated_at
            )
            db.bookStatusDao().upsert(bookStatus)
            bookStatus
        } catch (e: Exception) {
            logger.error("StatusRepository", "Failed to set book status for bookId: $bookId, status: $status", e)
            null
        }
    }
    
    // Устаревший метод, использует setBookStatus
    override suspend fun updateBookStatus(bookId: Long, status: String): BookStatus? {
        return setBookStatus(bookId, status)
    }
    
    override suspend fun deleteBookStatus(bookId: Long) {
        try {
            api.deleteBookStatus(bookId)
            db.bookStatusDao().delete(bookId)
        } catch (e: Exception) {
            logger.warn("StatusRepository", "Failed to delete book status for bookId: $bookId", e)
            // Пытаемся удалить из локальной БД даже если API запрос не удался
            try {
                db.bookStatusDao().delete(bookId)
            } catch (dbException: Exception) {
                logger.error("StatusRepository", "Failed to delete book status from database", dbException)
            }
        }
    }
    
    override suspend fun getBooksByStatus(status: String, offset: Int, limit: Int): List<ru.fire_core.xauplayer.data.local.entities.Book> {
        require(status.isNotBlank()) { "Status cannot be blank" }
        require(offset >= 0) { "Offset must be non-negative" }
        require(limit >= ru.fire_core.xauplayer.core.config.AppConfig.MIN_API_LIMIT) { "Limit must be at least ${ru.fire_core.xauplayer.core.config.AppConfig.MIN_API_LIMIT}" }
        require(limit <= ru.fire_core.xauplayer.core.config.AppConfig.MAX_API_LIMIT) { "Limit cannot exceed ${ru.fire_core.xauplayer.core.config.AppConfig.MAX_API_LIMIT}" }
        return try {
            // Используем новый единый endpoint с query параметром
            val remote = api.getBooksByStatus(status, offset, limit)
            val mapped = remote.map {
                ru.fire_core.xauplayer.data.local.entities.Book(
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
            db.bookDao().upsertAll(mapped)
            
            // Сохраняем статусы книг (получаем полные данные статусов через API)
            // Используем параллельные запросы с ограничением и кэшированием
            val now = System.currentTimeMillis()
            coroutineScope {
                // Фильтруем книги, для которых нужно обновить статус (нет в кэше или устарел)
                val booksToFetch = mapped.filter { book ->
                    val cached = statusCache[book.id]
                    cached == null || (now - cached.second) >= cacheValidityDuration
                }
                
                // Ограничиваем количество запросов (не более maxConcurrentStatusRequests одновременно)
                booksToFetch.chunked(maxConcurrentStatusRequests).forEach { chunk ->
                    coroutineScope {
                        val deferredList: List<Deferred<BookStatus?>> = chunk.map { book ->
                            async {
                                try {
                                    val statusDto = api.getBookStatus(book.id)
                                    val resolvedStatus = statusDto.status ?: status
                                    val bookStatus = BookStatus(
                                        bookId = statusDto.book_id,
                                        status = resolvedStatus,
                                        createdAt = statusDto.created_at,
                                        updatedAt = statusDto.updated_at
                                    )
                                    // Обновляем кэш
                                    statusCache[book.id] = Pair(bookStatus, now)
                                    bookStatus
                                } catch (e: Exception) {
                                    logger.debug("StatusRepository", "Failed to get status for book ${book.id}, using temporary status", e)
                                    // Если не удалось получить статус, создаем временный
                                    val tempStatus = BookStatus(
                                        bookId = book.id,
                                        status = status,
                                        createdAt = null,
                                        updatedAt = null
                                    )
                                    // Кэшируем временный статус на короткое время
                                    statusCache[book.id] = Pair(tempStatus, now - (cacheValidityDuration - ru.fire_core.xauplayer.core.config.AppConfig.TEMP_STATUS_CACHE_DURATION_MS))
                                    tempStatus
                                }
                            }
                        }
                        deferredList.forEach { deferred ->
                            try {
                                val bookStatus = deferred.await()
                                if (bookStatus != null) {
                                    db.bookStatusDao().upsert(bookStatus)
                                }
                            } catch (e: Exception) {
                                logger.debug("StatusRepository", "Failed to save book status from deferred result", e)
                            }
                        }
                    }
                }
                
                // Для книг, которые есть в кэше, просто сохраняем их в БД
                mapped.forEach { book ->
                    val cached = statusCache[book.id]
                    if (cached != null && (now - cached.second) < cacheValidityDuration) {
                        try {
                            db.bookStatusDao().upsert(cached.first)
                        } catch (e: Exception) {
                            logger.debug("StatusRepository", "Failed to save cached status for book ${book.id}", e)
                        }
                    }
                }
            }
            
            mapped
        } catch (e: Exception) {
            logger.warn("StatusRepository", "Failed to get books by status, using cached data", e)
            try {
                val statusIds = db.bookStatusDao().getByStatus(status).map { it.bookId }
                db.bookDao().getAll().filter { it.id in statusIds }
            } catch (dbException: Exception) {
                logger.error("StatusRepository", "Failed to get books from database", dbException)
                emptyList()
            }
        }
    }
    
    override suspend fun getAllBookStatuses(): List<BookStatus> {
        return try {
            // Синхронизируем статусы с сервера для всех категорий
            val statuses = ru.fire_core.xauplayer.core.config.AppConfig.VALID_BOOK_STATUSES
            statuses.forEach { status ->
                try {
                    // Используем новый endpoint
                    val books = api.getBooksByStatus(status, 0, ru.fire_core.xauplayer.core.config.AppConfig.STATUS_REQUEST_LIMIT)
                    // Получаем полные данные статусов для каждой книги
                    // Используем параллельные запросы с ограничением и кэшированием
                    val now = System.currentTimeMillis()
                    coroutineScope {
                        // Фильтруем книги, для которых нужно обновить статус
                        val booksToFetch = books.filter { bookDto ->
                            val cached = statusCache[bookDto.id]
                            cached == null || (now - cached.second) >= cacheValidityDuration
                        }
                        
                        booksToFetch.chunked(maxConcurrentStatusRequests).forEach { chunk ->
                            coroutineScope {
                                val deferredList: List<Deferred<BookStatus?>> = chunk.map { bookDto ->
                                    async {
                                        try {
                                            val statusDto = api.getBookStatus(bookDto.id)
                                            val resolvedStatus = statusDto.status ?: status
                                            val bookStatus = BookStatus(
                                                bookId = statusDto.book_id,
                                                status = resolvedStatus,
                                                createdAt = statusDto.created_at,
                                                updatedAt = statusDto.updated_at
                                            )
                                            // Обновляем кэш
                                            statusCache[bookDto.id] = Pair(bookStatus, now)
                                            bookStatus
                                        } catch (e: Exception) {
                                            logger.debug("StatusRepository", "Failed to get status for book ${bookDto.id}, using temporary status", e)
                                            // Если не удалось получить статус, создаем временный
                                            val tempStatus = BookStatus(
                                                bookId = bookDto.id,
                                                status = status,
                                                createdAt = null,
                                                updatedAt = null
                                            )
                                            // Кэшируем временный статус на короткое время
                                            statusCache[bookDto.id] = Pair(tempStatus, now - (cacheValidityDuration - ru.fire_core.xauplayer.core.config.AppConfig.TEMP_STATUS_CACHE_DURATION_MS))
                                            tempStatus
                                        }
                                    }
                                }
                                deferredList.forEach { deferred ->
                                    try {
                                        val bookStatus = deferred.await()
                                        if (bookStatus != null) {
                                            db.bookStatusDao().upsert(bookStatus)
                                        }
                                    } catch (e: Exception) {
                                        logger.debug("StatusRepository", "Failed to save book status from deferred result in getAllBookStatuses", e)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.warn("StatusRepository", "Failed to sync statuses for category: $status", e)
                }
            }
            // Возвращаем все статусы из базы
            db.bookStatusDao().getAll()
        } catch (e: Exception) {
            logger.warn("StatusRepository", "Failed to sync all book statuses, using cached data", e)
            // Если нет сети, возвращаем локальные данные
            try {
                db.bookStatusDao().getAll()
            } catch (dbException: Exception) {
                logger.error("StatusRepository", "Failed to get book statuses from database", dbException)
                emptyList()
            }
        }
    }
    
    // Series Status
    override suspend fun getSeriesStatus(seriesId: Long): SeriesStatus? {
        return try {
            val status = api.getSeriesStatus(seriesId)
            if (status.status.isNullOrBlank()) {
                db.seriesStatusDao().delete(seriesId)
                return null
            }
            val seriesStatus = SeriesStatus(
                seriesId = status.series_id,
                status = status.status,
                createdAt = status.created_at,
                updatedAt = status.updated_at
            )
            db.seriesStatusDao().upsert(seriesStatus)
            seriesStatus
        } catch (e: Exception) {
            logger.warn("StatusRepository", "Failed to get series status from API for seriesId: $seriesId, using cached data", e)
            try {
                db.seriesStatusDao().getBySeries(seriesId)
            } catch (dbException: Exception) {
                logger.error("StatusRepository", "Failed to get series status from database", dbException)
                null
            }
        }
    }
    
    override suspend fun setSeriesStatus(seriesId: Long, status: String): SeriesStatus? {
        return try {
            // PUT создает статус если не существует, или обновляет если существует
            val result = api.setSeriesStatus(
                seriesId,
                ru.fire_core.xauplayer.data.network.StatusRequest(status)
            )
            if (result.status.isNullOrBlank()) return null
            val seriesStatus = SeriesStatus(
                seriesId = result.series_id,
                status = result.status,
                createdAt = result.created_at,
                updatedAt = result.updated_at
            )
            db.seriesStatusDao().upsert(seriesStatus)
            seriesStatus
        } catch (e: Exception) {
            logger.error("StatusRepository", "Failed to set series status for seriesId: $seriesId, status: $status", e)
            null
        }
    }
    
    // Устаревший метод, использует setSeriesStatus
    override suspend fun updateSeriesStatus(seriesId: Long, status: String): SeriesStatus? {
        return setSeriesStatus(seriesId, status)
    }
    
    override suspend fun deleteSeriesStatus(seriesId: Long) {
        try {
            api.deleteSeriesStatus(seriesId)
            db.seriesStatusDao().delete(seriesId)
        } catch (e: Exception) {
            logger.warn("StatusRepository", "Failed to delete series status for seriesId: $seriesId", e)
            // Пытаемся удалить из локальной БД даже если API запрос не удался
            try {
                db.seriesStatusDao().delete(seriesId)
            } catch (dbException: Exception) {
                logger.error("StatusRepository", "Failed to delete series status from database", dbException)
            }
        }
    }
    
    override suspend fun getSeriesByStatus(status: String, offset: Int, limit: Int): List<ru.fire_core.xauplayer.data.local.entities.Series> {
        return try {
            // Используем новый единый endpoint с query параметром
            val remote = api.getSeriesByStatus(status, offset, limit)
            val mapped = remote.map {
                ru.fire_core.xauplayer.data.local.entities.Series(
                    id = it.id,
                    name = it.name,
                    description = it.description,
                    coverUrl = it.cover_url
                )
            }
            db.seriesDao().upsertAll(mapped)
            
            // Сохраняем статусы серий
            mapped.forEach { series ->
                try {
                    val statusDto = api.getSeriesStatus(series.id)
                    val resolvedStatus = statusDto.status ?: status
                    val seriesStatus = SeriesStatus(
                        seriesId = statusDto.series_id,
                        status = resolvedStatus,
                        createdAt = statusDto.created_at,
                        updatedAt = statusDto.updated_at
                    )
                    db.seriesStatusDao().upsert(seriesStatus)
                } catch (e: Exception) {
                    logger.debug("StatusRepository", "Failed to get status for series ${series.id}, using temporary status", e)
                    // Если не удалось получить статус, создаем временный
                    val seriesStatus = SeriesStatus(
                        seriesId = series.id,
                        status = status,
                        createdAt = null,
                        updatedAt = null
                    )
                    db.seriesStatusDao().upsert(seriesStatus)
                }
            }
            mapped
        } catch (e: Exception) {
            logger.warn("StatusRepository", "Failed to get series by status, using cached data", e)
            try {
                val statusIds = db.seriesStatusDao().getByStatus(status).map { it.seriesId }
                db.seriesDao().getAll().filter { it.id in statusIds }
            } catch (dbException: Exception) {
                logger.error("StatusRepository", "Failed to get series from database", dbException)
                emptyList()
            }
        }
    }
}

