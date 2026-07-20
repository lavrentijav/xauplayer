package ru.fire_core.xauplayer.core.humor

/**
 * Прикольные фразы и пасхалки для интерфейса.
 * Содержит отсылки на фильмы, сериалы и аниме. Использовать где угодно.
 */
object FunPhrases {

    private val random = java.util.Random()

    private fun <T> List<T>.pick(): T = this[random.nextInt(size)]

    // ===== Оффлайн-режим / служебный аккаунт =====
    private val offline = listOf(
        "Хмм, кажется я не могу подключиться к инету...",
        "Оффлайн-режим: слушаем то, что уже с нами.",
        "Инета нет, но книги-то есть.",
        "Ты, я и скачанные главы. Больше никого.",
        "Сервер спит. Мы — нет. 🌙",
        "Мы в оффлайне, как Уэйд в Оазисе, когда вырубили сеть.",
        "Связи нет. Но это не повод не слушать."
    )
    fun offlineCaption(): String = offline.pick()

    // ===== Обновления =====
    /** Установлена актуальная версия — подбадриваем */
    private val upToDate = listOf(
        "У тебя самая свежая версия. Идеально.",
        "Актуальная версия. Ты быстрее, чем Сайтама.",
        "Всё обновлено. Да пребудет с тобой Сила.",
        "Последняя версия — ты на гребне волны. 🏄",
        "Свежак. Даже свежее, чем мемы в твоей ленте.",
        "Всё по последнему слову. Тони Старк бы одобрил."
    )
    fun updateUpToDate(): String = upToDate.pick()

    /** Версия устарела — «вы в прошлом» */
    private val behind = listOf(
        "Ой... кажется, вы в прошлом. Пора обновиться.",
        "Дороги? Там, куда мы направляемся, дороги не нужны... а вот обновление — да.",
        "Вы застряли в прошлой эпохе. DeLorean подан — обновляйтесь.",
        "Ваша версия — это уже классика. Но пора в настоящее.",
        "Прошлое прекрасно, но новая версия лучше. Йарэ-йарэ..."
    )
    fun updateBehind(): String = behind.pick()

    /** Локальная версия новее серверной — удивляемся */
    private val ahead = listOf(
        "Оо... у вас версия НОВЕЕ, чем на сервере?! Вы из будущего?",
        "Стоп. Ваша сборка свежее серверной. Путешественник во времени, ты ли это?",
        "88 миль в час?! У вас версия из будущего. 🚗",
        "Как? Ваша версия обгоняет сервер. Доктор, это парадокс!",
        "Вы опередили время. Наруто бы сказал: «Dattebayo!»"
    )
    fun updateAhead(): String = ahead.pick()

    // ===== Отсылки для подписи «О приложении» =====
    private val references = listOf(
        "«Один плейлист, чтобы править всеми».",
        "«Я стану Королём аудиокниг!»",
        "«Кэйкаку доори» (примечание: кэйкаку значит план).",
        "«Посвяти своё сердце!»",
        "«Пробей небеса своим буром!»",
        "«I'll be back». И снова нажму play.",
        "«Да пребудет с тобой звук».",
        "«Yare yare daze...» — сказал плеер и продолжил играть.",
        "«Это не просто плеер. Это стенд»."
    )
    fun aboutReference(): String = references.pick()

    // ===== Пасхалки на цвета =====
    // Точное совпадение HEX (в верхнем регистре, с #) -> фраза
    private val colorEggs = mapOf(
        "#C0FFEE" to "C0FFEE — кто-то заварил кофе? ☕",
        "#BADA55" to "BADA55 — выглядит badass.",
        "#DECADE" to "DECADE — прошло целое десятилетие...",
        "#FACADE" to "FACADE — красивый фасад.",
        "#0FF1CE" to "0FF1CE — за работу.",
        "#DEFACE" to "DEFACE — только не порти чужое.",
        "#ACCE55" to "ACCE55 granted.",
        "#1DB954" to "Зелёный, как Spotify. Совпадение? Не думаю.",
        "#FF0000" to "Красная таблетка? Добро пожаловать в реальный мир. (Матрица)",
        "#0000FF" to "Синяя таблетка — история закончится, ты проснёшься в кровати...",
        "#00FF00" to "Матрица тебя держит. Следуй за белым кроликом.",
        "#000000" to "Тьма. Только тьма. И немного драмы.",
        "#FFFFFF" to "Чистый лист. Как тетрадь до первого имени...",
        "#FF00FF" to "Кислотный вайб. Кто-то тут фанат синтвейва.",
        "#FFD700" to "Золото. «Голд Экспириенс», не иначе.",
        // Цвет фона иконки приложения (дефолтный шаблон Android)
        "#3DDC84" to "Это же цвет иконки приложения. Не подражай — будь собой."
    )

    /** Отсылки на цвета. Оранжевый — цвет fire-core group. */
    fun colorEasterEgg(hex: String): String? {
        val key = hex.uppercase()
        colorEggs[key]?.let { return it }
        // Оранжевый в любом оттенке — «да ты ценитель»
        val hsv = hexToHsv(key) ?: return null
        val (h, s, v) = hsv
        if (h in 18f..45f && s >= 0.5f && v >= 0.5f) {
            return "Оранжевый... да ты ценитель. Это цвет fire-core group. 🔥"
        }
        return null
    }

