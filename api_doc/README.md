# Audio Book Server

Сервер для управления аудиокнигами с автоматической индексацией, отслеживанием прогресса, заметками и статистикой прослушивания.

## 🚀 Возможности

- 📚 **Управление книгами и сериями** - Организация аудиокниг в серии с автоматической индексацией
- 🎧 **Стриминг аудио** - Потоковая передача аудиофайлов
- 🖼️ **Обложки** - Получение обложек книг и серий
- 📊 **Отслеживание прогресса** - Синхронизация прогресса прослушивания между устройствами
- 📝 **Заметки** - Создание заметок к книгам с привязкой к времени
- 📈 **Статистика** - Детальная статистика прослушивания (по дням, месяцам, годам)
- 🏷️ **Статусы** - Управление статусами книг и серий (желаемое, слушаю, прослушано, брошено)
- 👤 **Мультиустройственность** - Поддержка нескольких устройств с синхронизацией
- 🔄 **Автоматическая индексация** - Автоматическое обнаружение и индексация новых аудиофайлов
- 🔒 **Безопасность** - JWT аутентификация с поддержкой refresh токенов
- 🔍 **Гибкий поиск** - Поиск книг по названию и автору

## 📋 Требования

- Python 3.10+
- MySQL/MariaDB
- Redis (опционально, для blacklist токенов)
- ffprobe (для определения длительности аудиофайлов)

## 🔧 Установка

1. **Клонируйте репозиторий:**
```bash
git clone <repository-url>
cd XAapp-ver.SERVER
```

2. **Установите зависимости:**
```bash
pip install -r requirements.txt
```

3. **Настройте переменные окружения:**
Создайте файл `.env` или экспортируйте переменные:
```bash
DATABASE_URL=mysql+pymysql://user:password@host/database
REDIS_URL=redis://host:port
SECRET_KEY=your-secret-key
AUDIO_DIR=./static/audio
```

4. **Подготовьте структуру папок для аудио:**
```
static/audio/
  Название серии/
    Том 1 Название тома/
      01.mp3
      02.mp3
      ...
      author.txt
      narrator.txt
      description.txt
      cover.jpg
```

Подробнее о структуре файлов см. [API_INDEXING.md](API_INDEXING.md)

## 🏃 Запуск

### Вариант 1: Использование run.py
```bash
python run.py
```

### Вариант 2: Использование uvicorn напрямую
```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

### Вариант 3: Использование start_server.bat (Windows)
```bash
start_server.bat
```

Сервер будет доступен по адресу: `http://localhost:8000`

## 📁 Структура проекта

```
XAapp-ver.SERVER/
├── README.md                 # Этот файл
├── API_README.md            # Обзор API документации
├── API_AUTH.md              # Документация: Аутентификация
├── API_BOOKS.md             # Документация: Книги
├── API_SERIES.md            # Документация: Серии
├── API_PROGRESS.md          # Документация: Прогресс
├── API_STATUS.md            # Документация: Статусы
├── API_STATISTICS.md        # Документация: Статистика
├── API_NOTES.md             # Документация: Заметки
├── API_ACCOUNT.md           # Документация: Аккаунт
├── API_HEALTH.md            # Документация: Health Check
├── API_INDEXING.md          # Документация: Индексация
├── requirements.txt         # Зависимости Python
├── run.py                   # Точка входа приложения
│
├── app/                     # Основное приложение
│   ├── main.py             # Точка входа FastAPI
│   ├── config.py           # Конфигурация
│   ├── database.py         # Подключение к БД
│   ├── redis_client.py     # Подключение к Redis
│   │
│   ├── core/               # Основные утилиты
│   │   ├── logging.py      # Настройка логирования
│   │   └── middleware.py   # Middleware (CORS, логирование, кэширование)
│   │
│   ├── models/             # SQLAlchemy модели
│   │   ├── base.py         # Base класс
│   │   ├── user.py         # User, Device
│   │   ├── book.py         # Book, Chapter, Series
│   │   ├── progress.py     # Progress, Activity
│   │   ├── note.py         # Note
│   │   └── status.py       # BookStatus, SeriesStatus, UserStatistics
│   │
│   ├── schemas/            # Pydantic схемы
│   │   ├── auth.py         # LoginRequest, TokenResponse
│   │   ├── user.py         # UserCreate, UserResponse, AccountResponse
│   │   ├── book.py         # BookResponse, ChapterResponse, SeriesResponse
│   │   ├── progress.py     # ProgressUpdateRequest, ProgressSyncResponse
│   │   ├── note.py         # NoteCreate, NoteResponse
│   │   └── status.py       # StatusResponse, StatisticsResponse
│   │
│   ├── crud/               # CRUD операции
│   │   ├── user.py         # Операции с пользователями и устройствами
│   │   ├── book.py         # Операции с книгами, главами, сериями
│   │   ├── progress.py     # Операции с прогрессом и активностью
│   │   ├── note.py         # Операции с заметками
│   │   ├── status.py       # Операции со статусами и статистикой
│   │   └── session.py      # Очистка неактивных сессий
│   │
│   ├── api/                # API роутеры
│   │   ├── deps.py         # Зависимости (get_current_user, auth функции)
│   │   └── v1/             # API версия 1
│   │       ├── auth.py     # /auth/* endpoints
│   │       ├── books.py    # /books/* endpoints
│   │       ├── series.py   # /series/* endpoints
│   │       ├── covers.py   # /covers/* endpoints
│   │       ├── stream.py   # /stream/* endpoints
│   │       ├── progress.py # /progress/* endpoints
│   │       ├── status.py   # /status/* endpoints
│   │       ├── statistics.py # /statistics/* endpoints
│   │       ├── notes.py    # /notes/* endpoints
│   │       ├── account.py  # /account/* endpoints
│   │       └── health.py   # /health, /ping endpoints
│   │
│   └── services/           # Бизнес-логика
│       ├── audio_utils.py  # Утилиты для работы с аудио
│       ├── audio_indexer.py # Индексация аудиобиблиотеки
│       └── file_watcher.py # Файловый watcher
│
└── static/                 # Статические файлы
    └── audio/              # Аудиофайлы и метаданные
```

