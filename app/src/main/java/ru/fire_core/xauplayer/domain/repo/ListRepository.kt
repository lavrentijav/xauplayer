package ru.fire_core.xauplayer.domain.repo

import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.data.local.AppDatabase
import ru.fire_core.xauplayer.data.local.entities.Book
import ru.fire_core.xauplayer.data.local.entities.BookList
import ru.fire_core.xauplayer.data.local.entities.Series
import ru.fire_core.xauplayer.data.network.ApiService
import javax.inject.Inject

interface ListRepository {
    suspend fun syncLists()
    suspend fun getLists(): List<BookList>
    suspend fun getList(id: Long): BookList?
    suspend fun createList(name: String, description: String? = null): BookList?
    suspend fun updateList(listId: Long, name: String? = null, description: String? = null): BookList?
    suspend fun deleteList(listId: Long): Boolean
    suspend fun getListBooks(listId: Long): List<Book>
    suspend fun getListSeries(listId: Long): List<Series>
    suspend fun addBookToList(listId: Long, bookId: Long): Boolean
    suspend fun removeBookFromList(listId: Long, bookId: Long): Boolean
    suspend fun addSeriesToList(listId: Long, seriesId: Long): Boolean
    suspend fun removeSeriesFromList(listId: Long, seriesId: Long): Boolean
}

class ListRepositoryImpl @Inject constructor(
    private val api: ApiService,
    private val db: AppDatabase,
    private val logger: AppLogger
) : ListRepository {
    override suspend fun syncLists() {
        try {
            val remote = api.getLists()
            val mapped = remote.map {
                BookList(
                    id = it.id,
                    name = it.name,
                    description = it.description,
                    createdAt = it.created_at,
                    updatedAt = it.updated_at
                )
            }
            db.listDao().upsertAll(mapped)
        } catch (e: Exception) {
            logger.warn("ListRepository", "Failed to sync lists", e)
        }
    }

    override suspend fun getLists(): List<BookList> = db.listDao().getAll()

    override suspend fun getList(id: Long): BookList? = db.listDao().getById(id)

    override suspend fun createList(name: String, description: String?): BookList? {
        return try {
            val created = api.createList(
                ru.fire_core.xauplayer.data.network.ListCreateRequest(name, description)
            )
            val list = BookList(
                id = created.id,
                name = created.name,
                description = created.description,
                createdAt = created.created_at,
                updatedAt = created.updated_at
            )
            db.listDao().upsert(list)
            list
        } catch (e: Exception) {
            logger.error("ListRepository", "Failed to create list", e)
            null
        }
    }

    override suspend fun updateList(listId: Long, name: String?, description: String?): BookList? {
        return try {
            val updated = api.updateList(
                listId,
                ru.fire_core.xauplayer.data.network.ListUpdateRequest(name, description)
            )
            val list = BookList(
                id = updated.id,
                name = updated.name,
                description = updated.description,
                createdAt = updated.created_at,
                updatedAt = updated.updated_at
            )
            db.listDao().upsert(list)
            list
        } catch (e: Exception) {
            logger.error("ListRepository", "Failed to update list", e)
            null
        }
    }

    override suspend fun deleteList(listId: Long): Boolean {
        return try {
            api.deleteList(listId)
            db.listDao().deleteById(listId)
            db.bookListRelationDao().deleteByListId(listId)
            db.seriesListRelationDao().deleteByListId(listId)
            true
        } catch (e: Exception) {
            logger.error("ListRepository", "Failed to delete list", e)
            false
        }
    }

    override suspend fun getListBooks(listId: Long): List<Book> {
        return try {
            val remote = api.getListBooks(listId)
            val mapped = remote.map {
                Book(
                    id = it.id,
                    title = it.title,
                    author = it.author,
                    narrator = it.narrator,
                    coverUrl = it.cover_url,
                    description = it.description,
                    seriesId = it.series_id,
                    seriesOrder = it.series_order,
                    uploadedAt = it.uploaded_at
                )
            }
            db.bookDao().upsertAll(mapped)
            // Обновляем связи
            mapped.forEach { book ->
                db.bookListRelationDao().insert(
                    ru.fire_core.xauplayer.data.local.entities.BookListRelation(listId, book.id)
                )
            }
            db.bookListRelationDao().getBooksByListId(listId)
        } catch (e: Exception) {
            logger.warn("ListRepository", "Failed to sync list books, using local data", e)
            db.bookListRelationDao().getBooksByListId(listId)
        }
    }

    override suspend fun getListSeries(listId: Long): List<Series> {
        return try {
            val remote = api.getListSeries(listId)
            val mapped = remote.map {
                Series(
                    id = it.id,
                    name = it.name,
                    description = it.description,
                    coverUrl = it.cover_url,
                    createdAt = it.created_at
                )
            }
            db.seriesDao().upsertAll(mapped)
            // Обновляем связи
            mapped.forEach { series ->
                db.seriesListRelationDao().insert(
                    ru.fire_core.xauplayer.data.local.entities.SeriesListRelation(listId, series.id)
                )
            }
            db.seriesListRelationDao().getSeriesByListId(listId)
        } catch (e: Exception) {
            logger.warn("ListRepository", "Failed to sync list series, using local data", e)
            db.seriesListRelationDao().getSeriesByListId(listId)
        }
    }

    override suspend fun addBookToList(listId: Long, bookId: Long): Boolean {
        return try {
            api.addBookToList(listId, bookId)
            db.bookListRelationDao().insert(
                ru.fire_core.xauplayer.data.local.entities.BookListRelation(listId, bookId)
            )
            true
        } catch (e: Exception) {
            logger.error("ListRepository", "Failed to add book to list", e)
            false
        }
    }

    override suspend fun removeBookFromList(listId: Long, bookId: Long): Boolean {
        return try {
            api.removeBookFromList(listId, bookId)
            db.bookListRelationDao().delete(listId, bookId)
            true
        } catch (e: Exception) {
            logger.error("ListRepository", "Failed to remove book from list", e)
            false
        }
    }

    override suspend fun addSeriesToList(listId: Long, seriesId: Long): Boolean {
        return try {
            api.addSeriesToList(listId, seriesId)
            db.seriesListRelationDao().insert(
                ru.fire_core.xauplayer.data.local.entities.SeriesListRelation(listId, seriesId)
            )
            true
        } catch (e: Exception) {
            logger.error("ListRepository", "Failed to add series to list", e)
            false
        }
    }

    override suspend fun removeSeriesFromList(listId: Long, seriesId: Long): Boolean {
        return try {
            api.removeSeriesFromList(listId, seriesId)
            db.seriesListRelationDao().delete(listId, seriesId)
            true
        } catch (e: Exception) {
            logger.error("ListRepository", "Failed to remove series from list", e)
            false
        }
    }
}

