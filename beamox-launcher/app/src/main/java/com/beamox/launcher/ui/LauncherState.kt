package com.beamox.launcher.ui

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
import com.beamox.launcher.core.AppEntry
import com.beamox.launcher.core.AppRepository
import com.beamox.launcher.core.BeamoxConfig
import com.beamox.launcher.core.ChatMessage
import com.beamox.launcher.core.ClaudeService
import com.beamox.launcher.core.Dashboard
import com.beamox.launcher.core.DeviceInfo
import com.beamox.launcher.core.MailService
import com.beamox.launcher.core.MailboxStatus
import com.beamox.launcher.core.OfficeTask
import com.beamox.launcher.core.Prefs
import com.beamox.launcher.core.Secrets
import com.beamox.launcher.core.Social
import com.beamox.launcher.core.StatsRepository
import com.beamox.launcher.core.Tasks
import com.beamox.launcher.voice.CommandRouter
import com.beamox.launcher.voice.VoiceAction
import com.beamox.launcher.voice.VoiceEngine
import com.beamox.launcher.voice.VoiceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/** Всё состояние лаунчера в одном месте. */
class LauncherState(
    val activity: ComponentActivity?,
    appContext: android.content.Context,
    val scope: CoroutineScope,
    /** Режим предпросмотра (скриншоты): без сети, ключей и системных вызовов */
    val preview: Boolean = false,
) {
    val ctx: android.content.Context = appContext
    /** Контекст для запуска экранов */
    val act: android.content.Context get() = activity ?: ctx
    val prefs = Prefs(ctx)
    val secrets = Secrets(ctx)

    var config by mutableStateOf(BeamoxConfig.load(ctx))

    // Приложения
    val appsRepo = AppRepository(ctx)
    var apps by mutableStateOf<List<AppEntry>>(emptyList())
    var hidden by mutableStateOf(prefs.hiddenApps)
    var dock by mutableStateOf(prefs.dock)

    // Навигация
    var screen by mutableStateOf<Module?>(null)
    var homeRequest by mutableIntStateOf(0)
    /** Стартовая страница (для предпросмотра) */
    var previewPage = 1
    var searchOpen by mutableStateOf(false)
    var controlOpen by mutableStateOf(false)
    var cleanupOpen by mutableStateOf(false)

    // Статистика
    val statsRepo = StatsRepository(prefs, secrets) { config }
    var stats by mutableStateOf(statsRepo.cached())
    var statsLoading by mutableStateOf(false)

    // Живая панель Beamox
    var dash by mutableStateOf<Dashboard?>(prefs.lastDashJson?.let { runCatching { Dashboard.parse(org.json.JSONObject(it)) }.getOrNull() })
    var dashError by mutableStateOf<String?>(null)

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
    /** Что ещё не настроено: главный экран, жесты, островок, панель (в предпросмотре — всё «готово») */
    var setup by mutableStateOf(if (preview) com.beamox.launcher.core.SetupState()
        else com.beamox.launcher.core.PhoneSetup.read(ctx, prefs.dashboardUrl))

    // Голос
    val voice by lazy { VoiceEngine(act) }
    var voiceOpen by mutableStateOf(false)
    var voiceListening by mutableStateOf(false)
    var voiceHeard by mutableStateOf("")
    var voiceAnswer by mutableStateOf("")
    var voiceLevel by mutableStateOf(0f)
    var handsFree by mutableStateOf(prefs.handsFree)

    fun claudeModel() = prefs.claudeModel ?: config.claudeModel

    fun reloadConfig() { config = BeamoxConfig.load(ctx) }

    // Набор иконок (iOS-стиль из Google Play)
    var iconPackPkg by mutableStateOf(prefs.iconPack)
    var sysIcons by mutableStateOf<Map<SysApp, androidx.compose.ui.graphics.ImageBitmap>>(emptyMap())

    fun setIconPack(pkg: String?) {
        prefs.iconPack = pkg; iconPackPkg = pkg
        appsRepo.invalidate(); reloadApps()
    }

    fun reloadApps() = scope.launch {
        if (preview) return@launch
        val pack = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            iconPackPkg?.let { p -> com.beamox.launcher.core.IconPack(ctx, p).takeIf { it.load() } }
        }
        apps = appsRepo.load(pack)
        // Системные разделы (Телефон, Сообщения, Камера…) берут иконку из набора по реальному приложению
        sysIcons = if (pack == null) emptyMap() else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            SysApp.entries.mapNotNull { a ->
                val ri = runCatching { ctx.packageManager.resolveActivity(a.intent(), 0) }.getOrNull()
                    ?.takeIf { it.activityInfo.packageName != "android" } ?: return@mapNotNull null
                pack.icon(android.content.ComponentName(ri.activityInfo.packageName, ri.activityInfo.name), 192)?.let { a to it }
            }.toMap()
        }
        // Первый запуск: сразу прячем рекламный мусор из библиотеки (удалить — в «Очистке»)
        if (!prefs.bloatScanned) {
            val junk = com.beamox.launcher.core.Bloat.detect(ctx, apps).map { it.pkg }.toSet()
            if (junk.isNotEmpty()) { hidden = hidden + junk; prefs.hiddenApps = hidden }
            prefs.bloatScanned = true
        }
    }

    fun goHome() { screen = null; searchOpen = false; controlOpen = false; homeRequest++ }

    fun open(m: Module) {
        searchOpen = false
        haptic()
        when {
            m == Module.VOICE -> startVoice()
            m == Module.CLAUDE && useClaudeApp() -> openClaudeApp(claudeDraft.takeIf { it.isNotBlank() }?.let { withBeamoxData(it) }).also { claudeDraft = "" }
            else -> screen = m
        }
    }

    // ---------- Claude через официальное приложение (аккаунт claude.ai) ----------
    val claudePkg = "com.anthropic.claude"
    var claudeViaApp by mutableStateOf(prefs.claudeViaApp)
    fun claudeAppInstalled() = runCatching { ctx.packageManager.getPackageInfo(claudePkg, 0); true }.getOrDefault(false)
    fun useClaudeApp() = claudeViaApp && (preview || claudeAppInstalled() || secrets.claudeKey.isBlank())

    /** Вопрос + живые данные Beamox с телефона, чтобы Claude в вашем аккаунте отвечал по делу */
    fun withBeamoxData(q: String) = q + "\n\n— Данные Beamox с рабочего телефона:\n" + officeContext()

    /** Открыть Claude под вашим аккаунтом; если передан текст — он сразу вставится в новый чат */
    fun openClaudeApp(text: String? = null) {
        if (preview) return
        if (!claudeAppInstalled()) {
            toast("Установите приложение Claude и войдите в свой аккаунт")
            if (!AppRepository.safeStart(act, Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$claudePkg"))))
                openUrl("https://play.google.com/store/apps/details?id=$claudePkg")
            return
        }
        if (!text.isNullOrBlank()) {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(claudePkg).putExtra(Intent.EXTRA_TEXT, text)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { act.startActivity(send); true }.getOrDefault(false)) return
        }
        appsRepo.launchPackage(claudePkg)
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

    fun refreshDevice() {
        device = DeviceInfo.read(ctx)
        if (!preview) setup = com.beamox.launcher.core.PhoneSetup.read(ctx, prefs.dashboardUrl)
    }

    /** Вопрос в официальное приложение Claude / ChatGPT / Gemini (вход под аккаунтом владельца) */
    fun askAi(app: com.beamox.launcher.core.AiApp, text: String) {
        haptic()
        val msg = com.beamox.launcher.core.AiApps.ask(act, app, text)
        if (msg.isNotBlank()) toast(msg)
    }

    fun refreshStats() = scope.launch {
        if (preview) return@launch
        statsLoading = true
        val url = prefs.dashboardUrl
        if (!url.isNullOrBlank()) {
            Dashboard.fetch(url).onSuccess { (d, body) ->
                dash = d; dashError = null; stats = d.toStats(); prefs.lastDashJson = body
            }.onFailure { dashError = "Панель недоступна: ${it.message}" }
        } else {
            stats = statsRepo.refresh()
        }
        statsLoading = false
    }

    init { dash?.let { stats = it.toStats() } }

    /** Ссылка на панель из буфера обмена (кнопка «Вставить ссылку») */
    fun pastePanelLink() {
        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim().orEmpty()
        if (!t.startsWith("https://")) { toast("Скопируйте ссылку на панель (https://…) и нажмите ещё раз"); return }
        setPanelLink(t)
    }

    fun setPanelLink(url: String) {
        prefs.dashboardUrl = url.trim()
        toast("Панель подключена, загружаю…")
        refreshStats()
    }

    fun refreshMail() = scope.launch {
        if (preview) return@launch
        val boxes = config.mailboxes.filter { secrets.mailPassword(it.address).isNotBlank() }
        mailStatus = boxes.map { async { mail.status(it) } }.awaitAll()
    }

    val unreadTotal: Int? get() = mailStatus.mapNotNull { it.unread }.takeIf { it.isNotEmpty() }?.sum()

    // ---------- Задачи ----------
    fun addTask(text: String, project: String = "Офис Beamox") {
        tasks.add(0, OfficeTask(System.currentTimeMillis(), text, false)); Tasks.save(prefs, tasks)
        // Если панель подключена — задача уходит в офис Beamox на Mac/сервер
        val url = prefs.dashboardUrl
        if (!preview && !url.isNullOrBlank()) scope.launch {
            Dashboard.postTask(url, secrets.statsToken, text, project)
                .onSuccess { toast("Задача поставлена в офис Beamox") }
                .onFailure { toast("Задача сохранена на телефоне (офис пока не принимает задачи)") }
        }
    }
    fun toggleTask(t: OfficeTask) {
        val i = tasks.indexOfFirst { it.id == t.id }
        if (i >= 0) { tasks[i] = t.copy(done = !t.done); Tasks.save(prefs, tasks) }
    }
    fun deleteTask(t: OfficeTask) { tasks.removeAll { it.id == t.id }; Tasks.save(prefs, tasks) }

    // ---------- Claude ----------
    fun officeContext(): String = buildString {
        dash?.let { appendLine("Панель Beamox (живые данные): ${it.summary()}") } ?: appendLine("Статистика: ${stats.summary()}")
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
        AppRepository.safeStart(act, Intent.createChooser(i, "Отправить письмо"))
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

    fun requestMic() { activity?.let { ActivityCompat.requestPermissions(it,
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), 77) } }

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
        com.beamox.launcher.core.Sounds.listen()
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
            is VoiceAction.Camera -> { closeVoice(); AppRepository.safeStart(act, AppRepository.cameraIntent) }
            is VoiceAction.Home -> { closeVoice(); goHome() }
            is VoiceAction.Call -> {
                closeVoice()
                val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                val i = Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:" + a.number))
                AppRepository.safeStart(act, i)
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
            is VoiceAction.AskClaude -> if (useClaudeApp()) {
                closeVoice(); openClaudeApp(withBeamoxData(a.text))
            } else {
                voiceAnswer = "Думаю…"
                sendClaude(a.text + "\n\n(Вопрос задан голосом — ответь коротко, 1–4 предложения, без форматирования.)") { ans ->
                    say(ans)
                }
            }
        }
    }

    fun openUrl(url: String) {
        if (url.isBlank()) return
        AppRepository.safeStart(act, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    /** Соцсеть компании: сначала в приложении соцсети, если его нет — в браузере. */
    fun openSocial(so: Social) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(so.url))
        if (so.pkg.isNotBlank() && apps.any { it.pkg == so.pkg }) {
            try { act.startActivity(Intent(i).setPackage(so.pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return }
            catch (_: Exception) { }
        }
        AppRepository.safeStart(act, i)
    }

    fun haptic() {
        activity?.window?.decorView?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun toast(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show()
}
