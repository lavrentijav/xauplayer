package ru.fire_core.xauplayer.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    indices = [Index(value = ["bookId"])]
)
data class Chapter(
    @PrimaryKey val id: Long, 
    val bookId: Long, 
    val title: String, 
    val duration: Long,  // в миллисекундах
    val chapterOrder: Int = 0,  // переименовано из order т.к. order - зарезервированное слово
    val realOrder: Int? = null,
    val fileSizeBytes: Long? = null,  // Размер файла главы в байтах
    val downloadUrl: String? = null  // URL для прямого скачивания главы
)

