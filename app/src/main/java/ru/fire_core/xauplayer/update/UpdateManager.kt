package ru.fire_core.xauplayer.update

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.fire_core.xauplayer.BuildConfig
import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.data.datastore.SettingsStore
import ru.fire_core.xauplayer.data.network.ApiUrlBuilder
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val description: String? = null,
    val mandatory: Boolean = false
)

enum class UpdateState {
    IDLE,
    CHECKING,
    UPDATE_AVAILABLE,
    DOWNLOADING,
    DOWNLOADED,
    INSTALLING,
    ERROR
}

data class UpdateProgress(
    val state: UpdateState,
    val progress: Int = 0, // 0-100
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val error: String? = null,
    val updateInfo: UpdateInfo? = null,
    val statusMessage: String? = null // Текстовое описание текущего статуса
)

@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val settingsStore: SettingsStore,
    private val logger: AppLogger
) {
    private val _updateProgress = MutableStateFlow<UpdateProgress>(
        UpdateProgress(state = UpdateState.IDLE)
    )
    val updateProgress: StateFlow<UpdateProgress> = _updateProgress

    private val updateFileDir = File(context.getExternalFilesDir(null), "updates")
    private var currentUpdateFile: File? = null

    init {
        if (!updateFileDir.exists()) {
            updateFileDir.mkdirs()
        }
    }

    private fun getCurrentVersionCode(): Int {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
        } catch (e: PackageManager.NameNotFoundException) {
            logger.error("UpdateManager", "Failed to get version code", e)
            BuildConfig.VERSION_CODE
        }
    }
    
    private fun getCurrentVersionName(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: BuildConfig.VERSION_NAME
        } catch (e: PackageManager.NameNotFoundException) {
            logger.error("UpdateManager", "Failed to get version name", e)
            BuildConfig.VERSION_NAME ?: "0.0.0"
        }
    }
    
    /**
     * Парсит версию в формате "major.stage.build" (например, "1.2.34")
     * Формат версии:
     * - major - глобальная версия сервиса
     * - stage - новый этап развития
     * - build - buildversion этапа развития (может доходить до тысяч, это просто номер билда с новой эры)
     * @return Triple(major, stage, build) или null если версия некорректна
     */
    private fun parseVersion(version: String): Triple<Int, Int, Int>? {
        return try {
            val parts = version.split(".")
            if (parts.size == 3) {
                val major = parts[0].toIntOrNull() ?: return null
                val stage = parts[1].toIntOrNull() ?: return null
                // buildversion может быть большим числом (до тысяч), это просто номер билда с новой эры
                val build = parts[2].toIntOrNull() ?: return null
                Triple(major, stage, build)
            } else {
                null
            }
        } catch (e: Exception) {
            logger.warn("UpdateManager", "Failed to parse version: $version", e)
            null
        }
    }
    
    /**
     * Сравнивает две версии в формате "major.stage.build"
     * @return true если serverVersion > currentVersion
     */
    private fun isVersionNewer(serverVersion: String, currentVersion: String): Boolean {
        val server = parseVersion(serverVersion) ?: return false
        val current = parseVersion(currentVersion) ?: return false
        
        // Сравниваем сначала глобальную версию (major)
        if (server.first > current.first) return true
        if (server.first < current.first) return false
        
        // Если major одинаковые, сравниваем этап развития (stage)
        if (server.second > current.second) return true
        if (server.second < current.second) return false
        
        // Если stage одинаковые, сравниваем buildversion этапа (build)
        return server.third > current.third
    }
    
    private fun getArchitecture(): String {
        val arch = android.os.Build.SUPPORTED_ABIS[0]
        return when {
            arch.contains("arm64") -> "arm64"
            arch.contains("arm") -> "arm"
            arch.contains("x86_64") -> "x64"
            arch.contains("x86") -> "x86"
            else -> "arm64"
        }
    }

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            _updateProgress.value = _updateProgress.value.copy(
                state = UpdateState.CHECKING,
                statusMessage = "Проверка наличия обновлений..."
            )

            val updateUrl = settingsStore.updateUrl.first()
            val apiBaseUrl = settingsStore.baseUrl.first()
            if (updateUrl.isBlank()) {
                logger.info("UpdateManager", "Update URL not configured")
                _updateProgress.value = _updateProgress.value.copy(
                    state = UpdateState.IDLE,
                    statusMessage = "URL обновлений не настроен"
                )
                return@withContext null
            }

            val versionBaseUrl = ApiUrlBuilder.resolveVersionBaseUrl(updateUrl, apiBaseUrl)
            val arch = getArchitecture()
            val versionUrl = ApiUrlBuilder.join(versionBaseUrl, "version?os_type=android&arch=$arch")
            logger.info("UpdateManager", "Checking for updates: $versionUrl")

            _updateProgress.value = _updateProgress.value.copy(
                statusMessage = "Запрос информации о версии с сервера..."
            )

            val request = Request.Builder().url(versionUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                logger.warn("UpdateManager", "Update check failed: HTTP ${response.code}")
                _updateProgress.value = _updateProgress.value.copy(
                    state = UpdateState.ERROR,
                    error = "HTTP ${response.code}",
                    statusMessage = "Ошибка при проверке обновлений: HTTP ${response.code}"
                )
                return@withContext null
            }

            _updateProgress.value = _updateProgress.value.copy(
                statusMessage = "Обработка ответа сервера..."
            )

            val body = response.body?.string() ?: return@withContext null
            val json = org.json.JSONObject(body)

            // Получаем версию с сервера
            val serverVersion = json.getString("version")
            val currentVersion = getCurrentVersionName()

            _updateProgress.value = _updateProgress.value.copy(
                statusMessage = "Сравнение версий: текущая $currentVersion, серверная $serverVersion"
            )

            // Парсим версии для проверки корректности
            val serverParsed = parseVersion(serverVersion)
            val currentParsed = parseVersion(currentVersion)
            
            if (serverParsed == null) {
                logger.warn("UpdateManager", "Invalid server version format: $serverVersion")
                _updateProgress.value = _updateProgress.value.copy(
                    state = UpdateState.ERROR,
                    error = "Некорректный формат версии на сервере: $serverVersion",
                    statusMessage = "Ошибка: некорректный формат версии на сервере"
                )
                return@withContext null
            }
            
            if (currentParsed == null) {
                logger.warn("UpdateManager", "Invalid current version format: $currentVersion")
                _updateProgress.value = _updateProgress.value.copy(
                    state = UpdateState.ERROR,
                    error = "Некорректный формат текущей версии: $currentVersion",
                    statusMessage = "Ошибка: некорректный формат текущей версии"
                )
                return@withContext null
            }

            // Сравниваем версии по компонентам (major.stage.build)
            // buildversion может доходить до тысяч - это просто номер билда с новой эры
            if (!isVersionNewer(serverVersion, currentVersion)) {
                logger.info("UpdateManager", "No updates available. Current: $currentVersion, Server: $serverVersion")
                _updateProgress.value = _updateProgress.value.copy(
                    state = UpdateState.IDLE,
                    statusMessage = "Обновления не найдены. У вас установлена актуальная версия."
                )
                return@withContext null
            }
            
            // Получаем build_number для versionCode (используем buildversion - последний компонент версии)
            // buildversion может быть большим числом (до тысяч)
            val serverVersionCode = serverParsed.third

            // Формируем URL для скачивания обновления
            val downloadUrl = ApiUrlBuilder.join(versionBaseUrl, "release?os_type=android&arch=$arch")
            
            val updateInfo = UpdateInfo(
                versionCode = serverVersionCode,
                versionName = serverVersion,
                downloadUrl = downloadUrl,
                description = "Доступна новая версия $serverVersion",
                mandatory = false
            )

            logger.info("UpdateManager", "Update available: ${updateInfo.versionName} (build ${updateInfo.versionCode})")
            _updateProgress.value = _updateProgress.value.copy(
                state = UpdateState.UPDATE_AVAILABLE,
                updateInfo = updateInfo,
                statusMessage = "Доступна новая версия: $serverVersion"
            )

            return@withContext updateInfo
        } catch (e: Exception) {
            logger.error("UpdateManager", "Failed to check for updates", e)
            _updateProgress.value = _updateProgress.value.copy(
                state = UpdateState.ERROR,
                error = e.message ?: "Unknown error",
                statusMessage = "Ошибка при проверке обновлений: ${e.message ?: "Неизвестная ошибка"}"
            )
            return@withContext null
        }
    }

    suspend fun downloadUpdate(updateInfo: UpdateInfo): File? = withContext(Dispatchers.IO) {
        try {
            _updateProgress.value = _updateProgress.value.copy(
                state = UpdateState.DOWNLOADING,
                updateInfo = updateInfo,
                progress = 0,
                statusMessage = "Подготовка к загрузке обновления..."
            )

            val fileName = "update_${updateInfo.versionCode}.apk"
            val outputFile = File(updateFileDir, fileName)
            
            // Удаляем старые файлы обновлений
            updateFileDir.listFiles()?.forEach { it.delete() }
            
            currentUpdateFile = outputFile

            _updateProgress.value = _updateProgress.value.copy(
                statusMessage = "Подключение к серверу..."
            )

            val request = Request.Builder().url(updateInfo.downloadUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                logger.error("UpdateManager", "Download failed: HTTP ${response.code}")
                _updateProgress.value = _updateProgress.value.copy(
                    state = UpdateState.ERROR,
                    error = "HTTP ${response.code}",
                    statusMessage = "Ошибка при загрузке: HTTP ${response.code}"
                )
                return@withContext null
            }

            val totalBytes = response.body?.contentLength() ?: 0L
            val totalMb = if (totalBytes > 0) totalBytes / (1024.0 * 1024.0) else 0.0
            
            _updateProgress.value = _updateProgress.value.copy(
                statusMessage = "Загрузка обновления (0%)..."
            )

            response.body?.byteStream()?.use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var downloadedBytes = 0L
                    
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break
                        
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        val progress = if (totalBytes > 0) {
                            ((downloadedBytes * 100) / totalBytes).toInt()
                        } else {
                            0
                        }
                        
                        val downloadedMb = downloadedBytes / (1024.0 * 1024.0)
                        val statusMsg = if (totalBytes > 0) {
                            "Загрузка обновления: $progress% (${"%.1f".format(downloadedMb)} / ${"%.1f".format(totalMb)} MB)"
                        } else {
                            "Загрузка обновления: ${"%.1f".format(downloadedMb)} MB..."
                        }
                        
                        _updateProgress.value = _updateProgress.value.copy(
                            progress = progress,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                            statusMessage = statusMsg
                        )
                    }
                }
            }

            logger.info("UpdateManager", "Update downloaded: ${outputFile.absolutePath}")
            _updateProgress.value = _updateProgress.value.copy(
                state = UpdateState.DOWNLOADED,
                progress = 100,
                statusMessage = "Обновление успешно загружено"
            )

            return@withContext outputFile
        } catch (e: Exception) {
            logger.error("UpdateManager", "Failed to download update", e)
            _updateProgress.value = _updateProgress.value.copy(
                state = UpdateState.ERROR,
                error = e.message ?: "Unknown error",
                statusMessage = "Ошибка при загрузке: ${e.message ?: "Неизвестная ошибка"}"
            )
            return@withContext null
        }
    }

    /**
     * Проверяет, есть ли у приложения разрешение на установку пакетов
     * Для Android 8.0+ требуется разрешение REQUEST_INSTALL_PACKAGES
     */
    fun canInstallPackages(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                context.packageManager.canRequestPackageInstalls().also { canInstall ->
                    logger.info("UpdateManager", "Can install packages: $canInstall (Android ${android.os.Build.VERSION.SDK_INT})")
                    if (!canInstall) {
                        logger.warn("UpdateManager", "Install permission not granted. User needs to grant REQUEST_INSTALL_PACKAGES permission")
                    }
                }
            } catch (e: Exception) {
                logger.error("UpdateManager", "Failed to check install permission", e)
                // Для старых версий Android разрешение не требуется
                android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O
            }
        } else {
            // Для Android < 8.0 разрешение не требуется
            true
        }
    }

    /**
     * Запрашивает разрешение на установку пакетов (для Android 8.0+)
     * Возвращает true, если разрешение уже есть или не требуется
     */
    fun requestInstallPermission(activity: android.app.Activity): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    logger.info("UpdateManager", "Requesting install permission...")
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    }
                    activity.startActivity(intent)
                    false
                } else {
                    true
                }
            } catch (e: Exception) {
                logger.error("UpdateManager", "Failed to request install permission", e)
                false
            }
        } else {
            true
        }
    }

    suspend fun installUpdate(apkFile: File): Boolean = withContext(Dispatchers.Main) {
        try {
            logger.info("UpdateManager", "Starting installation process for: ${apkFile.absolutePath}")
            logger.info("UpdateManager", "File exists: ${apkFile.exists()}, size: ${apkFile.length()} bytes")
            
            // Проверяем существование файла
            if (!apkFile.exists()) {
                val errorMsg = "APK file does not exist: ${apkFile.absolutePath}"
                logger.error("UpdateManager", errorMsg)
                _updateProgress.value = _updateProgress.value.copy(
                    state = UpdateState.ERROR,
                    error = errorMsg,
                    statusMessage = "Ошибка: файл обновления не найден"
                )
                return@withContext false
            }

            // Проверяем размер файла
            if (apkFile.length() == 0L) {
                val errorMsg = "APK file is empty: ${apkFile.absolutePath}"
                logger.error("UpdateManager", errorMsg)
                _updateProgress.value = _updateProgress.value.copy(
                    state = UpdateState.ERROR,
                    error = errorMsg,
                    statusMessage = "Ошибка: файл обновления пуст"
                )
                return@withContext false
            }

            // Проверяем разрешение на установку (для Android 8.0+)
            if (!canInstallPackages()) {
                val errorMsg = "Install permission not granted. Please grant REQUEST_INSTALL_PACKAGES permission in settings"
                logger.warn("UpdateManager", errorMsg)
                _updateProgress.value = _updateProgress.value.copy(
                    state = UpdateState.ERROR,
                    error = errorMsg,
                    statusMessage = "Требуется разрешение на установку приложений. Пожалуйста, предоставьте разрешение в настройках"
                )
                return@withContext false
            }

            _updateProgress.value = _updateProgress.value.copy(
                state = UpdateState.INSTALLING,
                statusMessage = "Подготовка к установке обновления..."
            )

            logger.info("UpdateManager", "Getting FileProvider URI...")
            val fileProviderUri = try {
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
            } catch (e: Exception) {
                val errorMsg = "Failed to get FileProvider URI: ${e.message}"
                logger.error("UpdateManager", errorMsg, e)
                _updateProgress.value = _updateProgress.value.copy(
                    state = UpdateState.ERROR,
                    error = errorMsg,
                    statusMessage = "Ошибка при подготовке файла: ${e.message}"
                )
                return@withContext false
            }

            logger.info("UpdateManager", "FileProvider URI: $fileProviderUri")

            _updateProgress.value = _updateProgress.value.copy(
                statusMessage = "Создание Intent для установки..."
            )

            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(fileProviderUri, "application/vnd.android.package-archive")
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            _updateProgress.value = _updateProgress.value.copy(
                statusMessage = "Запуск системного установщика..."
            )

            logger.info("UpdateManager", "Starting installation activity...")
            logger.info("UpdateManager", "Intent action: ${intent.action}, data: ${intent.data}, type: ${intent.type}")
            
            try {
                context.startActivity(intent)
                logger.info("UpdateManager", "Installation activity started successfully")
            } catch (e: android.content.ActivityNotFoundException) {
                val errorMsg = "No activity found to handle installation. ${e.message}"
                logger.error("UpdateManager", errorMsg, e)
                _updateProgress.value = _updateProgress.value.copy(
                    state = UpdateState.ERROR,
                    error = errorMsg,
                    statusMessage = "Ошибка: не найден установщик приложений. Убедитесь, что установщик включен в настройках"
                )
                return@withContext false
            } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                val errorMsg = "Package not found. ${e.message}"
                logger.error("UpdateManager", errorMsg, e)
                _updateProgress.value = _updateProgress.value.copy(
                    state = UpdateState.ERROR,
                    error = errorMsg,
                    statusMessage = "Ошибка: пакет не найден"
                )
                return@withContext false
            } catch (e: SecurityException) {
                val errorMsg = "Security exception: ${e.message}"
                logger.error("UpdateManager", errorMsg, e)
                _updateProgress.value = _updateProgress.value.copy(
                    state = UpdateState.ERROR,
                    error = errorMsg,
                    statusMessage = "Ошибка безопасности: ${e.message}"
                )
                return@withContext false
            }
            
            _updateProgress.value = _updateProgress.value.copy(
                statusMessage = "Установка запущена. Вы можете закрыть приложение - установка продолжится в фоне. Проверьте уведомления системы для отслеживания прогресса."
            )
            
            logger.info("UpdateManager", "Installation process initiated. User can close the app - installation will continue in background")
            
            // Не ждем завершения установки - она происходит в системном установщике
            // Пользователь может закрыть приложение, установка продолжится в фоне
            return@withContext true
        } catch (e: java.io.FileNotFoundException) {
            val errorMsg = "APK file not found: ${e.message}"
            logger.error("UpdateManager", errorMsg, e)
            _updateProgress.value = _updateProgress.value.copy(
                state = UpdateState.ERROR,
                error = errorMsg,
                statusMessage = "Ошибка: файл обновления не найден: ${e.message}"
            )
            return@withContext false
        } catch (e: java.io.IOException) {
            val errorMsg = "IO error during installation: ${e.message}"
            logger.error("UpdateManager", errorMsg, e)
            _updateProgress.value = _updateProgress.value.copy(
                state = UpdateState.ERROR,
                error = errorMsg,
                statusMessage = "Ошибка ввода-вывода: ${e.message}"
            )
            return@withContext false
        } catch (e: IllegalArgumentException) {
            val errorMsg = "Invalid argument: ${e.message}"
            logger.error("UpdateManager", errorMsg, e)
            _updateProgress.value = _updateProgress.value.copy(
                state = UpdateState.ERROR,
                error = errorMsg,
                statusMessage = "Ошибка: неверный аргумент - ${e.message}"
            )
            return@withContext false
        } catch (e: Exception) {
            val errorMsg = "Unexpected error during installation: ${e.javaClass.simpleName}: ${e.message}"
            logger.error("UpdateManager", errorMsg, e)
            logger.error("UpdateManager", "Stack trace:", e)
            _updateProgress.value = _updateProgress.value.copy(
                state = UpdateState.ERROR,
                error = errorMsg,
                statusMessage = "Неожиданная ошибка при установке: ${e.message ?: "Неизвестная ошибка"}"
            )
            return@withContext false
        }
    }

    fun getDownloadedApkFile(): File? {
        return if (_updateProgress.value.state == UpdateState.DOWNLOADED || 
                   _updateProgress.value.state == UpdateState.INSTALLING) {
            currentUpdateFile
        } else {
            null
        }
    }

    fun reset() {
        _updateProgress.value = UpdateProgress(
            state = UpdateState.IDLE,
            statusMessage = null
        )
        currentUpdateFile = null
    }
}

