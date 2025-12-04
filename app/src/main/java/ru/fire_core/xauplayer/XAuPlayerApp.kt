package ru.fire_core.xauplayer

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.data.datastore.SettingsStore
import ru.fire_core.xauplayer.di.AppEntryPoint
import ru.fire_core.xauplayer.player.PlayerHolder

@HiltAndroidApp
class XAuPlayerApp : Application() {
    
    private val entryPoint: AppEntryPoint by lazy {
        EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java)
    }
    
    fun getPlayerHolder(): PlayerHolder = entryPoint.playerHolder()
    fun getLogger(): AppLogger = entryPoint.logger()

    override fun onCreate() {
        try {
            Log.d("XAuPlayerApp", "=== XAuPlayerApp.onCreate() START ===")
            
            Log.d("XAuPlayerApp", "[0/4] Вызов super.onCreate() (инициализация Hilt)...")
            super.onCreate()
            Log.d("XAuPlayerApp", "[0/4] super.onCreate() завершен")
            
            // Устанавливаем обработчик необработанных исключений
            // Создаем logger напрямую, так как Application создается до Hilt
            Log.d("XAuPlayerApp", "[1/4] Создание SettingsStore...")
            val settingsStore = try {
                SettingsStore(this).also {
                    Log.d("XAuPlayerApp", "[1/4] SettingsStore создан успешно")
                }
            } catch (e: Exception) {
                Log.e("XAuPlayerApp", "[1/4] ОШИБКА создания SettingsStore: ${e.javaClass.simpleName}: ${e.message}", e)
                throw e
            }
            
            Log.d("XAuPlayerApp", "[2/4] Создание AppLogger...")
            val logger = try {
                AppLogger(this, settingsStore).also {
                    Log.d("XAuPlayerApp", "[2/4] AppLogger создан успешно")
                }
            } catch (e: Exception) {
                Log.e("XAuPlayerApp", "[2/4] ОШИБКА создания AppLogger: ${e.javaClass.simpleName}: ${e.message}", e)
                throw e
            }
            
            Log.d("XAuPlayerApp", "[3/4] Установка обработчика исключений...")
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    Log.e("XAuPlayerApp", "[UncaughtException] Thread: ${thread.name}")
                    Log.e("XAuPlayerApp", "[UncaughtException] Exception: ${throwable.javaClass.simpleName}: ${throwable.message}")
                    
                    // Логируем ошибку
                    try {
                        logger.error(
                            tag = "UncaughtException",
                            message = "Uncaught exception in thread: ${thread.name}",
                            throwable = throwable
                        )
                    } catch (e: StackOverflowError) {
                        // Если logger сам вызывает StackOverflowError, просто используем Android Log
                        Log.e("XAuPlayerApp", "[UncaughtException] StackOverflowError в logger, используем Android Log")
                        Log.e("XAuPlayerApp", "Uncaught exception: ${throwable.javaClass.name}: ${throwable.message}", throwable)
                    } catch (e: Exception) {
                        // Если не можем записать в лог, используем стандартный Android Log
                        Log.e("XAuPlayerApp", "[UncaughtException] Не удалось записать в AppLogger, используем Android Log")
                        Log.e("XAuPlayerApp", "Uncaught exception", throwable)
                    }
                } catch (e: StackOverflowError) {
                    // Если даже обработка исключения вызывает StackOverflowError, используем минимальный вывод
                    Log.e("XAuPlayerApp", "[UncaughtException] КРИТИЧЕСКИЙ StackOverflowError в обработчике")
                    Log.e("XAuPlayerApp", "[UncaughtException] Thread: ${thread.name}")
                    Log.e("XAuPlayerApp", "[UncaughtException] Exception: ${throwable.javaClass.name}")
                    Log.e("XAuPlayerApp", "Critical StackOverflowError in exception handler", throwable)
                } catch (e: Exception) {
                    // Если не можем записать в лог, используем стандартный Android Log
                    Log.e("XAuPlayerApp", "[UncaughtException] Не удалось обработать исключение: ${e.javaClass.simpleName}")
                    Log.e("XAuPlayerApp", "Uncaught exception", throwable)
                }
                
                // Вызываем стандартный обработчик
                try {
                    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
                    defaultHandler?.uncaughtException(thread, throwable)
                } catch (e: Exception) {
                    // Если даже стандартный обработчик падает, просто завершаем процесс
                    Log.e("XAuPlayerApp", "[UncaughtException] КРИТИЧЕСКАЯ ОШИБКА: не удалось вызвать стандартный обработчик")
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
            }
            Log.d("XAuPlayerApp", "[3/4] Обработчик исключений установлен")
            
            Log.d("XAuPlayerApp", "=== XAuPlayerApp.onCreate() END ===")
        } catch (e: Exception) {
            Log.e("XAuPlayerApp", "=== КРИТИЧЕСКАЯ ОШИБКА в XAuPlayerApp.onCreate() ===")
            Log.e("XAuPlayerApp", "Exception: ${e.javaClass.simpleName}")
            Log.e("XAuPlayerApp", "Message: ${e.message}", e)
            throw e
        }
    }
}


