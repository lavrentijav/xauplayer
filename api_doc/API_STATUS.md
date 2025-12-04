# Статусы книг и серий

## Статусы книг

### GET `/status/books/{book_id}`

Получить статус книги для текущего пользователя.

**Response:** `BookStatusResponse`
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

### POST `/status/books`

Установить статус книги.

**Request Body:**
```json
{
  "book_id": 1,
  "status": "wanted" // "wanted", "listening", "completed", "dropped"
}
```

**Response:** `BookStatusResponse`

### PUT `/status/books/{book_id}`

Обновить статус книги.

**Request Body:**
```json
{
  "status": "completed"
}
```

**Response:** `BookStatusResponse`

### DELETE `/status/books/{book_id}`

Удалить статус книги.

**Response:**
```json
{
  "status": "deleted"
}
```

### GET `/status/books/wanted`

Получить список желаемых книг.

**Query Parameters:**
- `offset` (int, default: 0)
- `limit` (int, default: 100)

**Response:** `list[BookResponse]`

### GET `/status/books/listening`

Получить список книг, которые слушает пользователь.

**Response:** `list[BookResponse]`

### GET `/status/books/completed`

Получить список прослушанных книг.

**Response:** `list[BookResponse]`

### GET `/status/books/dropped`

Получить список брошенных книг.

**Response:** `list[BookResponse]`

## Статусы серий

### GET `/status/series/{series_id}`

Получить статус серии для текущего пользователя.

**Response:** `SeriesStatusResponse`

### POST `/status/series`

Установить статус серии.

**Request Body:**
```json
{
  "series_id": 1,
  "status": "listening"
}
```

**Response:** `SeriesStatusResponse`

### PUT `/status/series/{series_id}`

Обновить статус серии.

**Request Body:**
```json
{
  "status": "completed"
}
```

**Response:** `SeriesStatusResponse`

### DELETE `/status/series/{series_id}`

Удалить статус серии.

**Response:**
```json
{
  "status": "deleted"
}
```

### GET `/status/series/wanted`

Получить список желаемых серий.

**Response:** `list[SeriesResponse]`

### GET `/status/series/listening`

Получить список серий, которые слушает пользователь.

**Response:** `list[SeriesResponse]`

### GET `/status/series/completed`

Получить список прослушанных серий.

**Response:** `list[SeriesResponse]`

### GET `/status/series/dropped`

Получить список брошенных серий.

**Response:** `list[SeriesResponse]`

