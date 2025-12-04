package ru.fire_core.xauplayer.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "SeriesListRelation",
    primaryKeys = ["listId", "seriesId"],
    foreignKeys = [
        ForeignKey(
            entity = BookList::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Series::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["listId"]), Index(value = ["seriesId"])]
)
data class SeriesListRelation(
    val listId: Long,
    val seriesId: Long
)

