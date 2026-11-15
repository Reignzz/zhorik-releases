package com.biamox.launcher.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.biamox.launcher.core.AppEntry
import com.biamox.launcher.core.AppRepository
import com.biamox.launcher.core.BiamoxConfig
import com.biamox.launcher.core.ChatMessage
import com.biamox.launcher.core.ClaudeService
import com.biamox.launcher.core.DeviceInfo
import com.biamox.launcher.core.MailService
import com.biamox.launcher.core.MailboxStatus
import com.biamox.launcher.core.OfficeTask
import com.biamox.launcher.core.Prefs
import com.biamox.launcher.core.Secrets
import com.biamox.launcher.core.Social
import com.biamox.launcher.core.StatsRepository
import com.biamox.launcher.core.Tasks
import com.biamox.launcher.voice.CommandRouter
import com.biamox.launcher.voice.VoiceAction
import com.biamox.launcher.voice.VoiceEngine
import com.biamox.launcher.voice.VoiceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/** Всё состояние лаунчера в одном месте. */
class LauncherState(val activity: ComponentActivity, val scope: CoroutineScope) {
    val ctx = activity.applicationContext
    val prefs = Prefs(ctx)
    val secrets = Secrets(ctx)

    var config by mutableStateOf(BiamoxConfig.load(ctx))

    // Приложения
    val appsRepo = AppRepository(ctx)
    var apps by mutableStateOf<List<AppEntry>>(emptyList())
    var hidden by mutableStateOf(prefs.hiddenApps)
    var dock by mutableStateOf(prefs.dock)

    // Навигация
    var screen by mutableStateOf<Module?>(null)
    var homeRequest by mutableIntStateOf(0)
    var searchOpen by mutableStateOf(false)
    var controlOpen by mutableStateOf(false)

    // Статистика
    val statsRepo = StatsRepository(prefs, secrets) { config }
    var stats by mutableStateOf(statsRepo.cached())
    var statsLoading by mutableStateOf(false)

    // Claude
    val claude = ClaudeService(secrets)
    val chat = mutableStateListOf<ChatMessage>().apply { addAll(ClaudeService.loadHistory(prefs)) }
    var claudeBusy by mutableStateOf(false)
    var claudeDraft by mutableStateOf("")

    // Почта
    val mail = MailService(secrets)
    var mailStatus by mutableStateOf<List<MailboxStatus>>(emptyList())

    // Задачи
    val tasks = mutableStateListOf<OfficeTask>().apply { addAll(Tasks.load(prefs)) }

    // Устройство
    var device by mutableStateOf(DeviceInfo.read(ctx))

    // Голос
    val voice by lazy { VoiceEngine(activity) }
    var voiceOpen by mutableStateOf(false)
    var voiceListening by mutableStateOf(false)
    var voiceHeard by mutableStateOf("")
    var voiceAnswer by mutableStateOf("")
    var voiceLevel by mutableStateOf(0f)
    var handsFree by mutableStateOf(prefs.handsFree)

    fun claudeModel() = prefs.claudeModel ?: config.claudeModel

    fun reloadConfig() { config = BiamoxConfig.load(ctx) }

    fun reloadApps() = scope.launch { apps = appsRepo.load() }

    fun goHome() { screen = null; searchOpen = false; controlOpen = false; homeRequest++ }

    fun open(m: Module) {
        searchOpen = false
        haptic()
        if (m == Module.VOICE) startVoice() else screen = m
    }

    fun launch(app: AppEntry) { searchOpen = false; haptic(); appsRepo.launch(app) }

    fun setHidden(pkg: String, hide: Boolean) {
        hidden = if (hide) hidden + pkg else hidden - pkg
        prefs.hiddenApps = hidden
    }

    fun setDockSlot(index: Int, value: String) {
        val d = dock.toMutableList()
        while (d.size < 4) d.add("")
        d[index] = value
        dock = d; prefs.dock = d
    }

