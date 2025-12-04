package ru.fire_core.xauplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ru.fire_core.xauplayer.data.local.entities.SeriesTagRelation
import ru.fire_core.xauplayer.data.local.entities.Tag

@Dao
interface SeriesTagRelationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(relation: SeriesTagRelation)

    @Query("DELETE FROM SeriesTagRelation WHERE seriesId = :seriesId AND tagId = :tagId")
    suspend fun delete(seriesId: Long, tagId: Long)

    @Transaction
    @Query("SELECT * FROM Tag WHERE id IN (SELECT tagId FROM SeriesTagRelation WHERE seriesId = :seriesId)")
    suspend fun getTagsBySeriesId(seriesId: Long): List<Tag>

    @Query("SELECT * FROM SeriesTagRelation WHERE seriesId = :seriesId")
    suspend fun getRelationsBySeriesId(seriesId: Long): List<SeriesTagRelation>

    @Query("SELECT * FROM SeriesTagRelation WHERE tagId = :tagId")
    suspend fun getRelationsByTagId(tagId: Long): List<SeriesTagRelation>

    @Query("DELETE FROM SeriesTagRelation WHERE seriesId = :seriesId")
    suspend fun deleteBySeriesId(seriesId: Long)

    @Query("DELETE FROM SeriesTagRelation WHERE tagId = :tagId")
    suspend fun deleteByTagId(tagId: Long)
}

