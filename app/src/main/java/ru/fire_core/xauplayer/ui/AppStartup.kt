package ru.fire_core.xauplayer.ui

import android.app.Application
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import ru.fire_core.xauplayer.work.ProgressWorker
import ru.fire_core.xauplayer.work.UpdateWorker

// Kick off the worker once the app starts via a simple object init from MainActivity
object AppStartup {
    fun scheduleWorkers(app: Application) {
        Log.d("AppStartup", "scheduleWorkers START")
        val workManager = WorkManager.getInstance(app)
        
        // Запускаем worker для синхронизации прогресса
        val progressReq = OneTimeWorkRequestBuilder<ProgressWorker>().build()
        workManager.enqueueUniqueWork("progress_sync", ExistingWorkPolicy.KEEP, progressReq)
        
        // Запускаем worker для автоматической проверки обновлений
        UpdateWorker.schedulePeriodicCheck(app)
        
        Log.d("AppStartup", "scheduleWorkers END")
    }
}


