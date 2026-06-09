package ru.fire_core.xauplayer.domain.repo

import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.data.cache.CoverManager
import ru.fire_core.xauplayer.data.local.AppDatabase
import ru.fire_core.xauplayer.data.local.entities.Series
import ru.fire_core.xauplayer.data.network.ApiService
import javax.inject.Inject

interface SeriesRepository {
    suspend fun syncSeries(search: String? = null)
    suspend fun getSeries(): List<Series>
    suspend fun getSeries(id: Long): Series?
    suspend fun getSeriesBooks(seriesId: Long, search: String? = null): List<ru.fire_core.xauplayer.data.local.entities.Book>
    suspend fun createSeries(name: String, description: String? = null, coverUrl: String? = null): Series?
    suspend fun updateSeries(seriesId: Long, name: String? = null, description: String? = null, coverUrl: String? = null): Series?
    suspend fun deleteSeries(seriesId: Long)
}

class SeriesRepositoryImpl @Inject constructor(
    private val api: ApiService,
    private val db: AppDatabase,
    private val coverManager: CoverManager,
    private val logger: AppLogger
) : SeriesRepository {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    override suspend fun syncSeries(search: String?) {
        try {
            val remote = api.getSeries(search = search)
            val mapped = remote.map {
                Series(
                    id = it.id,
                    name = it.name,
                    description = it.description,
                    coverUrl = it.cover_url,
                    createdAt = it.created_at
                )
            }
            db.seriesDao().upsertAll(mapped)
            
            // Загружаем обложки серий в фоне
            // Если у серии нет обложки, пытаемся взять обложку из первой доступной книги
            repositoryScope.launch {
                mapped.forEach { series ->
                    try {
                        val coverDownloaded = coverManager.downloadSeriesCover(series.id)
                        // Если обложка серии не загрузилась (404), пытаемся взять из книг
                        if (coverDownloaded == null) {
                            try {
                                val seriesBooks = getSeriesBooks(series.id)
                                if (seriesBooks.isNotEmpty()) {
                                    // Ищем первую книгу с обложкой
                                    for (book in seriesBooks) {
                                        val bookCover = coverManager.downloadBookCover(book.id)
                                        if (bookCover != null) {
                                            logger.info("SeriesRepository", "Using book ${book.id} cover for series ${series.id}")
                                            break
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                logger.warn("SeriesRepository", "Failed to get book cover for series ${series.id}", e)
                            }
                        }
                    } catch (e: Exception) {
                        logger.warn("SeriesRepository", "Failed to download cover for series ${series.id}", e)
                    }
                }
            }
        } catch (e: Exception) {
            // Игнорируем ошибки сети
        }
    }
    
    override suspend fun getSeries(): List<Series> = db.seriesDao().getAll()
    
    override suspend fun getSeries(id: Long): Series? = db.seriesDao().getById(id)
    
    override suspend fun getSeriesBooks(seriesId: Long, search: String?): List<ru.fire_core.xauplayer.data.local.entities.Book> {
        return try {
            val remote = api.getSeriesBooks(seriesId, search)
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
            db.bookDao().getBySeries(seriesId)
        } catch (e: Exception) {
            db.bookDao().getBySeries(seriesId)
        }
    }
    
    override suspend fun createSeries(name: String, description: String?, coverUrl: String?): Series? {
        logger.warn("SeriesRepository", "createSeries is not supported by public API")
        return null
    }

    override suspend fun updateSeries(seriesId: Long, name: String?, description: String?, coverUrl: String?): Series? {
        logger.warn("SeriesRepository", "updateSeries is not supported by public API")
        return null
    }

    override suspend fun deleteSeries(seriesId: Long) {
        logger.warn("SeriesRepository", "deleteSeries is not supported by public API")
    }
}

