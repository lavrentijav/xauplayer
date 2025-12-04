# Заметки

## GET `/notes/{book_id}`

Получить список заметок для книги.

**Response:** `list[NoteResponse]`
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

## POST `/notes/{book_id}`

Создать заметку для книги.

**Request Body:**
```json
{
  "text": "Интересный момент",
  "timestamp": 125000 // в миллисекундах
}
```

**Response:** `NoteResponse`

