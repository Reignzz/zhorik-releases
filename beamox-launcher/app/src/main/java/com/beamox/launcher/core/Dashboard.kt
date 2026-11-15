package com.beamox.launcher.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Живая панель Beamox (тот же data.json, что у «Генератора охватов»):
 * студия роликов, площадки, сессии Claude на Mac/сервере, лимиты, сервисы сервера.
 */
data class ClaudeSession(
    val title: String, val state: String, val doing: String, val model: String,
    val where: String, val since: Long, val updated: Long, val agents: Int,
)

data class Platform(
    val id: String, val name: String, val total: Int, val today: Int,
    val followers: Int, val views: Int, val er: Double?, val v7: Int?,
)

data class TopVideo(val title: String, val platform: String, val views: Int, val likes: Int, val er: Double?, val url: String)
data class StudioEvent(val at: Long, val ok: Boolean, val text: String)
data class Service(val name: String, val active: Boolean)
/** Проекты владельца (сессии/проекты на Mac): «Офис Beamox», «GTA 6 контент-конвейер» и т.д. */
data class Project(val name: String, val status: String, val summary: String, val updated: Long)
data class PanelTask(val id: String, val text: String, val project: String, val done: Boolean, val due: Long?)

data class Dashboard(
    val at: Long,
    // Студия
    val studioName: String,
    val reach: Int?, val er: Double?, val v7: Int?, val v30: Int?, val comments: Int?, val likes: Int?,
    val madeToday: Int, val postedToday: Int, val errorsToday: Int, val readyClips: Int,
    val topicsNew: Int, val topicsMade: Int,
    val jobWhat: String?, val jobSince: Long?, val progressDone: Int?, val progressOf: Int?, val stepWhat: String?,
    val nextRun: Long?,
    val platforms: List<Platform>,
    val days: List<String>,
    val viewsByPlatform: Map<String, List<Int>>,
    val top: List<TopVideo>,
    val events: List<StudioEvent>,
    // Claude
    val sessions: List<ClaudeSession>,
    val weekPct: Double?, val weekReset: Long?, val fivePct: Double?, val fiveReset: Long?, val budgetMode: String?,
    // Машины
    val macOnline: Boolean, val macRamUsed: Double?, val macRamLimit: Double?, val macBattery: Int?, val macCpu: Int?,
    val serverOnline: Boolean, val serverDiskPct: Int?, val services: List<Service>,
    val projects: List<Project> = emptyList(),
    val tasks: List<PanelTask> = emptyList(),
) {
    val working get() = sessions.count { it.state == "working" }
    val waiting get() = sessions.filter { it.state == "waiting" }
    val viewsPerDay: List<Int> get() =
        days.indices.map { i -> viewsByPlatform.values.sumOf { it.getOrElse(i) { 0 } } }

    /** Короткая сводка для Claude и голоса */
    fun summary(): String = buildString {
        append("Студия «$studioName»: охват ${reach ?: "—"}, за 7 дней ${v7 ?: "—"} просмотров, вовлечённость ${er ?: "—"}%, ")
        append("сегодня собрано $madeToday роликов, выложено $postedToday, ошибок $errorsToday, готовых клипов $readyClips. ")
        append("Площадки: " + platforms.joinToString { "${it.name} ${it.total} постов (+${it.today} сегодня), ${it.views} просмотров" } + ". ")
        if (top.isNotEmpty()) append("Лучшие ролики: " + top.take(3).joinToString { "«${it.title}» ${it.views} просм." } + ". ")
        append("Claude: ${sessions.size} сессий, работают $working, ждут подтверждения ${waiting.size}. ")
        weekPct?.let { append("Лимит недели ${it.toInt()}%, 5 часов ${fivePct?.toInt() ?: 0}%. ") }
        append("Сервисы сервера: " + services.joinToString { "${it.name} ${if (it.active) "работает" else "остановлен"}" } + ". ")
        if (projects.isNotEmpty()) append("Проекты: " + projects.joinToString { "${it.name} (${it.status}): ${it.summary}" } + ". ")
        val open = tasks.filter { !it.done }
        if (open.isNotEmpty()) append("Задачи офиса: " + open.take(20).joinToString { "${it.text} [${it.project}]" } + ".")
    }

    /** Переводим в KPI-формат лаунчера (виджеты, голос «какой охват») */
    fun toStats(): StatsSnapshot {
        val df = SimpleDateFormat("dd.MM HH:mm", Locale("ru"))
        fun n(v: Int?) = v?.let { formatNum(it) } ?: "—"
        val shortDays = days.map { it.takeLast(5).let { d -> d.substring(3) + "." + d.substring(0, 2) } }
        return StatsSnapshot(
            updated = df.format(Date(at)), demo = false,
            kpis = listOf(
                Kpi("reach", "Охват", n(reach), null, "👁"),
                Kpi("views", "Просмотры за 7 дней", n(v7), null, "🎬"),
                Kpi("er", "Вовлечённость", er?.let { "%.1f %%".format(it) } ?: "—", null, "⚡"),
                Kpi("posted", "Выложено сегодня", "$postedToday", null, "🚀"),
                Kpi("made", "Собрано роликов сегодня", "$madeToday", null, "✂️"),
                Kpi("ready", "Готовые клипы", "$readyClips", null, "📦"),
                Kpi("comments", "Комментарии", n(comments), null, "💬"),
                Kpi("errors", "Ошибки сегодня", "$errorsToday", null, "⚠️"),
            ),
            series = listOf(Series("Просмотры по дням", shortDays, viewsPerDay.map { it.toFloat() })),
            breakdown = platforms.map { Share(it.name, it.views.toFloat()) },
            notes = events.take(5).map { (if (it.ok) "✓ " else "✗ ") + it.text },
        )
    }

    companion object {
        fun formatNum(v: Int): String = when {
            v >= 1_000_000 -> "%.1f млн".format(v / 1e6).replace(".0", "")
            v >= 10_000 -> "${v / 1000} тыс"
            else -> v.toString()
        }

        private fun JSONObject.intOrNull(k: String): Int? = if (has(k) && !isNull(k)) optInt(k) else null
        private fun JSONObject.dblOrNull(k: String): Double? = if (has(k) && !isNull(k)) optDouble(k) else null
        private fun JSONObject.longOrNull(k: String): Long? = if (has(k) && !isNull(k)) optLong(k) else null
        private fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
        private fun iso(s: String?): Long? = s?.takeIf { it.isNotBlank() }?.let {
            runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
        }

        fun isDashboard(o: JSONObject) = o.has("mac") || o.has("server") || o.has("budget")

        private val platformNames = mapOf("youtube" to "YouTube", "tiktok" to "TikTok", "instagram" to "Instagram",
            "facebook" to "Facebook", "telegram" to "Telegram")

        fun parse(o: JSONObject): Dashboard {
            val mac = o.optJSONObject("mac")
            val srv = o.optJSONObject("server")
            val st = mac?.optJSONObject("studio")
            val ext = st?.optJSONObject("ext")
            val kpi = ext?.optJSONObject("kpi")
            val plat = ext?.optJSONObject("plat")
            val budget = o.optJSONObject("budget") ?: mac?.optJSONObject("budget")
            val macOn = mac?.optBoolean("online", false) == true

            fun sessions(src: JSONObject?, where: String) = src?.optJSONArray("sessions").objs().map {
                ClaudeSession(it.optString("title", "Сессия"), it.optString("state", "idle"), it.optString("doing"),
                    it.optString("model"), where, it.optLong("since"), it.optLong("updated"), it.optInt("agents"))
            }
            val order = mapOf("waiting" to 0, "working" to 1, "idle" to 2)
            val allSessions = ((if (macOn) sessions(mac, "Mac") else emptyList()) + sessions(srv, "Сервер"))
                .sortedWith(compareBy<ClaudeSession> { order[it.state] ?: 3 }.thenByDescending { it.updated })

            val viewsObj = ext?.optJSONObject("views")
            val views = mutableMapOf<String, List<Int>>()
            viewsObj?.keys()?.forEach { k ->
                val a = viewsObj.optJSONArray(k) ?: JSONArray()
                views[platformNames[k] ?: k] = (0 until a.length()).map { a.optInt(it) }
            }
            val daysArr = ext?.optJSONArray("days") ?: JSONArray()
            val job = st?.optJSONObject("job")
            val prog = ext?.optJSONObject("progress")
            val ram = mac?.optJSONObject("ram")
            val topics = st?.optJSONObject("topics")

            return Dashboard(
                at = o.optLong("at", System.currentTimeMillis()),
                studioName = st?.optString("name", "Студия") ?: "Студия",
                reach = kpi?.intOrNull("reach"), er = kpi?.dblOrNull("er"), v7 = kpi?.intOrNull("v7"),
                v30 = kpi?.intOrNull("v30"), comments = kpi?.intOrNull("comments"), likes = kpi?.intOrNull("likes"),
                madeToday = st?.optInt("madeToday") ?: 0, postedToday = st?.optInt("postedToday") ?: 0,
                errorsToday = st?.optInt("errorsToday") ?: 0, readyClips = st?.optInt("readyClips") ?: 0,
                topicsNew = topics?.optInt("new") ?: 0, topicsMade = topics?.optInt("made") ?: 0,
                jobWhat = job?.optString("what")?.takeIf { it.isNotBlank() }, jobSince = job?.longOrNull("since"),
                progressDone = job?.optJSONObject("progress")?.intOrNull("done")
                    ?: prog?.takeIf { it.optBoolean("active") }?.intOrNull("done"),
                progressOf = job?.optJSONObject("progress")?.intOrNull("of")
                    ?: prog?.takeIf { it.optBoolean("active") }?.intOrNull("total"),
                stepWhat = st?.optJSONObject("step")?.optString("what"),
                nextRun = st?.longOrNull("nextRun"),
                platforms = st?.optJSONArray("platforms").objs().map { p ->
                    val id = p.optString("id")
                    val e = plat?.optJSONObject(id)
                    Platform(id, p.optString("name", platformNames[id] ?: id), p.optInt("total"), p.optInt("today"),
                        maxOf(p.optInt("followers"), ext?.optJSONObject("followers")?.optJSONObject(id)?.optInt("now") ?: 0),
                        e?.optInt("views") ?: p.optInt("views"), e?.dblOrNull("er"), e?.intOrNull("v7"))
                },
                days = (0 until daysArr.length()).map { daysArr.optString(it) },
                viewsByPlatform = views,
                top = ext?.optJSONArray("top").objs().map {
                    TopVideo(it.optString("t"), platformNames[it.optString("p")] ?: it.optString("p"), it.optInt("v"),
                        it.optInt("l"), it.dblOrNull("er"), it.optString("u"))
                },
                events = (st?.optJSONArray("allEvents") ?: st?.optJSONArray("events")).objs().map {
                    StudioEvent(it.optLong("at"), it.optString("kind") != "err", it.optString("text"))
                },
                sessions = allSessions,
                weekPct = budget?.dblOrNull("weekPct"), weekReset = iso(budget?.optString("weekReset")),
                fivePct = budget?.dblOrNull("fivePct"), fiveReset = iso(budget?.optString("fiveReset")),
                budgetMode = budget?.optString("mode"),
                macOnline = macOn, macRamUsed = ram?.dblOrNull("used"), macRamLimit = ram?.dblOrNull("limit"),
                macBattery = mac?.optJSONObject("battery")?.intOrNull("pct"), macCpu = mac?.intOrNull("cpu"),
                serverOnline = srv != null, serverDiskPct = srv?.optJSONObject("disk")?.intOrNull("usedPct"),
                services = srv?.optJSONArray("services").objs().map { Service(it.optString("name"), it.optString("state") == "active") },
                projects = o.optJSONArray("projects").objs().map {
                    Project(it.optString("name"), it.optString("status", "idle"), it.optString("summary"), it.optLong("updated"))
                },
                tasks = o.optJSONArray("tasks").objs().map {
                    PanelTask(it.optString("id"), it.optString("text"), it.optString("project"), it.optBoolean("done"), it.longOrNull("due"))
                },
            )
        }

        /** Принимает и ссылку на страницу панели (…/c/<id>/), и прямую ссылку на data.json */
        fun dataUrl(url: String): String {
            val u = url.trim()
            if (u.isBlank() || u.endsWith(".json")) return u
            return u.trimEnd('/') + "/data.json"
        }

        private val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

        /** Поставить задачу в офис Beamox: POST <база панели>/task {"text","project"} (+ Bearer-токен) */
        suspend fun postTask(panelUrl: String, token: String, text: String, project: String): Result<Unit> = withContext(Dispatchers.IO) {
            runCatching {
                val base = dataUrl(panelUrl).substringBeforeLast("/")
                val body = JSONObject().put("text", text).put("project", project).toString()
                    .toRequestBody("application/json".toMediaType())
                val rb = Request.Builder().url("$base/task").post(body)
                if (token.isNotBlank()) rb.header("Authorization", "Bearer $token")
                http.newCall(rb.build()).execute().use { r -> if (!r.isSuccessful) error("HTTP ${r.code}") }
            }
        }

        suspend fun fetch(url: String): Result<Pair<Dashboard, String>> = withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(Request.Builder().url(dataUrl(url)).header("Cache-Control", "no-store").build()).execute().use { r ->
                    if (!r.isSuccessful) error("HTTP ${r.code}")
                    val body = r.body!!.string()
                    parse(JSONObject(body)) to body
                }
            }
        }
    }
}
