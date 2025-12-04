package ru.fire_core.xauplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ru.fire_core.xauplayer.data.local.entities.Series
import ru.fire_core.xauplayer.data.local.entities.SeriesListRelation

@Dao
interface SeriesListRelationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(relation: SeriesListRelation)

    @Query("DELETE FROM SeriesListRelation WHERE listId = :listId AND seriesId = :seriesId")
    suspend fun delete(listId: Long, seriesId: Long)

    @Transaction
    @Query("SELECT * FROM Series WHERE id IN (SELECT seriesId FROM SeriesListRelation WHERE listId = :listId)")
    suspend fun getSeriesByListId(listId: Long): List<Series>

    @Query("SELECT * FROM SeriesListRelation WHERE listId = :listId")
    suspend fun getRelationsByListId(listId: Long): List<SeriesListRelation>

    @Query("SELECT * FROM SeriesListRelation WHERE seriesId = :seriesId")
    suspend fun getRelationsBySeriesId(seriesId: Long): List<SeriesListRelation>

    @Query("DELETE FROM SeriesListRelation WHERE listId = :listId")
    suspend fun deleteByListId(listId: Long)

    @Query("DELETE FROM SeriesListRelation WHERE seriesId = :seriesId")
    suspend fun deleteBySeriesId(seriesId: Long)
}

