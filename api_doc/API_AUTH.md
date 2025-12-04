# Аутентификация и Регистрация

## POST `/auth/register`

Регистрация нового пользователя.

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "password123",
  "name": "Имя пользователя" // опционально
}
```

**Response:** `UserResponse`
```json
{
  "id": 1,
  "email": "user@example.com",
  "name": "user"
}
```

## POST `/auth/login`

Вход в систему.

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "password123",
  "device_name": "iPhone 12"
}
```

**Response:** `TokenResponse`
```json
{
  "access_token": "eyJ...",
  "refresh_token": "eyJ...",
  "token_type": "bearer",
  "device_id": 1
}
```

## POST `/auth/logout`

Выход из системы (добавляет refresh_token в черный список).

**Query Parameters:**
- `refresh_token` (string, required) - Refresh token для добавления в черный список

**Response:**
```json
{
  "status": "logged out"
}
```

