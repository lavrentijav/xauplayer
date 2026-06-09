package ru.fire_core.xauplayer.domain.repo

import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.data.local.AppDatabase
import ru.fire_core.xauplayer.data.local.entities.Tag
import java.time.Instant
import javax.inject.Inject

/**
 * Пользовательские теги — локальная функция приложения.
 * Публичный API /tags возвращает переводы статусов, не пользовательские теги.
 */
interface TagRepository {
    suspend fun syncTags()
    suspend fun getTags(): List<Tag>
    suspend fun getTag(id: Long): Tag?
    suspend fun createTag(name: String, color: String? = null): Tag?
    suspend fun updateTag(tagId: Long, name: String? = null, color: String? = null): Tag?
    suspend fun deleteTag(tagId: Long): Boolean
    suspend fun getBookTags(bookId: Long): List<Tag>
    suspend fun getSeriesTags(seriesId: Long): List<Tag>
    suspend fun addTagToBook(bookId: Long, tagId: Long): Boolean
    suspend fun removeTagFromBook(bookId: Long, tagId: Long): Boolean
    suspend fun addTagToSeries(seriesId: Long, tagId: Long): Boolean
    suspend fun removeTagFromSeries(seriesId: Long, tagId: Long): Boolean
}

class TagRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val logger: AppLogger
) : TagRepository {

    private suspend fun nextLocalId(): Long {
        val maxId = db.tagDao().getAll().maxOfOrNull { it.id } ?: 0L
        return if (maxId < 0) maxId - 1 else -(maxId + 1)
    }

    override suspend fun syncTags() {
        // Теги хранятся только локально
    }

    override suspend fun getTags(): List<Tag> = db.tagDao().getAll()

    override suspend fun getTag(id: Long): Tag? = db.tagDao().getById(id)

    override suspend fun createTag(name: String, color: String?): Tag? {
        return try {
            val tag = Tag(
                id = nextLocalId(),
                name = name,
                color = color,
                createdAt = Instant.now().toString()
            )
            db.tagDao().upsert(tag)
            tag
        } catch (e: Exception) {
            logger.error("TagRepository", "Failed to create tag", e)
            null
        }
    }

    override suspend fun updateTag(tagId: Long, name: String?, color: String?): Tag? {
        return try {
            val existing = db.tagDao().getById(tagId) ?: return null
            val updated = existing.copy(
                name = name ?: existing.name,
                color = color ?: existing.color
            )
            db.tagDao().upsert(updated)
            updated
        } catch (e: Exception) {
            logger.error("TagRepository", "Failed to update tag", e)
            null
        }
    }

    override suspend fun deleteTag(tagId: Long): Boolean {
        return try {
            db.tagDao().deleteById(tagId)
            db.bookTagRelationDao().deleteByTagId(tagId)
            db.seriesTagRelationDao().deleteByTagId(tagId)
            true
        } catch (e: Exception) {
            logger.error("TagRepository", "Failed to delete tag", e)
            false
        }
    }

    override suspend fun getBookTags(bookId: Long): List<Tag> {
        return try {
            db.bookTagRelationDao().getTagsByBookId(bookId)
        } catch (e: Exception) {
            logger.warn("TagRepository", "Failed to get book tags", e)
            emptyList()
        }
    }

    override suspend fun getSeriesTags(seriesId: Long): List<Tag> {
        return try {
            db.seriesTagRelationDao().getTagsBySeriesId(seriesId)
        } catch (e: Exception) {
            logger.warn("TagRepository", "Failed to get series tags", e)
            emptyList()
        }
    }

    override suspend fun addTagToBook(bookId: Long, tagId: Long): Boolean {
        return try {
            db.bookTagRelationDao().insert(
                ru.fire_core.xauplayer.data.local.entities.BookTagRelation(bookId, tagId)
            )
            true
        } catch (e: Exception) {
            logger.error("TagRepository", "Failed to add tag to book", e)
            false
        }
    }

    override suspend fun removeTagFromBook(bookId: Long, tagId: Long): Boolean {
        return try {
            db.bookTagRelationDao().delete(bookId, tagId)
            true
        } catch (e: Exception) {
            logger.error("TagRepository", "Failed to remove tag from book", e)
            false
        }
    }

    override suspend fun addTagToSeries(seriesId: Long, tagId: Long): Boolean {
        return try {
            db.seriesTagRelationDao().insert(
                ru.fire_core.xauplayer.data.local.entities.SeriesTagRelation(seriesId, tagId)
            )
            true
        } catch (e: Exception) {
            logger.error("TagRepository", "Failed to add tag to series", e)
            false
        }
    }

    override suspend fun removeTagFromSeries(seriesId: Long, tagId: Long): Boolean {
        return try {
            db.seriesTagRelationDao().delete(seriesId, tagId)
            true
        } catch (e: Exception) {
            logger.error("TagRepository", "Failed to remove tag from series", e)
            false
        }
    }
}
