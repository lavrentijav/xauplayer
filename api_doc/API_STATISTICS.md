# Статистика

## GET `/statistics/year/{year}`

Получить статистику пользователя за год.

**Response:** `StatisticsResponse`
```json
{
  "statistics": [
    {
      "date": "2025-01-01",
      "minutes_listened": 120,
      "books_completed": 1,
      "chapters_listened": 5
    }
  ],
  "total_minutes": 3600,
  "total_books_completed": 12,
  "total_chapters_listened": 150
}
```

## GET `/statistics/month/{year}/{month}`

Получить статистику пользователя за месяц.

**Response:** `StatisticsResponse`

## GET `/statistics/range`

Получить статистику пользователя за период.

**Query Parameters:**
- `start_date` (string, required) - Начальная дата в формате YYYY-MM-DD
- `end_date` (string, required) - Конечная дата в формате YYYY-MM-DD

**Response:** `StatisticsResponse`

