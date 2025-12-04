package ru.fire_core.xauplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ru.fire_core.xauplayer.data.local.entities.Book
import ru.fire_core.xauplayer.data.local.entities.BookListRelation

@Dao
interface BookListRelationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(relation: BookListRelation)

    @Query("DELETE FROM BookListRelation WHERE listId = :listId AND bookId = :bookId")
    suspend fun delete(listId: Long, bookId: Long)

    @Transaction
    @Query("SELECT * FROM Book WHERE id IN (SELECT bookId FROM BookListRelation WHERE listId = :listId)")
    suspend fun getBooksByListId(listId: Long): List<Book>

    @Query("SELECT * FROM BookListRelation WHERE listId = :listId")
    suspend fun getRelationsByListId(listId: Long): List<BookListRelation>

    @Query("SELECT * FROM BookListRelation WHERE bookId = :bookId")
    suspend fun getRelationsByBookId(bookId: Long): List<BookListRelation>

    @Query("DELETE FROM BookListRelation WHERE listId = :listId")
    suspend fun deleteByListId(listId: Long)

    @Query("DELETE FROM BookListRelation WHERE bookId = :bookId")
    suspend fun deleteByBookId(bookId: Long)
}