    fun refreshDevice() { device = DeviceInfo.read(ctx) }

    fun refreshStats() = scope.launch {
        statsLoading = true
        stats = statsRepo.refresh()
        statsLoading = false
    }

    fun refreshMail() = scope.launch {
        val boxes = config.mailboxes.filter { secrets.mailPassword(it.address).isNotBlank() }
        mailStatus = boxes.map { async { mail.status(it) } }.awaitAll()
    }

    val unreadTotal: Int? get() = mailStatus.mapNotNull { it.unread }.takeIf { it.isNotEmpty() }?.sum()

    // ---------- Задачи ----------
    fun addTask(text: String) {
        tasks.add(0, OfficeTask(System.currentTimeMillis(), text, false)); Tasks.save(prefs, tasks)
    }
    fun toggleTask(t: OfficeTask) {
        val i = tasks.indexOfFirst { it.id == t.id }
        if (i >= 0) { tasks[i] = t.copy(done = !t.done); Tasks.save(prefs, tasks) }
    }
    fun deleteTask(t: OfficeTask) { tasks.removeAll { it.id == t.id }; Tasks.save(prefs, tasks) }

    // ---------- Claude ----------
    fun officeContext(): String = buildString {
        appendLine("Статистика: ${stats.summary()}")
        val open = tasks.filter { !it.done }
        appendLine("Открытые задачи (${open.size}): ${open.take(15).joinToString("; ") { it.text }.ifBlank { "нет" }}")
        appendLine("Почтовые ящики: ${config.mailboxes.joinToString { "${it.name} <${it.address}>" }}")
        if (mailStatus.isNotEmpty()) appendLine("Непрочитанных писем: ${mailStatus.joinToString { "${it.mailbox.name}: ${it.unread ?: "?"}" }}")
        appendLine("Соцсети компании: ${config.socials.joinToString { "${it.title}: ${it.url}" }}")
        appendLine("Команда: ${config.team.joinToString { "${it.name} (${it.role})" }}")
        val d = device
        appendLine("Телефон: батарея ${d.batteryPct}%, свободно ${"%.1f".format(d.storageFreeGb)} ГБ")
    }

    fun sendClaude(text: String, onAnswer: (String) -> Unit = {}) {
        if (text.isBlank() || claudeBusy) return
        chat.add(ChatMessage("user", text.trim()))
        claudeBusy = true
        scope.launch {
            val ans = claude.ask(claudeModel(), chat.toList(), officeContext(), config.claudeSystemExtra)
            chat.add(ChatMessage("assistant", ans))
            ClaudeService.saveHistory(prefs, chat)
            claudeBusy = false
            onAnswer(ans)
        }
    }

    fun clearChat() { chat.clear(); ClaudeService.saveHistory(prefs, chat) }

