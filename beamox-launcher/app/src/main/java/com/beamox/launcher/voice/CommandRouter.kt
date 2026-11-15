package com.beamox.launcher.voice

import com.beamox.launcher.core.AppEntry
import com.beamox.launcher.core.BeamoxConfig
import com.beamox.launcher.core.Social
import com.beamox.launcher.core.StatsSnapshot

/** Что сделать по голосовой команде. */
sealed class VoiceAction {
    data class OpenModule(val module: String, val say: String) : VoiceAction()
    data class OpenApp(val app: AppEntry) : VoiceAction()
    data class OpenSocial(val social: Social) : VoiceAction()
    data class Call(val number: String, val name: String) : VoiceAction()
    data object Camera : VoiceAction()
    data class OpenSys(val name: String, val say: String) : VoiceAction()
    data object Home : VoiceAction()
    data class AddTask(val text: String) : VoiceAction()
    data class SayText(val text: String) : VoiceAction()
    data class ComposeMail(val request: String) : VoiceAction()
    data class AskClaude(val text: String) : VoiceAction()
}

object CommandRouter {
    private val openVerbs = listOf("открой", "открыть", "покажи", "показать", "запусти", "запустить",
        "зайди в", "зайди", "перейди в", "перейди", "включи")

    private val modules = listOf(
        listOf("офис", "кабинет") to ("OFFICE" to "Открываю офис"),
        listOf("статист", "аналит", "цифр", "дашборд") to ("STATS" to "Открываю статистику"),
        listOf("студи", "ролик", "видео") to ("STUDIO" to "Открываю студию роликов"),
        listOf("почт", "письм", "ящик") to ("MAIL" to "Открываю почту"),
        listOf("настройк") to ("SETTINGS" to "Открываю настройки"),
        listOf("клод", "claude", "ассистент", "чат") to ("CLAUDE" to "Открываю Claude"),
        listOf("задач", "дела") to ("OFFICE" to "Открываю задачи"),
    )

    private val sysApps = listOf(
        listOf("смс", "сообщени", "эсэмэс", "месседж") to "MESSAGES",
        listOf("настройки телефона", "системные настройки", "настройки андроид", "настройки системы") to "SYSTEM_SETTINGS",
        listOf("телефон", "звонилк", "набор номера") to "PHONE",
        listOf("контакт") to "CONTACTS",
        listOf("фото", "галере") to "PHOTOS",
        listOf("часы", "будильник", "таймер") to "CLOCK",
        listOf("календар") to "CALENDAR",
        listOf("калькулятор") to "CALCULATOR",
        listOf("браузер", "интернет") to "BROWSER",
        listOf("карты", "навигатор") to "MAPS",
        listOf("файлы", "загрузки") to "FILES",
        listOf("музык") to "MUSIC",
    )

    private fun matchSys(target: String): String? =
        sysApps.firstOrNull { (keys, _) -> keys.any { target.startsWith(it) || target.contains(" $it") } }?.second

