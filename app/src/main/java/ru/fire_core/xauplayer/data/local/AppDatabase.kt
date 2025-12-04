package ru.fire_core.xauplayer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import ru.fire_core.xauplayer.data.local.dao.BookDao
import ru.fire_core.xauplayer.data.local.dao.ChapterDao
import ru.fire_core.xauplayer.data.local.dao.DeviceDao
import ru.fire_core.xauplayer.data.local.dao.NoteDao
import ru.fire_core.xauplayer.data.local.dao.ProgressDao
import ru.fire_core.xauplayer.data.local.dao.SavedAccountDao
import ru.fire_core.xauplayer.data.local.dao.SeriesDao
import ru.fire_core.xauplayer.data.local.dao.BookStatusDao
import ru.fire_core.xauplayer.data.local.dao.SeriesStatusDao
import ru.fire_core.xauplayer.data.local.dao.DownloadedChapterDao
import ru.fire_core.xauplayer.data.local.dao.BufferedProgressDao
import ru.fire_core.xauplayer.data.local.dao.ListDao
import ru.fire_core.xauplayer.data.local.dao.TagDao
import ru.fire_core.xauplayer.data.local.dao.BookListRelationDao
import ru.fire_core.xauplayer.data.local.dao.SeriesListRelationDao
import ru.fire_core.xauplayer.data.local.dao.BookTagRelationDao
import ru.fire_core.xauplayer.data.local.dao.SeriesTagRelationDao
import ru.fire_core.xauplayer.data.local.entities.Book
import ru.fire_core.xauplayer.data.local.entities.Chapter
import ru.fire_core.xauplayer.data.local.entities.Device
import ru.fire_core.xauplayer.data.local.entities.Note
import ru.fire_core.xauplayer.data.local.entities.Progress
import ru.fire_core.xauplayer.data.local.entities.SavedAccount
import ru.fire_core.xauplayer.data.local.entities.Series
import ru.fire_core.xauplayer.data.local.entities.BookStatus
import ru.fire_core.xauplayer.data.local.entities.SeriesStatus
import ru.fire_core.xauplayer.data.local.entities.DownloadedChapter
import ru.fire_core.xauplayer.data.local.entities.BufferedProgress
import ru.fire_core.xauplayer.data.local.entities.BookList
import ru.fire_core.xauplayer.data.local.entities.Tag
import ru.fire_core.xauplayer.data.local.entities.BookListRelation
import ru.fire_core.xauplayer.data.local.entities.SeriesListRelation
import ru.fire_core.xauplayer.data.local.entities.BookTagRelation
import ru.fire_core.xauplayer.data.local.entities.SeriesTagRelation

@Database(
    entities = [
        Book::class, 
        Chapter::class, 
        Note::class, 
        Progress::class, 
        Device::class, 
        SavedAccount::class,
        Series::class,
        BookStatus::class,
        SeriesStatus::class,
        DownloadedChapter::class, 
        BufferedProgress::class,
        BookList::class,
        Tag::class,
        BookListRelation::class,
        SeriesListRelation::class,
        BookTagRelation::class,
        SeriesTagRelation::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun noteDao(): NoteDao
    abstract fun progressDao(): ProgressDao
    abstract fun deviceDao(): DeviceDao
    abstract fun savedAccountDao(): SavedAccountDao
    abstract fun seriesDao(): SeriesDao
    abstract fun bookStatusDao(): BookStatusDao
    abstract fun seriesStatusDao(): SeriesStatusDao
    abstract fun downloadedChapterDao(): DownloadedChapterDao
    abstract fun bufferedProgressDao(): BufferedProgressDao
    abstract fun listDao(): ListDao
    abstract fun tagDao(): TagDao
    abstract fun bookListRelationDao(): BookListRelationDao
    abstract fun seriesListRelationDao(): SeriesListRelationDao
    abstract fun bookTagRelationDao(): BookTagRelationDao
    abstract fun seriesTagRelationDao(): SeriesTagRelationDao
}


