package ru.fire_core.xauplayer.domain.usecase.series

import ru.fire_core.xauplayer.domain.repo.SeriesRepository
import javax.inject.Inject

class SeriesUseCases @Inject constructor(private val repo: SeriesRepository) {
    suspend fun syncSeries() = repo.syncSeries()
    suspend fun getSeries() = repo.getSeries()
    suspend fun getSeries(id: Long) = repo.getSeries(id)
    suspend fun getSeriesBooks(seriesId: Long) = repo.getSeriesBooks(seriesId)
    suspend fun createSeries(name: String, description: String? = null, coverUrl: String? = null) = repo.createSeries(name, description, coverUrl)
    suspend fun updateSeries(seriesId: Long, name: String? = null, description: String? = null, coverUrl: String? = null) = repo.updateSeries(seriesId, name, description, coverUrl)
    suspend fun deleteSeries(seriesId: Long) = repo.deleteSeries(seriesId)
}

