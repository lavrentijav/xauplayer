package ru.fire_core.xauplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.fire_core.xauplayer.update.UpdateManager
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateManager: UpdateManager
) : ViewModel() {

    val updateProgress: StateFlow<ru.fire_core.xauplayer.update.UpdateProgress> = updateManager.updateProgress

    fun checkForUpdate() {
        viewModelScope.launch {
            updateManager.checkForUpdate()
        }
    }

    /**
     * Проверяет, есть ли разрешение на установку пакетов
     */
    fun canInstallPackages(): Boolean {
        return updateManager.canInstallPackages()
    }

    /**
     * Запрашивает разрешение на установку пакетов
     * @param activity Activity для запуска Intent запроса разрешения
     * @return true, если разрешение уже есть или не требуется
     */
    fun requestInstallPermission(activity: android.app.Activity): Boolean {
        return updateManager.requestInstallPermission(activity)
    }

    suspend fun downloadAndInstall() {
        val updateInfo = updateManager.updateProgress.value.updateInfo
        if (updateInfo != null) {
            val apkFile = updateManager.downloadUpdate(updateInfo)
            if (apkFile != null) {
                updateManager.installUpdate(apkFile)
            }
        }
    }

    suspend fun installUpdate() {
        val apkFile = updateManager.getDownloadedApkFile()
        if (apkFile != null && apkFile.exists()) {
            updateManager.installUpdate(apkFile)
        }
    }
}

