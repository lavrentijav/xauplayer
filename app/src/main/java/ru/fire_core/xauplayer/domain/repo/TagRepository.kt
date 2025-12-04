package ru.fire_core.xauplayer.domain.repo

import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.data.local.AppDatabase
import ru.fire_core.xauplayer.data.local.entities.Tag
import ru.fire_core.xauplayer.data.network.ApiService
import javax.inject.Inject

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
    private val api: ApiService,
    private val db: AppDatabase,
    private val logger: AppLogger
) : TagRepository {
    override suspend fun syncTags() {
        try {
            val remote = api.getTags()
            val mapped = remote.map {
                Tag(
                    id = it.id,
                    name = it.name,
                    color = it.color,
                    createdAt = it.created_at
                )
            }
            db.tagDao().upsertAll(mapped)
        } catch (e: Exception) {
            logger.warn("TagRepository", "Failed to sync tags", e)
        }
    }

    override suspend fun getTags(): List<Tag> = db.tagDao().getAll()

    override suspend fun getTag(id: Long): Tag? = db.tagDao().getById(id)

    override suspend fun createTag(name: String, color: String?): Tag? {
        return try {
            val created = api.createTag(
                ru.fire_core.xauplayer.data.network.TagCreateRequest(name, color)
            )
            val tag = Tag(
                id = created.id,
                name = created.name,
                color = created.color,
                createdAt = created.created_at
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
            val updated = api.updateTag(
                tagId,
                ru.fire_core.xauplayer.data.network.TagUpdateRequest(name, color)
            )
            val tag = Tag(
                id = updated.id,
                name = updated.name,
                color = updated.color,
                createdAt = updated.created_at
            )
            db.tagDao().upsert(tag)
            tag
        } catch (e: Exception) {
            logger.error("TagRepository", "Failed to update tag", e)
            null
        }
    }

    override suspend fun deleteTag(tagId: Long): Boolean {
        return try {
            api.deleteTag(tagId)
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
            val remote = api.getBookTags(bookId)
            val mapped = remote.map {
                Tag(
                    id = it.id,
                    name = it.name,
                    color = it.color,
                    createdAt = it.created_at
                )
            }
            db.tagDao().upsertAll(mapped)
            
            // Удаляем все старые связи для этой книги
            db.bookTagRelationDao().deleteByBookId(bookId)
            
            // Добавляем новые связи
            mapped.forEach { tag ->
                db.bookTagRelationDao().insert(
                    ru.fire_core.xauplayer.data.local.entities.BookTagRelation(bookId, tag.id)
                )
            }
            db.bookTagRelationDao().getTagsByBookId(bookId)
        } catch (e: Exception) {
            logger.warn("TagRepository", "Failed to sync book tags, using local data", e)
            db.bookTagRelationDao().getTagsByBookId(bookId)
        }
    }

    override suspend fun getSeriesTags(seriesId: Long): List<Tag> {
        return try {
            val remote = api.getSeriesTags(seriesId)
            val mapped = remote.map {
                Tag(
                    id = it.id,
                    name = it.name,
                    color = it.color,
                    createdAt = it.created_at
                )
            }
            db.tagDao().upsertAll(mapped)
            
            // Удаляем все старые связи для этой серии
            db.seriesTagRelationDao().deleteBySeriesId(seriesId)
            
            // Добавляем новые связи
            mapped.forEach { tag ->
                db.seriesTagRelationDao().insert(
                    ru.fire_core.xauplayer.data.local.entities.SeriesTagRelation(seriesId, tag.id)
                )
            }
            db.seriesTagRelationDao().getTagsBySeriesId(seriesId)
        } catch (e: Exception) {
            logger.warn("TagRepository", "Failed to sync series tags, using local data", e)
            db.seriesTagRelationDao().getTagsBySeriesId(seriesId)
        }
    }

    override suspend fun addTagToBook(bookId: Long, tagId: Long): Boolean {
        return try {
            api.addTagToBook(bookId, tagId)
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
            api.removeTagFromBook(bookId, tagId)
            db.bookTagRelationDao().delete(bookId, tagId)
            true
        } catch (e: Exception) {
            logger.error("TagRepository", "Failed to remove tag from book", e)
            false
        }
    }

    override suspend fun addTagToSeries(seriesId: Long, tagId: Long): Boolean {
        return try {
            api.addTagToSeries(seriesId, tagId)
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
            api.removeTagFromSeries(seriesId, tagId)
            db.seriesTagRelationDao().delete(seriesId, tagId)
            true
        } catch (e: Exception) {
            logger.error("TagRepository", "Failed to remove tag from series", e)
            false
        }
    }
}

