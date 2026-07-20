package ru.fire_core.xauplayer.core.config

import java.util.concurrent.TimeUnit

/**
 * Централизованная конфигурация приложения
 * Содержит все магические числа, задержки, таймауты и значения по умолчанию
 */
object AppConfig {
    
    // ========== API и сетевые настройки ==========
    
    /** URL API по умолчанию */
    const val DEFAULT_API_BASE_URL = "https://api.xau.fire-core.ru/api/v1/"
    
    /** URL для обновлений по умолчанию (использует API endpoint /release) */
    const val DEFAULT_UPDATE_URL = "https://api.xau.fire-core.ru/release"


    // ========== Оффлайн-режим и служебный аккаунт ==========

    /**
     * Email служебного (сервисного) аккаунта приложения.
     * Когда активен этот аккаунт, сетевой слой перехватывает запросы к серверу
     * и эмулирует положительные ответы, позволяя слушать уже скачанный контент
     * без доступа к серверу и с истёкшими сессиями.
     */
    const val SERVICE_ACCOUNT_EMAIL = "offline@xauplayer.local"

    /** Отображаемое имя служебного аккаунта */
    const val SERVICE_ACCOUNT_NAME = "Оффлайн-режим"

    /** Access-токен служебного аккаунта (используется как маркер оффлайн-режима) */
    const val SERVICE_ACCESS_TOKEN = "offline-service-access-token"

    /** Refresh-токен служебного аккаунта */
    const val SERVICE_REFRESH_TOKEN = "offline-service-refresh-token"

    /** Через сколько дней по умолчанию истекает локальная сессия */
    const val DEFAULT_SESSION_EXPIRY_DAYS = 30

    /** Минимально допустимое время жизни сессии (дни) */
    const val MIN_SESSION_EXPIRY_DAYS = 1

    /** Максимально допустимое время жизни сессии (дни) */
    const val MAX_SESSION_EXPIRY_DAYS = 365
    
    /** Интервал проверки обновлений (24 часа) */
    const val UPDATE_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    
    /** Таймаут подключения в секундах */
    const val NETWORK_CONNECT_TIMEOUT_SECONDS = 30L
    
    /** Таймаут чтения в секундах */
    const val NETWORK_READ_TIMEOUT_SECONDS = 60L
    
    /** Максимальное время хранения кэша в днях */
    const val NETWORK_CACHE_MAX_STALE_DAYS = 7
    
    /** Максимальный лимит записей в API запросах */
    const val API_MAX_LIMIT = 1000
    
    /** Начальный лимит книг для быстрой загрузки */
    const val API_INITIAL_BOOKS_LIMIT = 10
    
    /** Дефолтный лимит для API запросов */
    const val API_DEFAULT_LIMIT = 50
    
    
    // ========== Задержки и интервалы (в миллисекундах) ==========
    
    /** Задержка для debounce UI обновлений */
    const val UI_DEBOUNCE_DELAY_MS = 300L
    
    /** Задержка для периодического обновления списков */
    const val PERIODIC_UPDATE_INTERVAL_MS = 5000L
    
    /** Задержка для анимаций и переходов */
    const val ANIMATION_DELAY_MS = 300L
    
    /** Задержка для коротких операций */
    const val SHORT_DELAY_MS = 100L
    
    /** Задержка для средних операций */
    const val MEDIUM_DELAY_MS = 500L
    
    /** Задержка для длинных операций */
    const val LONG_DELAY_MS = 1000L
    
    /** Задержка для очень длинных операций */
    const val VERY_LONG_DELAY_MS = 2000L
    
    /** Интервал синхронизации прогресса по умолчанию (30 секунд) */
    const val DEFAULT_PROGRESS_SYNC_INTERVAL_MS = 30000L
    
    /** Интервал обновления позиции по умолчанию (2 секунды) */
    const val DEFAULT_POSITION_UPDATE_INTERVAL_MS = 2000L

    /**
     * Интервал плавного обновления позиции в интерфейсе (мс).
     * Отдельно от DEFAULT_POSITION_UPDATE_INTERVAL_MS: последний используется для
     * сохранения/синхронизации прогресса (реже, ради батареи), а этот — только для
     * гладкого движения ползунка и таймера в UI (чтение позиции без ввода-вывода).
     */
    const val UI_POSITION_POLL_INTERVAL_MS = 500L
    
    /** Интервал синхронизации офлайн прогресса (30 секунд) */
    const val OFFLINE_PROGRESS_SYNC_INTERVAL_MS = 30000L
    
    /** Интервал ожидания готовности плеера (100ms между проверками) */
    const val PLAYER_READY_CHECK_INTERVAL_MS = 100L
    
    /** Максимальное количество попыток проверки готовности плеера */
    const val PLAYER_READY_MAX_ATTEMPTS = 100
    
