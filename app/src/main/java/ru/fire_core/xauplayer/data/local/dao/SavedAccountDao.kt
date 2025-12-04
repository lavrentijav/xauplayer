package ru.fire_core.xauplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.fire_core.xauplayer.data.local.entities.SavedAccount

@Dao
interface SavedAccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: SavedAccount)

    @Query("SELECT * FROM SavedAccount ORDER BY lastLogin DESC")
    suspend fun getAll(): List<SavedAccount>

    @Query("SELECT * FROM SavedAccount WHERE email = :email LIMIT 1")
    suspend fun getByEmail(email: String): SavedAccount?

    @Query("DELETE FROM SavedAccount WHERE email = :email")
    suspend fun delete(email: String)
}

