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
    val path: String? = null,
    val series_id: Long? = null,
    val series_order: Int? = null,
    val is_hidden: Boolean = false,
    val total_size_bytes: Long? = null,
    val uploaded_at: String? = null
)

data class ChapterDto(
    val id: Long,
    val title: String,
    val duration: Double? = null,
    val path: String,
    val order: Int,
    val real_order: Int? = null,
    val file_size_bytes: Long? = null,
    val download_url: String? = null
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
    val timestamp: Long,
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
    val created_at: String? = null,
    val is_admin: Boolean = false
)

/** Ответ GET /status/books/{book_id} — status может быть null */
data class BookStatusDto(
    val id: Long? = null,
    val user_id: Long? = null,
    val book_id: Long,
    val status: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

/** Ответ GET /status/series/{series_id} */
data class SeriesStatusDto(
    val id: Long? = null,
    val user_id: Long? = null,
    val series_id: Long,
    val status: String? = null,
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
    val date: String,
    val minutes_listened: Int,
    val books_completed: Int,
    val chapters_listened: Int
)

data class StatusRequest(val status: String)

data class StatusDto(
    val code: String,
    val name: String,
    val description: String? = null
)

data class StatusesResponse(
    val statuses: List<StatusDto>
)

data class StreamTokenResponse(
    // Может отсутствовать: при сегментированном воспроизведении сервер отдаёт "type": "parts"
    val url: String? = null,
    val type: String? = null,
    val stream_token: String? = null,
    val expires_in: Int? = null
)

data class HealthResponse(
    val status: String,
    val message: String? = null
)

data class VersionResponse(
    val version: String,
    val build_number: Int
)

data class ReleaseInfoResponse(
    val version: String? = null,
    val build_number: Int? = null,
    val download_url: String? = null
)

data class SimpleStatusResponse(val status: String)
