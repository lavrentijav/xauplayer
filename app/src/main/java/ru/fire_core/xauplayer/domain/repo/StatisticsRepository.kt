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
            // Используем новый единый endpoint
            api.getStatistics(year = year)
        } catch (e: Exception) {
            // Пробуем использовать старый endpoint для обратной совместимости
            try {
                api.getYearStatistics(year)
            } catch (e2: Exception) {
                null
            }
        }
    }
    
    override suspend fun getMonthStatistics(year: Int, month: Int): StatisticsDto? {
        return try {
            // Используем новый единый endpoint
            api.getStatistics(year = year, month = month)
        } catch (e: Exception) {
            // Пробуем использовать старый endpoint для обратной совместимости
            try {
                api.getMonthStatistics(year, month)
            } catch (e2: Exception) {
                null
            }
        }
    }
    
    override suspend fun getRangeStatistics(startDate: String, endDate: String): StatisticsDto? {
        return try {
            // Используем новый единый endpoint
            api.getStatistics(startDate = startDate, endDate = endDate)
        } catch (e: Exception) {
            // Пробуем использовать старый endpoint для обратной совместимости
            try {
                api.getRangeStatistics(startDate, endDate)
            } catch (e2: Exception) {
                null
            }
        }
    }
}
