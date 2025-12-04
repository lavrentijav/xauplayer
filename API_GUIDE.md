# 📚 API Документация - Полное руководство

**Версия:** 2.0.0  
**Последнее обновление:** 2025-01-XX  
**Базовый URL:** `https://api.xau.fire-core.ru`

> ⚠️ **ВАЖНО:** Для домена `api.xau.fire-core.ru` автоматически добавляется префикс `/api/v1` ко всем путям.  
> Например, запрос к `/books` фактически обращается к `/api/v1/books`.

---

## 📋 Содержание

1. [Быстрый старт](#быстрый-старт)
2. [Аутентификация](#аутентификация)
3. [Книги](#книги)
4. [Серии](#серии)
5. [Обложки](#обложки)
6. [Прогресс прослушивания](#прогресс-прослушивания)
7. [Статусы книг и серий](#статусы-книг-и-серий)
8. [Заметки](#заметки)
9. [Статистика](#статистика)
10. [Аккаунт](#аккаунт)
11. [Версия и релизы](#версия-и-релизы)
12. [Админ-панель](#админ-панель)
13. [Telegram интеграция](#telegram-интеграция)
14. [Health Check](#health-check)
15. [Коды ошибок](#коды-ошибок)
16. [Важные примечания](#важные-примечания)

---

## 🚀 Быстрый старт

### 1. Регистрация нового пользователя

```javascript
const response = await fetch('https://api.xau.fire-core.ru/auth/register', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    email: 'user@example.com',
    password: 'password123',
    name: 'Имя пользователя'
  })
});

const user = await response.json();
console.log('Пользователь создан:', user);
```

### 2. Вход в систему

```javascript
const response = await fetch('https://api.xau.fire-core.ru/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    email: 'user@example.com',
    password: 'password123',
    device_name: 'iPhone 12'
  })
});

const { access_token, refresh_token } = await response.json();

// Сохраните токены
localStorage.setItem('access_token', access_token);
localStorage.setItem('refresh_token', refresh_token);
```

### 3. Использование токена в запросах

```javascript
const token = localStorage.getItem('access_token');

const response = await fetch('https://api.xau.fire-core.ru/books', {
  headers: {
    'Authorization': `Bearer ${token}`
  }
});

const books = await response.json();
```

---

## 🔐 Аутентификация

Все endpoints (кроме `/auth/*`, `/health`, `/version`, `/release`, `/covers/*`) требуют Bearer токен в заголовке:

```
Authorization: Bearer <access_token>
```

### POST `/auth/register`

Регистрация нового пользователя.

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "password123",
  "name": "Имя пользователя"
}
```

**Response:** `200 OK`
```json
{
  "id": 1,
  "email": "user@example.com",
  "name": "Имя пользователя"
}
```

**Ошибки:**
- `400` - Email уже используется или неверный формат
- `422` - Неверные данные (валидация)

---

### POST `/auth/login`

Вход в систему. Возвращает access token и refresh token.

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "password123",
  "device_name": "iPhone 12"
}
```

**Response:** `200 OK`
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "bearer",
  "device_id": 1
}
```

**Важно:**
- `access_token` используется для всех запросов (истекает через 7 дней)
- `refresh_token` используется для обновления access token (истекает через 30 дней)
- `device_id` - ID устройства, можно использовать для управления устройствами

**Ошибки:**
- `401` - Неверный email или пароль
- `422` - Неверные данные

---

### POST `/auth/refresh`

Обновить access token используя refresh token.

**Когда использовать:**
- Когда получили заголовок `X-Token-Expires-Soon: true`
- Когда получили ошибку `401` с заголовком `X-Token-Expired: true`

**Request Body:**
```json
{
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response:** `200 OK`
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "bearer",
  "device_id": 0
}
```

**Ошибки:**
- `401` - Refresh token неверный или истек

---

### POST `/auth/logout`

Выход из системы. Добавляет refresh token в черный список.

**Query Parameters:**
- `refresh_token` (string, required) - Refresh token для добавления в черный список

**Пример:**
```
POST /auth/logout?refresh_token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Response:** `200 OK`
```json
{
  "status": "logged out"
}
```

---

## 📖 Книги

### GET `/books`

Получить список книг с пагинацией и поиском.

**Query Parameters:**
- `offset` (int, default: 0) - Количество записей для пропуска (пагинация)
- `limit` (int, default: 100, max: 1000) - Максимальное количество записей
- `search` (string, optional, max: 200) - Поисковый запрос (безопасно обработанный, защищен от SQL-инъекций)

**Примеры:**
```
GET /books
GET /books?offset=0&limit=20
GET /books?search=Гарри Поттер
GET /books?offset=20&limit=20&search=фантастика
```

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "title": "Том 1 Название книги",
    "author": "Автор",
    "narrator": "Чтец",
    "cover_url": "/covers/серия/том/cover.jpg",
    "description": "Описание книги",
    "path": "серия/том_название",
    "series_id": 1,
    "series_order": 1,
    "is_hidden": false,
    "uploaded_at": "2025-01-01T00:00:00"
  }
]
```

**Важно:**
- `path` - уникальный идентификатор папки книги (используется системой для поиска файлов)
- `title` - название книги (можно изменять без переименования папки)
- `series_order` - порядок книги в серии (для сортировки)
- Книги с `is_hidden: true` не возвращаются в этом списке

---

### GET `/books/{book_id}`

Получить подробную информацию о книге.

**Path Parameters:**
- `book_id` (int, required) - ID книги

**Пример:**
```
GET /books/1
```

**Response:** `200 OK`
```json
{
  "id": 1,
  "title": "Том 1 Название книги",
  "author": "Автор",
  "narrator": "Чтец",
  "cover_url": "/covers/серия/том/cover.jpg",
  "description": "Описание книги",
  "path": "серия/том_название",
  "series_id": 1,
  "series_order": 1,
  "is_hidden": false,
  "uploaded_at": "2025-01-01T00:00:00"
}
```

**Ошибки:**
- `404` - Книга не найдена

---

### GET `/books/{book_id}/chapters`

Получить список глав книги.

**Path Parameters:**
- `book_id` (int, required) - ID книги

**Пример:**
```
GET /books/1/chapters
```

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "title": "Глава 1",
    "duration": 3600.0,
    "path": "серия/том/01.mp3",
    "order": 1,
    "real_order": null
  },
  {
    "id": 2,
    "title": "Глава 2",
    "duration": 3800.5,
    "path": "серия/том/02.mp3",
    "order": 2,
    "real_order": null
  }
]
```

**Поля:**
- `duration` - длительность в секундах (float)
- `order` - порядок из имени файла
- `real_order` - реальная позиция для ручной корректировки (если `null`, используется `order`)

**Ошибки:**
- `404` - Книга не найдена

---

### GET `/books/{book_id}/stream/{chapter_id}`

Стриминг аудиофайла. Возвращает аудиофайл для прослушивания.

**Path Parameters:**
- `book_id` (int, required) - ID книги
- `chapter_id` (int, required) - ID главы

**Пример:**
```
GET /books/1/stream/27
```

**Response:** `200 OK` (audio/mpeg или другой аудио формат)

**Особенности:**
- Поддерживает HTTP Range-запросы для перемотки
- Автоматически определяет формат файла (MP3, M4A, WAV, OGG, FLAC, AAC)
- Возвращает правильные заголовки для стриминга

**Пример использования в HTML:**
```html
<audio src="https://api.xau.fire-core.ru/books/1/stream/27" controls>
  Your browser does not support the audio element.
</audio>
```

**Пример с перемоткой:**
```javascript
// Запрос с Range заголовком для перемотки
const response = await fetch('https://api.xau.fire-core.ru/books/1/stream/27', {
  headers: {
    'Authorization': `Bearer ${token}`,
    'Range': 'bytes=1000000-' // Начать с 1MB
  }
});
```

**Ошибки:**
- `404` - Книга или глава не найдена
- `416` - Range не выполним

---

## 📚 Серии

### GET `/series`

Получить список серий с пагинацией и поиском.

**Query Parameters:**
- `offset` (int, default: 0) - Количество записей для пропуска
- `limit` (int, default: 100, max: 1000) - Максимальное количество записей
- `search` (string, optional, max: 200) - Поисковый запрос

**Примеры:**
```
GET /series
GET /series?offset=0&limit=20
GET /series?search=Гарри Поттер
```

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "name": "Название серии",
    "description": "Описание серии",
    "cover_url": "/covers/серия/cover.jpg",
    "created_at": "2025-01-01T00:00:00"
  }
]
```

---

### GET `/series/{series_id}`

Получить информацию о серии.

**Path Parameters:**
- `series_id` (int, required) - ID серии

**Пример:**
```
GET /series/1
```

**Response:** `200 OK`
```json
{
  "id": 1,
  "name": "Название серии",
  "description": "Описание серии",
  "cover_url": "/covers/серия/cover.jpg",
  "created_at": "2025-01-01T00:00:00"
}
```

**Ошибки:**
- `404` - Серия не найдена

---

### GET `/series/{series_id}/books`

Получить все книги в серии. **Книги возвращаются в порядке `series_order`.**

**Path Parameters:**
- `series_id` (int, required) - ID серии

**Query Parameters:**
- `search` (string, optional, max: 200) - Поисковый запрос

**Примеры:**
```
GET /series/1/books
GET /series/1/books?search=том 1
```

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "title": "Том 1 Название",
    "author": "Автор",
    "narrator": "Чтец",
    "cover_url": "/covers/серия/том/cover.jpg",
    "description": "Описание",
    "path": "серия/том_название",
    "series_id": 1,
    "series_order": 1,
    "is_hidden": false,
    "uploaded_at": "2025-01-01T00:00:00"
  },
  {
    "id": 2,
    "title": "Том 2 Название",
    "series_order": 2,
    ...
  }
]
```

**Важно:**
- Книги отсортированы по `series_order` (NULL значения в конце)
- Скрытые книги (`is_hidden: true`) не возвращаются

**Ошибки:**
- `404` - Серия не найдена

---

## 🖼️ Обложки

### GET `/covers/books/{book_id}`

Получить обложку книги.

**Path Parameters:**
- `book_id` (int, required) - ID книги

**Пример:**
```
GET /covers/books/1
```

**Response:** `200 OK` (image/jpeg или image/png)

**Особенности:**
- Публично доступны, не требуют аутентификации
- Поддерживаемые форматы: JPEG, PNG
- Автоматически определяет MIME type

**Пример использования:**
```html
<img src="https://api.xau.fire-core.ru/covers/books/1" alt="Обложка книги" />
```

**Ошибки:**
- `404` - Обложка не найдена

---

### GET `/covers/series/{series_id}`

Получить обложку серии.

**Path Parameters:**
- `series_id` (int, required) - ID серии

**Пример:**
```
GET /covers/series/1
```

**Response:** `200 OK` (image/jpeg или image/png)

**Ошибки:**
- `404` - Обложка не найдена

---

## 📊 Прогресс прослушивания

### POST `/progress/update`

Обновить прогресс прослушивания книги.

**Request Body:**
```json
{
  "book_id": 1,
  "chapter_id": 5,
  "position_ms": 25100,
  "playback_speed": 1.25,
  "last_update": "2025-11-04T22:13:20Z"
}
```

**Поля:**
- `book_id` (int, required) - ID книги
- `chapter_id` (int, required) - ID текущей главы
- `position_ms` (int, required) - Позиция в миллисекундах от начала главы
- `playback_speed` (float, required) - Скорость воспроизведения (например, 1.0, 1.25, 1.5)
- `last_update` (string, required) - Время обновления в формате ISO 8601

**Response:** `200 OK`
```json
{
  "status": "success"
}
```

**Важно:**
- Автоматически обновляет статистику пользователя
- Создает запись активности (activity) для статистики
- Если прогресс уже существует, обновляет его

**Ошибки:**
- `400` - Неверные данные
- `404` - Книга или глава не найдена

---

### GET `/progress/sync`

Получить все данные для синхронизации (прогресс + активность).

**Response:** `200 OK`
```json
{
  "progress": [
    {
      "book_id": 1,
      "chapter_id": 5,
      "position_ms": 25100,
      "playback_speed": 1.25,
      "last_update": "2025-11-04T22:13:20Z"
    }
  ],
  "activity": {
    "2025-10-30": 15,
    "2025-10-31": 47,
    "2025-11-01": 120
  }
}
```

**Поля:**
- `progress` - массив прогрессов для всех книг
- `activity` - объект где ключ - дата (YYYY-MM-DD), значение - минуты прослушивания

**Использование:**
- Используйте для синхронизации между устройствами
- `activity` можно использовать для отображения "heatmap" активности (как в GitHub)

---

## 🏷️ Статусы книг и серий

Статусы: `wanted`, `listening`, `completed`, `dropped`

### GET `/tags`

Получить все доступные статусы с переводами.

**Query Parameters:**
- `language` (string, default: "en") - Код языка: "en" или "ru"

**Примеры:**
```
GET /tags
GET /tags?language=ru
```

**Response:** `200 OK`
```json
{
  "statuses": [
    {
      "code": "wanted",
      "name": "Wanted",
      "description": "Books I want to listen to"
    },
    {
      "code": "listening",
      "name": "Listening",
      "description": "Currently listening to"
    },
    {
      "code": "completed",
      "name": "Completed",
      "description": "Books I have finished listening to"
    },
    {
      "code": "dropped",
      "name": "Dropped",
      "description": "Books I stopped listening to"
    }
  ]
}
```

---

### GET `/status/books`

Получить список книг по статусу.

**Query Parameters:**
- `status` (string, required) - Один из: `wanted`, `listening`, `completed`, `dropped`
- `offset` (int, default: 0) - Количество записей для пропуска
- `limit` (int, default: 100, max: 1000) - Максимальное количество записей

**Примеры:**
```
GET /status/books?status=listening
GET /status/books?status=completed&offset=0&limit=20
```

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "title": "Том 1",
    "author": "Автор",
    ...
  }
]
```

---

### GET `/status/books/{book_id}`

Получить статус книги для текущего пользователя.

**Path Parameters:**
- `book_id` (int, required) - ID книги

**Response:** `200 OK`
```json
{
  "id": 1,
  "user_id": 1,
  "book_id": 1,
  "status": "listening",
  "created_at": "2025-01-01T00:00:00",
  "updated_at": "2025-01-01T00:00:00"
}
```

**Ошибки:**
- `404` - Статус не установлен

---

### PUT `/status/books/{book_id}`

Установить или обновить статус книги.

**Path Parameters:**
- `book_id` (int, required) - ID книги

**Request Body:**
```json
{
  "status": "listening"
}
```

**Response:** `200 OK`
```json
{
  "id": 1,
  "user_id": 1,
  "book_id": 1,
  "status": "listening",
  "created_at": "2025-01-01T00:00:00",
  "updated_at": "2025-01-01T00:00:00"
}
```

---

### DELETE `/status/books/{book_id}`

Удалить статус книги.

**Path Parameters:**
- `book_id` (int, required) - ID книги

**Response:** `200 OK`
```json
{
  "status": "deleted"
}
```

---

### GET `/status/series`

Получить список серий по статусу.

**Query Parameters:**
- `status` (string, required) - Один из: `wanted`, `listening`, `completed`, `dropped`
- `offset` (int, default: 0)
- `limit` (int, default: 100, max: 1000)

**Примеры:**
```
GET /status/series?status=listening
```

---

### GET `/status/series/{series_id}`

Получить статус серии.

**Path Parameters:**
- `series_id` (int, required) - ID серии

**Response:** `200 OK` (аналогично статусу книги)

---

### PUT `/status/series/{series_id}`

Установить статус серии.

**Path Parameters:**
- `series_id` (int, required) - ID серии

**Request Body:**
```json
{
  "status": "listening"
}
```

---

### DELETE `/status/series/{series_id}`

Удалить статус серии.

**Path Parameters:**
- `series_id` (int, required) - ID серии

---

## 📝 Заметки

### GET `/notes/{book_id}`

Получить список заметок для книги.

**Path Parameters:**
- `book_id` (int, required) - ID книги

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "text": "Интересный момент",
    "timestamp": 125000,
    "created_at": "2025-01-01T00:00:00",
    "updated_at": "2025-01-01T00:00:00"
  }
]
```

**Поля:**
- `timestamp` - позиция в миллисекундах от начала аудиофайла

---

### POST `/notes/{book_id}`

Создать заметку для книги.

**Path Parameters:**
- `book_id` (int, required) - ID книги

**Request Body:**
```json
{
  "text": "Интересный момент",
  "timestamp": 125000
}
```

**Response:** `200 OK`
```json
{
  "id": 1,
  "text": "Интересный момент",
  "timestamp": 125000,
  "created_at": "2025-01-01T00:00:00",
  "updated_at": "2025-01-01T00:00:00"
}
```

---

## 📈 Статистика

### GET `/statistics`

Получить статистику прослушивания за период.

**Query Parameters (один из вариантов):**

1. **За месяц:**
   - `year` (int, required) - Год
   - `month` (int, required, 1-12) - Месяц

2. **За год:**
   - `year` (int, required) - Год

3. **За произвольный период:**
   - `start_date` (string, required, YYYY-MM-DD) - Начальная дата
   - `end_date` (string, required, YYYY-MM-DD) - Конечная дата

**Примеры:**
```
GET /statistics?year=2025&month=11
GET /statistics?year=2025
GET /statistics?start_date=2025-10-01&end_date=2025-10-31
```

**Response:** `200 OK`
```json
{
  "statistics": [
    {
      "date": "2025-01-01",
      "minutes_listened": 120,
      "books_completed": 1,
      "chapters_listened": 5
    }
  ],
  "total_minutes": 3600,
  "total_books_completed": 12,
  "total_chapters_listened": 150
}
```

---

## 👤 Аккаунт

### GET `/account`

Получить информацию об аккаунте текущего пользователя.

**Response:** `200 OK`
```json
{
  "name": "Имя",
  "email": "user@example.com",
  "total_time_minutes": 3600,
  "created_at": "2025-01-01T00:00:00"
}
```

---

### GET `/account/activity`

Получить активность пользователя (GitHub-подобная сетка).

**Response:** `200 OK`
```json
{
  "2025-10-30": 15,
  "2025-10-31": 47,
  "2025-11-01": 120
}
```

**Формат:**
- Ключ - дата в формате YYYY-MM-DD
- Значение - минуты прослушивания за день

---

### GET `/account/devices`

Получить список устройств пользователя.

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "device_name": "iPhone 12",
    "last_login": "2025-01-01T00:00:00",
    "is_active": true,
    "token": "token_hash"
  }
]
```

---

### DELETE `/account/device/{device_id}`

Удалить устройство (выйти с устройства).

**Path Parameters:**
- `device_id` (int, required) - ID устройства

**Response:** `200 OK`
```json
{
  "status": "deleted"
}
```

---

### GET `/account/check-admin`

Проверить, является ли текущий пользователь администратором.

**Response:** `200 OK`
```json
{
  "is_admin": true
}
```

---

## 🔄 Версия и релизы

### GET `/version`

Получить информацию о версии приложения и системе.

**Response:** `200 OK`
```json
{
  "version": "1.0.0",
  "build_date": "2025-01-01",
  "build_number": "123",
  "system": {
    "platform": "Linux",
    "platform_release": "6.1.0",
    "platform_version": "6.1.0-40-amd64",
    "architecture": "x86_64",
    "processor": "x86_64",
    "python_version": "3.11.0"
  }
}
```

---

### GET `/release`

Скачать релиз для установки или обновления.

**Query Parameters:**
- `os_type` (string, optional) - Тип ОС: `windows`, `linux`, `mac`, `darwin`
- `arch` (string, optional) - Архитектура: `x86`, `x64`, `amd64`, `arm64`, `arm`
- `version` (string, optional) - Версия для скачивания (если не указана, используется последняя)

**Примеры:**
```
GET /release
GET /release?os_type=windows&arch=x64
GET /release?os_type=linux&arch=amd64&version=1.0.0
```

**Response:** `200 OK` (файл для скачивания)

**Особенности:**
- Если параметры не указаны, определяется автоматически по User-Agent
- Поддерживаемые форматы: `.exe`, `.zip`, `.tar.gz`, `.dmg`, `.deb`, `.rpm`, `.AppImage`, `.msi`, `.pkg`

**Ошибки:**
- `404` - Релиз не найден

---

## 🔧 Админ-панель

> ⚠️ **ВАЖНО:** Все endpoints админ-панели требуют права администратора (`is_admin: true`).

### Управление пользователями

#### GET `/admin/users`

Получить список всех пользователей.

**Query Parameters:**
- `offset` (int, default: 0)
- `limit` (int, default: 100, max: 1000)
- `search` (string, optional) - Поиск по email или имени

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "email": "user@example.com",
    "name": "Имя",
    "is_admin": false,
    "created_at": "2025-01-01T00:00:00"
  }
]
```

---

#### GET `/admin/users/{user_id}`

Получить информацию о пользователе.

**Path Parameters:**
- `user_id` (int, required) - ID пользователя

**Response:** `200 OK` (аналогично списку пользователей)

---

#### PUT `/admin/users/{user_id}`

Обновить данные пользователя.

**Path Parameters:**
- `user_id` (int, required) - ID пользователя

**Request Body:**
```json
{
  "name": "Новое имя",
  "email": "newemail@example.com",
  "is_admin": true,
  "password": "newpassword123"
}
```

**Все поля опциональны.** Можно обновить только нужные.

**Response:** `200 OK` (обновленный пользователь)

---

#### DELETE `/admin/users/{user_id}`

Удалить пользователя.

**Path Parameters:**
- `user_id` (int, required) - ID пользователя

**Response:** `200 OK`
```json
{
  "status": "deleted"
}
```

---

#### GET `/admin/users/{user_id}/stats`

Получить статистику пользователя.

**Path Parameters:**
- `user_id` (int, required) - ID пользователя

**Response:** `200 OK`
```json
{
  "user_id": 1,
  "total_progress_entries": 50,
  "total_notes": 10,
  "total_devices": 2,
  "total_book_statuses": 15
}
```

---

#### GET `/admin/users/{user_id}/progress`

Получить прогресс пользователя.

**Path Parameters:**
- `user_id` (int, required) - ID пользователя

**Query Parameters:**
- `book_id` (int, optional) - Фильтр по книге

**Примеры:**
```
GET /admin/users/1/progress
GET /admin/users/1/progress?book_id=5
```

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "user_id": 1,
    "book_id": 5,
    "chapter_id": 10,
    "position_ms": 25100,
    "playback_speed": 1.25,
    "last_update": "2025-11-04T22:13:20Z"
  }
]
```

---

#### PUT `/admin/users/{user_id}/progress/{book_id}`

Обновить прогресс пользователя для книги.

**Path Parameters:**
- `user_id` (int, required) - ID пользователя
- `book_id` (int, required) - ID книги

**Request Body:**
```json
{
  "chapter_id": 10,
  "position_ms": 25100,
  "playback_speed": 1.25,
  "last_update": "2025-11-04T22:13:20Z"
}
```

**Response:** `200 OK` (обновленный прогресс)

---

#### DELETE `/admin/users/{user_id}/progress/{progress_id}`

Удалить прогресс пользователя.

**Path Parameters:**
- `user_id` (int, required) - ID пользователя
- `progress_id` (int, required) - ID прогресса

**Response:** `200 OK`
```json
{
  "status": "deleted"
}
```

---

### Управление книгами

#### GET `/admin/books`

Получить список всех книг (включая скрытые).

**Query Parameters:**
- `offset` (int, default: 0)
- `limit` (int, default: 100, max: 1000)
- `search` (string, optional)

**Response:** `200 OK` (аналогично `/books`, но включает скрытые)

---

#### GET `/admin/books/{book_id}`

Получить информацию о книге (включая скрытые).

**Path Parameters:**
- `book_id` (int, required) - ID книги

**Response:** `200 OK` (аналогично `/books/{book_id}`)

---

#### PUT `/admin/books/{book_id}`

Обновить данные книги.

**Path Parameters:**
- `book_id` (int, required) - ID книги

**Request Body:**
```json
{
  "title": "Новое название",
  "author": "Новый автор",
  "narrator": "Новый чтец",
  "description": "Новое описание",
  "cover_url": "/covers/серия/том/cover.jpg",
  "series_id": 1,
  "series_order": 1,
  "is_hidden": false
}
```

**Все поля опциональны.**

**Важно:**
- Изменение `title` не требует переименования папки (система использует `path`)
- `path` обновляется автоматически при перемещении книги между сериями

**Response:** `200 OK` (обновленная книга)

---

#### DELETE `/admin/books/{book_id}`

Удалить книгу.

**Path Parameters:**
- `book_id` (int, required) - ID книги

**Response:** `200 OK`
```json
{
  "status": "deleted"
}
```

---

#### POST `/admin/books/{book_id}/move`

Переместить книгу из одной серии в другую.

**Path Parameters:**
- `book_id` (int, required) - ID книги

**Request Body:**
```json
{
  "target_series_id": 2,
  "series_order": 5
}
```

**Поля:**
- `target_series_id` (int, required) - ID целевой серии
- `series_order` (int, optional) - Порядок в новой серии

**Важно:**
- Физически перемещает файлы книги между папками серий
- Обновляет пути к главам
- Обновляет `path` книги в БД
- Убирает флаг `is_hidden`

**Response:** `200 OK` (обновленная книга)

---

#### GET `/admin/books/{book_id}/chapters`

Получить все главы книги (включая скрытые).

**Path Parameters:**
- `book_id` (int, required) - ID книги

**Response:** `200 OK` (аналогично `/books/{book_id}/chapters`)

---

#### POST `/admin/books/{book_id}/cover`

Загрузить новую обложку для книги.

**Path Parameters:**
- `book_id` (int, required) - ID книги

**Request:**
- `Content-Type: multipart/form-data`
- `file` (file, required) - Файл изображения (JPEG, PNG)

**Пример:**
```javascript
const formData = new FormData();
formData.append('file', fileInput.files[0]);

const response = await fetch(`https://api.xau.fire-core.ru/admin/books/${bookId}/cover`, {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`
  },
  body: formData
});
```

**Response:** `200 OK`
```json
{
  "status": "success",
  "cover_url": "/covers/серия/том/cover.jpg"
}
```

---

#### POST `/admin/books/reorder`

Изменить порядок книг в серии.

**Request Body:**
```json
[
  {
    "book_id": 1,
    "series_order": 1
  },
  {
    "book_id": 2,
    "series_order": 2
  },
  {
    "book_id": 3,
    "series_order": 3
  }
]
```

**Важно:**
- Обновляет `series_order` для каждой книги
- Книги в серии будут возвращаться в этом порядке

**Response:** `200 OK`
```json
{
  "status": "success"
}
```

---

### Управление главами

#### PUT `/admin/chapters/{chapter_id}`

Обновить данные главы.

**Path Parameters:**
- `chapter_id` (int, required) - ID главы

**Request Body:**
```json
{
  "title": "Новое название главы",
  "order": 1,
  "real_order": 2
}
```

**Все поля опциональны.**

**Response:** `200 OK` (обновленная глава)

---

#### DELETE `/admin/chapters/{chapter_id}`

Удалить главу.

**Path Parameters:**
- `chapter_id` (int, required) - ID главы

**Response:** `200 OK`
```json
{
  "status": "deleted"
}
```

---

### Управление сериями

#### GET `/admin/series`

Получить список всех серий.

**Response:** `200 OK` (аналогично `/series`)

---

#### GET `/admin/series/{series_id}`

Получить информацию о серии.

**Path Parameters:**
- `series_id` (int, required) - ID серии

**Response:** `200 OK` (аналогично `/series/{series_id}`)

---

#### GET `/admin/series/{series_id}/books`

Получить все книги в серии (включая скрытые).

**Path Parameters:**
- `series_id` (int, required) - ID серии

**Response:** `200 OK` (аналогично `/series/{series_id}/books`, но включает скрытые)

---

#### PUT `/admin/series/{series_id}`

Обновить данные серии.

**Path Parameters:**
- `series_id` (int, required) - ID серии

**Request Body:**
```json
{
  "name": "Новое название серии",
  "description": "Новое описание",
  "cover_url": "/covers/серия/cover.jpg"
}
```

**Все поля опциональны.**

**Response:** `200 OK` (обновленная серия)

---

### Системная статистика

#### GET `/admin/stats`

Получить системную статистику.

**Response:** `200 OK`
```json
{
  "total_users": 100,
  "total_books": 500,
  "total_series": 50,
  "total_chapters": 5000,
  "total_progress_entries": 10000,
  "total_notes": 500
}
```

---

### Логи

#### GET `/admin/logs`

Получить логи системы.

**Query Parameters:**
- `lines` (int, default: 100) - Количество последних строк
- `level` (string, optional) - Фильтр по уровню: `DEBUG`, `INFO`, `WARNING`, `ERROR`

**Примеры:**
```
GET /admin/logs
GET /admin/logs?lines=500
GET /admin/logs?level=ERROR
```

**Response:** `200 OK`
```json
{
  "logs": [
    "2025-01-01 12:00:00 INFO: Server started",
    "2025-01-01 12:01:00 ERROR: Database connection failed"
  ]
}
```

---

#### GET `/admin/logs/download`

Скачать файл логов.

**Response:** `200 OK` (text/plain файл)

---

## 🤖 Telegram интеграция

> ⚠️ **ВАЖНО:** Telegram интеграция может быть отключена на сервере.

### POST `/telegram`

Создать задачу на скачивание из Telegram.

**Request Body:**
```json
{
  "message_url": "https://t.me/channel/123",
  "markup": {
    "series_name": "Название серии",
    "book_title": "Название книги"
  }
}
```

**Response:** `200 OK`
```json
{
  "id": 1,
  "message_url": "https://t.me/channel/123",
  "status": "pending",
  "created_at": "2025-01-01T00:00:00"
}
```

---

### GET `/telegram/{download_id}`

Получить информацию о задаче скачивания.

**Path Parameters:**
- `download_id` (int, required) - ID задачи

**Response:** `200 OK` (аналогично созданию)

---

### POST `/telegram/{download_id}/markup`

Обновить разметку задачи.

**Path Parameters:**
- `download_id` (int, required) - ID задачи

**Request Body:**
```json
{
  "series_name": "Новое название серии",
  "book_title": "Новое название книги"
}
```

---

### POST `/telegram/{download_id}/confirm`

Подтвердить задачу (начать скачивание).

**Path Parameters:**
- `download_id` (int, required) - ID задачи

**Response:** `200 OK`
```json
{
  "id": 1,
  "status": "downloading",
  ...
}
```

---

### GET `/telegram/{download_id}/status`

Получить статус задачи.

**Path Parameters:**
- `download_id` (int, required) - ID задачи

**Response:** `200 OK`
```json
{
  "id": 1,
  "status": "completed",
  "progress": 100,
  "files_downloaded": 10,
  "total_files": 10
}
```

---

### GET `/telegram`

Получить список всех задач.

**Response:** `200 OK`
```json
[
  {
    "id": 1,
    "message_url": "https://t.me/channel/123",
    "status": "completed",
    ...
  }
]
```

---

## ❤️ Health Check

### GET `/health`

Проверка доступности сервера.

**Response:** `200 OK`
```json
{
  "status": "ok",
  "message": "Server is running"
}
```

**Особенности:**
- Не требует аутентификации
- Используйте для мониторинга сервера

---

## 🚨 Коды ошибок

| Код | Описание | Когда возникает |
|-----|----------|-----------------|
| `200` | Успешный запрос | Все хорошо |
| `400` | Неверный запрос | Неверные параметры или данные |
| `401` | Не авторизован | Требуется токен или токен истек |
| `403` | Доступ запрещен | Недостаточно прав (для админ-панели) |
| `404` | Не найдено | Ресурс не существует |
| `416` | Range не выполним | Неверный Range заголовок для стриминга |
| `422` | Ошибка валидации | Неверный формат данных |
| `500` | Внутренняя ошибка сервера | Ошибка на сервере |
| `503` | Сервис недоступен | Telegram функция отключена |

---

## 📌 Важные примечания

### 1. Токены

- **Access token** истекает через **7 дней**
- **Refresh token** истекает через **30 дней**
- Всегда проверяйте заголовки `X-Token-Expires-Soon` и обновляйте токен заранее

### 2. Автоматическое обновление токена

Сервер автоматически сигнализирует о необходимости обновления токена через HTTP заголовки:

- **`X-Token-Expires-Soon`** - `"true"` если токен истечет в ближайшие 5 минут
- **`X-Token-Expires-At`** - Время истечения в ISO 8601 формате
- **`X-Token-Expired`** - `"true"` если токен уже истек (в заголовках ошибки 401)

**Рекомендуемая логика:**

```javascript
async function makeRequest(url, options = {}) {
  const token = localStorage.getItem('access_token');
  
  const response = await fetch(url, {
    ...options,
    headers: {
      ...options.headers,
      'Authorization': `Bearer ${token}`
    }
  });
  
  // Проверяем заголовки
  if (response.headers.get('X-Token-Expires-Soon') === 'true') {
    await refreshAccessToken();
  }
  
  // Обработка 401
  if (response.status === 401) {
    if (response.headers.get('X-Token-Expired') === 'true') {
      const refreshed = await refreshAccessToken();
      if (refreshed) {
        // Повторяем запрос с новым токеном
        return makeRequest(url, options);
      } else {
        // Требуем повторного входа
        redirectToLogin();
        return;
      }
    }
  }
  
  return response;
}

async function refreshAccessToken() {
  try {
    const refreshToken = localStorage.getItem('refresh_token');
    const response = await fetch('https://api.xau.fire-core.ru/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refresh_token: refreshToken })
    });
    
    if (response.ok) {
      const { access_token } = await response.json();
      localStorage.setItem('access_token', access_token);
      return true;
    }
  } catch (error) {
    console.error('Failed to refresh token:', error);
  }
  return false;
}
```

### 3. Форматы данных

- **Даты:** ISO 8601 (YYYY-MM-DDTHH:MM:SSZ)
- **Длительность:** секунды (float)
- **Позиция:** миллисекунды (int)
- **Timestamp заметок:** миллисекунды (int)
- **Статусы:** `wanted`, `listening`, `completed`, `dropped`
- **Языки тегов:** `en`, `ru`

### 4. Range запросы

Аудио стриминг поддерживает HTTP Range-запросы для перемотки:

```javascript
// Запрос с Range заголовком
const response = await fetch('https://api.xau.fire-core.ru/books/1/stream/27', {
  headers: {
    'Authorization': `Bearer ${token}`,
    'Range': 'bytes=1000000-' // Начать с 1MB
  }
});
```

### 5. Скрытые книги

- Книги с `is_hidden: true` не возвращаются в обычных API endpoints
- В админ-панели можно видеть и управлять скрытыми книгами

### 6. Обложки

- Публично доступны, не требуют аутентификации
- Поддерживаемые форматы: JPEG, PNG
- Автоматически определяет MIME type

### 7. Пагинация

- Используйте `offset` и `limit` для больших списков
- Максимальный `limit`: 1000
- Рекомендуемый `limit`: 20-100

### 8. Поиск

- Поиск безопасен (защищен от SQL-инъекций)
- Максимальная длина поискового запроса: 200 символов
- Поиск выполняется по нескольким полям (title, author, narrator, description)

### 9. Порядок книг в серии

- Книги в серии возвращаются в порядке `series_order`
- Книги с `series_order: null` идут в конце
- Порядок можно изменить через админ-панель

### 10. Путь к книге (path)

- `path` - уникальный идентификатор папки книги
- Используется системой для поиска файлов
- Можно изменять `title` без переименования папки
- `path` обновляется автоматически при перемещении книги между сериями

---

## 💡 Примеры использования

### Полный цикл работы с книгой

```javascript
// 1. Получить список книг
const books = await fetch('https://api.xau.fire-core.ru/books?limit=20', {
  headers: { 'Authorization': `Bearer ${token}` }
}).then(r => r.json());

// 2. Получить главы первой книги
const chapters = await fetch(`https://api.xau.fire-core.ru/books/${books[0].id}/chapters`, {
  headers: { 'Authorization': `Bearer ${token}` }
}).then(r => r.json());

// 3. Начать прослушивание (обновить прогресс)
await fetch('https://api.xau.fire-core.ru/progress/update', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    book_id: books[0].id,
    chapter_id: chapters[0].id,
    position_ms: 0,
    playback_speed: 1.0,
    last_update: new Date().toISOString()
  })
});

// 4. Установить статус "слушаю"
await fetch(`https://api.xau.fire-core.ru/status/books/${books[0].id}`, {
  method: 'PUT',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({ status: 'listening' })
});

// 5. Создать заметку во время прослушивания
await fetch(`https://api.xau.fire-core.ru/notes/${books[0].id}`, {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    text: 'Интересный момент',
    timestamp: 125000  // в миллисекундах
  })
});
```

### Работа с сериями

```javascript
// 1. Получить список серий
const series = await fetch('https://api.xau.fire-core.ru/series', {
  headers: { 'Authorization': `Bearer ${token}` }
}).then(r => r.json());

