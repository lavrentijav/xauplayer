package ru.fire_core.xauplayer.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["status"])
    ]
)
data class BookStatus(
    @PrimaryKey val bookId: Long,
    val status: String,  // "wanted", "listening", "completed", "dropped"
    val createdAt: String? = null,
    val updatedAt: String? = null
)

