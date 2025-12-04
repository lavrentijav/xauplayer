package ru.fire_core.xauplayer.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "BookListRelation",
    primaryKeys = ["listId", "bookId"],
    foreignKeys = [
        ForeignKey(
            entity = BookList::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["listId"]), Index(value = ["bookId"])]
)
data class BookListRelation(
    val listId: Long,
    val bookId: Long
)

