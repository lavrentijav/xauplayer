package ru.fire_core.xauplayer.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.fire_core.xauplayer.XAuPlayerApp
import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.player.PlayerHolder

/**
 * EntryPoint для доступа к зависимостям из BroadcastReceiver
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun playerHolder(): PlayerHolder
    fun logger(): AppLogger
}

