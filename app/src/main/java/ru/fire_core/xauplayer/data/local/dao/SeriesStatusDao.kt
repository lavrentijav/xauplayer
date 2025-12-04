package ru.fire_core.xauplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.fire_core.xauplayer.data.local.entities.SeriesStatus

@Dao
interface SeriesStatusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(status: SeriesStatus)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(statuses: List<SeriesStatus>)

    @Query("SELECT * FROM SeriesStatus WHERE seriesId = :seriesId LIMIT 1")
    suspend fun getBySeries(seriesId: Long): SeriesStatus?
    
    @Query("SELECT * FROM SeriesStatus WHERE status = :status")
    suspend fun getByStatus(status: String): List<SeriesStatus>
    
    @Query("DELETE FROM SeriesStatus WHERE seriesId = :seriesId")
    suspend fun delete(seriesId: Long)
    
    @Query("DELETE FROM SeriesStatus")
    suspend fun clear()
}

