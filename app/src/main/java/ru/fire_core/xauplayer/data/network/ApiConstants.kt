package ru.fire_core.xauplayer.data.network

/**
 * Константы публичного API согласно API.md
 */
object ApiConstants {
    const val API_PREFIX = "api/v1"

    // Auth
    const val AUTH_REGISTER = "auth/register"
    const val AUTH_LOGIN = "auth/login"
    const val AUTH_REFRESH = "auth/refresh"
    const val AUTH_LOGOUT = "auth/logout"

    // Books
    const val BOOKS = "books"
    const val BOOK_BY_ID = "books/{book_id}"
    const val BOOK_CHAPTERS = "books/{book_id}/chapters"
    const val BOOK_STREAM = "books/{book_id}/stream/{chapter_id}"
    const val BOOK_DOWNLOAD = "books/{book_id}/download/{chapter_id}"

    // Series
    const val SERIES = "series"
    const val SERIES_BY_ID = "series/{series_id}"
    const val SERIES_BOOKS = "series/{series_id}/books"

    // Covers
    const val COVERS_BOOKS = "covers/books/{book_id}"
    const val COVERS_SERIES = "covers/series/{series_id}"

    // Status
    const val STATUS_BOOKS = "status/books"
    const val STATUS_BOOK_BY_ID = "status/books/{book_id}"
    const val STATUS_SERIES = "status/series"
    const val STATUS_SERIES_BY_ID = "status/series/{series_id}"

    // Progress
    const val PROGRESS_UPDATE = "progress/update"
    const val PROGRESS_SYNC = "progress/sync"

    // Notes
    const val NOTES_BY_BOOK = "notes/{book_id}"

    // Statistics
    const val STATISTICS = "statistics"

    // Account
    const val ACCOUNT = "account"
    const val ACCOUNT_ACTIVITY = "account/activity"
    const val ACCOUNT_DEVICES = "account/devices"
    const val ACCOUNT_DEVICE_BY_ID = "account/device/{device_id}"

    // Tags (переводы статусов)
    const val TAGS = "tags"

    // Health / Version / Release
    const val HEALTH = "health"
    const val VERSION = "version"
    const val RELEASE = "release"
    const val RELEASE_INFO = "release/info"

    // Статусы книг
    const val STATUS_WANTED = "wanted"
    const val STATUS_LISTENING = "listening"
    const val STATUS_COMPLETED = "completed"
    const val STATUS_DROPPED = "dropped"

    // Пагинация
    const val DEFAULT_LIMIT = 100
    const val MAX_LIMIT = 1000
    const val INITIAL_BOOKS_LIMIT = 10
}
