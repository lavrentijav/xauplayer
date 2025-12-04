# Серии

## GET `/series`

Получить список серий.

**Query Parameters:**
- `offset` (int, default: 0)
- `limit` (int, default: 100)

**Response:** `list[SeriesResponse]`

## GET `/series/{series_id}`

Получить информацию о серии.

**Response:** `SeriesResponse`

**Примечание:** Для получения обложки серии используйте endpoint `/covers/series/{series_id}` (см. [API_COVERS.md](API_COVERS.md))

## POST `/series`

Создать новую серию.

**Request Body:**
```json
{
  "name": "Название серии",
  "description": "Описание", // опционально
  "cover_url": "/covers/..." // опционально
}
```

**Response:** `SeriesResponse`

## PUT `/series/{series_id}`

Обновить информацию о серии.

**Request Body:**
```json
{
  "name": "Новое название", // опционально
  "description": "Новое описание", // опционально
  "cover_url": "/covers/..." // опционально
}
```

**Response:** `SeriesResponse`

## DELETE `/series/{series_id}`

Удалить серию.

**Response:**
```json
{
  "status": "deleted"
}
```

## GET `/series/{series_id}/books`

Получить все книги в серии.

**Response:** `list[BookResponse]`

