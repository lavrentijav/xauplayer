package ru.fire_core.xauplayer.domain.usecase.account

import ru.fire_core.xauplayer.domain.repo.AccountRepository
import javax.inject.Inject

class AccountUseCases @Inject constructor(private val repo: AccountRepository) {
    suspend fun getAccount() = repo.getAccount()
    suspend fun getActivity() = repo.getActivity()
    suspend fun refreshDevices() = repo.refreshDevices()
    suspend fun getDevices() = repo.getDevices()
    suspend fun revokeDevice(id: Long) = repo.revokeDevice(id)
}

