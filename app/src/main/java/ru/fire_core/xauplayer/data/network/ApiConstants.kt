package ru.fire_core.xauplayer.data.network

/**
 * Константы для API endpoints согласно API.md
 */
object ApiConstants {
    // Аутентификация
    const val AUTH_REGISTER = "auth/register"
    const val AUTH_LOGIN = "auth/login"
    const val AUTH_REFRESH = "auth/refresh"
    const val AUTH_LOGOUT = "auth/logout"
    
    // Книги
    const val BOOKS = "books"
    const val BOOK_BY_ID = "books/{book_id}"
    const val BOOK_CHAPTERS = "books/{book_id}/chapters"
    const val BOOK_STREAM = "books/{book_id}/stream/{chapter_id}"
    
    // Серии
    const val SERIES = "series"
    const val SERIES_BY_ID = "series/{series_id}"
    const val SERIES_BOOKS = "series/{series_id}/books"
    
    // Обложки
    const val COVERS_BOOKS = "covers/books/{book_id}"
    const val COVERS_SERIES = "covers/series/{series_id}"
    
    // Статусы
    const val STATUS_BOOKS = "status/books"
    const val STATUS_BOOK_BY_ID = "status/books/{book_id}"
    const val STATUS_SERIES = "status/series"
    const val STATUS_SERIES_BY_ID = "status/series/{series_id}"
    
    // Прогресс
    const val PROGRESS_UPDATE = "progress/update"
    const val PROGRESS_SYNC = "progress/sync"
    
    // Заметки
    const val NOTES_BY_BOOK = "notes/{book_id}"
    
    // Статистика
    const val STATISTICS = "statistics"
    
    // Аккаунт
    const val ACCOUNT = "account"
    const val ACCOUNT_ACTIVITY = "account/activity"
    const val ACCOUNT_DEVICES = "account/devices"
    const val ACCOUNT_DEVICE_BY_ID = "account/device/{device_id}"
    
    // Health Check
    const val HEALTH = "health"
    
    // Теги
    const val TAGS = "tags"
    const val TAG_BY_ID = "tags/{tag_id}"
    const val BOOK_TAGS = "books/{book_id}/tags"
    const val SERIES_TAGS = "series/{series_id}/tags"
    
    // Статусы книг (для фильтрации)
    const val STATUS_WANTED = "wanted"
    const val STATUS_LISTENING = "listening"
    const val STATUS_COMPLETED = "completed"
    const val STATUS_DROPPED = "dropped"
    
    // Параметры пагинации
    const val DEFAULT_LIMIT = 100
    const val MAX_LIMIT = 1000
    const val INITIAL_BOOKS_LIMIT = 10 // Первые 10 книг для быстрой загрузки
}

