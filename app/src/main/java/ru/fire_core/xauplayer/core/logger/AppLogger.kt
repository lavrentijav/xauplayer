package ru.fire_core.xauplayer.core.logger

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.fire_core.xauplayer.data.datastore.SettingsStore
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock
import ru.fire_core.xauplayer.core.config.AppConfig

@Singleton
class AppLogger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore
) {
    private val logDir: File by lazy {
        File(settingsStore.basepath, "logs").apply {
            if (!exists()) mkdirs()
        }
    }

    private val logFile: File by lazy {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = dateFormat.format(Date())
        File(logDir, "${AppConfig.LOG_FILE_PREFIX}$today${AppConfig.LOG_FILE_EXTENSION}")
    }

    private val lock = ReentrantLock()
    private val maxLogSize = AppConfig.MAX_LOG_FILE_SIZE_BYTES
    private val maxLogFiles = AppConfig.MAX_LOG_FILES_DAYS

    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        lock.withLock {
            try {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                val stackTrace = throwable?.let {
                    try {
                        StringWriter().apply {
                            PrintWriter(this).use { pw -> it.printStackTrace(pw) }
                        }.toString()
                    } catch (e: StackOverflowError) {
                        // Если printStackTrace вызывает StackOverflowError (например, из-за рекурсии в coroutine context),
                        // используем альтернативный метод получения стека
                        buildString {
                            append("${it.javaClass.name}: ${it.message}\n")
                            it.stackTrace.take(50).forEach { element ->
                                append("  at ${element.className}.${element.methodName}(${element.fileName}:${element.lineNumber})\n")
                            }
                            if (it.cause != null) {
                                append("Caused by: ${it.cause}\n")
                            }
                        }
                    } catch (e: Exception) {
                        // Если даже альтернативный метод не работает, используем минимальную информацию
                        "${it.javaClass.name}: ${it.message}"
                    }
                } ?: ""

                val logEntry = buildString {
                    append("[$timestamp] ")
                    append("[$level] ")
                    append("[$tag] ")
                    append(message)
                    if (stackTrace.isNotEmpty()) {
                        append("\n")
                        append(stackTrace)
                    }
                    append("\n")
                    append(AppConfig.LOG_SEPARATOR)
                    append("\n")
                }

                // Также пишем в Android Log
                when (level) {
                    LogLevel.DEBUG -> Log.d(tag, message, throwable)
                    LogLevel.INFO -> Log.i(tag, message, throwable)
                    LogLevel.WARN -> Log.w(tag, message, throwable)
                    LogLevel.ERROR -> Log.e(tag, message, throwable)
                }

                // Записываем в файл
                FileWriter(logFile, true).use { writer ->
                    writer.append(logEntry)
                }

                // Проверяем размер файла и ротируем если нужно
                if (logFile.length() > maxLogSize) {
                    rotateLogs()
                }

                // Удаляем старые логи
                cleanupOldLogs()
            } catch (e: Exception) {
                // Если не можем записать в файл, пишем только в Android Log
                Log.e("AppLogger", "Failed to write log to file", e)
            }
        }
    }

    fun debug(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.DEBUG, tag, message, throwable)
    }

    fun info(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.INFO, tag, message, throwable)
    }

    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.WARN, tag, message, throwable)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, tag, message, throwable)
    }

    fun getLogs(limit: Int = AppConfig.LOGS_DISPLAY_LIMIT): List<LogEntry> {
        lock.withLock {
            return try {
                val allLogs = mutableListOf<LogEntry>()
                val logFiles = logDir.listFiles { file ->
                    file.name.startsWith(AppConfig.LOG_FILE_PREFIX) && file.name.endsWith(AppConfig.LOG_FILE_EXTENSION)
                }?.sortedByDescending { it.lastModified() } ?: emptyList()

                // Читаем файлы в обратном порядке (от новых к старым)
                for (file in logFiles) {
                    if (allLogs.size >= limit) break
                    try {
                        val content = file.readText()
                        // Разбиваем на записи по разделителю
                        val entries = content.split(AppConfig.LOG_SEPARATOR)
                        // Читаем записи в обратном порядке
                        entries.reversed().forEach { entryText ->
                            if (allLogs.size >= limit) return@forEach
                            if (entryText.trim().isNotEmpty()) {
                                val lines = entryText.trim().split("\n")
                                if (lines.isNotEmpty()) {
                                    val firstLine = lines[0]
                                    if (firstLine.startsWith("[")) {
                                        val entry = parseLogLine(firstLine, lines.drop(1).joinToString("\n"))
                                        if (entry != null) {
                                            allLogs.add(entry)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Пропускаем файлы с ошибками
                        Log.e("AppLogger", "Failed to read log file: ${file.name}", e)
                    }
                }

                allLogs.sortedByDescending { it.timestamp }
            } catch (e: Exception) {
                Log.e("AppLogger", "Failed to read logs", e)
                emptyList()
            }
        }
    }

    fun clearLogs() {
        lock.withLock {
            try {
                logDir.listFiles()?.forEach { it.delete() }
            } catch (e: Exception) {
                Log.e("AppLogger", "Failed to clear logs", e)
            }
        }
    }

    fun exportLogsToFile(): File? {
        lock.withLock {
            return try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
                val timestamp = dateFormat.format(Date())
                val exportFile = File(settingsStore.basepath, "${AppConfig.LOG_EXPORT_PREFIX}$timestamp${AppConfig.LOG_FILE_EXTENSION}")
                
                val logFiles = logDir.listFiles { file ->
                    file.name.startsWith(AppConfig.LOG_FILE_PREFIX) && file.name.endsWith(AppConfig.LOG_FILE_EXTENSION)
                }?.sortedBy { it.lastModified() } ?: emptyList()

                FileWriter(exportFile, false).use { writer ->
                    writer.append("=== XAuPlayer Logs Export ===\n")
                    writer.append("Export Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
                    writer.append("=".repeat(80))
                    writer.append("\n\n")

                    for (file in logFiles) {
                        writer.append("--- File: ${file.name} ---\n")
                        writer.append(file.readText())
                        writer.append("\n")
                    }
                }

                exportFile
            } catch (e: Exception) {
                Log.e("AppLogger", "Failed to export logs", e)
                null
            }
        }
    }

    private fun rotateLogs() {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
            val timestamp = dateFormat.format(Date())
            val rotatedFile = File(logDir, "${AppConfig.LOG_ROTATED_PREFIX}${logFile.nameWithoutExtension}_$timestamp${AppConfig.LOG_FILE_EXTENSION}")
            logFile.copyTo(rotatedFile, overwrite = true)
            logFile.delete()
        } catch (e: Exception) {
            Log.e("AppLogger", "Failed to rotate logs", e)
        }
    }

    private fun cleanupOldLogs() {
        try {
            val logFiles = logDir.listFiles { file ->
                file.name.startsWith(AppConfig.LOG_FILE_PREFIX) && file.name.endsWith(AppConfig.LOG_FILE_EXTENSION)
            }?.sortedByDescending { it.lastModified() } ?: emptyList()

            if (logFiles.size > maxLogFiles) {
                logFiles.drop(maxLogFiles).forEach { it.delete() }
            }
        } catch (e: Exception) {
            Log.e("AppLogger", "Failed to cleanup old logs", e)
        }
    }

    private fun parseLogLine(line: String, fullMessage: String = ""): LogEntry? {
        try {
            // Формат: [timestamp] [level] [tag] message
            val regex = Regex("\\[(.*?)\\] \\[(.*?)\\] \\[(.*?)\\] (.*)")
            val match = regex.find(line) ?: return null
            val (timestamp, level, tag, message) = match.destructured
            val fullMsg = if (fullMessage.isNotEmpty()) {
                "$message\n$fullMessage"
            } else {
                message
            }
            return LogEntry(
                timestamp = timestamp,
                level = LogLevel.valueOf(level),
                tag = tag,
                message = fullMsg.trim()
            )
        } catch (e: Exception) {
            return null
        }
    }
}

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String
)

