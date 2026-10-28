package ru.fire_core.xauplayer.data.admin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import ru.fire_core.xauplayer.di.NetworkCache
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/** Поле операции из манифеста. */
data class AdminField(
    val name: String,
    val type: String,
    val required: Boolean,
    val location: String, // path | query | body
    val enum: List<String>?,
    val default: String?,
    val description: String?
)

/** Операция из манифеста. Приложение не знает ни одного админского пути заранее. */
data class AdminOp(
    val id: String,
    val group: String,
    val title: String,
    val description: String?,
    val method: String,
    val path: String,
    val fields: List<AdminField>,
    val hasBody: Boolean,
    val danger: Boolean
)

data class AdminResponse(val code: Int, val body: String)

/**
 * Динамический админ-клиент.
 *
 * * Единственное, что зашито: вход (`auth/admin/login`) и запрос манифеста. Всё остальное — пути, методы, поля —
 *   берётся из ответа сервера, который отдаёт настоящую карту только валидной админ-сессии.
 * * К каждому запросу подмешиваются лишние поля (имена из ВСЕХ ручек манифеста + пустышки), поэтому по перехваченному
 *   запросу или дампу памяти не видно, какие поля нужны ручке на самом деле; сервер лишнее игнорирует.
 * * К каждому запросу добавляется временное доказательство (HMAC от метода, пути и 30-секундного окна) в заголовке,
 *   имя и значение которого считаются в рантайме из ключа сессии и нигде в коде/манифесте не названы.
 */