// 2. Получить книги серии (в порядке series_order)
const books = await fetch(`https://api.xau.fire-core.ru/series/${series[0].id}/books`, {
  headers: { 'Authorization': `Bearer ${token}` }
}).then(r => r.json());

// 3. Отобразить книги в правильном порядке
books.forEach(book => {
  console.log(`Порядок ${book.series_order}: ${book.title}`);
});
```

### Синхронизация между устройствами

```javascript
// Получить все данные для синхронизации
const syncData = await fetch('https://api.xau.fire-core.ru/progress/sync', {
  headers: { 'Authorization': `Bearer ${token}` }
}).then(r => r.json());

// Восстановить прогресс на новом устройстве
syncData.progress.forEach(progress => {
  // Восстановить позицию прослушивания
  console.log(`Книга ${progress.book_id}, глава ${progress.chapter_id}, позиция ${progress.position_ms}ms`);
});

// Отобразить активность
Object.entries(syncData.activity).forEach(([date, minutes]) => {
  console.log(`${date}: ${minutes} минут`);
});
```

---

## 📞 Поддержка

Если у вас возникли вопросы или проблемы:

1. Проверьте коды ошибок в ответах
2. Убедитесь, что токен не истек
3. Проверьте формат данных запроса
4. Обратитесь к администратору сервера

---

**Версия документации:** 2.0.0  
**Последнее обновление:** 2025-01-XX
