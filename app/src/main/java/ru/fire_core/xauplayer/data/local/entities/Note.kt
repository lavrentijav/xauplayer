package ru.fire_core.xauplayer.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Note(
    @PrimaryKey val id: Long,
    val bookId: Long, 
    val text: String, 
    val timestamp: Long,  // в миллисекундах
    val createdAt: String? = null,
    val updatedAt: String? = null
)

