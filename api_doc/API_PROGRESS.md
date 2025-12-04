# Прогресс и Синхронизация

## POST `/progress/update`

Обновить прогресс прослушивания.

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

**Response:**
```json
{
  "status": "success"
}
```

## GET `/progress/sync`

Получить все актуальные данные для пользователя.

**Response:** `ProgressSyncResponse`
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

