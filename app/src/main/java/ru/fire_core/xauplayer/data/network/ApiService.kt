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

interface ApiService {
    // Аутентификация
    @POST("auth/register")
    suspend fun register(@Body req: RegisterRequest): RegisterResponse

    @POST("auth/login")
    suspend fun login(@Body req: LoginRequest): LoginResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body req: RefreshRequest): TokenResponse

    @POST("auth/logout")
    suspend fun logout(@Query("refresh_token") refreshToken: String): ResponseBody

    // Книги
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
    suspend fun stream(
        @Path("book_id") bookId: Long,
        @Path("chapter_id") chapterId: Long,
        @Header("Range") range: String? = null
    ): ResponseBody

    @PUT("books/chapters/{chapter_id}/order")
    suspend fun updateChapterOrder(
        @Path("chapter_id") chapterId: Long,
        @Body req: ChapterOrderRequest
    ): ResponseBody

    @POST("books/{book_id}/series/{series_id}")
    suspend fun addBookToSeries(
        @Path("book_id") bookId: Long,
        @Path("series_id") seriesId: Long,
        @Query("series_order") seriesOrder: Int? = null
    ): ResponseBody

    @DELETE("books/{book_id}/series")
    suspend fun removeBookFromSeries(@Path("book_id") bookId: Long): ResponseBody

    // Серии
    @GET("series")
    suspend fun getSeries(
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 100,
        @Query("search") search: String? = null
    ): List<SeriesDto>

    @GET("series/{series_id}")
    suspend fun getSeries(@Path("series_id") seriesId: Long): SeriesDto

    @POST("series")
    suspend fun createSeries(@Body req: SeriesCreateRequest): SeriesDto

    @PUT("series/{series_id}")
    suspend fun updateSeries(
        @Path("series_id") seriesId: Long,
        @Body req: SeriesUpdateRequest
    ): SeriesDto

    @DELETE("series/{series_id}")
    suspend fun deleteSeries(@Path("series_id") seriesId: Long): ResponseBody

    @GET("series/{series_id}/books")
    suspend fun getSeriesBooks(
        @Path("series_id") seriesId: Long,
        @Query("search") search: String? = null
    ): List<BookDto>

    // Прогресс
    @POST("progress/update")
    suspend fun updateProgress(@Body req: ProgressRequest): ResponseBody

    @GET("progress/sync")
    suspend fun syncProgress(): ProgressSyncResponse

    // Статусы книг
    @GET("status/books/{book_id}")
    suspend fun getBookStatus(@Path("book_id") bookId: Long): BookStatusDto

    @PUT("status/books/{book_id}")
    suspend fun setBookStatus(
        @Path("book_id") bookId: Long,
        @Body req: StatusRequest
    ): BookStatusDto

    @DELETE("status/books/{book_id}")
    suspend fun deleteBookStatus(@Path("book_id") bookId: Long): ResponseBody

    @GET("status/books")
    suspend fun getBooksByStatus(
        @Query("status") status: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 100
    ): List<BookDto>
    
    // Устаревшие методы для обратной совместимости
    @GET("status/books/wanted")
    suspend fun getWantedBooks(
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 100
    ): List<BookDto>

    @GET("status/books/listening")
    suspend fun getListeningBooks(): List<BookDto>

    @GET("status/books/completed")
    suspend fun getCompletedBooks(): List<BookDto>

    @GET("status/books/dropped")
    suspend fun getDroppedBooks(): List<BookDto>

    // Статусы серий
    @GET("status/series/{series_id}")
    suspend fun getSeriesStatus(@Path("series_id") seriesId: Long): SeriesStatusDto

    @PUT("status/series/{series_id}")
    suspend fun setSeriesStatus(
        @Path("series_id") seriesId: Long,
        @Body req: StatusRequest
    ): SeriesStatusDto

    @DELETE("status/series/{series_id}")
    suspend fun deleteSeriesStatus(@Path("series_id") seriesId: Long): ResponseBody

    @GET("status/series")
    suspend fun getSeriesByStatus(
        @Query("status") status: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 100
    ): List<SeriesDto>
    
    // Устаревшие методы для обратной совместимости
    @GET("status/series/wanted")
    suspend fun getWantedSeries(): List<SeriesDto>

    @GET("status/series/listening")
    suspend fun getListeningSeries(): List<SeriesDto>

    @GET("status/series/completed")
    suspend fun getCompletedSeries(): List<SeriesDto>

    @GET("status/series/dropped")
    suspend fun getDroppedSeries(): List<SeriesDto>

    // Статистика
    @GET("statistics")
    suspend fun getStatistics(
        @Query("year") year: Int? = null,
        @Query("month") month: Int? = null,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null
    ): StatisticsDto
    
    // Устаревшие методы для обратной совместимости
    @GET("statistics/year/{year}")
    suspend fun getYearStatistics(@Path("year") year: Int): StatisticsDto

    @GET("statistics/month/{year}/{month}")
    suspend fun getMonthStatistics(
        @Path("year") year: Int,
        @Path("month") month: Int
    ): StatisticsDto

    @GET("statistics/range")
    suspend fun getRangeStatistics(
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String
    ): StatisticsDto

    // Заметки
    @GET("notes/{book_id}")
    suspend fun getNotes(@Path("book_id") bookId: Long): List<NoteDto>

    @POST("notes/{book_id}")
    suspend fun createNote(
        @Path("book_id") bookId: Long,
        @Body req: NoteRequest
    ): NoteDto

    // Аккаунт
    @GET("account")
    suspend fun getAccount(): AccountDto

    @GET("account/activity")
    suspend fun getActivity(): Map<String, Int>

    @GET("account/devices")
    suspend fun getDevices(): List<DeviceDto>

    @DELETE("account/device/{device_id}")
    suspend fun revokeDevice(@Path("device_id") deviceId: Long): ResponseBody

    // Health check
    @GET("ping")
    suspend fun ping(): ResponseBody

    @GET("health")
    suspend fun health(): ResponseBody

    // Списки
    @GET("lists")
    suspend fun getLists(): List<ListDto>

    @GET("lists/{list_id}")
    suspend fun getList(@Path("list_id") listId: Long): ListDto

    @POST("lists")
    suspend fun createList(@Body req: ListCreateRequest): ListDto

    @PUT("lists/{list_id}")
    suspend fun updateList(
        @Path("list_id") listId: Long,
        @Body req: ListUpdateRequest
    ): ListDto

    @DELETE("lists/{list_id}")
    suspend fun deleteList(@Path("list_id") listId: Long): ResponseBody

    // Книги в списках
    @GET("lists/{list_id}/books")
    suspend fun getListBooks(@Path("list_id") listId: Long): List<BookDto>

    @POST("lists/{list_id}/books/{book_id}")
    suspend fun addBookToList(
        @Path("list_id") listId: Long,
        @Path("book_id") bookId: Long
    ): ResponseBody

    @DELETE("lists/{list_id}/books/{book_id}")
    suspend fun removeBookFromList(
        @Path("list_id") listId: Long,
        @Path("book_id") bookId: Long
    ): ResponseBody

    // Серии в списках
    @GET("lists/{list_id}/series")
    suspend fun getListSeries(@Path("list_id") listId: Long): List<SeriesDto>

    @POST("lists/{list_id}/series/{series_id}")
    suspend fun addSeriesToList(
        @Path("list_id") listId: Long,
        @Path("series_id") seriesId: Long
    ): ResponseBody

    @DELETE("lists/{list_id}/series/{series_id}")
    suspend fun removeSeriesFromList(
        @Path("list_id") listId: Long,
        @Path("series_id") seriesId: Long
    ): ResponseBody

    // Статусы (теги) согласно API_GUIDE.md
    // GET /tags возвращает статусы с переводами (wanted, listening, completed, dropped)
    @GET("tags")
    suspend fun getStatuses(@Query("language") language: String = "en"): StatusesResponse

    // Устаревшие endpoints для тегов (оставлены для обратной совместимости)
    // ВАЖНО: согласно API_GUIDE.md, /tags теперь возвращает статусы, а не теги
    // Эти endpoints могут быть неактуальными, если сервер обновлен согласно API_GUIDE.md
    // В таком случае нужно будет обновить TagRepository для работы с новой структурой
    // Используем разные пути для тегов, чтобы не конфликтовать с /tags для статусов
    // Если сервер не поддерживает эти endpoints, нужно будет обновить TagRepository
    @GET("tags/custom")
    suspend fun getTags(): List<TagDto>

    @GET("tags/custom/{tag_id}")
    suspend fun getTag(@Path("tag_id") tagId: Long): TagDto

    @POST("tags/custom")
    suspend fun createTag(@Body req: TagCreateRequest): TagDto

    @PUT("tags/custom/{tag_id}")
    suspend fun updateTag(
        @Path("tag_id") tagId: Long,
        @Body req: TagUpdateRequest
    ): TagDto

    @DELETE("tags/custom/{tag_id}")
    suspend fun deleteTag(@Path("tag_id") tagId: Long): ResponseBody

    // Теги книг (устаревшие, если не используются в приложении)
    @GET("books/{book_id}/tags")
    suspend fun getBookTags(@Path("book_id") bookId: Long): List<TagDto>

    @POST("books/{book_id}/tags/{tag_id}")
    suspend fun addTagToBook(
        @Path("book_id") bookId: Long,
        @Path("tag_id") tagId: Long
    ): ResponseBody

    @DELETE("books/{book_id}/tags/{tag_id}")
    suspend fun removeTagFromBook(
        @Path("book_id") bookId: Long,
        @Path("tag_id") tagId: Long
    ): ResponseBody

    // Теги серий (устаревшие, если не используются в приложении)
    @GET("series/{series_id}/tags")
    suspend fun getSeriesTags(@Path("series_id") seriesId: Long): List<TagDto>

    @POST("series/{series_id}/tags/{tag_id}")
    suspend fun addTagToSeries(
        @Path("series_id") seriesId: Long,
        @Path("tag_id") tagId: Long
    ): ResponseBody

    @DELETE("series/{series_id}/tags/{tag_id}")
    suspend fun removeTagFromSeries(
        @Path("series_id") seriesId: Long,
        @Path("tag_id") tagId: Long
    ): ResponseBody
}


