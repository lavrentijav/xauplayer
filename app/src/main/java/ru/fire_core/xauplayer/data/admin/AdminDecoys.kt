package ru.fire_core.xauplayer.data.admin

/**
 * Обманные данные. Эти строки лежат в приложении «на виду», но ничего настоящего не значат:
 *  - [FAKE_ROUTES] — правдоподобная «карта админ-ручек». Реальных путей в приложении нет вообще
 *    (их выдаёт сервер админ-сессии); все пути отсюда на сервере — ловушки, обращение к ним фиксируется.
 *    Приложение само их не вызывает.
 *  - [FIELD_NAMES] — имена полей-пустышек, из которых [AdminClient] подмешивает шум в запросы
 *    (вместе с реальными именами полей из манифеста).
 */
internal object AdminDecoys {
    val FAKE_ROUTES = listOf(
        "/api/v1/admin/export_all",
        "/api/v1/admin/users/dump",
        "/api/v1/internal/debug/config",
        "/api/v1/internal/s3/credentials",
        "/api/v1/admin/backdoor",
    )

    val FIELD_NAMES = listOf(
        "trace", "ctx", "rev", "nonce", "sid", "tz", "lang", "build", "cursor", "scope",
        "sort", "dir", "page", "fmt", "ref", "shard", "epoch", "hint",
    )
}
