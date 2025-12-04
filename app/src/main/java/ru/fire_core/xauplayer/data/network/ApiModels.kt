package ru.fire_core.xauplayer.data.network

data class RegisterRequest(val email: String, val password: String, val name: String? = null)
data class RegisterResponse(val id: Long, val email: String, val name: String?)
data class LoginRequest(val email: String, val password: String, val device_name: String)
data class LoginResponse(val access_token: String, val refresh_token: String, val token_type: String = "bearer", val device_id: Long)
data class TokenResponse(val access_token: String, val refresh_token: String, val token_type: String = "bearer", val device_id: Long = 0)
data class RefreshRequest(val refresh_token: String)

data class BookDto(
    val id: Long, 
    val title: String, 
    val author: String, 
    val narrator: String?, 
    val cover_url: String?, 
    val description: String?,
    val series_id: Long? = null,
    val series_order: Int? = null,
    val is_hidden: Boolean = false,
    val total_size_bytes: Long? = null,  // Общий размер всех глав книги в байтах
    val uploaded_at: String? = null
)

data class ChapterDto(
    val id: Long, 
    val title: String, 
    val duration: Double,  // в секундах (float в JSON, Double в Kotlin)
    val path: String,  // относительный путь
    val order: Int,  // порядок сортировки
    val real_order: Int? = null,  // реальный порядок (может быть изменен пользователем)
    val file_size_bytes: Long? = null,  // Размер файла главы в байтах
    val download_url: String? = null  // URL для прямого скачивания главы
)

data class SeriesDto(
    val id: Long,
    val name: String,
    val description: String? = null,
    val cover_url: String? = null,
    val created_at: String? = null
)

data class ProgressRequest(
    val book_id: Long, 
    val chapter_id: Long, 
    val position_ms: Long, 
    val playback_speed: Float, 
    val last_update: String
)

data class ProgressDto(
    val book_id: Long, 
    val chapter_id: Long, 
    val position_ms: Long, 
    val playback_speed: Float, 
    val last_update: String
)

data class ProgressSyncResponse(
    val progress: List<ProgressDto>,
    val activity: Map<String, Int>
)

data class NoteRequest(val text: String, val timestamp: Long)
data class NoteDto(
    val id: Long,
    val text: String,
    val timestamp: Long,  // в миллисекундах
    val created_at: String? = null,
    val updated_at: String? = null
)

data class DeviceDto(
    val id: Long, 
    val device_name: String, 
    val last_login: String,
    val is_active: Boolean = true,
    val token: String? = null
)

data class AccountDto(
    val name: String?,
    val email: String,
    val total_time_minutes: Long? = null,
    val created_at: String? = null
)

data class BookStatusDto(
    val id: Long? = null,
    val user_id: Long? = null,
    val book_id: Long,
    val status: String,  // "wanted", "listening", "completed", "dropped"
    val created_at: String? = null,
    val updated_at: String? = null
)

data class SeriesStatusDto(
    val id: Long? = null,
    val user_id: Long? = null,
    val series_id: Long,
    val status: String,  // "wanted", "listening", "completed", "dropped"
    val created_at: String? = null,
    val updated_at: String? = null
)

data class StatisticsDto(
    val statistics: List<DayStatisticsDto>,
    val total_minutes: Long,
    val total_books_completed: Int,
    val total_chapters_listened: Int
)

data class DayStatisticsDto(
    val date: String,  // YYYY-MM-DD
    val minutes_listened: Int,
    val books_completed: Int,
    val chapters_listened: Int
)

data class ChapterOrderRequest(val real_order: Int?)

data class SeriesCreateRequest(
    val name: String,
    val description: String? = null,
    val cover_url: String? = null
)

data class SeriesUpdateRequest(
    val name: String? = null,
    val description: String? = null,
    val cover_url: String? = null
)

data class StatusRequest(val status: String)
// Устаревшие модели для обратной совместимости
data class BookStatusRequest(val book_id: Long, val status: String)
data class SeriesStatusRequest(val series_id: Long, val status: String)

// Списки
data class ListDto(
    val id: Long,
    val name: String,
    val description: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

data class ListCreateRequest(
    val name: String,
    val description: String? = null
)

data class ListUpdateRequest(
    val name: String? = null,
    val description: String? = null
)

// Теги
data class TagDto(
    val id: Long,
    val name: String,
    val color: String? = null,
    val created_at: String? = null
)

data class TagCreateRequest(
    val name: String,
    val color: String? = null
)

data class TagUpdateRequest(
    val name: String? = null,
    val color: String? = null
)

// Статусы (теги) согласно API_GUIDE.md
data class StatusDto(
    val code: String,  // "wanted", "listening", "completed", "dropped"
    val name: String,  // Локализованное название
    val description: String  // Локализованное описание
)

data class StatusesResponse(
    val statuses: List<StatusDto>
)


