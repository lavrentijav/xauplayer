package ru.fire_core.xauplayer.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "SeriesTagRelation",
    primaryKeys = ["seriesId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = Series::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["seriesId"]), Index(value = ["tagId"])]
)
data class SeriesTagRelation(
    val seriesId: Long,
    val tagId: Long
)

