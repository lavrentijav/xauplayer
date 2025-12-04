# Книги

## GET `/books`

Получить список книг.

**Query Parameters:**
- `offset` (int, default: 0) - Смещение для пагинации
- `limit` (int, default: 100) - Количество записей
- `filter` (string, optional) - Фильтр по названию или автору

**Response:** `list[BookResponse]`
```json
[
  {
    "id": 1,
    "title": "Том 1 Название",
    "author": "Автор",
    "narrator": "Чтец",
    "cover_url": "/covers/...",
    "description": "Описание",
    "series_id": 1,
    "series_order": 1,
    "is_hidden": false,
    "uploaded_at": "2025-01-01T00:00:00"
  }
]
```

**Примечание:** Скрытые книги (is_hidden=true) не возвращаются через API. Книги автоматически помечаются как скрытые, если они не найдены при индексации.

## GET `/books/{book_id}`

Получить информацию о книге.

**Response:** `BookResponse`

**Примечание:** Для получения обложки книги используйте endpoint `/covers/books/{book_id}` (см. [API_COVERS.md](API_COVERS.md))

## GET `/books/{book_id}/chapters`

Получить список глав книги.

**Response:** `list[ChapterResponse]`
```json
[
  {
    "id": 1,
    "title": "Глава 1",
    "duration": 3600.0,
    "path": "серия/том/01.mp3",
    "order": 1,
    "real_order": null
  }
]
```

## GET `/books/{book_id}/stream/{chapter_id}`

Стриминг аудиофайла главы.

**Response:** Audio file (MP3)

## GET `/stream/{book_id}/{chapter_id}`

Стриминг аудиофайла главы (legacy путь для совместимости).

**Response:** Audio file (MP3)

**Примечание:** Этот endpoint является альтернативным путем для совместимости со старыми клиентами. Рекомендуется использовать `/books/{book_id}/stream/{chapter_id}`.

## PUT `/books/chapters/{chapter_id}/order`

Обновить реальный порядок главы.

**Request Body:**
```json
{
  "real_order": 5  // null для использования order из имени файла
}
```

**Response:**
```json
{
  "status": "success",
  "chapter_id": 1,
  "real_order": 5
}
```

## POST `/books/{book_id}/series/{series_id}`

Добавить книгу в серию.

**Query Parameters:**
- `series_order` (int, optional) - Порядок книги в серии

**Response:**
```json
{
  "status": "success",
  "book_id": 1,
  "series_id": 1
}
```

## DELETE `/books/{book_id}/series`

Удалить книгу из серии.

**Response:**
```json
{
  "status": "success",
  "book_id": 1
}
```

