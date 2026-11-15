package com.beamox.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamox.launcher.core.MailHeader
import com.beamox.launcher.core.Mailbox
import kotlinx.coroutines.launch

@Composable
fun MailScreen(s: LauncherState) {
    var box by remember { mutableStateOf<Mailbox?>(null) }
    var msgs by remember { mutableStateOf<List<MailHeader>>(emptyList()) }
    var open by remember { mutableStateOf<MailHeader?>(null) }
    var body by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadBox(mb: Mailbox) {
        box = mb; msgs = emptyList(); error = null; loading = true
        scope.launch {
            runCatching { s.mail.latest(mb) }.onSuccess { msgs = it }.onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(Unit) { s.refreshMail() }
    BackHandler(enabled = box != null) { if (open != null) open = null else box = null }

    val title = open?.subject?.take(28) ?: box?.name ?: "Почта"
    ModuleSheet(title, onBack = { when { open != null -> open = null; box != null -> box = null; else -> s.goHome() } }, actions = {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        IconButton(onClick = { box?.let { loadBox(it) } ?: s.refreshMail() }) { Icon(Icons.Default.Refresh, null, tint = B.Blue) }
        Text("✏️", fontSize = 22.sp, modifier = Modifier.padding(end = 14.dp).clickableNoRipple { s.composeMail("", "") })
    }) {
        val o = open; val mb = box
        when {
            o != null && mb != null -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Text(o.from, color = Color.White, fontSize = 16.sp)
                Text(o.date, color = B.Gray, fontSize = 13.sp)
                VSpace(14.dp)
                Text(body.ifBlank { "Загрузка…" }, color = Color.White, fontSize = 15.sp)
                VSpace(18.dp)
                androidx.compose.foundation.layout.Row(horizontalArrangement = Gap) {
                    Pill("↩️ Ответить") { s.composeMail("Re: ${o.subject}", "") }
                    Pill("✳️ Ответ с Claude", B.Violet) {
                        s.claudeDraft = "Составь вежливый ответ на письмо от ${o.from}, тема «${o.subject}». Текст письма:\n${body.take(4000)}\n\nПервая строка — «Тема: Re: …»."
                        s.open(Module.CLAUDE)
                    }
                }
                VSpace(40.dp)
            }
            mb != null -> {
                error?.let { Hint("⚠️ $it") }
                LazyColumn(contentPadding = ListPadding) {
                    items(msgs, key = { it.uid }) { m ->
                        RowItem(m.from, m.subject, trailing = m.date, end = {
                            if (!m.seen) Box(Modifier.padding(start = 8.dp).size(9.dp).clip(CircleShape).background(B.Blue))
                        }, onClick = {
                            open = m; body = ""
                            scope.launch { body = runCatching { s.mail.body(mb, m.uid) }.getOrElse { "Ошибка: ${it.message}" } }
                        })
                    }
                }
            }
            else -> LazyColumn(contentPadding = ListPadding) {
                item {
                    Hint("Почты компании вшиты в лаунчер. Для чтения писем внутри нужен пароль приложения " +
                        "(Gmail: myaccount.google.com → Безопасность → Пароли приложений) — введите в Настройках.")
                }
                item {
                    Group {
                        s.config.mailboxes.forEach { m ->
                            val st = s.mailStatus.firstOrNull { it.mailbox.address == m.address }
                            val hasPass = s.secrets.mailPassword(m.address).isNotBlank()
                            RowItem(m.name, if (!hasPass) "${m.address} · нет пароля" else st?.error?.let { "${m.address} · $it" } ?: m.address,
                                emoji = "✉️", trailing = st?.unread?.let { "$it" } ?: "",
                                onClick = { if (hasPass) loadBox(m) else if (m.webUrl.isNotBlank()) s.openUrl(m.webUrl) else s.open(Module.SETTINGS) })
                        }
                    }
                }
                item {
                    SectionTitle("Действия")
                    Group {
                        RowItem("Написать письмо", emoji = "✏️", onClick = { s.composeMail("", "") })
                        RowItem("Открыть Gmail", emoji = "📮", onClick = {
                            if (!s.appsRepo.launchPackage("com.google.android.gm")) s.openUrl("https://mail.google.com")
                        })
                    }
                }
            }
        }
    }
}

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(
        interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
        indication = null, onClick = onClick,
    )
)
