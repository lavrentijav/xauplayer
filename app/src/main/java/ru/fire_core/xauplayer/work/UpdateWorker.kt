package ru.fire_core.xauplayer.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ru.fire_core.xauplayer.core.config.AppConfig
import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.data.datastore.SettingsStore
import ru.fire_core.xauplayer.update.UpdateManager
import java.util.concurrent.TimeUnit

@HiltWorker
class UpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val updateManager: UpdateManager,
    private val settingsStore: SettingsStore,
    private val logger: AppLogger
) : CoroutineWorker(appContext, params) {
    
    override suspend fun doWork(): Result {
        return try {
            logger.info("UpdateWorker", "Starting update check...")
            
            // Проверяем, прошло ли 24 часа с последней проверки
            val lastCheck = settingsStore.getLastUpdateCheck()
            val now = System.currentTimeMillis()
            
            if (lastCheck > 0 && (now - lastCheck) < AppConfig.UPDATE_CHECK_INTERVAL_MS) {
                logger.info("UpdateWorker", "Update check skipped: only ${(now - lastCheck) / 1000 / 60} minutes since last check")
                // Планируем следующую проверку через оставшееся время
                scheduleNextCheck(now - lastCheck)
                return Result.success()
            }
            
            // Выполняем проверку обновлений
            val updateInfo = updateManager.checkForUpdate()
            
            // Сохраняем время последней проверки
            settingsStore.setLastUpdateCheck(now)
            
            if (updateInfo != null) {
                logger.info("UpdateWorker", "Update available: ${updateInfo.versionName} (${updateInfo.versionCode})")
            } else {
                logger.info("UpdateWorker", "No updates available")
            }
            
            // Планируем следующую проверку через 24 часа
            scheduleNextCheck(AppConfig.UPDATE_CHECK_INTERVAL_MS)
            
            Result.success()
        } catch (e: Exception) {
            logger.error("UpdateWorker", "Failed to check for updates", e)
            // Планируем повторную попытку через час в случае ошибки
            scheduleNextCheck(60 * 60 * 1000L)
            Result.retry()
        }
    }
    
    private fun scheduleNextCheck(delayMs: Long) {
        val delay = delayMs.coerceAtLeast(TimeUnit.HOURS.toMillis(1)) // Минимум 1 час
        val request = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "auto_update_check",
            ExistingWorkPolicy.REPLACE,
            request
        )
        
        logger.info("UpdateWorker", "Next update check scheduled in ${delay / 1000 / 60} minutes")
    }
    
    companion object {
        /**
         * Запускает периодическую проверку обновлений
         */
        fun schedulePeriodicCheck(context: Context) {
            val request = OneTimeWorkRequestBuilder<UpdateWorker>()
                .setInitialDelay(1, TimeUnit.HOURS) // Первая проверка через час после запуска
                .build()
            
            WorkManager.getInstance(context).enqueueUniqueWork(
                "auto_update_check",
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}

