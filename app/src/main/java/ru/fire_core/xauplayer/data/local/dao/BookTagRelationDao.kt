package ru.fire_core.xauplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ru.fire_core.xauplayer.data.local.entities.BookTagRelation
import ru.fire_core.xauplayer.data.local.entities.Tag

@Dao
interface BookTagRelationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(relation: BookTagRelation)

    @Query("DELETE FROM BookTagRelation WHERE bookId = :bookId AND tagId = :tagId")
    suspend fun delete(bookId: Long, tagId: Long)

    @Transaction
    @Query("SELECT * FROM Tag WHERE id IN (SELECT tagId FROM BookTagRelation WHERE bookId = :bookId)")
    suspend fun getTagsByBookId(bookId: Long): List<Tag>

    @Query("SELECT * FROM BookTagRelation WHERE bookId = :bookId")
    suspend fun getRelationsByBookId(bookId: Long): List<BookTagRelation>

    @Query("SELECT * FROM BookTagRelation WHERE tagId = :tagId")
    suspend fun getRelationsByTagId(tagId: Long): List<BookTagRelation>

    @Query("DELETE FROM BookTagRelation WHERE bookId = :bookId")
    suspend fun deleteByBookId(bookId: Long)

    @Query("DELETE FROM BookTagRelation WHERE tagId = :tagId")
    suspend fun deleteByTagId(tagId: Long)
}

