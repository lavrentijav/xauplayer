package ru.fire_core.xauplayer.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class SeriesStatus(
    @PrimaryKey val seriesId: Long,
    val status: String,  // "wanted", "listening", "completed", "dropped"
    val createdAt: String? = null,
    val updatedAt: String? = null
)

