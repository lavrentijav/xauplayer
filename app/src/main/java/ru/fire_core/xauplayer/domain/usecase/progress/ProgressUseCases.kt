package ru.fire_core.xauplayer.domain.usecase.progress

import ru.fire_core.xauplayer.data.local.entities.Progress
import ru.fire_core.xauplayer.domain.repo.ProgressRepository
import javax.inject.Inject

class SaveProgressUseCase @Inject constructor(private val repo: ProgressRepository) {
    suspend operator fun invoke(p: Progress) = repo.saveLocal(p)
}

class SyncProgressUseCase @Inject constructor(private val repo: ProgressRepository) {
    suspend operator fun invoke(p: Progress) = repo.syncToServer(p)
}

class SyncProgressFromServerUseCase @Inject constructor(private val repo: ProgressRepository) {
    suspend operator fun invoke() = repo.syncFromServer()
}

class GetProgressUseCase @Inject constructor(private val repo: ProgressRepository) {
    suspend operator fun invoke(bookId: Long) = repo.getLocal(bookId)
}

class GetAllProgressUseCase @Inject constructor(private val repo: ProgressRepository) {
    suspend operator fun invoke() = repo.getAllLocal()
}

