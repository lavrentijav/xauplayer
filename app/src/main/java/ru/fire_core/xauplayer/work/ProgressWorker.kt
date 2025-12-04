package ru.fire_core.xauplayer.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ru.fire_core.xauplayer.domain.repo.ProgressRepository

@HiltWorker
class ProgressWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: ProgressRepository
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        try {
            // For demo: nothing specific; in real code, iterate progresses and sync
            // Reschedule self after ~30 seconds
            val request = OneTimeWorkRequestBuilder<ProgressWorker>()
                .setInitialDelay(java.time.Duration.ofSeconds(30))
                .build()
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                "progress_sync",
                ExistingWorkPolicy.REPLACE,
                request
            )
            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }
}


