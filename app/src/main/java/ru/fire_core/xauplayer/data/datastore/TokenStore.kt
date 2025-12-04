package ru.fire_core.xauplayer.data.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "xau_tokens")

class TokenStore(private val context: Context) {
    private val keyAccess = stringPreferencesKey("access_token")
    private val keyRefresh = stringPreferencesKey("refresh_token")
    private val keyDeviceId = longPreferencesKey("device_id")
    private val keyCurrentEmail = stringPreferencesKey("current_email")

    val accessToken: Flow<String?> = context.dataStore.data.map { it[keyAccess] }
    val refreshToken: Flow<String?> = context.dataStore.data.map { it[keyRefresh] }
    val deviceId: Flow<Long?> = context.dataStore.data.map { it[keyDeviceId] }
    val currentEmail: Flow<String?> = context.dataStore.data.map { it[keyCurrentEmail] }

    suspend fun save(access: String, refresh: String, deviceId: Long, email: String? = null) {
        context.dataStore.edit { preferences ->
            preferences[keyAccess] = access
            preferences[keyRefresh] = refresh
            preferences[keyDeviceId] = deviceId
            email?.let { emailValue ->
                preferences[keyCurrentEmail] = emailValue
            }
        }
    }

    suspend fun setCurrentEmail(email: String) {
        context.dataStore.edit {
            it[keyCurrentEmail] = email
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}


