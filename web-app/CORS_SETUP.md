# Настройка CORS на сервере API

## Проблема

Веб-приложение запущено на `http://api.xau.fire-core.ru:8000`, а API на `https://api.xau.fire-core.ru`. 
Браузер блокирует запросы из-за CORS политики.

## Решение: Настройка CORS в FastAPI

### 1. Базовая настройка CORS

Добавьте в ваш файл `app/main.py` (или где у вас инициализируется FastAPI приложение):

```python
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

app = FastAPI()

# Настройка CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://api.xau.fire-core.ru:8000",  # Ваше веб-приложение
        "http://localhost:8000",              # Для локальной разработки
        "http://localhost:3000",              # Альтернативный порт
        "http://localhost:5000",              # Альтернативный порт
        "https://api.xau.fire-core.ru",       # Если будете использовать HTTPS для веб-приложения
    ],
    allow_credentials=True,
    allow_methods=["*"],  # Разрешить все HTTP методы
    allow_headers=["*"],  # Разрешить все заголовки
    expose_headers=["*"], # Разрешить доступ ко всем заголовкам ответа
)
```

### 2. Для разработки (разрешить все origins)

Если нужно временно разрешить все домены (только для разработки!):

```python
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # ⚠️ Только для разработки!
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
```

**⚠️ ВАЖНО:** Не используйте `allow_origins=["*"]` в production с `allow_credentials=True` - это небезопасно!

### 3. Динамическая настройка через переменные окружения

Лучший вариант - использовать переменные окружения:

```python
import os
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

app = FastAPI()

# Получаем список разрешенных origins из переменных окружения
ALLOWED_ORIGINS = os.getenv(
    "ALLOWED_ORIGINS",
    "http://api.xau.fire-core.ru:8000,http://localhost:8000,http://localhost:3000"
).split(",")

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
    expose_headers=["*"],
)
```

Затем в `.env` файле или при запуске:
```bash
ALLOWED_ORIGINS="http://api.xau.fire-core.ru:8000,http://localhost:8000,https://api.xau.fire-core.ru"
```

### 4. Полный пример с проверкой

```python
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
import os

app = FastAPI()

# Список разрешенных origins
ALLOWED_ORIGINS = [
    "http://api.xau.fire-core.ru:8000",
    "http://localhost:8000",
    "http://localhost:3000",
    "http://localhost:5000",
    "https://api.xau.fire-core.ru",  # Если будете использовать HTTPS
]

# Добавляем origins из переменных окружения если есть
env_origins = os.getenv("ALLOWED_ORIGINS", "")
if env_origins:
    ALLOWED_ORIGINS.extend(env_origins.split(","))

# Убираем дубликаты
ALLOWED_ORIGINS = list(set(ALLOWED_ORIGINS))

print(f"CORS allowed origins: {ALLOWED_ORIGINS}")

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"],
    allow_headers=["*"],
    expose_headers=["*"],
    max_age=3600,  # Кэшировать preflight запросы на 1 час
)
```

### 5. Проверка работы CORS

После настройки проверьте:

1. **В логах сервера** должны появиться OPTIONS запросы (preflight)
2. **В консоли браузера** не должно быть CORS ошибок
3. **Запросы должны проходить** с заголовком `Origin`

### 6. Отладка CORS

Если CORS все еще не работает:

1. Проверьте, что middleware добавлен **до** всех роутеров
2. Проверьте логи сервера на наличие OPTIONS запросов
3. Убедитесь, что origin точно совпадает (включая протокол и порт)
4. Проверьте, что сервер возвращает заголовки:
   - `Access-Control-Allow-Origin`
   - `Access-Control-Allow-Credentials`
   - `Access-Control-Allow-Methods`
   - `Access-Control-Allow-Headers`

### 7. Проверка через curl

```bash
# Проверка preflight запроса
curl -X OPTIONS https://api.xau.fire-core.ru/auth/login \
  -H "Origin: http://api.xau.fire-core.ru:8000" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: content-type" \
  -v

# Должны увидеть заголовки:
# Access-Control-Allow-Origin: http://api.xau.fire-core.ru:8000
# Access-Control-Allow-Methods: POST
# Access-Control-Allow-Headers: content-type
```

### 8. Если используете Nginx как reverse proxy

Если у вас Nginx перед FastAPI, можно настроить CORS там:

```nginx
location / {
    # CORS заголовки
    if ($request_method = 'OPTIONS') {
        add_header 'Access-Control-Allow-Origin' 'http://api.xau.fire-core.ru:8000';
        add_header 'Access-Control-Allow-Methods' 'GET, POST, PUT, DELETE, OPTIONS';
        add_header 'Access-Control-Allow-Headers' 'Authorization, Content-Type';
        add_header 'Access-Control-Allow-Credentials' 'true';
        add_header 'Access-Control-Max-Age' '3600';
        return 204;
    }
    
    add_header 'Access-Control-Allow-Origin' 'http://api.xau.fire-core.ru:8000' always;
    add_header 'Access-Control-Allow-Credentials' 'true' always;
    
    proxy_pass http://localhost:8000;  # Ваш FastAPI сервер
}
```

## После настройки

1. Перезапустите сервер API
2. Обновите страницу веб-приложения
3. Попробуйте войти снова
4. Проверьте консоль браузера - CORS ошибок быть не должно

## Дополнительные заголовки для токенов

Если используете заголовки для токенов (X-Token-Expires-Soon и т.д.), убедитесь, что они в `expose_headers`:

```python
app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
    expose_headers=[
        "X-Token-Expires-Soon",
        "X-Token-Expires-At",
        "X-Token-Expired",
        "*"
    ],
)
```

---

**Примечание:** После настройки CORS на сервере, веб-приложение должно работать корректно.

