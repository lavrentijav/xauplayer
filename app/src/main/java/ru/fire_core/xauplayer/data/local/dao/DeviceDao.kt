package ru.fire_core.xauplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.fire_core.xauplayer.data.local.entities.Device

@Dao
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Device>)

    @Query("SELECT * FROM Device ORDER BY lastLogin DESC")
    suspend fun getAll(): List<Device>
    
    @Query("DELETE FROM Device WHERE id = :id")
    suspend fun delete(id: Long)
}