    /** Максимальное количество попыток для подготовки плеера */
    const val PLAYER_PREPARE_MAX_ATTEMPTS = 50
    
    /** Максимальное количество попыток ожидания загрузки главы (30 секунд) */
    const val CHAPTER_DOWNLOAD_WAIT_MAX_ATTEMPTS = 60
    
    /** Максимальное количество попыток ожидания загрузки главы для подготовки (5 секунд) */
    const val CHAPTER_DOWNLOAD_PREPARE_MAX_ATTEMPTS = 10
    
    /** Задержка перед сбросом флага воспроизведения */
    const val PLAYBACK_FLAG_RESET_DELAY_MS = 2000L
    
    /** Задержка перед перемоткой после смены главы */
    const val CHAPTER_SEEK_DELAY_MS = 500L
    
    
    // ========== Кэширование ==========
    
    /** Время жизни кэша статусов (5 минут) */
    const val STATUS_CACHE_VALIDITY_DURATION_MS = 5 * 60 * 1000L
    
    /** Время жизни кэша списков книг в ViewModel (5 секунд) */
    const val BOOKS_LIST_CACHE_VALIDITY_DURATION_MS = 5000L
    
    /** Интервал синхронизации книг (5 минут) */
    const val BOOKS_SYNC_INTERVAL_MS = 5 * 60 * 1000L
    
    /** Интервал синхронизации глав (5 минут) */
    const val CHAPTERS_SYNC_INTERVAL_MS = 5 * 60 * 1000L
    
    /** Время жизни временного статуса в кэше (1 минута) */
    const val TEMP_STATUS_CACHE_DURATION_MS = 60 * 1000L
    
    /** Период для очистки старых буферизованных прогрессов (7 дней) */
    const val BUFFERED_PROGRESS_CLEANUP_PERIOD_MS = 7 * 24 * 60 * 60 * 1000L
    
    
    // ========== Размеры кэша и файлов ==========
    
    /** Размер кэша медиа по умолчанию (50 MB) */
    const val DEFAULT_MEDIA_CACHE_SIZE_BYTES = 50L * 1024L * 1024L
    
    /** Максимальный размер лог-файла (5 MB) */
    const val MAX_LOG_FILE_SIZE_BYTES = 5 * 1024 * 1024
    
    /** Количество дней хранения логов */
    const val MAX_LOG_FILES_DAYS = 7
    
    /** Количество байт для конвертации в MB */
    const val BYTES_PER_MB = 1024L * 1024L
    
    /** Количество байт для конвертации в KB */
    const val BYTES_PER_KB = 1024L
    
    
    // ========== Плеер ==========
    
    /** Шаг перемотки назад по умолчанию (15 секунд) */
    const val DEFAULT_SEEK_BACK_INCREMENT_MS = 15000L
    
    /** Шаг перемотки вперед по умолчанию (15 секунд) */
    const val DEFAULT_SEEK_FORWARD_INCREMENT_MS = 15000L
    
    /** Скорость воспроизведения по умолчанию */
    const val DEFAULT_PLAYBACK_SPEED = 1.0f
    
    /** Секунды перемотки назад по умолчанию */
    const val DEFAULT_REWIND_SECONDS = 30
    
    /** Секунды перемотки вперед по умолчанию */
    const val DEFAULT_FORWARD_SECONDS = 15
    
    /** Интервалы задержки для retry при ошибках загрузки */
    val RETRY_DELAYS_MS = listOf(
        1000L,   // 1 секунда
        2000L,   // 2 секунды
        5000L,   // 5 секунд
        10000L   // 10 секунд
    )
    
    /** Максимальное количество попыток retry */
    const val MAX_RETRY_ATTEMPTS = Int.MAX_VALUE
    
    /** Интервал проверки скорости загрузки (100ms) */
    const val DOWNLOAD_SPEED_CHECK_INTERVAL_MS = 100L
    
    /** Максимальная задержка для ограничения скорости (200ms) */
    const val MAX_DOWNLOAD_THROTTLE_DELAY_MS = 200L
    
    /** Интервал обновления прогресса загрузки (500ms) */
    const val DOWNLOAD_PROGRESS_UPDATE_INTERVAL_MS = 500L
    
    
    // ========== Загрузки ==========
    
    /** Максимальное количество параллельных загрузок по умолчанию */
    const val DEFAULT_MAX_CONCURRENT_DOWNLOADS = 3
    
    /** Максимальная скорость загрузки по умолчанию (0 = без ограничений) */
    const val DEFAULT_MAX_DOWNLOAD_SPEED_KBPS = 0
    
