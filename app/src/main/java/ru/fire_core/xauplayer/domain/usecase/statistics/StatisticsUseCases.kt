package ru.fire_core.xauplayer.domain.usecase.statistics

import ru.fire_core.xauplayer.domain.repo.StatisticsRepository
import javax.inject.Inject

class StatisticsUseCases @Inject constructor(private val repo: StatisticsRepository) {
    suspend fun getYearStatistics(year: Int) = repo.getYearStatistics(year)
    suspend fun getMonthStatistics(year: Int, month: Int) = repo.getMonthStatistics(year, month)
    suspend fun getRangeStatistics(startDate: String, endDate: String) = repo.getRangeStatistics(startDate, endDate)
}

