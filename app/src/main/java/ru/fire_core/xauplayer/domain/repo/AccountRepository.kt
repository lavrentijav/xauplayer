package ru.fire_core.xauplayer.domain.repo

import ru.fire_core.xauplayer.data.local.AppDatabase
import ru.fire_core.xauplayer.data.local.entities.Device
import ru.fire_core.xauplayer.data.network.ApiService
import ru.fire_core.xauplayer.data.network.AccountDto
import ru.fire_core.xauplayer.core.logger.AppLogger
import javax.inject.Inject

interface AccountRepository {
    suspend fun getAccount(): AccountDto
    suspend fun getActivity(): Map<String, Int>
    suspend fun refreshDevices()
    suspend fun getDevices(): List<Device>
    suspend fun revokeDevice(id: Long)
}

class AccountRepositoryImpl @Inject constructor(
    private val api: ApiService,
    private val db: AppDatabase,
    private val logger: AppLogger
) : AccountRepository {
    override suspend fun getAccount() = api.getAccount()
    override suspend fun getActivity() = api.getActivity()
    override suspend fun refreshDevices() {
        val remote = api.getDevices()
        val mapped = remote.map { 
            Device(
                id = it.id, 
                deviceName = it.device_name, 
                lastLogin = parseIso(it.last_login),
                isActive = it.is_active,
                token = it.token
            ) 
        }
        db.deviceDao().upsertAll(mapped)
    }
    override suspend fun getDevices(): List<Device> = db.deviceDao().getAll()
    override suspend fun revokeDevice(id: Long) { api.revokeDevice(id) }

    private fun parseIso(s: String): Long = try {
        java.time.Instant.parse(s).toEpochMilli()
    } catch (e: Exception) {
        logger.warn("AccountRepository", "Failed to parse ISO date: $s", e)
        System.currentTimeMillis()
    }
}