    /** Размер буфера для загрузки (8 KB) */
    const val DOWNLOAD_BUFFER_SIZE_BYTES = 8192
    
    
    // ========== Сетевые запросы ==========
    
    /** Максимальное количество параллельных запросов статусов */
    const val MAX_CONCURRENT_STATUS_REQUESTS = 5
    
    /** Лимит для запросов статусов */
    const val STATUS_REQUEST_LIMIT = 1000
    
    
    // ========== UI настройки ==========
    
    /** Таймаут подписки на StateFlow (5 секунд) */
    const val STATE_FLOW_SUBSCRIPTION_TIMEOUT_MS = 5000L
    
    /** Лимит логов для отображения */
    const val LOGS_DISPLAY_LIMIT = 1000
    
    /** Количество секунд в минуте */
    const val SECONDS_PER_MINUTE = 60
    
    /** Количество миллисекунд в секунде */
    const val MILLISECONDS_PER_SECOND = 1000L
    
    /** Количество миллисекунд в минуте */
    const val MILLISECONDS_PER_MINUTE = 60 * 1000L
    
    /** Количество миллисекунд в часе */
    const val MILLISECONDS_PER_HOUR = 60 * 60 * 1000L
    
    /** Количество миллисекунд в дне */
    const val MILLISECONDS_PER_DAY = 24 * 60 * 60 * 1000L
    
    
    // ========== Валидация ==========
    
    /** Минимальная длина пароля */
    const val MIN_PASSWORD_LENGTH = 4
    
    /** Минимальный лимит для API запросов */
    const val MIN_API_LIMIT = 1
    
    /** Максимальный лимит для API запросов */
    const val MAX_API_LIMIT = 1000
    
    /** Минимальный ID (для валидации) */
    const val MIN_ID = 1L
    
    
    // ========== Цвета по умолчанию ==========
    
    /** Цвет акцента по умолчанию (Spotify green) */
    const val DEFAULT_ACCENT_COLOR = "#1DB954"
    
    /** Цвет плеера по умолчанию (Spotify green) */
    const val DEFAULT_PLAYER_COLOR = "#1DB954"
    
    
    // ========== Пути и файлы ==========
    
    /** Имя директории для загруженных глав */
    const val DOWNLOADED_CHAPTERS_DIR = "downloaded_chapters"
    
    /** Имя директории для кэша медиа */
    const val MEDIA_CACHE_DIR = "media_cache"
    
    /** Имя директории для логов */
    const val LOGS_DIR = "logs"
    
    /** Префикс файла лога */
    const val LOG_FILE_PREFIX = "log_"
    
    /** Расширение файла лога */
    const val LOG_FILE_EXTENSION = ".txt"
    
    /** Префикс экспорта логов */
    const val LOG_EXPORT_PREFIX = "logs_export_"
    
    /** Префикс ротированного лога */
    const val LOG_ROTATED_PREFIX = "log_"
    
    /** Разделитель для логов */
    const val LOG_SEPARATOR = "=================================================================================="
    
    
    // ========== Пользовательский агент ==========
    
    /** User-Agent для HTTP запросов */
    const val HTTP_USER_AGENT = "XAuPlayer"
    
    
    // ========== Статусы книг ==========
    
    /** Допустимые статусы книг */
    val VALID_BOOK_STATUSES = listOf("wanted", "listening", "completed", "dropped")
    
    /** Статус по умолчанию при создании */
    const val DEFAULT_BOOK_STATUS = "listening"
    
    
    // ========== Буферизация ==========
    
    /** Размер буфера перед воспроизведением (в секундах) */
    const val DEFAULT_BUFFER_BEFORE_SECONDS = 1
    
    /** Размер буфера после воспроизведения (в секундах) */
    const val DEFAULT_BUFFER_AFTER_SECONDS = 1
    
    
    // ========== Эквалайзер ==========
    
    /** Количество полос эквалайзера */
    const val EQUALIZER_BANDS_COUNT = 5
    
    /** Минимальная частота для отображения (1000 Hz) */
    const val EQUALIZER_MIN_FREQ_DISPLAY = 1000
    
    
    // ========== Вспомогательные функции ==========
    
    /**
     * Конвертирует миллисекунды в секунды
     */
    fun msToSeconds(ms: Long): Long = ms / MILLISECONDS_PER_SECOND
    
    /**
     * Конвертирует секунды в миллисекунды
     */
    fun secondsToMs(seconds: Long): Long = seconds * MILLISECONDS_PER_SECOND
    
    /**
     * Конвертирует байты в мегабайты
     */
    fun bytesToMB(bytes: Long): Double = bytes.toDouble() / BYTES_PER_MB
    
    /**
     * Конвертирует байты в килобайты
     */
    fun bytesToKB(bytes: Long): Double = bytes.toDouble() / BYTES_PER_KB
}

