package ru.fire_core.xauplayer.domain.usecase.status

import ru.fire_core.xauplayer.domain.repo.StatusRepository
import javax.inject.Inject

class StatusUseCases @Inject constructor(private val repo: StatusRepository) {
    // Book Status
    suspend fun getBookStatus(bookId: Long) = repo.getBookStatus(bookId)
    suspend fun setBookStatus(bookId: Long, status: String) = repo.setBookStatus(bookId, status)
    suspend fun updateBookStatus(bookId: Long, status: String) = repo.updateBookStatus(bookId, status)
    suspend fun deleteBookStatus(bookId: Long) = repo.deleteBookStatus(bookId)
    suspend fun getBooksByStatus(status: String) = repo.getBooksByStatus(status)
    suspend fun getAllBookStatuses() = repo.getAllBookStatuses()
    
    // Series Status
    suspend fun getSeriesStatus(seriesId: Long) = repo.getSeriesStatus(seriesId)
    suspend fun setSeriesStatus(seriesId: Long, status: String) = repo.setSeriesStatus(seriesId, status)
    suspend fun updateSeriesStatus(seriesId: Long, status: String) = repo.updateSeriesStatus(seriesId, status)
    suspend fun deleteSeriesStatus(seriesId: Long) = repo.deleteSeriesStatus(seriesId)
    suspend fun getSeriesByStatus(status: String) = repo.getSeriesByStatus(status)
}

