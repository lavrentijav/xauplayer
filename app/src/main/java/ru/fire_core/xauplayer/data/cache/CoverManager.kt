package ru.fire_core.xauplayer.data.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.data.datastore.SettingsStore
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val settingsStore: SettingsStore,
    private val logger: AppLogger
) {
    private val coversDir = File(settingsStore.basepath, "covers")
    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        if (!coversDir.exists()) {
            coversDir.mkdirs()
        }
    }
    
    /**
     * Очищает старые обложки, если размер кэша превышает лимит
     * Использует LRU стратегию - удаляет самые старые файлы
     */
    private suspend fun enforceCacheSizeLimit() = withContext(Dispatchers.IO) {
        try {
            val maxSize = settingsStore.coverCacheSize.first()
            val files = coversDir.listFiles() ?: return@withContext
            
            // Вычисляем текущий размер кэша
            var currentSize = files.sumOf { it.length() }
            
            if (currentSize <= maxSize) {
                return@withContext // Размер в пределах лимита
            }
            
            // Сортируем файлы по времени последнего доступа (LRU)
            val sortedFiles = files.sortedBy { it.lastModified() }
            
            // Удаляем самые старые файлы пока не достигнем лимита
            for (file in sortedFiles) {
                if (currentSize <= maxSize) {
                    break
                }
                val fileSize = file.length()
                if (file.delete()) {
                    currentSize -= fileSize
                    logger.info("CoverManager", "Deleted old cover to free space: ${file.name}")
                }
            }
            
            logger.info("CoverManager", "Cache size after cleanup: ${currentSize / 1024 / 1024}MB / ${maxSize / 1024 / 1024}MB")
        } catch (e: Exception) {
            logger.error("CoverManager", "Error enforcing cache size limit", e)
        }
    }
    
    /**
     * Получает URL обложки книги из API
     */
    suspend fun getBookCoverUrl(bookId: Long): String {
        val baseUrl = settingsStore.baseUrl.first()
        return "$baseUrl/covers/books/$bookId".replace("//", "/")
    }
    
    /**
     * Получает URL обложки серии из API
     */
    suspend fun getSeriesCoverUrl(seriesId: Long): String {
        val baseUrl = settingsStore.baseUrl.first()
        return "$baseUrl/covers/series/$seriesId".replace("//", "/")
    }
    
    /**
     * Загружает обложку книги и сохраняет в кэш
     * @return Путь к локальному файлу или null, если загрузка не удалась
     */
    suspend fun downloadBookCover(bookId: Long): String? = withContext(Dispatchers.IO) {
        try {
            val cachedFile = File(coversDir, "book_$bookId.jpg")
            // Если файл уже есть в кэше, возвращаем его путь
            if (cachedFile.exists()) {
                logger.info("CoverManager", "Book cover $bookId already cached")
                return@withContext cachedFile.absolutePath
            }
            
            val url = getBookCoverUrl(bookId)
            logger.info("CoverManager", "Downloading book cover from $url")
            
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                logger.warn("CoverManager", "Failed to download book cover $bookId: HTTP ${response.code}")
                return@withContext null
            }
            
            // Читаем данные в память для повторного использования
            val bodyBytes = response.body?.bytes() ?: return@withContext null
            
            // Ограничиваем размер bitmap для предотвращения OutOfMemoryError
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bodyBytes, 0, bodyBytes.size, options)
            
            // Вычисляем inSampleSize для уменьшения размера
            val maxSize = 512 // Максимальный размер обложки
            var sampleSize = 1
            if (options.outHeight > maxSize || options.outWidth > maxSize) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / sampleSize) >= maxSize && (halfWidth / sampleSize) >= maxSize) {
                    sampleSize *= 2
                }
            }
            
            // Декодируем с уменьшенным размером
            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize
            
            val bitmap = BitmapFactory.decodeByteArray(bodyBytes, 0, bodyBytes.size, options)
            
            if (bitmap != null) {
                // Сохраняем в кэш
                FileOutputStream(cachedFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                bitmap.recycle() // Освобождаем память
                logger.info("CoverManager", "Book cover $bookId cached successfully")
                
                // Проверяем размер кэша и очищаем при необходимости
                enforceCacheSizeLimit()
                
                cachedFile.absolutePath
            } else {
                logger.warn("CoverManager", "Failed to decode book cover $bookId")
                null
            }
        } catch (e: Exception) {
            logger.error("CoverManager", "Error downloading book cover $bookId", e)
            null
        }
    }
    
    /**
     * Загружает обложку серии и сохраняет в кэш
     * @return Путь к локальному файлу или null, если загрузка не удалась
     */
    suspend fun downloadSeriesCover(seriesId: Long): String? = withContext(Dispatchers.IO) {
        try {
            val cachedFile = File(coversDir, "series_$seriesId.jpg")
            // Если файл уже есть в кэше, возвращаем его путь
            if (cachedFile.exists()) {
                logger.info("CoverManager", "Series cover $seriesId already cached")
                return@withContext cachedFile.absolutePath
            }
            
            val url = getSeriesCoverUrl(seriesId)
            logger.info("CoverManager", "Downloading series cover from $url")
            
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                logger.warn("CoverManager", "Failed to download series cover $seriesId: HTTP ${response.code}")
                return@withContext null
            }
            
            // Читаем данные в память для повторного использования
            val bodyBytes = response.body?.bytes() ?: return@withContext null
            
            // Ограничиваем размер bitmap для предотвращения OutOfMemoryError
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bodyBytes, 0, bodyBytes.size, options)
            
            // Вычисляем inSampleSize для уменьшения размера
            val maxSize = 512 // Максимальный размер обложки
            var sampleSize = 1
            if (options.outHeight > maxSize || options.outWidth > maxSize) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / sampleSize) >= maxSize && (halfWidth / sampleSize) >= maxSize) {
                    sampleSize *= 2
                }
            }
            
            // Декодируем с уменьшенным размером
            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize
            
            val bitmap = BitmapFactory.decodeByteArray(bodyBytes, 0, bodyBytes.size, options)
            
            if (bitmap != null) {
                // Сохраняем в кэш
                FileOutputStream(cachedFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                bitmap.recycle() // Освобождаем память
                logger.info("CoverManager", "Series cover $seriesId cached successfully")
                
                // Проверяем размер кэша и очищаем при необходимости
                enforceCacheSizeLimit()
                
                cachedFile.absolutePath
            } else {
                logger.warn("CoverManager", "Failed to decode series cover $seriesId")
                null
            }
        } catch (e: Exception) {
            logger.error("CoverManager", "Error downloading series cover $seriesId", e)
            null
        }
    }
    
    /**
     * Получает путь к кэшированной обложке книги
     * @return Путь к локальному файлу или null, если файл не найден
     */
    fun getCachedBookCoverPath(bookId: Long): String? {
        val cachedFile = File(coversDir, "book_$bookId.jpg")
        return if (cachedFile.exists()) cachedFile.absolutePath else null
    }
    
    /**
     * Получает путь к кэшированной обложке серии
     * @return Путь к локальному файлу или null, если файл не найден
     */
    fun getCachedSeriesCoverPath(seriesId: Long): String? {
        val cachedFile = File(coversDir, "series_$seriesId.jpg")
        return if (cachedFile.exists()) cachedFile.absolutePath else null
    }
    
    /**
     * Получает URL обложки для отображения (сначала проверяет кэш, затем API)
     */
    suspend fun getBookCoverUrlForDisplay(bookId: Long): String {
        val cachedPath = getCachedBookCoverPath(bookId)
        return if (cachedPath != null) {
            "file://$cachedPath"
        } else {
            getBookCoverUrl(bookId)
        }
    }
    
    // Кэш для запомнивания отсутствия обложки серии
    private val seriesCoverNotFound = mutableSetOf<Long>()
    
    /**
     * Получает URL обложки серии для отображения (сначала проверяет кэш, затем API)
     * Если у серии нет обложки, пытается взять обложку из первой доступной книги серии
     */
    suspend fun getSeriesCoverUrlForDisplay(seriesId: Long, booksInSeries: List<ru.fire_core.xauplayer.data.local.entities.Book>? = null): String {
        // Сначала проверяем кэш обложки серии
        val cachedSeriesPath = getCachedSeriesCoverPath(seriesId)
        if (cachedSeriesPath != null) {
            return "file://$cachedSeriesPath"
        }
        
        // Если знаем что обложки нет, сразу используем обложку книги из кэша
        if (seriesId in seriesCoverNotFound && booksInSeries != null && booksInSeries.isNotEmpty()) {
            for (book in booksInSeries) {
                val bookCoverPath = getCachedBookCoverPath(book.id)
                if (bookCoverPath != null) {
                    // В фоне пытаемся загрузить правильную обложку серии
                    backgroundScope.launch {
                        try {
                            downloadSeriesCover(seriesId)
                            // Если загрузка успешна, удаляем из списка "нет обложки"
                            seriesCoverNotFound.remove(seriesId)
                        } catch (e: Exception) {
                            // Игнорируем ошибки
                        }
                    }
                    return "file://$bookCoverPath"
                }
            }
        }
        
        // Пытаемся загрузить обложку серии
        val seriesCover = downloadSeriesCover(seriesId)
        if (seriesCover != null) {
            seriesCoverNotFound.remove(seriesId) // Удаляем из списка "нет обложки"
            return "file://$seriesCover"
        }
        
        // Если обложка серии не загрузилась, запоминаем это
        seriesCoverNotFound.add(seriesId)
        
        // Если у серии нет обложки, пытаемся взять обложку из книг (сначала из кэша)
        if (booksInSeries != null && booksInSeries.isNotEmpty()) {
            // Сначала ищем в кэше
            for (book in booksInSeries) {
                val bookCoverPath = getCachedBookCoverPath(book.id)
                if (bookCoverPath != null) {
                    // В фоне продолжаем пытаться загрузить правильную обложку серии
                    backgroundScope.launch {
                        try {
                            downloadSeriesCover(seriesId)
                            // Если загрузка успешна, удаляем из списка "нет обложки"
                            seriesCoverNotFound.remove(seriesId)
                        } catch (e: Exception) {
                            // Игнорируем ошибки
                        }
                    }
                    return "file://$bookCoverPath"
                }
            }
            
            // Если в кэше нет, пытаемся загрузить обложку книги
            for (book in booksInSeries) {
                val downloadedBookCover = downloadBookCover(book.id)
                if (downloadedBookCover != null) {
                    // В фоне продолжаем пытаться загрузить правильную обложку серии
                    backgroundScope.launch {
                        try {
                            downloadSeriesCover(seriesId)
                            // Если загрузка успешна, удаляем из списка "нет обложки"
                            seriesCoverNotFound.remove(seriesId)
                        } catch (e: Exception) {
                            // Игнорируем ошибки
                        }
                    }
                    return "file://$downloadedBookCover"
                }
            }
        }
        
        // Если ничего не найдено, возвращаем URL API
        return getSeriesCoverUrl(seriesId)
    }
    
    /**
     * Удаляет кэшированную обложку книги
     */
    fun deleteBookCover(bookId: Long) {
        val cachedFile = File(coversDir, "book_$bookId.jpg")
        if (cachedFile.exists()) {
            cachedFile.delete()
            logger.info("CoverManager", "Deleted cached book cover $bookId")
        }
    }
    
    /**
     * Удаляет кэшированную обложку серии
     */
    fun deleteSeriesCover(seriesId: Long) {
        val cachedFile = File(coversDir, "series_$seriesId.jpg")
        if (cachedFile.exists()) {
            cachedFile.delete()
            logger.info("CoverManager", "Deleted cached series cover $seriesId")
        }
    }
}

