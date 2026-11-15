package com.beamox.launcher.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ClaudeScreen(s: LauncherState) {
    val list = rememberLazyListState()
    LaunchedEffect(s.chat.size, s.claudeBusy) {
        val last = list.layoutInfo.totalItemsCount - 1
        if (last > 0) list.animateScrollToItem(last)
    }
    ModuleSheet("Claude", onBack = { s.goHome() }, actions = {
        IconButton(onClick = { s.clearChat() }) { Icon(Icons.Default.Delete, "Очистить", tint = B.Gray) }
    }) {
        if (s.secrets.claudeKey.isBlank()) {
            Column(Modifier.padding(16.dp).fillMaxWidth().glass(RoundedCornerShape(14.dp)).padding(14.dp)) {
                Text("Нужен ключ Claude API", color = Color.White, fontSize = 16.sp)
                Text("Настройки → Claude → вставьте ключ (sk-ant-…) с console.anthropic.com. Или его передаст install.sh.",
                    color = B.Gray, fontSize = 13.sp)
                Spacer(Modifier.size(8.dp))
                Pill("Открыть настройки") { s.open(Module.SETTINGS) }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.chat.isEmpty()) item {
                Text("Спросите что угодно о работе Beamox: Claude видит статистику, задачи, почты и команду.",
                    color = B.Gray, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
            }
            itemsIndexed(s.chat) { _, m -> Bubble(s, m.role == "user", m.text) }
            if (s.claudeBusy) item { Bubble(s, false, "Claude думает…", typing = true) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QUICK_PROMPTS.forEach { (label, prompt) -> Pill(label, B.Violet) { s.sendClaude(prompt) } }
        }
        Row(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(10.dp),
            verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(Color(0xFF1C1F33))
                .padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (s.claudeDraft.isEmpty()) Text("Сообщение…", color = B.Gray, fontSize = 16.sp)
                BasicTextField(s.claudeDraft, { s.claudeDraft = it }, maxLines = 6,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(B.Blue),
                    modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.size(8.dp))
            val canSend = s.claudeDraft.isNotBlank() && !s.claudeBusy
            Box(Modifier.size(44.dp).clip(CircleShape)
                .background(if (canSend) Module.CLAUDE.brush else Module.VOICE.brush)
                .clickable {
                    if (canSend) { val t = s.claudeDraft; s.claudeDraft = ""; s.sendClaude(t) } else s.startVoice()
                }, contentAlignment = Alignment.Center) {
                Text(if (canSend) "↑" else "🎙️", color = Color.White, fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun Bubble(s: LauncherState, mine: Boolean, text: String, typing: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = 310.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (mine) B.Blue else Color(0xFF26293F))
            .clickable(enabled = !typing) {
                val cm = s.ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Claude", text)); s.toast("Скопировано")
            }
            .padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(text.replace("**", ""), color = if (typing) B.Gray else Color.White, fontSize = 15.sp)
            if (!mine && !typing && text.lowercase().contains("тема:")) {
                Spacer(Modifier.size(6.dp))
                Pill("✉️ Открыть как письмо", B.Cyan) { val (su, b) = s.splitLetter(text); s.composeMail(su, b) }
            }
        }
    }
}
