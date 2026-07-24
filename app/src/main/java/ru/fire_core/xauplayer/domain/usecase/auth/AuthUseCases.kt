package ru.fire_core.xauplayer.domain.usecase.auth

import ru.fire_core.xauplayer.domain.repo.AuthRepository
import javax.inject.Inject

class RegisterUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke(email: String, password: String, name: String?) = repo.register(email, password, name)
}

class LoginUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke(email: String, password: String, device: String) = repo.login(email, password, device)
}

class SaveTokensUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke(a: String, r: String, d: Long, email: String, userId: Long, name: String?, passwordHash: String? = null) = 
        repo.saveTokens(a, r, d, email, userId, name, passwordHash)
}

class GetSavedAccountsUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke() = repo.getSavedAccounts()
}

class SelectAccountUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke(email: String): Boolean = repo.selectAccount(email)
}

class HasActiveSessionUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke(): Boolean = repo.hasActiveSession()
}

class DeleteAccountUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke(email: String) = repo.deleteAccount(email)
}

class LogoutUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke(saveAccount: Boolean = false) = repo.logout(saveAccount)
}

class EnterServiceAccountUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke() = repo.enterServiceAccount()
}

class RefreshSessionUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke(): Boolean = repo.refreshSession()
}