    /** Парсит #RRGGBB в (hue[0..360], saturation[0..1], value[0..1]) или null. */
    private fun hexToHsv(hex: String): Triple<Float, Float, Float>? {
        val m = Regex("^#([0-9A-F]{2})([0-9A-F]{2})([0-9A-F]{2})$").find(hex) ?: return null
        val r = m.groupValues[1].toInt(16) / 255f
        val g = m.groupValues[2].toInt(16) / 255f
        val b = m.groupValues[3].toInt(16) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val h = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val s = if (max == 0f) 0f else delta / max
        return Triple(h, s, max)
    }

    // ===== Пасхалка: многократное нажатие на версию =====
    /**
     * Возвращает фразу только на «вехах» нажатий (иначе null), чтобы сообщение
     * не мелькало на каждый тык.
     */
    fun versionTap(count: Int): String? = when (count) {
        10 -> "А зачем ты тыкаешь?"
        13 -> "Нафига ты тыкаешь? Я ничего не дам."
        15 -> "Ну может, на 100-й раз что-то будет?"
        50 -> "Половина пути до 100. Ещё не поздно заняться делом."
        100 -> "Нуу... поздравляю тебя с бездарно потраченными несколькими минутами. Ачивка «Задрот-тыкальщик» получена. 🏆"
        else -> null
    }

    /**
     * Пасхалка «стань разработчиком» — как в Android, где тыкают по номеру сборки.
     * Обратный отсчёт показывается только на последних нажатиях, а не на каждом.
     */
    fun buildNumberTap(count: Int): String? = when {
        count in 3..6 -> "Вы уже почти разработчик! Осталось ${7 - count} нажатий."
        count == 7 -> "Поздравляем, теперь вы разработчик! ...шучу. Ничего не разблокировалось. 🧑‍💻"
        else -> null
    }

    // ===== Пасхалки на «магические» числа (в полях настроек) =====
    fun numberEgg(n: Int): String? = when (n) {
        0 -> "Ноль. Абсолютный дзен."
        42 -> "Ответ на главный вопрос жизни, вселенной и всего такого. (Автостопом по галактике)"
        69 -> "Nice."
        404 -> "Не найдено... но всё же найдено."
        420 -> "Расслабься."
        777 -> "Джекпот!"
        1337 -> "Ты — элита (l33t)."
        9000 -> "IT'S OVER 9000!!! (Dragon Ball Z)"
        else -> null
    }

    /** Пасхалка на скорость воспроизведения. */
    fun speedEgg(speed: Float): String? = when {
        speed >= 2.99f -> "Тройная скорость! Ты слушаешь быстрее, чем читает рэп Эминем."
        speed <= 0.51f -> "Медленно... наслаждаешься каждым словом. Дзен."
        kotlin.math.abs(speed - 1.21f) < 0.005f -> "1.21 гигаватта?! (Назад в будущее)"
        else -> null
    }

    // ===== Эффекты для некоторых книг по названию =====
    /**
     * Эффект книги: эмодзи-бейдж и цвет свечения/рамки поверх обложки.
     * @param emoji значок в углу обложки
     * @param colorHex цвет свечения/рамки (#RRGGBB)
     */
    /**
     * @param kind тип полноэкранного эффекта: "matrix" (цифровой дождь),
     *   "rising" (искры/частицы взмывают вверх), "stars" (звёзды/космос).
     */
    data class BookEffect(val emoji: String, val colorHex: String, val kind: String)

    // Ключевое слово (в нижнем регистре) -> эффект. Проверяется вхождением в название.
    private val bookEffects: List<Pair<List<String>, BookEffect>> = listOf(
        listOf("гарри поттер", "harry potter", "хогвартс", "поттер") to BookEffect("⚡", "#B388FF", "rising"),
        listOf("властелин колец", "хоббит", "lord of the rings", "кольц", "толкин", "средиземь") to BookEffect("💍", "#FFD700", "rising"),
        listOf("ведьмак", "witcher", "геральт") to BookEffect("🐺", "#B0BEC5", "rising"),
        listOf("дюна", "dune", "арракис") to BookEffect("🏜️", "#E8A33D", "rising"),
        listOf("метро 2033", "метро 2034", "метро 2035", "метро") to BookEffect("☢️", "#7CB342", "rising"),
        listOf("дракон", "dragon", "драконь") to BookEffect("🐉", "#FF7043", "rising"),
        listOf("звёзд", "звезд", "star wars", "звёздные войны", "космос", "галактик") to BookEffect("🚀", "#42A5F5", "stars"),
        listOf("1984", "оруэлл", "orwell", "большой брат") to BookEffect("👁️", "#EF5350", "matrix"),
        listOf("матриц", "matrix") to BookEffect("🟩", "#00E676", "matrix"),
        listOf("шерлок", "sherlock", "холмс", "holmes") to BookEffect("🔎", "#B0A18F", "rising"),
        listOf("зомби", "zombie", "мертвец", "апокалипс") to BookEffect("🧟", "#66BB6A", "rising"),
        listOf("вампир", "vampire", "дракула") to BookEffect("🦇", "#B388FF", "rising")
    )

    fun bookEffect(title: String?): BookEffect? {
        if (title.isNullOrBlank()) return null
        val t = title.lowercase()
        for ((keys, effect) in bookEffects) {
            if (keys.any { t.contains(it) }) return effect
        }
        return null
    }

    // ===== Прикольные подписи к статусам книг/серий =====
    fun statusEmoji(status: String?): String = when (status) {
        "listening" -> "🎧"
        "wanted" -> "📚"
        "completed" -> "✅"
        "dropped" -> "🥀"
        else -> ""
    }
}