@Singleton
class AdminClient @Inject constructor(
    baseClient: OkHttpClient,
    private val networkCache: NetworkCache
) {
    // Свой клиент без пользовательских интерцепторов (иначе к админ-запросу прицепится пользовательский Bearer)
    private val http: OkHttpClient = baseClient.newBuilder().apply {
        interceptors().clear()
        networkInterceptors().clear()
        cache(null)
    }.build()

    private val rnd = SecureRandom()
    private var token: String? = null
    private var proofKey: ByteArray? = null
    private var noisePool: List<Pair<String, String>> = emptyList() // имя -> тип

    val isLoggedIn: Boolean get() = token != null

    /** Уведомление безопасности из последнего входа (напр. «токен утёк, смените пароль»). */
    var lastNotice: String? = null
        private set

    fun logout() {
        token = null
        proofKey = null
        noisePool = emptyList()
    }

    private fun base(): String = networkCache.getBaseUrl().trimEnd('/')

    /** Вход администратора. Возвращает список операций, полученный от сервера. */
    suspend fun login(email: String, password: String): List<AdminOp> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", email).put("password", password).put("app", true)
        val req = Request.Builder()
            .url("${base()}/auth/admin/login")
            .post(body.toString().toRequestBody(JSON))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException(errorText(resp.code, text))
            val json = JSONObject(text)
            token = json.getString("admin_token")
            proofKey = json.optString("proof_key").takeIf { it.isNotBlank() }?.let { fromHex(it) }
            lastNotice = json.optString("security_notice").takeIf { it.isNotBlank() && it != "null" }
        }
        loadManifest()
    }

    private suspend fun loadManifest(): List<AdminOp> {
        val resp = rawCall("GET", "/admin/manifest", emptyMap(), emptyMap(), null, noise = false)
        if (resp.code != 200) throw IllegalStateException(errorText(resp.code, resp.body))
        val ops = parseManifest(resp.body)
        // Пул имён для шума: поля всех ручек + пустышки
        noisePool = (ops.flatMap { op -> op.fields.filter { it.location != "path" }.map { it.name to it.type } } +
            AdminDecoys.FIELD_NAMES.map { it to "string" }).distinct()
        return ops
    }

    /** Выполнить операцию. [values] — введённые пользователем значения по имени поля. */
    suspend fun execute(op: AdminOp, values: Map<String, String>): AdminResponse = withContext(Dispatchers.IO) {
        var path = op.path
        val query = linkedMapOf<String, String>()
        val body = JSONObject()
        for (f in op.fields) {
            val v = values[f.name]?.takeIf { it.isNotBlank() } ?: f.default
            if (v == null) {
                if (f.required) throw IllegalArgumentException("Поле «${f.name}» обязательно")
                continue
            }
            when (f.location) {
                "path" -> path = path.replace("{${f.name}}", java.net.URLEncoder.encode(v, "UTF-8"))
                "query" -> query[f.name] = v
                else -> body.put(f.name, typed(f.type, v))
            }
        }
        addNoise(op, query, body)
        rawCall(op.method, path, query, emptyMap(), if (op.hasBody) body else null, noise = true)
    }

    private fun addNoise(op: AdminOp, query: MutableMap<String, String>, body: JSONObject) {
        val own = op.fields.map { it.name }.toSet() + query.keys
        val candidates = noisePool.filter { it.first !in own }
        if (candidates.isEmpty()) return
        val count = 2 + rnd.nextInt(4) // 2..5 полей — достаточно, чтобы не выделялись, но не раздувает запрос
        candidates.shuffled(rnd).take(count).forEach { (name, type) ->
            val value = fakeValue(type)
            if (op.hasBody && rnd.nextBoolean()) body.put(name, typed(type, value)) else query[name] = value
        }
    }

    private suspend fun rawCall(
        method: String,
        relPath: String,
        query: Map<String, String>,
        extraHeaders: Map<String, String>,
        body: JSONObject?,
        noise: Boolean
    ): AdminResponse = withContext(Dispatchers.IO) {
        val urlBuilder = "${base()}$relPath".toHttpUrl().newBuilder()
        query.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
        val url = urlBuilder.build()

        val rb = Request.Builder().url(url)
        val payload = body?.toString()?.toRequestBody(JSON)
        when (method) {
            "GET" -> rb.get()
            "DELETE" -> if (payload != null) rb.delete(payload) else rb.delete()
            else -> rb.method(method, payload ?: "{}".toRequestBody(JSON))
        }
        token?.let { rb.header("Authorization", "Bearer $it") }
        proofKey?.let { key ->
            val window = System.currentTimeMillis() / 1000 / 30
            rb.header(
                "x-" + hex(mac(key, "name")).take(10),
                b64(mac(key, "$window|$method|${url.encodedPath}")).take(22)
            )
        }
        if (noise) repeat(1 + rnd.nextInt(2)) { rb.header("x-" + randomHex(5), randomHex(8)) }
        extraHeaders.forEach { (k, v) -> rb.header(k, v) }

        http.newCall(rb.build()).execute().use { AdminResponse(it.code, it.body?.string().orEmpty()) }
    }

    // --- манифест ---------------------------------------------------------------------------

    private fun parseManifest(text: String): List<AdminOp> {
        val ops = JSONObject(text).getJSONArray("ops")
        return (0 until ops.length()).map { i ->
            val o = ops.getJSONObject(i)
            val fields = mutableListOf<AdminField>()
            o.optJSONArray("params")?.let { arr ->
                for (j in 0 until arr.length()) fields += field(arr.getJSONObject(j), arr.getJSONObject(j).optString("in", "query"))
            }
            val bodyArr = o.optJSONArray("body")
            bodyArr?.let { arr -> for (j in 0 until arr.length()) fields += field(arr.getJSONObject(j), "body") }
            AdminOp(
                id = o.getString("id"),
                group = o.optString("group"),
                title = o.optString("title"),
                description = o.optString("description").takeIf { it.isNotBlank() && it != "null" },
                method = o.getString("method"),
                path = o.getString("path"),
                fields = fields,
                hasBody = bodyArr != null,
                danger = o.optBoolean("danger")
            )
        }
    }

    private fun field(j: JSONObject, location: String): AdminField {
        val enum = j.optJSONArray("enum")?.let { a -> (0 until a.length()).map { a.get(it).toString() } }
        return AdminField(
            name = j.getString("name"),
            type = j.optString("type", "string"),
            required = j.optBoolean("required"),
            location = location,
            enum = enum,
            default = if (j.isNull("default")) null else j.opt("default")?.toString(),
            description = j.optString("description").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    // --- утилиты ----------------------------------------------------------------------------

    private fun typed(type: String, v: String): Any = when (type) {
        "integer" -> v.toLongOrNull() ?: v
        "number" -> v.toDoubleOrNull() ?: v
        "boolean" -> v.equals("true", true)
        "array" -> JSONArray().also { arr -> v.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach(arr::put) }
        else -> v
    }

    private fun fakeValue(type: String): String = when (type) {
        "integer", "number" -> (1 + rnd.nextInt(9999)).toString()
        "boolean" -> rnd.nextBoolean().toString()
        else -> randomHex(4)
    }

    private fun errorText(code: Int, body: String): String {
        val detail = try { JSONObject(body).opt("detail")?.toString() } catch (e: Exception) { null }
        return "HTTP $code" + (detail?.let { ": $it" } ?: "")
    }

    private fun mac(key: ByteArray, msg: String): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(msg.toByteArray())

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun fromHex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private fun b64(b: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    private fun randomHex(n: Int) = hex(ByteArray(n).also(rnd::nextBytes))

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
