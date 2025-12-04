package ru.fire_core.xauplayer.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import ru.fire_core.xauplayer.data.datastore.TokenStore
import ru.fire_core.xauplayer.data.datastore.SettingsStore
import ru.fire_core.xauplayer.data.local.AppDatabase
import ru.fire_core.xauplayer.data.network.ApiService
import ru.fire_core.xauplayer.core.logger.AppLogger
import ru.fire_core.xauplayer.core.config.AppConfig
import ru.fire_core.xauplayer.di.NetworkInterceptors
import ru.fire_core.xauplayer.domain.repo.AuthRepository
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideTokenStore(@ApplicationContext context: Context): TokenStore {
        Log.d("AppModule", "[DI] Создание TokenStore...")
        return TokenStore(context).also {
            Log.d("AppModule", "[DI] TokenStore создан")
        }
    }

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore {
        Log.d("AppModule", "[DI] Создание SettingsStore...")
        return SettingsStore(context).also {
            Log.d("AppModule", "[DI] SettingsStore создан")
        }
    }

    @Provides
    @Singleton
    fun provideHttpLogger(): ru.fire_core.xauplayer.core.logger.HttpLogger {
        return ru.fire_core.xauplayer.core.logger.HttpLogger
    }

    @Provides
    @Singleton
    fun provideOkHttp(
        @ApplicationContext context: Context, 
        tokenStore: TokenStore,
        settingsStore: SettingsStore,
        logger: AppLogger,
        networkCache: NetworkCache,
        httpLogger: ru.fire_core.xauplayer.core.logger.HttpLogger
    ): OkHttpClient {
        logger.debug("AppModule", "[DI] Создание OkHttpClient...")
        // Кэш для офлайн работы
        val cacheDir = File(settingsStore.basepath, "http_cache")
        val cacheSize = AppConfig.DEFAULT_MEDIA_CACHE_SIZE_BYTES
        val cache = Cache(cacheDir, cacheSize)
        logger.debug("AppModule", "[DI] HTTP кэш создан")
        
        logger.debug("AppModule", "[DI] Настройка OkHttpClient interceptors...")
        // authRepository передаем как null, чтобы избежать циклической зависимости
        // Он будет доступен через Lazy или Provider, если понадобится
        return OkHttpClient.Builder()
            .cache(cache)
            // Base URL interceptor должен быть первым, чтобы изменять URL перед другими interceptors
            .addInterceptor(NetworkInterceptors.createBaseUrlInterceptor(networkCache))
            .addInterceptor(NetworkInterceptors.createLoggingInterceptor())
            .addInterceptor(NetworkInterceptors.createAuthInterceptor(networkCache))
            .addInterceptor(NetworkInterceptors.createAuthErrorInterceptor(networkCache, tokenStore, settingsStore, logger, httpLogger, null))
            .addInterceptor(NetworkInterceptors.createErrorLoggingInterceptor(logger, httpLogger, null, tokenStore))
            .addInterceptor(NetworkInterceptors.createCacheInterceptor())
            .addNetworkInterceptor(NetworkInterceptors.createNetworkCacheInterceptor())
            .connectTimeout(AppConfig.NETWORK_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(AppConfig.NETWORK_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
            .also {
                logger.debug("AppModule", "[DI] OkHttpClient создан")
            }
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, settingsStore: SettingsStore, logger: AppLogger): Retrofit {
        logger.debug("AppModule", "[DI] Создание Retrofit...")
        // Используем дефолтный URL, чтобы избежать блокировки главного потока
        // Base URL будет обновляться динамически через interceptors или можно сделать Retrofit.Builder ленивым
        val defaultBaseUrl = AppConfig.DEFAULT_API_BASE_URL
        
        logger.debug("AppModule", "[DI] Настройка Moshi конвертера...")
        return Retrofit.Builder()
            .baseUrl(defaultBaseUrl)
            .addConverterFactory(
                MoshiConverterFactory.create(
                    Moshi.Builder()
                        .add(KotlinJsonAdapterFactory())
                        .build()
                )
            )
            .client(client)
            .build()
            .also {
                logger.debug("AppModule", "[DI] Retrofit создан")
            }
    }

    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit, logger: AppLogger): ApiService {
        logger.debug("AppModule", "[DI] Создание ApiService...")
        return retrofit.create(ApiService::class.java).also {
            logger.debug("AppModule", "[DI] ApiService создан")
        }
    }

    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): AppDatabase {
        Log.d("AppModule", "[DI] Создание AppDatabase (Room)...")
        return Room.databaseBuilder(
            context, AppDatabase::class.java, "xau_db"
        ).fallbackToDestructiveMigration().build().also {
            Log.d("AppModule", "[DI] AppDatabase создан")
        }
    }
}