## 📚 Документация API

Полная документация API разделена по темам:

- **[API_README.md](API_README.md)** - Обзор API и общая информация
- **[API_AUTH.md](API_AUTH.md)** - Аутентификация и регистрация
- **[API_BOOKS.md](API_BOOKS.md)** - Работа с книгами и главами
- **[API_SERIES.md](API_SERIES.md)** - Управление сериями книг
- **[API_COVERS.md](API_COVERS.md)** - Получение обложек книг и серий
- **[API_PROGRESS.md](API_PROGRESS.md)** - Отслеживание прогресса прослушивания
- **[API_STATUS.md](API_STATUS.md)** - Статусы книг и серий
- **[API_STATISTICS.md](API_STATISTICS.md)** - Статистика прослушивания
- **[API_NOTES.md](API_NOTES.md)** - Заметки к книгам
- **[API_ACCOUNT.md](API_ACCOUNT.md)** - Управление аккаунтом и устройствами
- **[API_HEALTH.md](API_HEALTH.md)** - Health Check endpoints
- **[API_INDEXING.md](API_INDEXING.md)** - Структура файлов и индексация

## 🔑 Быстрый старт

### 1. Регистрация пользователя
```bash
curl -X POST http://localhost:8000/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123",
    "name": "Имя пользователя"
  }'
```

### 2. Вход в систему
```bash
curl -X POST http://localhost:8000/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123",
    "device_name": "My Device"
  }'
```

### 3. Получение списка книг
```bash
curl -X GET http://localhost:8000/books \
  -H "Authorization: Bearer <access_token>"
```

### 4. Получение обложки книги
```bash
curl -X GET http://localhost:8000/covers/books/1
```

### 5. Стриминг аудиофайла
```bash
curl -X GET http://localhost:8000/stream/1/27 \
  -H "Authorization: Bearer <access_token>"
```

## 🔐 Аутентификация

Большинство endpoints требуют аутентификации через Bearer токен:

```
Authorization: Bearer <access_token>
```

Получите токен через `/auth/login` и используйте его в заголовках всех запросов.

## 📊 Основные функции

### Автоматическая индексация
- Автоматическое обнаружение новых аудиофайлов
- Извлечение метаданных (автор, чтец, описание, обложка)
- Определение длительности глав через ffprobe
- Поддержка структуры: серия/том/главы
- Автоматическое обновление при изменении файлов (file watcher)

### Скрытые книги
- Книги, не найденные при индексации, автоматически помечаются как скрытые
- Скрытые книги не отображаются в API (но их обложки доступны)
- Автоматическое восстановление при повторном появлении файлов

