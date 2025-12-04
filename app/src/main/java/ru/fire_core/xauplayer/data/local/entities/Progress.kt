package ru.fire_core.xauplayer.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    indices = [
        Index(value = ["chapterId"]),
        Index(value = ["bookId", "chapterId"])
    ]
)
data class Progress(
    @PrimaryKey val bookId: Long, 
    val chapterId: Long = 0, 
    val positionMs: Long = 0, 
    val speed: Float = 1f, 
    val lastUpdate: Long = 0
)

