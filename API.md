# XAuPlayer API — полная документация

Сервер аудиокниг **XAuPlayer** (FastAPI). Базовый префикс большинства эндпоинтов: `/api/v1`.

Интерактивная схема (OpenAPI/Swagger): `https://<host>/docs`  
ReDoc: `https://<host>/redoc`

---

## Содержание

1. [Публичное API vs Admin API](#публичное-api-vs-admin-api) ⚠️
2. [Общие сведения](#общие-сведения)
3. [Аутентификация](#аутентификация)
4. [Ошибки и заголовки](#ошибки-и-заголовки)
5. [Health](#health)
6. [Auth — вход и токены](#auth--вход-и-токены) — *публичное*
7. [Книги и главы](#книги-и-главы) — *публичное*
8. [Стриминг и скачивание](#стриминг-и-скачивание) — *публичное*
9. [Обложки](#обложки) — *публичное*
10. [Серии](#серии) — *публичное*
11. [Прогресс](#прогресс) — *публичное*
12. [Заметки](#заметки) — *публичное*
13. [Статусы книг и серий](#статусы-книг-и-серий) — *публичное*
14. [Статистика](#статистика) — *публичное*
15. [Аккаунт](#аккаунт) — *публичное*
16. [Теги / переводы статусов](#теги--переводы-статусов) — *публичное*
17. [Версии и релизы приложения](#версии-и-релизы-приложения) — *публичное*
18. [Telegram-загрузки](#telegram-загрузки) — *публичное (опционально)*
19. [Admin API](#admin-api) — **только админка, не для клиентов**
20. [Yandex Books (admin)](#yandex-books-admin) — **только админка**
21. [Модели данных](#модели-данных)
22. [Особые механизмы](#особые-механизмы)

---

## Публичное API vs Admin API

API разделено на два контура. **Не смешивайте их в одном клиенте.**

### Публичное API — для пользовательских приложений

Используйте в:
- веб-плеере (`static/index.html`)
- мобильных приложениях (Android/iOS)
- любых клиентах для слушателей

| Префикс / группа | Auth |
|------------------|------|
| `/api/v1/auth` (кроме `admin/login`) | public / user JWT |
| `/api/v1/books`, `/covers`, `/series` | public / user / stream |
| `/api/v1/progress`, `/notes`, `/status`, `/statistics`, `/account` | user JWT |
| `/api/v1/tags`, `/health`, `/version`, `/release` | public |
| `/api/v1/tg/download` | user JWT (если включено) |

Токен: **`access_token`** из `POST /auth/login`. Обновление: `POST /auth/refresh`.

---

### Admin API — только для админ-панели

> **Не использовать в публичных приложениях.**
>
> Эндпоинты `/api/v1/admin/*` и `/api/v1/admin/yandex/*` предназначены **исключительно** для внутренней админки (`/admin`, `static/admin.html`) и доверенных операторов сервера.

**Почему нельзя в клиентских приложениях:**

| Риск | Примеры эндпоинтов |
|------|-------------------|
| Утечка секретов | `/admin/s3-storages`, `/admin/config` |
| Произвольный SQL | `/admin/database/query` |
| Управление всеми пользователями | `/admin/users`, `impersonate` |
| Удаление книг/глав/серий | `DELETE /admin/books/...` |
| Миграция и перезапись хранилища | `/admin/storage/migrate` |
| Загрузка из внешних источников | `/admin/yandex/download`, `/admin/telegram/*` |

Токен: **`admin_token`** из `POST /auth/admin/login` — **отдельный** от user JWT, срок 24 часа.

**Допустимое использование admin API:**
- браузерная админ-панель на том же домене
- внутренние скрипты оператора (CI, бэкап, миграция) с защищённым хранением `admin_token`

**Недопустимо:**
- вшивать `admin_token` в APK/IPA или веб-клиент для слушателей
- вызывать admin-методы из мобильного приложения «для удобства»
- отдавать admin-доступ конечным пользователям

Если клиенту нужны данные, которых нет в публичном API — расширяйте **публичные** эндпоинты на сервере, а не подключайте admin.

---

## Общие сведения

| Параметр | Значение |
|----------|----------|
| Формат | JSON (`Content-Type: application/json`) |
| Кодировка | UTF-8 |
| Даты/время | ISO 8601 (`2026-06-09T12:00:00`) |
| Пагинация | `offset` (с 0) + `limit` |
| CORS | Настраивается через `CORS_ORIGINS` |

### Маршруты вне `/api/v1`

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/` | Веб-клиент (`static/index.html`) |
| GET | `/admin` | Админ-панель (`static/admin.html`) |
| GET | `/static/*` | Статические файлы |
| GET | `/version`, `/versions/latest`, `/release`, `/release/info` | Дубликаты version-эндпоинтов (обратная совместимость) |

### Типы доступа

| Метка | Описание |
|-------|----------|
| **public** | Без авторизации |
| **user** | `Authorization: Bearer <access_token>` |
| **admin** | `Authorization: Bearer <admin_token>` |
| **stream** | JWT **или** query `stream_token` |

---

## Аутентификация

### Пользовательский JWT

1. `POST /api/v1/auth/login` → `access_token` + `refresh_token` + `device_id`
2. Все защищённые запросы: `Authorization: Bearer <access_token>`
3. `POST /api/v1/auth/refresh` → новый `access_token` (refresh тот же)
4. `POST /api/v1/auth/logout?refresh_token=...` → refresh в чёрный список (Redis)

| Токен | Срок жизни (по умолчанию) |
|-------|---------------------------|
| Access token | 7 дней (`ACCESS_TOKEN_EXPIRE_MINUTES=10080`) |
| Refresh token | 30 дней (`REFRESH_TOKEN_EXPIRE_DAYS`) |

**Заголовки при истечении access token:**
- `401` + `X-Token-Expired: true` — токен просрочен
- `X-Token-Expires-Soon: true`, `X-Token-Expires-At` — истекает в ближайшие 5 минут

### Admin token

1. `POST /api/v1/auth/admin/login` (только пользователь с `is_admin=true`)
2. Ответ: `admin_token` (opaque), срок **24 часа**
3. Все `/api/v1/admin/*` и `/api/v1/admin/yandex/*`: `Authorization: Bearer <admin_token>`

> Admin token **не** совместим с user JWT. Это отдельный механизм.

### Stream token (для `<audio>`)

1. Клиент с JWT вызывает `GET /books/{id}/stream/{chapter_id}` с `Accept: application/json`
2. Получает `{ url, stream_token, expires_in: 3600 }`
3. `<audio src="...?stream_token=...">` — без Bearer-заголовка
4. Токен привязан к `(user_id, book_id, chapter_id)`, TTL **1 час**

### Исключение: SSE логов

`GET /api/v1/admin/logs/stream?token=<admin_token>` — токен в query (EventSource не поддерживает кастомные заголовки).

---

## Ошибки и заголовки

### Коды ответов

| Код | Значение |
|-----|----------|
| 200 | Успех |
| 307 | Редирект (скачивание через S3 presigned URL) |
| 400 | Невалидные параметры |
| 401 | Не авторизован / токен недействителен |
| 403 | Нет прав |
| 404 | Не найдено |
| 503 | Функция отключена (`TELEGRAM_DOWNLOAD_ENABLED`, `YANDEX_DOWNLOAD_ENABLED`) |

Типичное тело ошибки:

```json
{ "detail": "Book not found" }
```

### Кэширование (middleware)

| Путь | Cache-Control |
|------|---------------|
| `/api/*` (кроме stream) | `no-store` |
| `/api/v1/books/*/stream/*` | `public, max-age=3600` |
| `/static/*` | `public, max-age=86400` |

---

## Health

### `GET /api/v1/health` — public

Проверка работоспособности сервера.

**Ответ:**
```json
{ "status": "ok", "message": "Server is running" }
```

---

## Auth — вход и токены

Префикс: `/api/v1/auth`

### `POST /register` — public

Регистрация пользователя.

**Тело:**
```json
{
  "email": "user@example.com",
  "password": "secret",
  "name": "Имя"
}
```

**Ответ:** `UserResponse` — `{ id, email, name }`

---

### `POST /login` — public

**Тело:**
```json
{
  "email": "user@example.com",
  "password": "secret",
  "device_name": "Chrome / Windows"
}
```

**Ответ:**
```json
{
  "access_token": "eyJ...",
  "refresh_token": "eyJ...",
  "token_type": "bearer",
  "device_id": 1
}
```

---

### `POST /refresh` — public

**Тело:** `{ "refresh_token": "eyJ..." }`  
**Ответ:** как у `/login` (новый `access_token`, тот же `refresh_token`)

---

### `POST /logout` — user

**Query:** `refresh_token` (обязательный)  
**Ответ:** `{ "status": "logged out" }`

---

### `POST /admin/login` — public

Вход в админ-панель. Пользователь должен иметь `is_admin=true`.

**Тело:** `{ "email": "...", "password": "..." }`

**Ответ:**
```json
{
  "admin_token": "opaque-token",
  "token_type": "bearer",
  "expires_in": 86400,
  "user": { "id": 1, "email": "...", "name": "..." }
}
```

---

## Книги и главы

Префикс: `/api/v1/books`

### `GET /` — public

Список книг (скрытые `is_hidden=true` не возвращаются).

| Query | Тип | По умолчанию | Описание |
|-------|-----|--------------|----------|
| `offset` | int | 0 | Смещение |
| `limit` | int | 100 (max 1000) | Лимит |
| `search` | string | — | Поиск по title, author, narrator, description, названию серии |

**Ответ:** `BookResponse[]`

---

### `GET /{book_id}` — public

Детали книги. При отсутствии `total_size_bytes` — пересчитывается автоматически.

**Ответ:** `BookResponse`

---

### `GET /{book_id}/chapters` — public

Список глав. Главы с `duration=0` или без размера переиндексируются автоматически.

**Ответ:** `ChapterResponse[]` (с полем `download_url`)

---

## Стриминг и скачивание

### `GET /{book_id}/stream/{chapter_id}` — stream

Два режима в зависимости от заголовка `Accept`:

#### Режим 1: JSON (веб-плеер)

**Заголовок:** `Accept: application/json`  
**Auth:** user JWT

**Ответ:**
```json
{
  "url": "https://xau.example.com/api/v1/books/72/stream/301?stream_token=abc...",
  "type": "stream",
  "stream_token": "abc...",
  "expires_in": 3600
}
```

#### Режим 2: Аудиопоток

**Auth:** JWT **или** `?stream_token=...`

**Ответ:** бинарный поток (`audio/mpeg`, `audio/mp4`, …)

- Локальные файлы: `FileResponse` / chunked stream с **HTTP Range**
- S3: прокси через сервер (same-origin), не прямой presigned URL в веб-плеере

---

### `GET /{book_id}/download/{chapter_id}` — user

Прямое скачивание главы.

- S3 включён → `307 Redirect` на presigned/public URL
- Локально → `FileResponse` с `Content-Disposition: attachment`

---

## Обложки

Префикс: `/api/v1/covers`

### `GET /books/{book_id}` — public

Обложка книги (jpeg/png). Поддержка скрытых книг.

### `GET /series/{series_id}` — public

Обложка серии.

**Ответ:** файл изображения, `Accept-Ranges: bytes`

---

## Серии

Префикс: `/api/v1/series`

### `GET /` — public

| Query | Описание |
|-------|----------|
| `offset`, `limit` | Пагинация |
| `search` | Поиск по названию |

**Ответ:** `SeriesResponse[]`

### `GET /{series_id}` — public

**Ответ:** `SeriesResponse`

### `GET /{series_id}/books` — public

Книги в серии. Query: `search?`  
**Ответ:** `BookResponse[]`

---

## Прогресс

Префикс: `/api/v1/progress` — **user**

### `POST /update`

Сохранить позицию воспроизведения.

**Тело:**
```json
{
  "book_id": 72,
  "chapter_id": 301,
  "position_ms": 125000,
  "playback_speed": 1.5,
  "last_update": "2026-06-09T12:00:00"
}
```

**Ответ:** `{ "status": "success" }`

### `GET /sync`

Синхронизация прогресса и активности пользователя.

**Ответ:**
```json
{
  "progress": [
    {
      "book_id": 72,
      "chapter_id": 301,
      "position_ms": 125000,
      "playback_speed": 1.5,
      "last_update": "2026-06-09T12:00:00"
    }
  ],
  "activity": {
    "2026-06-09": 45,
    "2026-06-08": 120
  }
}
```

`activity` — минуты прослушивания по дням (ключ `YYYY-MM-DD`).

---

## Заметки

Префикс: `/api/v1/notes` — **user**

### `GET /{book_id}`

Заметки к книге. **Ответ:** `NoteResponse[]`

### `POST /{book_id}`

**Тело:** `{ "text": "Важный момент", "timestamp": 125000 }`  
`timestamp` — позиция в миллисекундах.

**Ответ:** `NoteResponse`

---

## Статусы книг и серий

Префикс: `/api/v1/status` — **user**

Допустимые значения статуса: `wanted`, `listening`, `completed`, `dropped`

### Книги

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/books?status=listening&offset=0&limit=50` | Книги с заданным статусом → `BookResponse[]` |
| GET | `/books/{book_id}` | Статус книги → `BookStatusOptionalResponse` |
| PUT | `/books/{book_id}` | Установить статус → `BookStatusResponse` |
| DELETE | `/books/{book_id}` | Удалить статус → `{ "status": "deleted" }` |

`BookStatusOptionalResponse` при отсутствии статуса:
```json
{ "book_id": 72, "status": null }
```

### Серии

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/series?status=...` | Серии с статусом → `SeriesResponse[]` |
| GET | `/series/{series_id}` | → `SeriesStatusResponse` |
| PUT | `/series/{series_id}` | Тело: `{ "status": "listening" }` |
| DELETE | `/series/{series_id}` | Удалить статус |

---

## Статистика

Префикс: `/api/v1/statistics` — **user**

### `GET /`

Один из вариантов query (обязателен один набор):

| Вариант | Query |
|---------|-------|
| Месяц | `year=2026&month=6` |
| Год | `year=2026` |
| Период | `start_date=2026-01-01&end_date=2026-06-09` |

**Ответ:**
```json
{
  "statistics": [
    {
      "date": "2026-06-09",
      "minutes_listened": 45,
      "books_completed": 0,
      "chapters_listened": 3
    }
  ],
  "total_minutes": 45,
  "total_books_completed": 0,
  "total_chapters_listened": 3
}
```

---

## Аккаунт

Префикс: `/api/v1/account` — **user**

| Метод | Путь | Ответ |
|-------|------|-------|
| GET | `/` | `AccountResponse` + поле `is_admin` |
| GET | `/activity` | `dict[date, minutes]` — сетка активности |
| GET | `/devices` | `DeviceResponse[]` — устройства/сессии |
| DELETE | `/device/{device_id}` | `{ "status": "deleted" }` |
| GET | `/check-admin` | Отладочная информация о правах admin |

---

## Теги / переводы статусов

Префикс: `/api/v1/tags` — **public**

### `GET /?language=ru`

Переводы статусов для UI.

**Ответ:**
```json
{
  "statuses": [
    { "code": "wanted", "name": "Хочу послушать", "description": "..." },
    { "code": "listening", "name": "Слушаю", "description": "..." }
  ]
}
```

Языки: `en`, `ru` (по умолчанию `en`).

---

## Версии и релизы приложения

Доступны по `/api/v1/...` **и** по корню `/...`.

### `GET /version` — public

Проверка обновлений (Android-клиент).

| Query | Описание |
|-------|----------|
| `os_type` | `windows`, `linux`, `mac`, `android`, `ios` |
| `arch` | `x64`, `x86`, `arm64`, `arm` |

**Ответ:** `{ "version": "1.2.0", "build_number": 42 }`

### `GET /versions/latest` — public

Расширенная информация о последней версии + `download_url`.

### `GET /release` — public

Скачивание бинарника релиза (`FileResponse`).

| Query | Описание |
|-------|----------|
| `os_type`, `arch` | Платформа |
| `version` | Конкретная версия (опционально) |

### `GET /release/info` — public

Метаданные релиза без скачивания файла.

---

## Telegram-загрузки

Префикс: `/api/v1/tg/download` — **user**

> Требует `TELEGRAM_DOWNLOAD_ENABLED=true`, иначе **503**.

### Жизненный цикл

```
searching → files_found → (markup) → markup_provided → (confirm) → downloading → processing → completed | failed
```

### Эндпоинты

| Метод | Путь | Описание |
|-------|------|----------|
| POST | `/` | Начать поиск в канале |
| GET | `/` | Список загрузок (`status?`, `limit`) |
| GET | `/{download_id}` | Детали загрузки |
| POST | `/{download_id}/markup` | Разметка файлов |
| POST | `/{download_id}/confirm` | Запуск скачивания |
| GET | `/{download_id}/status` | Статус |

**Создание:**
```json
{
  "channel_username": "my_channel",
  "search_query": "Название книги"
}
```

**Разметка (`DownloadMarkup`):**
```json
{
  "series_name": "Название серии",
  "books": [
    {
      "title": "Книга 1",
      "chapters": [
        { "file_index": 0, "title": "Глава 1" },
        { "file_index": 1, "title": "Глава 2" }
      ]
    }
  ]
}
```

---

## Admin API

> ⚠️ **Только админ-панель. Не использовать в публичных приложениях** (веб-плеер, Android, iOS и т.д.). См. [Публичное API vs Admin API](#публичное-api-vs-admin-api).

Префикс: `/api/v1/admin` — **admin** (кроме SSE логов — см. [Аутентификация](#аутентификация))

### Пользователи

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/users` | Список (`search?`, `offset`, `limit`) |
| GET | `/users/{user_id}` | Детали |
| PUT | `/users/{user_id}` | Обновление (`UserAdminUpdate`) |
| DELETE | `/users/{user_id}` | Удаление |
| GET | `/users/{user_id}/stats` | Статистика пользователя |
| GET | `/users/{user_id}/progress` | Прогресс (`book_id?`) |
| PUT | `/users/{user_id}/progress/{book_id}` | Query: `chapter_id`, `position_ms`, `playback_speed` |
| DELETE | `/users/{user_id}/progress/{progress_id}` | Удалить запись |
| POST | `/users/{user_id}/impersonate` | JWT от имени пользователя |
| GET | `/users/{user_id}/files` | Файлы пользователя (`include_audio`) |

### Книги и главы

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/books` | Список (`include_hidden=true` по умолчанию) |
| GET | `/books/{book_id}` | `BookAdminResponse` (+ `storage_location`) |
| PUT | `/books/{book_id}` | `BookAdminUpdate` |
| DELETE | `/books/{book_id}` | Удаление |
| POST | `/books/{book_id}/move` | Перемещение в другую серию |
| GET | `/books/{book_id}/chapters` | Главы |
| PUT | `/chapters/{chapter_id}` | `ChapterAdminUpdate` |
| DELETE | `/chapters/{chapter_id}` | Удаление главы |
| POST | `/books/{book_id}/cover` | multipart: `file` (jpg/png) |
| POST | `/books/reorder` | Порядок книг в серии |

`BookAdminResponse` дополнительно содержит `storage_location`: `local` или `s3_<profile>`.

### Серии (admin)

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/series` | Список |
| GET | `/series/{series_id}` | Детали |
| GET | `/series/{series_id}/books` | Книги серии |
| PUT | `/series/{series_id}` | `SeriesAdminUpdate` |

### Система

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/stats` | `SystemStatsResponse` — счётчики БД |

### Логи

| Метод | Путь | Auth | Описание |
|-------|------|------|----------|
| GET | `/logs` | admin | Последние строки (`lines`, `level?`) |
| GET | `/logs/stream` | query `token` | **SSE** — поток логов |
| GET | `/logs/download` | admin | Скачать файл логов |

SSE формат: `data: <строка лога>\n\n`

### Версии приложения (admin)

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/versions` | Список (`platform?`) |
| GET | `/versions/{version_id}` | Детали |
| POST | `/versions` | Создать |
| POST | `/versions/{version_id}/upload` | multipart: бинарник |
| PUT | `/versions/{version_id}` | Обновить |
| DELETE | `/versions/{version_id}` | Удалить |
| POST | `/versions/{version_id}/set-latest` | Пометить как latest |

### Миграция хранилища

| Метод | Путь | Описание |
|-------|------|----------|
| POST | `/storage/migrate` | Синхронная миграция |
| POST | `/storage/migrate/stream` | **SSE** с прогрессом |

**Тело (`StorageMigrationRequest`):**
```json
{
  "from_location": "local",
  "to_location": "s3_msk-1",
  "book_id": null,
  "series_id": null,
  "dry_run": false,
  "copy_only": false
}
```

`from_location` / `to_location`: `local` или `s3_<profile>` (имя профиля из БД).

SSE-события: JSON с полями `progress`, `complete`, `error`.

### Telegram (admin)

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/telegram/status` | Статус клиента |
| POST | `/telegram/reconnect` | Переподключение |
| POST | `/telegram/disconnect` | Отключение |
| POST | `/telegram/session/upload` | multipart: session-файл Telethon |

### База данных (admin)

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/database/tables` | Список таблиц |
| GET | `/database/tables/{table_name}` | Данные (`offset`, `limit`) |
| POST | `/database/query` | Произвольный SQL (`max_rows`) |
| PUT | `/database/tables/{table_name}/rows/{row_id}` | Обновить строку |
| DELETE | `/database/tables/{table_name}/rows/{row_id}` | Удалить строку |

> `/database/query` выполняет любой SQL — используйте с осторожностью.

### Конфигурация (admin)

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/config` | Текущие настройки (секреты замаскированы) |
| PUT | `/config` | Обновление runtime-настроек (не пишет в `.env`) |

Допустимые ключи PUT: `AUDIO_DIR`, `MEDIA_SERVER_URL`, `CHAPTER_DOWNLOAD_PREFIX`, `RELEASES_DIR`, `S3_*`, `TELEGRAM_*`, сроки токенов.

### S3-хранилища (admin)

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/s3-storages` | Список (`include_inactive`) |
| GET | `/s3-storages/{id}` | Детали |
| POST | `/s3-storages` | Создать профиль |
| PUT | `/s3-storages/{id}` | Обновить |
| POST | `/s3-storages/{id}/test-connection` | Проверка подключения |
| DELETE | `/s3-storages/{id}` | Удалить |

Профили S3 хранятся в БД. Credentials из `.env` используются только как fallback при отсутствии профиля.

---

## Yandex Books (admin)

> ⚠️ **Только админка.** Загрузка аудиокниг с Яндекс Книг — не для пользовательских клиентов.

Префикс: `/api/v1/admin/yandex` — **admin**

> Большинство эндпоинтов требует `YANDEX_DOWNLOAD_ENABLED=true`.

### Авторизация Bookmate

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/status` | Статус интеграции |
| GET | `/oauth/url` | URL для OAuth Яндекса |
| POST | `/auth/oauth` | Сохранить OAuth token (`access_token`) |
| POST | `/auth/session` | Сохранить `Session_id` cookie |
| POST | `/auth/logout` | Удалить credentials |

### Загрузка аудиокниг

| Метод | Путь | Описание |
|-------|------|----------|
| POST | `/preview` | Предпросмотр по URL/UUID |
| POST | `/download` | Запуск загрузки |
| GET | `/downloads` | Последние 50 загрузок |
| GET | `/downloads/{id}` | Детали |
| POST | `/downloads/{id}/cancel` | Отмена |

**Preview — тело:** `{ "url_or_uuid": "https://books.yandex.ru/audiobooks/uBb0slBR" }`

**Preview — ответ:**
```json
{
  "uuid": "uBb0slBR",
  "title": "Книга 4. ...",
  "author": "Николай Новиков",
  "series": "Похоже, я доигрался",
  "narrators": ["Ященко Игорь"],
  "duration_seconds": 33420,
  "chapters_count": 32,
  "cover_url": "https://...",
  "description": "...",
  "can_be_listened": true
}
```

**Download — тело:**
```json
{
  "url_or_uuid": "uBb0slBR",
  "series_name": "Похоже, я доигрался",
  "max_bitrate": true,
  "storage_target": "local_then_s3"
}
```

`storage_target`: `local` | `s3` | `local_then_s3`

**Статусы загрузки:** `pending`, `downloading`, `processing`, `completed`, `failed`, `cancelled`

---

## Модели данных

### BookResponse

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | int | ID |
| `title` | string | Название |
| `author` | string | Автор |
| `narrator` | string? | Чтец |
| `cover_url` | string? | Путь к обложке (`/api/v1/covers/...`) |
| `description` | string? | Описание |
| `path` | string? | Путь в хранилище (`серия/книга`) |
| `series_id` | int? | ID серии |
| `series_order` | int? | Порядок в серии |
| `is_hidden` | bool | Скрыта из публичного API |
| `total_size_bytes` | int? | Суммарный размер глав |
| `uploaded_at` | datetime | Дата добавления |

### ChapterResponse

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | int | ID |
| `title` | string | Название |
| `duration` | float? | Секунды |
| `path` | string | Путь/ключ файла |
| `file_size_bytes` | int? | Размер |
| `order` | int | Порядок |
| `real_order` | int? | Ручной порядок |
| `download_url` | string? | URL скачивания |

### SeriesResponse

| Поле | Тип |
|------|-----|
| `id` | int |
| `name` | string |
| `description` | string? |
| `cover_url` | string? |
| `created_at` | datetime |

### NoteResponse

| Поле | Тип |
|------|-----|
| `id` | int |
| `text` | string |
| `timestamp` | int (мс) |
| `created_at` | datetime |
| `updated_at` | datetime |

### SystemStatsResponse

`total_users`, `total_admins`, `total_books`, `total_hidden_books`, `total_series`, `total_chapters`, `total_progress_entries`, `total_notes`, `total_devices`

---

## Особые механизмы

### Хранилище аудио

Книги и главы могут находиться:
- **локально** — `AUDIO_DIR/<серия>/<книга>/01.m4a`
- **S3** — `chapter.location = s3_<profile>`, ключ в `chapter.path`

Поле `BookAdminResponse.storage_location` показывает, где лежат файлы.

### Индексация

При старте сервера и при изменении файлов (`file_watcher`) запускается `index_audio_library()` — сканирование `AUDIO_DIR` и S3-профилей.

### Фоновые задачи

| Задача | Триггер |
|--------|---------|
| Telegram download | `POST /tg/download/{id}/confirm` |
| Yandex download | `POST /admin/yandex/download` |
| Storage migration SSE | `POST /admin/storage/migrate/stream` |

### Webhooks

Входящих webhook-эндпоинтов нет.

### Примеры curl

**Логин:**
```bash
curl -X POST https://xau.example.com/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"secret","device_name":"curl"}'
```

**Список книг:**
```bash
curl https://xau.example.com/api/v1/books?search=ведьмак&limit=10
```

**Stream URL для плеера:**
```bash
curl https://xau.example.com/api/v1/books/72/stream/301 \
  -H "Authorization: Bearer <access_token>" \
  -H "Accept: application/json"
```

**Admin login:**
```bash
curl -X POST https://xau.example.com/api/v1/auth/admin/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@example.com","password":"secret"}'
```

---

## Сводная таблица эндпоинтов

### Публичное API (для клиентских приложений)

| Группа | Кол-во | Auth |
|--------|--------|------|
| Health | 1 | public |
| Auth (login/register/refresh/logout) | 4 | public / user |
| Books | 5 | public / user / stream |
| Covers | 2 | public |
| Series | 3 | public |
| Progress | 2 | user |
| Notes | 2 | user |
| Account | 5 | user |
| Status | 8 | user |
| Statistics | 1 | user |
| Tags | 1 | public |
| Version/Release | 4 (+4 root) | public |
| Telegram | 6 | user |

**~44 эндпоинта** — этим ограничивайтесь в публичных приложениях.

### Admin API (только админ-панель, не для клиентов)

| Группа | Кол-во | Auth |
|--------|--------|------|
| Auth (`admin/login`) | 1 | public → admin token |
| Admin | 53 | admin |
| Yandex admin | 11 | admin |

**~65 эндпоинтов** — не подключать к APK, веб-плееру и т.п.

**Итого на сервере:** ~109 обработчиков под `/api/v1`.

---

*Документация сгенерирована по коду XAuServer. При расхождении с `/docs` приоритет у OpenAPI-схемы FastAPI.*
