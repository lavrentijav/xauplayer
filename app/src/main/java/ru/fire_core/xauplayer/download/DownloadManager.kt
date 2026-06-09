package ru.fire_core.xauplayer.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.data.datastore.SettingsStore
import ru.fire_core.xauplayer.data.network.ApiUrlBuilder
import ru.fire_core.xauplayer.data.local.AppDatabase
import ru.fire_core.xauplayer.data.local.entities.Chapter
import ru.fire_core.xauplayer.data.local.entities.DownloadedChapter
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class DownloadState {
    IDLE, QUEUED, DOWNLOADING, COMPLETED, ERROR, CANCELLED
}

data class DownloadProgress(
    val chapterId: Long,
    val bookId: Long,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val speed: Long, // bytes per second
    val state: DownloadState = DownloadState.IDLE,
    val error: String? = null
)

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val db: AppDatabase,
    private val settingsStore: SettingsStore,
    private val notificationHelper: DownloadNotificationHelper,
    private val logger: AppLogger
) {
    private val _downloadProgress = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<Long, DownloadProgress>> = _downloadProgress

    private val activeDownloads = mutableMapOf<Long, Job>()
    private val downloadQueue = mutableListOf<Pair<Chapter, Long>>() // Очередь загрузок
    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob()) // Отдельный scope для загрузок
    private val downloadDir = File(settingsStore.basepath, "downloaded_chapters")
    private val cachedChapters = mutableMapOf<Long, List<Chapter>>() // Кэш глав по bookId
    private val bookTotalSizes = mutableMapOf<Long, Long>() // Кэш тотального размера книги

    init {
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
    }

    private suspend fun startNextDownload() {
        val maxConcurrent = settingsStore.getMaxConcurrentDownloads()
        while (activeDownloads.values.count { it.isActive } < maxConcurrent && downloadQueue.isNotEmpty()) {
            val (chapter, bookId) = downloadQueue.removeFirstOrNull() ?: break
            downloadChapterInternal(chapter, bookId)
        }
    }
    
    fun downloadChapter(chapter: Chapter, bookId: Long) {
        // Проверяем, не загружается ли уже эта глава
        if (activeDownloads.containsKey(chapter.id)) {
            logger.info("DownloadManager", "Chapter ${chapter.id} is already downloading")
            return
        }
        
        // Проверяем, не в очереди ли уже
        if (downloadQueue.any { it.first.id == chapter.id }) {
            logger.info("DownloadManager", "Chapter ${chapter.id} is already in queue")
            return
        }
        
        downloadQueue.add(Pair(chapter, bookId))

        val existing = _downloadProgress.value[chapter.id]
        val totalBytes = existing?.totalBytes?.takeIf { it > 0 }
            ?: chapter.fileSizeBytes
            ?: 0L
        updateProgress(chapter.id, bookId, totalBytes, 0, 0, DownloadState.QUEUED, null)
        
        // Пытаемся начать загрузку
        downloadScope.launch {
            startNextDownload()
        }
    }
    
    private fun downloadChapterInternal(chapter: Chapter, bookId: Long) {
        val job = downloadScope.launch {
            try {
                // Проверяем не загружена ли уже глава
                val existing = db.downloadedChapterDao().getByChapterId(chapter.id)
                if (existing != null && File(existing.localPath).exists()) {
                    logger.info("DownloadManager", "Chapter ${chapter.id} already downloaded, skipping")
                    clearProgress(chapter.id)
                    activeDownloads.remove(chapter.id)
                    startNextDownload()
                    return@launch
                }
                
                val baseUrl = settingsStore.baseUrl.first()
                val url = chapter.downloadUrl?.let {
                    logger.debug("DownloadManager", "Using chapter.downloadUrl: $it")
                    ApiUrlBuilder.resolveAbsolute(baseUrl, it)
                } ?: run {
                    val finalUrl = ApiUrlBuilder.join(baseUrl, "books/${bookId}/download/${chapter.id}")
                    logger.debug("DownloadManager", "Using download endpoint: $finalUrl")
                    finalUrl
                }
                
                logger.info("DownloadManager", "Downloading chapter ${chapter.id} from URL: $url")
                
                // Проверяем, есть ли уже известный размер для этой главы
                val existingProgress = _downloadProgress.value[chapter.id]
                val knownTotalBytes = existingProgress?.totalBytes ?: 0L
                
                updateProgress(chapter.id, bookId, knownTotalBytes, 0, 0, DownloadState.DOWNLOADING, null)
                
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    clearProgress(chapter.id)
                    activeDownloads.remove(chapter.id)
                    notificationHelper.showDownloadError(chapter.title, "HTTP ${response.code}")
                    startNextDownload()
                    return@launch
                }
                
                val totalBytes = response.body?.contentLength() ?: knownTotalBytes
                // Если получили реальный размер, обновляем его
                if (totalBytes > 0 && totalBytes != knownTotalBytes) {
                    updateProgress(chapter.id, bookId, totalBytes, 0, 0, DownloadState.DOWNLOADING, null)
                }
                val outputFile = File(downloadDir, "${bookId}_${chapter.id}.mp3")
                
                // Кэшируем главы при первом запросе
                if (!cachedChapters.containsKey(bookId)) {
                    cachedChapters[bookId] = db.chapterDao().getByBook(bookId)
                }
                val bookChapters = cachedChapters[bookId] ?: emptyList()
                
                // Получаем настройку ограничения скорости (0 = без ограничений)
                val maxSpeedKBps = settingsStore.getMaxDownloadSpeed()
                val maxBytesPerSecond = if (maxSpeedKBps > 0) maxSpeedKBps * ru.fire_core.xauplayer.core.config.AppConfig.BYTES_PER_KB else Long.MAX_VALUE
                val throttleIntervalMs = ru.fire_core.xauplayer.core.config.AppConfig.DOWNLOAD_SPEED_CHECK_INTERVAL_MS
                
                response.body?.byteStream()?.use { input ->
                    outputFile.outputStream().use { output ->
                        val buffer = ByteArray(ru.fire_core.xauplayer.core.config.AppConfig.DOWNLOAD_BUFFER_SIZE_BYTES)
                        var downloadedBytes = 0L
                        var lastUpdate = System.currentTimeMillis()
                        var bytesAtLastUpdate = 0L
                        var lastThrottleCheck = System.currentTimeMillis()
                        var bytesReadSinceThrottle = 0L
                        
                        while (isActive) {
                            val bytesRead = input.read(buffer)
                            if (bytesRead == -1) break
                            
                            val now = System.currentTimeMillis()
                            
                            // Ограничение скорости только если настроено
                            if (maxSpeedKBps > 0 && now - lastThrottleCheck >= throttleIntervalMs) {
                                val elapsed = (now - lastThrottleCheck).coerceAtLeast(1)
                                val bytesPerSecond = (bytesReadSinceThrottle * ru.fire_core.xauplayer.core.config.AppConfig.MILLISECONDS_PER_SECOND) / elapsed
                                
                                if (bytesPerSecond > maxBytesPerSecond) {
                                    val excessBytes = bytesPerSecond - maxBytesPerSecond
                                    val delayMs = (excessBytes * throttleIntervalMs) / maxBytesPerSecond
                                    kotlinx.coroutines.delay(delayMs.coerceAtMost(ru.fire_core.xauplayer.core.config.AppConfig.MAX_DOWNLOAD_THROTTLE_DELAY_MS))
                                }
                                
                                bytesReadSinceThrottle = 0L
                                lastThrottleCheck = now
                            }
                            
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (maxSpeedKBps > 0) {
                                bytesReadSinceThrottle += bytesRead
                            }
                            
                            if (now - lastUpdate >= ru.fire_core.xauplayer.core.config.AppConfig.DOWNLOAD_PROGRESS_UPDATE_INTERVAL_MS) {
                                val speed = ((downloadedBytes - bytesAtLastUpdate) * ru.fire_core.xauplayer.core.config.AppConfig.MILLISECONDS_PER_SECOND) / (now - lastUpdate)
                                updateProgress(chapter.id, bookId, totalBytes, downloadedBytes, speed, DownloadState.DOWNLOADING, null)
                                
                                // Вычисляем общий прогресс загрузки книги
                                val allProgress = _downloadProgress.value
                                val bookProgresses = allProgress.values.filter { it.bookId == bookId }
                                val totalDownloaded = bookProgresses.sumOf { it.downloadedBytes }
                                // Используем заранее посчитанный тотал, если есть, иначе суммируем из прогрессов
                                val totalBookSize = bookTotalSizes[bookId] ?: bookProgresses.sumOf { it.totalBytes }
                                
                                // Подсчитываем количество скачанных глав: уже скачанные из БД
                                // Текущая глава еще не скачана (она в процессе), так что не включаем её
                                val downloadedCountFromDb = db.downloadedChapterDao().countByBookId(bookId)
                                // Добавляем завершенные главы из прогресса, исключая текущую (которая еще загружается)
                                val completedInProgress = bookProgresses.count { 
                                    it.state == ru.fire_core.xauplayer.download.DownloadState.COMPLETED && it.chapterId != chapter.id
                                }
                                val downloadedChaptersCount = downloadedCountFromDb + completedInProgress
                                
                                // Находим индекс текущей главы среди всех глав книги (используем кэш)
                                val currentChapterIndex = bookChapters.indexOfFirst { it.id == chapter.id }
                                val validChapterIndex = if (currentChapterIndex >= 0) currentChapterIndex else null
                                
                                notificationHelper.showDownloadProgress(
                                    chapterTitle = chapter.title,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                    speed = speed,
                                    currentChapterIndex = validChapterIndex,
                                    totalChapters = if (bookChapters.isNotEmpty()) bookChapters.size else null,
                                    totalDownloadedBytes = if (totalDownloaded > 0) totalDownloaded else null,
                                    totalBookBytes = if (totalBookSize > 0) totalBookSize else null,
                                    downloadedChaptersCount = if (bookChapters.isNotEmpty()) downloadedChaptersCount else null
                                )
                                lastUpdate = now
                                bytesAtLastUpdate = downloadedBytes
                            }
                        }
                        
                        // Проверяем не была ли отменена загрузка
                        if (!isActive) {
                            updateProgress(chapter.id, bookId, totalBytes, downloadedBytes, 0, DownloadState.CANCELLED, null)
                            activeDownloads.remove(chapter.id)
                            // НЕ удаляем частично загруженный файл
                            // Запускаем следующую загрузку из очереди
                            startNextDownload()
                            return@launch
                        }
                        
                        // Save to database
                        val downloaded = DownloadedChapter(
                            chapterId = chapter.id,
                            bookId = bookId,
                            localPath = outputFile.absolutePath,
                            downloadedAt = System.currentTimeMillis(),
                            fileSize = outputFile.length(),
                            isComplete = true
                        )
                        db.downloadedChapterDao().upsert(downloaded)
                        
                        updateProgress(chapter.id, bookId, totalBytes, downloadedBytes, 0, DownloadState.COMPLETED, null)
                        notificationHelper.showDownloadComplete(chapter.title)
                        activeDownloads.remove(chapter.id)
                        logger.info("DownloadManager", "Chapter ${chapter.id} downloaded successfully")
                        // Убираем из карты прогресса — UI переключится на «скачано»
                        clearProgress(chapter.id)
                        
                        // Проверяем, все ли главы книги скачаны, и обновляем статус
                        checkAndUpdateBookStatus(bookId)
                        
                        // Запускаем следующую загрузку из очереди
                        startNextDownload()
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                logger.info("DownloadManager", "Download cancelled for chapter ${chapter.id}")
                updateProgress(chapter.id, bookId, 0, 0, 0, DownloadState.CANCELLED, null)
                activeDownloads.remove(chapter.id)
                // Запускаем следующую загрузку из очереди
                startNextDownload()
            } catch (e: Exception) {
                logger.error("DownloadManager", "Failed to download chapter ${chapter.id}", e)
                clearProgress(chapter.id)
                notificationHelper.showDownloadError(chapter.title, e.message ?: "Unknown error")
                activeDownloads.remove(chapter.id)
                startNextDownload()
            }
        }
        activeDownloads[chapter.id] = job
    }

    fun cancelDownload(chapterId: Long) {
        activeDownloads[chapterId]?.cancel()
        activeDownloads.remove(chapterId)
        // Удаляем из очереди, если там есть
        downloadQueue.removeAll { it.first.id == chapterId }
        // Обновляем состояние
        clearProgress(chapterId)
        // Запускаем следующую загрузку из очереди
        downloadScope.launch {
            startNextDownload()
        }
    }
    
    fun removeFromQueue(chapterId: Long) {
        if (!activeDownloads.containsKey(chapterId)) {
            downloadQueue.removeAll { it.first.id == chapterId }
            clearProgress(chapterId)
            downloadScope.launch { startNextDownload() }
        }
    }
    
    suspend fun cancelBookDownload(bookId: Long, chapters: List<Chapter>) {
        withContext(Dispatchers.IO) {
            try {
                // Получаем список уже скачанных глав
                val downloadedChapters = db.downloadedChapterDao().getByBookId(bookId)
                val downloadedChapterIds = downloadedChapters.map { it.chapterId }.toSet()
                
                // Отменяем все активные загрузки для этой книги (только не скачанные)
                chapters.forEach { chapter ->
                    // Пропускаем уже скачанные главы
                    if (chapter.id in downloadedChapterIds) {
                        return@forEach
                    }
                    
                    activeDownloads[chapter.id]?.cancel()
                    activeDownloads.remove(chapter.id)
                    clearProgress(chapter.id)
                }
                
                // Очищаем очередь от глав этой книги (только не скачанные)
                downloadQueue.removeAll { (ch, _) -> 
                    ch.bookId == bookId && ch.id !in downloadedChapterIds
                }
                
                // Удаляем недоскачанные главы (те, которые не полностью скачаны)
                downloadedChapters.forEach { downloaded ->
                    val file = File(downloaded.localPath)
                    // Проверяем, полностью ли скачана глава
                    if (file.exists() && !downloaded.isComplete) {
                        // Удаляем частично скачанную главу
                        file.delete()
                        db.downloadedChapterDao().delete(downloaded.chapterId)
                        logger.info("DownloadManager", "Deleted partially downloaded chapter ${downloaded.chapterId}")
                    }
                }
                
                // Запускаем следующую загрузку из очереди
                startNextDownload()
            } catch (e: Exception) {
                logger.error("DownloadManager", "Failed to cancel book download $bookId", e)
            }
        }
    }

    fun downloadBook(chapters: List<Chapter>, bookId: Long) {
        // Кэшируем главы для этой книги
        cachedChapters[bookId] = chapters
        
        // Скачиваем главы по порядку (сначала 1, потом 2 и т.д.)
        // Сортируем главы по их порядку (realOrder или chapterOrder)
        val sortedChapters = chapters.sortedBy { 
            it.realOrder ?: it.chapterOrder 
        }
        
        downloadScope.launch {
            var totalBookSize = 0L

            sortedChapters.forEach { chapter ->
                val existing = withContext(Dispatchers.IO) {
                    db.downloadedChapterDao().getByChapterId(chapter.id)
                }
                val alreadyDownloaded = existing != null && File(existing.localPath).exists()
                val chapterSize = chapter.fileSizeBytes ?: existing?.fileSize ?: 0L

                if (alreadyDownloaded) {
                    clearProgress(chapter.id)
                    val size = chapterSize.takeIf { it > 0 } ?: existing!!.fileSize
                    if (size > 0) totalBookSize += size
                } else {
                    if (chapterSize > 0) {
                        totalBookSize += chapterSize
                        updateProgress(chapter.id, bookId, chapterSize, 0, 0, DownloadState.QUEUED, null)
                    } else {
                        updateProgress(chapter.id, bookId, 0, 0, 0, DownloadState.QUEUED, null)
                    }
                }
            }

            if (totalBookSize > 0) {
                bookTotalSizes[bookId] = totalBookSize
            } else {
                val calculatedSize = calculateBookTotalSize(sortedChapters, bookId)
                if (calculatedSize > 0) {
                    bookTotalSizes[bookId] = calculatedSize
                }
            }

            sortedChapters.forEach { chapter ->
                val existing = withContext(Dispatchers.IO) {
                    db.downloadedChapterDao().getByChapterId(chapter.id)
                }
                if (existing == null || !File(existing.localPath).exists()) {
                    downloadChapter(chapter, bookId)
                } else {
                    clearProgress(chapter.id)
                }
            }
        }
    }
    
    private suspend fun calculateBookTotalSize(chapters: List<Chapter>, bookId: Long): Long {
        return withContext(Dispatchers.IO) {
            try {
                val knownSize = chapters.sumOf { it.fileSizeBytes ?: 0L }
                if (knownSize > 0) {
                    chapters.forEach { chapter ->
                        val size = chapter.fileSizeBytes ?: 0L
                        if (size > 0) {
                            updateProgress(chapter.id, bookId, size, 0, 0, DownloadState.QUEUED, null)
                        }
                    }
                    return@withContext knownSize
                }

                val baseUrl = settingsStore.baseUrl.first()
                var totalSize = 0L

                chapters.forEach { chapter ->
                    try {
                        val url = ApiUrlBuilder.join(baseUrl, "books/${bookId}/download/${chapter.id}")
                        val headRequest = Request.Builder().url(url).head().build()
                        val headResponse = okHttpClient.newCall(headRequest).execute()

                        if (headResponse.isSuccessful) {
                            val contentLength = headResponse.header("Content-Length")?.toLongOrNull() ?: 0L
                            if (contentLength > 0) {
                                totalSize += contentLength
                                updateProgress(chapter.id, bookId, contentLength, 0, 0, DownloadState.QUEUED, null)
                            }
                        }
                        headResponse.close()
                    } catch (e: Exception) {
                        logger.warn("DownloadManager", "Failed to get size for chapter ${chapter.id}: ${e.message}")
                    }
                }

                totalSize
            } catch (e: Exception) {
                logger.error("DownloadManager", "Failed to calculate book total size", e)
                0L
            }
        }
    }

    suspend fun deleteBook(bookId: Long) {
        withContext(Dispatchers.IO) {
            try {
                val chapters = db.downloadedChapterDao().getByBookId(bookId)
                chapters.forEach { downloaded ->
                    val file = File(downloaded.localPath)
                    if (file.exists()) {
                        file.delete()
                    }
                }
                db.downloadedChapterDao().deleteByBookId(bookId)
            } catch (e: Exception) {
                logger.error("DownloadManager", "Failed to delete book $bookId", e)
            }
        }
    }

    suspend fun deleteChapter(chapterId: Long) {
        withContext(Dispatchers.IO) {
            try {
                val downloaded = db.downloadedChapterDao().getByChapterId(chapterId)
                downloaded?.let {
                    val file = File(it.localPath)
                    if (file.exists()) {
                        file.delete()
                    }
                    db.downloadedChapterDao().delete(chapterId)
                }
            } catch (e: Exception) {
                logger.error("DownloadManager", "Failed to delete chapter $chapterId", e)
            }
        }
    }

    suspend fun getDownloadedChapter(chapterId: Long): DownloadedChapter? {
        return db.downloadedChapterDao().getByChapterId(chapterId)
    }

    private fun updateProgress(
        chapterId: Long,
        bookId: Long,
        totalBytes: Long,
        downloadedBytes: Long,
        speed: Long,
        state: DownloadState,
        error: String?
    ) {
        val current = _downloadProgress.value.toMutableMap()
        current[chapterId] = DownloadProgress(
            chapterId = chapterId,
            bookId = bookId,
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            speed = speed,
            state = state,
            error = error
        )
        _downloadProgress.value = current
    }

    private fun clearProgress(chapterId: Long) {
        val current = _downloadProgress.value.toMutableMap()
        current.remove(chapterId)
        _downloadProgress.value = current
    }

    suspend fun isBookFullyDownloaded(chapters: List<Chapter>): Boolean {
        return chapters.all { chapter ->
            val downloaded = db.downloadedChapterDao().getByChapterId(chapter.id)
            downloaded != null && File(downloaded.localPath).exists()
        }
    }
    
    private suspend fun checkAndUpdateBookStatus(bookId: Long) {
        try {
            val chapters = db.chapterDao().getByBook(bookId)
            if (chapters.isNotEmpty() && isBookFullyDownloaded(chapters)) {
                // Все главы скачаны - можно обновить статус книги
                // Это будет сделано через ViewModel при необходимости
                logger.info("DownloadManager", "Book $bookId is fully downloaded")
            }
        } catch (e: Exception) {
            logger.error("DownloadManager", "Failed to check book status for $bookId", e)
        }
    }
}

