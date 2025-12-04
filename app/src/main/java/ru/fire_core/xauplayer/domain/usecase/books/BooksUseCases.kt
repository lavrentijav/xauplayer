package ru.fire_core.xauplayer.domain.usecase.books

import ru.fire_core.xauplayer.domain.repo.BooksRepository
import javax.inject.Inject

class RefreshBooksUseCase @Inject constructor(private val repo: BooksRepository) {
    suspend operator fun invoke() = repo.refreshBooks()
}

class SyncBooksUseCase @Inject constructor(private val repo: BooksRepository) {
    suspend operator fun invoke(force: Boolean = false, loadAll: Boolean = false) = repo.syncBooks(force, loadAll)
}

class ShouldSyncBooksUseCase @Inject constructor(private val repo: BooksRepository) {
    suspend operator fun invoke() = repo.shouldSyncBooks()
}

class GetBooksUseCase @Inject constructor(private val repo: BooksRepository) {
    suspend operator fun invoke() = repo.getBooks()
}

class LoadBooksByIdsUseCase @Inject constructor(private val repo: BooksRepository) {
    suspend operator fun invoke(bookIds: List<Long>) = repo.loadBooksByIds(bookIds)
}

class RefreshChaptersUseCase @Inject constructor(private val repo: BooksRepository) {
    suspend operator fun invoke(bookId: Long) = repo.refreshChapters(bookId)
}

class SyncChaptersUseCase @Inject constructor(private val repo: BooksRepository) {
    suspend operator fun invoke(bookId: Long, force: Boolean = false) = repo.syncChapters(bookId, force)
}

class ShouldSyncChaptersUseCase @Inject constructor(private val repo: BooksRepository) {
    suspend operator fun invoke(bookId: Long) = repo.shouldSyncChapters(bookId)
}

class GetChaptersUseCase @Inject constructor(private val repo: BooksRepository) {
    suspend operator fun invoke(bookId: Long) = repo.getChapters(bookId)
}

