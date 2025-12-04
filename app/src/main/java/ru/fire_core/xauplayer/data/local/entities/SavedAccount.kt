package ru.fire_core.xauplayer.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class SavedAccount(
    @PrimaryKey val email: String,
    val name: String?,
    val userId: Long,
    val lastLogin: Long,
    val accessToken: String? = null, // Опционально, для быстрого доступа
    val refreshToken: String? = null, // Опционально, для быстрого доступа
    val passwordHash: String? = null // Хэш пароля для перевхода при истекшем токене
)

