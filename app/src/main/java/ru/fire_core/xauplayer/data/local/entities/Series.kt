package ru.fire_core.xauplayer.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Series(
    @PrimaryKey val id: Long,
    val name: String,
    val description: String? = null,
    val coverUrl: String? = null,
    val createdAt: String? = null
)

