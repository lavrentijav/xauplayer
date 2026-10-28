package ru.fire_core.xauplayer.core.security

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

/**
 * Отпечаток устройства для заголовка X-Device-Fp: хэш ANDROID_ID и аппаратных признаков.
 * Сырых идентификаторов наружу не уходит. Сервер использует его, чтобы забанить устройство нарушителя
 * (попавшего в ловушку), даже если он сменил IP.
 */
object DeviceFingerprint {
    const val HEADER = "X-Device-Fp"

    @Volatile
    private var cached: String? = null

    fun get(context: Context): String {
        cached?.let { return it }
        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        } catch (e: Exception) {
            ""
        }
        val raw = listOf(androidId, Build.MANUFACTURER, Build.MODEL, Build.BRAND, Build.HARDWARE, Build.BOARD, context.packageName)
            .joinToString("|")
        val hash = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
        return hash.take(40).also { cached = it }
    }
}
