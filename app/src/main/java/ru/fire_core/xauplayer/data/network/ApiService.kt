package ru.fire_core.xauplayer.data.network

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Публичное API XAuPlayer согласно API.md.
 * Admin-эндпоинты намеренно не включены.
 */
interface ApiService {
    // Auth
    @POST("auth/register")
    suspend fun register(@Body req: RegisterRequest): RegisterResponse

    @POST("auth/login")
    suspend fun login(@Body req: LoginRequest): LoginResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body req: RefreshRequest): TokenResponse

    @POST("auth/logout")
    suspend fun logout(@Query("refresh_token") refreshToken: String): SimpleStatusResponse

    // Books
    @GET("books")
    suspend fun getBooks(
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 100,
        @Query("search") search: String? = null
    ): List<BookDto>

    @GET("books/{book_id}")
    suspend fun getBook(@Path("book_id") bookId: Long): BookDto

    @GET("books/{book_id}/chapters")
    suspend fun getChapters(@Path("book_id") bookId: Long): List<ChapterDto>

    @GET("books/{book_id}/stream/{chapter_id}")
    suspend fun getStreamInfo(
        @Path("book_id") bookId: Long,
        @Path("chapter_id") chapterId: Long,
        @Header("Accept") accept: String = "application/json"
    ): StreamTokenResponse

    @GET("books/{book_id}/stream/{chapter_id}")
    suspend fun stream(
        @Path("book_id") bookId: Long,
        @Path("chapter_id") chapterId: Long,
        @Header("Range") range: String? = null
    ): ResponseBody

    @GET("books/{book_id}/download/{chapter_id}")
    suspend fun downloadChapter(
        @Path("book_id") bookId: Long,
        @Path("chapter_id") chapterId: Long
    ): ResponseBody

    // Series
    @GET("series")
    suspend fun getSeries(
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 100,
        @Query("search") search: String? = null
    ): List<SeriesDto>

    @GET("series/{series_id}")
    suspend fun getSeries(@Path("series_id") seriesId: Long): SeriesDto

    @GET("series/{series_id}/books")
    suspend fun getSeriesBooks(
        @Path("series_id") seriesId: Long,
        @Query("search") search: String? = null
    ): List<BookDto>

    // Progress
    @POST("progress/update")
    suspend fun updateProgress(@Body req: ProgressRequest): SimpleStatusResponse

    @GET("progress/sync")
    suspend fun syncProgress(): ProgressSyncResponse

    // Book status
    @GET("status/books/{book_id}")
    suspend fun getBookStatus(@Path("book_id") bookId: Long): BookStatusDto

    @PUT("status/books/{book_id}")
    suspend fun setBookStatus(
        @Path("book_id") bookId: Long,
        @Body req: StatusRequest
    ): BookStatusDto

    @DELETE("status/books/{book_id}")
    suspend fun deleteBookStatus(@Path("book_id") bookId: Long): SimpleStatusResponse

    @GET("status/books")
    suspend fun getBooksByStatus(
        @Query("status") status: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 100
    ): List<BookDto>

    // Series status
    @GET("status/series/{series_id}")
    suspend fun getSeriesStatus(@Path("series_id") seriesId: Long): SeriesStatusDto

    @PUT("status/series/{series_id}")
    suspend fun setSeriesStatus(
        @Path("series_id") seriesId: Long,
        @Body req: StatusRequest
    ): SeriesStatusDto

    @DELETE("status/series/{series_id}")
    suspend fun deleteSeriesStatus(@Path("series_id") seriesId: Long): SimpleStatusResponse

    @GET("status/series")
    suspend fun getSeriesByStatus(
        @Query("status") status: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 100
    ): List<SeriesDto>

    // Statistics
    @GET("statistics")
    suspend fun getStatistics(
        @Query("year") year: Int? = null,
        @Query("month") month: Int? = null,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null
    ): StatisticsDto

    // Notes
    @GET("notes/{book_id}")
    suspend fun getNotes(@Path("book_id") bookId: Long): List<NoteDto>

    @POST("notes/{book_id}")
    suspend fun createNote(
        @Path("book_id") bookId: Long,
        @Body req: NoteRequest
    ): NoteDto

    // Account
    @GET("account")
    suspend fun getAccount(): AccountDto

    @GET("account/activity")
    suspend fun getActivity(): Map<String, Int>

    @GET("account/devices")
    suspend fun getDevices(): List<DeviceDto>

    @DELETE("account/device/{device_id}")
    suspend fun revokeDevice(@Path("device_id") deviceId: Long): SimpleStatusResponse

    // Health
    @GET("health")
    suspend fun health(): HealthResponse

    // Status translations (public)
    @GET("tags")
    suspend fun getStatusTranslations(@Query("language") language: String = "ru"): StatusesResponse

    // Version / Release (доступны и по /api/v1/..., и по корню)
    @GET("version")
    suspend fun getVersion(
        @Query("os_type") osType: String = "android",
        @Query("arch") arch: String
    ): VersionResponse

    @GET("release/info")
    suspend fun getReleaseInfo(
        @Query("os_type") osType: String = "android",
        @Query("arch") arch: String,
        @Query("version") version: String? = null
    ): ReleaseInfoResponse
}
