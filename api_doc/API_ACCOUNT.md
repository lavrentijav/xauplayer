# Аккаунт

## GET `/account`

Получить информацию об аккаунте.

**Response:** `AccountResponse`
```json
{
  "name": "Имя",
  "email": "user@example.com",
  "total_time_minutes": 3600,
  "created_at": "2025-01-01T00:00:00"
}
```

## GET `/account/activity`

Получить активность пользователя (GitHub-подобная сетка).

**Response:**
```json
{
  "2025-10-30": 15,
  "2025-10-31": 47,
  "2025-11-01": 120
}
```

## GET `/account/devices`

Получить список устройств пользователя.

**Response:** `list[DeviceResponse]`
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

## DELETE `/account/device/{device_id}`

Удалить устройство.

**Response:**
```json
{
  "status": "deleted"
}
```

