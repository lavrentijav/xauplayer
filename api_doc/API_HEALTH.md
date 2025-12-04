# Health Check

## GET `/` или `/ping`

Проверка доступности сервера.

**Response:**
```json
{
  "status": "ok",
  "message": "Server is running"
}
```

## GET `/health`

Проверка здоровья сервиса (БД, Redis).

**Response:**
```json
{
  "status": "ok",
  "database": "connected",
  "redis": "connected"
}
```

## OPTIONS `/{full_path:path}`

Обработка OPTIONS запросов для CORS preflight.

**Response:**
```json
{
  "status": "ok"
}
```