    fun route(raw: String, config: BeamoxConfig, apps: List<AppEntry>, stats: StatsSnapshot?): VoiceAction {
        val t = raw.lowercase().trim().trimEnd('.', '!', '?')

        if (t == "домой" || t.contains("на главн") || t.contains("рабочий стол")) return VoiceAction.Home

        // Задачи
        for (p in listOf("добавь задачу", "новая задача", "запиши задачу", "напомни", "задача")) {
            if (t.startsWith(p)) {
                val rest = t.removePrefix(p).trim(' ', ':', ',')
                if (rest.isNotBlank()) return VoiceAction.AddTask(rest.replaceFirstChar { it.uppercase() })
            }
        }

        // Звонки
        for (p in listOf("позвони", "набери", "вызови")) {
            if (t.startsWith(p)) {
                val rest = t.removePrefix(p).trim()
                val digits = rest.filter { it.isDigit() || it == '+' }
                if (digits.count { it.isDigit() } >= 5) return VoiceAction.Call(digits, digits)
                val m = config.team.firstOrNull { tm ->
                    tm.phone.isNotBlank() && (rest.contains(tm.name.lowercase().take(5)) ||
                        tm.role.isNotBlank() && rest.contains(tm.role.lowercase().take(5)))
                }
                if (m != null) return VoiceAction.Call(m.phone, m.name)
                return VoiceAction.SayText("Не нашёл контакт «$rest» в команде Beamox. Добавьте телефон в настройках команды.")
            }
        }

        // Письма — черновик пишет Claude, отправка через почтовое приложение
        if (listOf("напиши письмо", "составь письмо", "отправь письмо", "письмо для", "письмо клиенту").any { t.startsWith(it) })
            return VoiceAction.ComposeMail(raw)

        // Открыть раздел / приложение
        val verb = openVerbs.firstOrNull { t.startsWith(it) }
        if (verb != null) {
            val target = t.removePrefix(verb).trim()
            if (target.startsWith("камер")) return VoiceAction.Camera
            matchSys(target)?.let { return VoiceAction.OpenSys(it, "Открываю") }
            modules.firstOrNull { (keys, _) -> keys.any { target.contains(it) } }?.let { (_, m) ->
                return VoiceAction.OpenModule(m.first, m.second)
            }
            findSocial(target, config)?.let { return VoiceAction.OpenSocial(it) }
            findApp(target, apps)?.let { return VoiceAction.OpenApp(it) }
            return VoiceAction.SayText("Не нашёл «$target» на телефоне")
        }
        // Короткая команда без глагола: «офис», «почта», «телеграм»
        if (t.split(" ").size <= 3) {
            matchSys(t)?.let { return VoiceAction.OpenSys(it, "Открываю") }
            modules.firstOrNull { (keys, _) -> keys.any { t.startsWith(it) } }?.let { (_, m) ->
                return VoiceAction.OpenModule(m.first, m.second)
            }
            findSocial(t, config)?.let { return VoiceAction.OpenSocial(it) }
            findApp(t, apps)?.let { return VoiceAction.OpenApp(it) }
        }
        if (t.startsWith("камер") || t == "сфотографируй") return VoiceAction.Camera

        // Быстрые ответы по статистике без интернета
        if (stats != null && listOf("сколько", "какая", "какой", "какие", "покажи").any { t.startsWith(it) }) {
            val hits = stats.kpis.filter { k ->
                val stem = k.title.lowercase().split(" ").first().take(5)
                stem.length >= 4 && t.contains(stem)
            }
            if (hits.isNotEmpty()) {
                val s = hits.joinToString(". ") { k ->
                    val d = k.delta?.let { if (it >= 0) ", рост ${"%.0f".format(it)} процентов" else ", падение ${"%.0f".format(-it)} процентов" } ?: ""
                    "${k.title}: ${k.value}$d"
                }
                return VoiceAction.SayText(if (stats.demo) "$s. Это демо-данные." else s)
            }
        }

        return VoiceAction.AskClaude(raw)
    }

    private val aliases = mapOf(
        "телеграм" to "telegram", "ватсап" to "whatsapp", "вотсап" to "whatsapp", "вайбер" to "viber",
        "хром" to "chrome", "браузер" to "chrome", "ютуб" to "youtube", "карты" to "maps",
        "гмейл" to "gmail", "джимейл" to "gmail", "календарь" to "calendar", "фото" to "photos",
        "галерея" to "photos", "диск" to "drive", "инстаграм" to "instagram", "тикток" to "tiktok",
        "плей маркет" to "play", "маркет" to "play", "калькулятор" to "calculator", "часы" to "clock",
        "контакты" to "contacts", "сообщения" to "messages", "джемини" to "gemini", "гемини" to "gemini",
    )

    /** «открой наш инстаграм» → страница компании в соцсети */
    fun findSocial(target: String, config: BeamoxConfig): Social? {
        val q = aliases.entries.firstOrNull { target.contains(it.key) }?.value ?: target
        return config.socials.firstOrNull { q.contains(it.title.lowercase()) || it.title.lowercase().contains(q) }
    }

    fun findApp(target: String, apps: List<AppEntry>): AppEntry? {
        if (target.isBlank()) return null
        val q = aliases.entries.firstOrNull { target.contains(it.key) }?.value ?: target
        return apps.firstOrNull { it.label.lowercase() == q }
            ?: apps.firstOrNull { it.label.lowercase().startsWith(q) }
            ?: apps.firstOrNull { it.label.lowercase().contains(q) }
            ?: apps.firstOrNull { it.pkg.lowercase().contains(q.replace(" ", "")) }
    }
}
