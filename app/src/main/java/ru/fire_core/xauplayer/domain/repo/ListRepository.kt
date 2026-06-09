package ru.fire_core.xauplayer.domain.repo

import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.data.local.AppDatabase
import ru.fire_core.xauplayer.data.local.entities.Book
import ru.fire_core.xauplayer.data.local.entities.BookList
import ru.fire_core.xauplayer.data.local.entities.Series
import java.time.Instant
import javax.inject.Inject

/**
 * Пользовательские списки — локальная функция приложения.
 * В публичном API (API.md) эндпоинтов /lists нет.
 */
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
    private val db: AppDatabase,
    private val logger: AppLogger
) : ListRepository {

    private suspend fun nextLocalId(): Long {
        val maxId = db.listDao().getAll().maxOfOrNull { it.id } ?: 0L
        return if (maxId < 0) maxId - 1 else -(maxId + 1)
    }

    override suspend fun syncLists() {
        // Списки хранятся только локально
    }

    override suspend fun getLists(): List<BookList> = db.listDao().getAll()

    override suspend fun getList(id: Long): BookList? = db.listDao().getById(id)

    override suspend fun createList(name: String, description: String?): BookList? {
        return try {
            val now = Instant.now().toString()
            val list = BookList(
                id = nextLocalId(),
                name = name,
                description = description,
                createdAt = now,
                updatedAt = now
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
            val existing = db.listDao().getById(listId) ?: return null
            val updated = existing.copy(
                name = name ?: existing.name,
                description = description ?: existing.description,
                updatedAt = Instant.now().toString()
            )
            db.listDao().upsert(updated)
            updated
        } catch (e: Exception) {
            logger.error("ListRepository", "Failed to update list", e)
            null
        }
    }

    override suspend fun deleteList(listId: Long): Boolean {
        return try {
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
            db.bookListRelationDao().getBooksByListId(listId)
        } catch (e: Exception) {
            logger.warn("ListRepository", "Failed to get list books", e)
            emptyList()
        }
    }

    override suspend fun getListSeries(listId: Long): List<Series> {
        return try {
            db.seriesListRelationDao().getSeriesByListId(listId)
        } catch (e: Exception) {
            logger.warn("ListRepository", "Failed to get list series", e)
            emptyList()
        }
    }

    override suspend fun addBookToList(listId: Long, bookId: Long): Boolean {
        return try {
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
            db.bookListRelationDao().delete(listId, bookId)
            true
        } catch (e: Exception) {
            logger.error("ListRepository", "Failed to remove book from list", e)
            false
        }
    }

    override suspend fun addSeriesToList(listId: Long, seriesId: Long): Boolean {
        return try {
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
            db.seriesListRelationDao().delete(listId, seriesId)
            true
        } catch (e: Exception) {
            logger.error("ListRepository", "Failed to remove series from list", e)
            false
        }
    }
}