    fun composeMail(subject: String, body: String, to: String = "") {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(to)))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body)
        AppRepository.safeStart(activity, Intent.createChooser(i, "Отправить письмо"))
    }

    /** Разбирает ответ Claude вида «Тема: …\n\nтекст» */
    fun splitLetter(ans: String): Pair<String, String> {
        val lines = ans.lines()
        val idx = lines.indexOfFirst { it.trim().lowercase().startsWith("тема:") }
        if (idx < 0) return "" to ans
        val subject = lines[idx].substringAfter(":").trim().trim('*')
        val body = lines.drop(idx + 1).joinToString("\n").trim()
        return subject to body
    }

    // ---------- Голос ----------
    fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun requestMic() = ActivityCompat.requestPermissions(activity,
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), 77)

    fun setHandsFreeMode(on: Boolean) {
        if (on && !hasMic()) { requestMic(); return }
        handsFree = on; prefs.handsFree = on
        if (on) VoiceService.start(ctx) else VoiceService.stop(ctx)
    }

    /** Кнопка микрофона / кнопка ассистента: слушаем одну команду. */
    fun startVoice() {
        if (!hasMic()) { requestMic(); return }
        VoiceService.instance?.pause()
        voice.stopSpeaking()
        voiceOpen = true
        voiceHeard = ""
        voiceAnswer = ""
        voiceListening = true
        com.biamox.launcher.core.Sounds.listen()
        voice.listenOnce(
            onPartial = { voiceHeard = it },
            onLevel = { voiceLevel = it },
            longSilence = true,
        ) { text, _ ->
            voiceListening = false
            voiceLevel = 0f
            if (text.isNullOrBlank()) voiceAnswer = "Не расслышал. Нажмите на микрофон и повторите."
            else handleVoiceText(VoiceEngine.stripWakeWord(text, config.wakeWords)?.ifBlank { null } ?: text)
        }
    }

    fun closeVoice() {
        voice.cancelListening()
        voice.stopSpeaking()
        voiceOpen = false
        voiceListening = false
        VoiceService.instance?.resume()
    }

    private fun say(text: String, then: (() -> Unit)? = null) {
        voiceAnswer = text
        if (prefs.speakAnswers) voice.speak(text) { then?.invoke() } else then?.invoke()
    }

    /** Выполнить голосовую команду (из кнопки, из режима «без рук» или из кнопки ассистента). */
    fun handleVoiceText(text: String) {
        voiceOpen = true
        voiceHeard = text
        val low = text.lowercase()
        if (low.contains("пункт управления") || low.contains("фонарик")) {
            closeVoice()
            if (low.contains("фонарик")) ControlState.setTorch(ctx, !(low.contains("выключ")))
            else controlOpen = true
            return
        }
        when (val a = CommandRouter.route(text, config, apps.filter { it.pkg !in hidden }, stats)) {
            is VoiceAction.OpenModule -> {
                screen = Module.valueOf(a.module)
                voiceOpen = false
                if (prefs.speakAnswers) voice.speak(a.say)
                VoiceService.instance?.resume()
            }
            is VoiceAction.OpenApp -> { closeVoice(); appsRepo.launch(a.app) }
            is VoiceAction.OpenSocial -> { closeVoice(); openSocial(a.social) }
            is VoiceAction.OpenSys -> { closeVoice(); runCatching { openSys(SysApp.valueOf(a.name)) } }
            is VoiceAction.Camera -> { closeVoice(); AppRepository.safeStart(activity, AppRepository.cameraIntent) }
            is VoiceAction.Home -> { closeVoice(); goHome() }
            is VoiceAction.Call -> {
                closeVoice()
                val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                val i = Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:" + a.number))
                AppRepository.safeStart(activity, i)
            }
            is VoiceAction.AddTask -> { addTask(a.text); say("Задача добавлена: ${a.text}") }
            is VoiceAction.SayText -> say(a.text)
            is VoiceAction.ComposeMail -> {
                voiceAnswer = "Claude пишет черновик письма…"
                sendClaude("Составь деловое письмо по просьбе: «${a.request}». Первая строка — «Тема: …».") { ans ->
                    val (subj, body) = splitLetter(ans)
                    say("Черновик готов, открываю почту") { }
                    composeMail(subj, body)
                }
            }
            is VoiceAction.AskClaude -> {
                voiceAnswer = "Думаю…"
                sendClaude(a.text + "\n\n(Вопрос задан голосом — ответь коротко, 1–4 предложения, без форматирования.)") { ans ->
                    say(ans)
                }
            }
        }
    }

    fun openUrl(url: String) {
        if (url.isBlank()) return
        AppRepository.safeStart(activity, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    /** Соцсеть компании: сначала в приложении соцсети, если его нет — в браузере. */
    fun openSocial(so: Social) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(so.url))
        if (so.pkg.isNotBlank() && apps.any { it.pkg == so.pkg }) {
            try { activity.startActivity(Intent(i).setPackage(so.pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return }
            catch (_: Exception) { }
        }
        AppRepository.safeStart(activity, i)
    }

    fun haptic() {
        activity.window?.decorView?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun toast(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show()
}
