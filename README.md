# XAuPlayer

[![Build APK](https://github.com/lavrentijav/xauplayer/actions/workflows/android-build.yml/badge.svg)](https://github.com/lavrentijav/xauplayer/actions/workflows/android-build.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-brightgreen.svg)](#требования)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF.svg)](#технологии)

**XAuPlayer** — аудиоплеер для прослушивания аудиокниг на Android с поддержкой
серий, статусов, оффлайн-режима и синхронизации прогресса между устройствами.

> *An Android audiobook player: streaming + offline playback, series & statuses,
> progress sync, downloads, and an offline "service account" that lets you keep
> listening to downloaded books with no server and expired sessions.*

---

## Возможности

- 🎧 **Прослушивание аудиокниг** через ExoPlayer / Media3 с фоновым сервисом,
  уведомлением и управлением с гарнитуры.
- 📚 **Библиотека**: книги, серии, книги вне серий, теги, статусы
  (Слушаю / В планах / Прослушано / Брошено).
- 🔍 **Поиск** по книгам и сериям (название, автор, описание).
- 📖 **Страницы книги и серии** — с обложкой, описанием, списком книг серии
  и тематическими полноэкранными эффектами.
- 📴 **Оффлайн-режим и служебный аккаунт** — слушайте скачанное без интернета и
  даже с истёкшей сессией; запросы буферизуются и синхронизируются позже.
- 🔄 **Синхронизация прогресса** и офлайн-буфер прогресса.
- ⬇️ **Загрузки** с настройкой скорости и числа параллельных потоков.
- 🎚️ **Эквалайзер**, скорость воспроизведения, буферизация, перемотка.
- 🎨 **Кастомизация**: акцентный цвет и цвет плеера (с ручным вводом HEX).
- 📊 **Статистика** прослушивания (сетка активности как на GitHub).
- 🔐 **Параметры безопасности**: время истечения сессии, локальное продление,
  ручное обновление сессии.
- ⬆️ **Автообновление** APK с проверкой версии на сервере.
- 🥚 **Пасхалки** — отсылки на фильмы/сериалы/аниме в разных местах интерфейса.

## Скриншоты

<!-- Добавьте скриншоты в docs/screenshots и вставьте их сюда -->
_Скоро._

## Требования

- Android **8.0 (API 26)** и выше.
- Для сборки: JDK 17, Android SDK (compileSdk 35), Gradle (через wrapper).

## Сборка

```bash
git clone https://github.com/lavrentijav/xauplayer.git
cd xauplayer

# Debug APK
./gradlew assembleDebug
# → app/build/outputs/apk/debug/

# Release APK (без подписи, если не заданы переменные ключа)
./gradlew assembleRelease
```

Сборка также идёт автоматически через GitHub Actions
(`.github/workflows/android-build.yml`) — готовые APK доступны в артефактах
каждого запуска.

### Подпись релиза

Ключ подписи передаётся через переменные окружения / секреты GitHub Actions —
подробности в [`.github/SIGNING.md`](.github/SIGNING.md).

## Технологии

- **Язык/UI**: Kotlin, Jetpack Compose, Material 3, Navigation Compose
- **DI**: Hilt (Dagger)
- **Сеть**: Retrofit + OkHttp + Moshi
- **БД/хранилище**: Room, DataStore Preferences
- **Медиа**: AndroidX Media3 (ExoPlayer)
- **Фон**: WorkManager
- **Изображения**: Coil

## Структура проекта

```
app/src/main/java/ru/fire_core/xauplayer/
├── core/         # конфиг, логгер, auth-менеджер, юмор/пасхалки
├── data/         # network (API), local (Room), datastore, cache
├── di/           # Hilt-модули, сетевые интерцепторы
├── domain/       # репозитории и use-case'ы
├── download/     # загрузки и офлайн-прогресс
├── player/       # плеер-сервис, эквалайзер
├── ui/           # экраны, вкладки, диалоги, компоненты, ViewModel
└── update/       # проверка и установка обновлений
```

Документация API сервера — в [`API.md`](API.md) и [`API_GUIDE.md`](API_GUIDE.md).

## Оффлайн-режим и служебный аккаунт

Приложение умеет работать без сервера: сетевой слой перехватывает запросы к
эндпоинтам сессии/записи и эмулирует положительные ответы, а плеер использует
уже скачанные главы. Включить можно через переключатель «Оффлайн-режим» в
настройках безопасности или кнопкой «Слушать оффлайн» на экране входа. При
недоступности сети и истёкшей сессии приложение автоматически входит в
служебный (оффлайн) аккаунт.

## Вклад в проект

PR и issue приветствуются! См. [CONTRIBUTING.md](CONTRIBUTING.md). Отправляя
изменения, вы соглашаетесь лицензировать их под GPL-3.0.

## Лицензия

Проект распространяется под лицензией **GNU General Public License v3.0**.
Полный текст — в файле [LICENSE](LICENSE).

```
XAuPlayer — audiobook player for Android
Copyright (C) 2026  lavrentijav

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
```
