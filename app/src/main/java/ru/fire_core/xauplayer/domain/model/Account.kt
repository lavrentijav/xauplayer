package ru.fire_core.xauplayer.domain.model

data class Account(
    val id: String,
    val username: String,
    val email: String,
    val passwordHash: String,
    val profilePictureUrl: String? = null
)
