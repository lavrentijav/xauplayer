package ru.fire_core.xauplayer.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "BookList")
data class BookList(
    @PrimaryKey val id: Long,
    val name: String,
    val description: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

