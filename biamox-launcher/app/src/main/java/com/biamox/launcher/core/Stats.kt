package com.biamox.launcher.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class Kpi(val id: String, val title: String, val value: String, val delta: Double?, val emoji: String)
data class Series(val title: String, val labels: List<String>, val points: List<Float>)
data class Share(val title: String, val value: Float)

data class StatsSnapshot(
    val updated: String,
    val demo: Boolean,
    val kpis: List<Kpi>,
    val series: List<Series>,
    val breakdown: List<Share>,
    val notes: List<String>,
    val error: String? = null,
) {
    /** Короткий текст для Claude и голосового ассистента. */
    fun summary(): String = buildString {
        append(if (demo) "(ДЕМО-данные) " else "")
        append("Обновлено: $updated. ")
        kpis.forEach { k ->
            append("${k.title}: ${k.value}")
            k.delta?.let { append(" (${if (it >= 0) "+" else ""}${"%.1f".format(it)}%)") }
            append("; ")
        }
        if (notes.isNotEmpty()) append("Заметки: ${notes.joinToString("; ")}")
    }
}

/**
 * Статистика Biamox.
 * Ожидаемый формат JSON с вашего сервера (GET, заголовок Authorization: Bearer <token>):
 * {
 *   "updated": "2026-11-15 12:00",
 *   "kpis":   [{"id":"revenue","title":"Выручка сегодня","value":"124 500 ₴","delta":12.4,"emoji":"💰"}],
 *   "series": [{"title":"Выручка, 7 дней","labels":["Пн",...],"points":[10,12,...]}],
 *   "breakdown": [{"title":"Instagram","value":42}],
 *   "notes":  ["Текст"]
 * }
 */
class StatsRepository(private val prefs: Prefs, private val secrets: Secrets, private val config: () -> BiamoxConfig) {
    private val http = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()

    fun cached(): StatsSnapshot = prefs.lastStatsJson?.let { runCatching { parse(JSONObject(it), false) }.getOrNull() } ?: demo()

    suspend fun refresh(): StatsSnapshot = withContext(Dispatchers.IO) {
        val url = (prefs.statsUrl ?: "").ifBlank { config().statsUrl }
        if (url.isBlank()) return@withContext demo()
        try {
            val rb = Request.Builder().url(url)
            if (secrets.statsToken.isNotBlank()) rb.header("Authorization", "Bearer ${secrets.statsToken}")
            http.newCall(rb.build()).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                val body = r.body?.string() ?: error("Пустой ответ")
                val snap = parse(JSONObject(body), false)
                prefs.lastStatsJson = body
                snap
            }
        } catch (e: Exception) {
            cached().copy(error = "Нет связи с сервером статистики: ${e.message}")
        }
    }

    private fun parse(o: JSONObject, demo: Boolean): StatsSnapshot {
        fun arr(n: String) = o.optJSONArray(n) ?: JSONArray()
        val k = arr("kpis"); val s = arr("series"); val b = arr("breakdown"); val n = arr("notes")
        return StatsSnapshot(
            updated = o.optString("updated", now()),
            demo = demo,
            kpis = (0 until k.length()).map { i ->
                val x = k.getJSONObject(i)
                Kpi(x.optString("id", "k$i"), x.optString("title"), x.optString("value"),
                    if (x.has("delta")) x.optDouble("delta") else null, x.optString("emoji", "📈"))
            },
            series = (0 until s.length()).map { i ->
                val x = s.getJSONObject(i)
                val l = x.optJSONArray("labels") ?: JSONArray(); val p = x.optJSONArray("points") ?: JSONArray()
                Series(x.optString("title"), (0 until l.length()).map { l.getString(it) },
                    (0 until p.length()).map { p.getDouble(it).toFloat() })
            },
            breakdown = (0 until b.length()).map { i ->
                val x = b.getJSONObject(i); Share(x.optString("title"), x.optDouble("value").toFloat())
            },
            notes = (0 until n.length()).map { n.getString(it) },
        )
    }

    private fun now() = SimpleDateFormat("dd.MM HH:mm", Locale("ru")).format(Date())

    /** Пока сервер не подключён — показываем демо, чтобы было видно, как всё выглядит. */
    fun demo() = StatsSnapshot(
        updated = now(), demo = true,
        kpis = listOf(
            Kpi("revenue", "Выручка сегодня", "48 200 ₴", 12.4, "💰"),
            Kpi("orders", "Заказы", "37", 8.0, "🛒"),
            Kpi("leads", "Новые лиды", "64", -3.1, "🎯"),
            Kpi("conv", "Конверсия", "5,8 %", 0.6, "⚡"),
            Kpi("views", "Просмотры роликов", "21,4 K", 34.0, "🎬"),
            Kpi("check", "Средний чек", "1 303 ₴", 4.2, "🧾"),
        ),
        series = listOf(
            Series("Выручка, 7 дней", listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"),
                listOf(31f, 36f, 29f, 42f, 45f, 39f, 48f)),
            Series("Просмотры роликов, 7 дней", listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"),
                listOf(9f, 12f, 10f, 15f, 18f, 17f, 21f)),
        ),
        breakdown = listOf(Share("Instagram", 42f), Share("TikTok", 27f), Share("Сайт", 19f), Share("Telegram", 12f)),
        notes = listOf("Подключите свой сервер статистики в Настройках — сейчас показаны демо-цифры."),
    )
}
