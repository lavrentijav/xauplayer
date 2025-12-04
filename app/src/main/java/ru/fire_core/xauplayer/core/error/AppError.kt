package ru.fire_core.xauplayer.core.error

import android.database.sqlite.SQLiteException

/**
 * Типизированные ошибки приложения
 */
sealed class AppError {
    abstract val message: String
    abstract val throwable: Throwable?
    
    /**
     * Сетевая ошибка (нет интернета, таймаут, ошибка сервера)
     */
    data class NetworkError(
        override val message: String,
        override val throwable: Throwable? = null
    ) : AppError()
    
    /**
     * Ошибка базы данных
     */
    data class DatabaseError(
        override val message: String,
        override val throwable: Throwable? = null
    ) : AppError()
    
    /**
     * Ошибка валидации (неверные входные данные)
     */
    data class ValidationError(
        override val message: String,
        override val throwable: Throwable? = null
    ) : AppError()
    
    /**
     * Ошибка аутентификации (401, токен истек)
     */
    data class AuthenticationError(
        override val message: String,
        override val throwable: Throwable? = null
    ) : AppError()
    
    /**
     * Неизвестная ошибка
     */
    data class UnknownError(
        override val message: String,
        override val throwable: Throwable? = null
    ) : AppError()
    
    companion object {
        /**
         * Создает AppError из Exception
         */
        fun fromException(throwable: Throwable): AppError {
            return when (throwable) {
                is java.net.UnknownHostException,
                is java.net.ConnectException,
                is java.net.SocketTimeoutException,
                is java.io.IOException -> {
                    NetworkError(
                        message = "Ошибка сети: ${throwable.message ?: "Нет подключения к интернету"}",
                        throwable = throwable
                    )
                }
                is SQLiteException -> {
                    DatabaseError(
                        message = "Ошибка базы данных: ${throwable.message ?: "Неизвестная ошибка"}",
                        throwable = throwable
                    )
                }
                // Проверяем Room исключения по имени класса, так как RoomDatabaseException не существует
                // Room выбрасывает различные исключения, но все они наследуются от RuntimeException
                is IllegalArgumentException,
                is IllegalStateException -> {
                    ValidationError(
                        message = "Ошибка валидации: ${throwable.message ?: "Неверные данные"}",
                        throwable = throwable
                    )
                }
                is retrofit2.HttpException -> {
                    when (throwable.code()) {
                        401 -> AuthenticationError(
                            message = "Ошибка аутентификации: требуется повторный вход",
                            throwable = throwable
                        )
                        403 -> AuthenticationError(
                            message = "Доступ запрещен",
                            throwable = throwable
                        )
                        404 -> NetworkError(
                            message = "Ресурс не найден",
                            throwable = throwable
                        )
                        500, 502, 503 -> NetworkError(
                            message = "Ошибка сервера: ${throwable.message ?: "Сервер временно недоступен"}",
                            throwable = throwable
                        )
                        else -> NetworkError(
                            message = "Ошибка сети: ${throwable.message ?: "HTTP ${throwable.code()}"}",
                            throwable = throwable
                        )
                    }
                }
                else -> {
                    // Проверяем Room исключения по имени класса для совместимости
                    if (throwable.javaClass.name.contains("room", ignoreCase = true) ||
                        throwable.javaClass.name.contains("Room", ignoreCase = true)) {
                        DatabaseError(
                            message = "Ошибка базы данных: ${throwable.message ?: "Неизвестная ошибка"}",
                            throwable = throwable
                        )
                    } else {
                        UnknownError(
                            message = "Неизвестная ошибка: ${throwable.message ?: throwable.javaClass.simpleName}",
                            throwable = throwable
                        )
                    }
                }
            }
        }
        
        /**
         * Создает AppError из сообщения
         */
        fun fromMessage(message: String): AppError {
            return UnknownError(message = message)
        }
    }
}

