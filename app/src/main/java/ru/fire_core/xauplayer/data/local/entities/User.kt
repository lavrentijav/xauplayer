package ru.fire_core.xauplayer.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class User(@PrimaryKey val id: Long, val email: String, val name: String?)

