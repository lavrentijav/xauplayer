package ru.fire_core.xauplayer.domain.repo

import ru.fire_core.xauplayer.data.network.ApiService
import ru.fire_core.xauplayer.data.network.StatisticsDto
import javax.inject.Inject

interface StatisticsRepository {
    suspend fun getYearStatistics(year: Int): StatisticsDto?
    suspend fun getMonthStatistics(year: Int, month: Int): StatisticsDto?
    suspend fun getRangeStatistics(startDate: String, endDate: String): StatisticsDto?
}

class StatisticsRepositoryImpl @Inject constructor(
    private val api: ApiService
) : StatisticsRepository {
    override suspend fun getYearStatistics(year: Int): StatisticsDto? {
        return try {
            api.getStatistics(year = year)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getMonthStatistics(year: Int, month: Int): StatisticsDto? {
        return try {
            api.getStatistics(year = year, month = month)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getRangeStatistics(startDate: String, endDate: String): StatisticsDto? {
        return try {
            api.getStatistics(startDate = startDate, endDate = endDate)
        } catch (e: Exception) {
            null
        }
    }
}
