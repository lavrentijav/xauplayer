package ru.fire_core.xauplayer.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Device(
    @PrimaryKey val id: Long, 
    val deviceName: String, 
    val lastLogin: Long,
    val isActive: Boolean = true,
    val token: String? = null
)

