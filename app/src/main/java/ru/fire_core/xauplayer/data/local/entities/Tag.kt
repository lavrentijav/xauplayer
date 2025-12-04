package ru.fire_core.xauplayer.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Tag")
data class Tag(
    @PrimaryKey val id: Long,
    val name: String,
    val color: String? = null,
    val createdAt: String? = null
)

