package ru.fire_core.xauplayer.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Book(
    @PrimaryKey val id: Long, 
    val title: String, 
    val author: String, 
    val narrator: String?, 
    val coverUrl: String?, 
    val description: String? = null,
    val seriesId: Long? = null,
    val seriesOrder: Int? = null,
    val totalSizeBytes: Long? = null,  // Общий размер всех глав книги в байтах
    val uploadedAt: String? = null,
    val localPath: String? = null
)