### Обложки
- Поддержка обложек для книг и серий
- Форматы: JPEG, PNG
- Автоматический поиск обложек в файловой системе
- Публичный доступ (не требует аутентификации)

### Синхронизация прогресса
- Синхронизация прогресса между устройствами
- Автоматический пересчет ежедневной активности
- Отслеживание статистики прослушивания

### Статусы
- Управление статусами книг и серий:
  - `wanted` - Желаемое
  - `listening` - Слушаю
  - `completed` - Прослушано
  - `dropped` - Брошено

### Статистика
- Детальная статистика прослушивания
- Статистика за год, месяц или период
- Изометрические визуализации активности
- Автоматическое обновление при прослушивании

### Стриминг аудио
- Потоковая передача аудиофайлов
- Поддержка нескольких форматов (MP3, M4A, WAV, OGG, FLAC, AAC)
- Два пути доступа: `/stream/{book_id}/{chapter_id}` и `/books/{book_id}/stream/{chapter_id}`
- Правильные заголовки для кэширования

## 🛠️ Технологии

- **FastAPI** - Современный веб-фреймворк для создания API
- **SQLAlchemy** - ORM для работы с базой данных
- **Pydantic** - Валидация данных
- **JWT** - Аутентификация через JSON Web Tokens
- **Redis** - Кэширование и blacklist токенов
- **bcrypt** - Хеширование паролей
- **ffprobe** - Определение длительности аудиофайлов

## 🔧 Конфигурация

Основные настройки в `app/config.py`:

```python
DATABASE_URL = "mysql+pymysql://user:password@host/database"
REDIS_URL = "redis://host:port"
SECRET_KEY = "your-secret-key"
AUDIO_DIR = "./static/audio"
ACCESS_TOKEN_EXPIRE_MINUTES = 60
REFRESH_TOKEN_EXPIRE_DAYS = 30
```

## 📝 Форматы данных

- **Даты**: ISO 8601 (YYYY-MM-DDTHH:MM:SSZ)
- **Длительность**: секунды (float)
- **Позиция**: миллисекунды (int)
- **Timestamp заметок**: миллисекунды (int)
- **Обложки**: JPEG, PNG
- **Аудио**: MP3, M4A, WAV, OGG, FLAC, AAC

## 🎯 Основные Endpoints

### Аутентификация
- `POST /auth/register` - Регистрация
- `POST /auth/login` - Вход
- `POST /auth/logout` - Выход

### Книги
- `GET /books` - Список книг
- `GET /books/{book_id}` - Информация о книге
- `GET /books/{book_id}/chapters` - Список глав
- `GET /covers/books/{book_id}` - Обложка книги
- `GET /stream/{book_id}/{chapter_id}` - Стриминг аудио

### Серии
- `GET /series` - Список серий
- `GET /series/{series_id}` - Информация о серии
- `GET /series/{series_id}/books` - Книги в серии
- `GET /covers/series/{series_id}` - Обложка серии

### Прогресс
- `POST /progress/update` - Обновить прогресс
- `GET /progress/sync` - Синхронизация данных

### Статусы
- `GET /status/books/{book_id}` - Статус книги
- `POST /status/books` - Установить статус книги
- `GET /status/books/wanted` - Желаемые книги
- `GET /status/books/listening` - Слушаю сейчас
- `GET /status/books/completed` - Прослушано
- `GET /status/books/dropped` - Брошено

### Статистика
- `GET /statistics/year/{year}` - Статистика за год
- `GET /statistics/month/{year}/{month}` - Статистика за месяц
- `GET /statistics/range` - Статистика за период

### Заметки
- `GET /notes/{book_id}` - Список заметок
- `POST /notes/{book_id}` - Создать заметку

### Аккаунт
- `GET /account` - Информация об аккаунте
- `GET /account/activity` - Активность (GitHub-подобная сетка)
- `GET /account/devices` - Список устройств
- `DELETE /account/device/{device_id}` - Удалить устройство

## 🐛 Решение проблем

Если возникли проблемы:
1. Проверьте подключение к базе данных
2. Убедитесь, что Redis доступен (если используется)
3. Проверьте наличие ffprobe в системе
4. Убедитесь, что папка `AUDIO_DIR` существует и доступна
5. Проверьте логи приложения

## 📄 Лицензия

[Укажите лицензию]

## 🤝 Вклад

[Инструкции по вкладу в проект]

## 📞 Контакты

[Контактная информация]

---

**Версия:** 1.0.0  
**Последнее обновление:** 2025-01-XX

