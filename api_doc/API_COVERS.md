# Обложки

## GET `/covers/books/{book_id}`

Получить обложку книги.

**Response:** Image file (JPEG/PNG)

**Status Codes:**
- `200` - Обложка найдена и возвращена
- `404` - Обложка не найдена для данной книги

**Пример:**
```
GET /covers/books/1
```

Возвращает файл обложки книги с ID=1. Поддерживаются форматы: JPEG, PNG.

## GET `/covers/series/{series_id}`

Получить обложку серии.

**Response:** Image file (JPEG/PNG)

**Status Codes:**
- `200` - Обложка найдена и возвращена
- `404` - Обложка не найдена для данной серии

**Пример:**
```
GET /covers/series/1
```

Возвращает файл обложки серии с ID=1. Поддерживаются форматы: JPEG, PNG.

## Примечания

1. Обложки ищутся сначала по пути, указанному в `cover_url` в базе данных
2. Если обложка не найдена по `cover_url`, система пытается найти файл обложки в структуре файловой системы:
   - Для книг: `AUDIO_DIR/{series_name}/{book_title}/cover.jpg` или `cover.png`
   - Для серий: `AUDIO_DIR/{series_name}/cover.jpg` или `cover.png`
3. Поддерживаемые форматы: `.jpg`, `.jpeg`, `.png`
4. MIME types: `image/jpeg` для JPEG файлов, `image/png` для PNG файлов
5. Обложки скрытых книг также доступны через этот endpoint

## Использование в клиенте

В HTML/JS:
```html
<img src="/covers/books/1" alt="Обложка книги" />
<img src="/covers/series/1" alt="Обложка серии" />
```

В React/других фреймворках:
```jsx
<img src="http://localhost:8000/covers/books/1" alt="Обложка книги" />
<img src="http://localhost:8000/covers/series/1" alt="Обложка серии" />
```

