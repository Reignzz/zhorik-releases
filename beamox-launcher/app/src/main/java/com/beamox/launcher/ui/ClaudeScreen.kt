package com.beamox.launcher.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Mic
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
    // Экран переписки как «Сообщения» iPhone
    Column(Modifier.fillMaxSize().background(Color.Black).padding(top = IosStatusBarHeight)) {
        // Шапка: стеклянная кнопка «назад», аватар и имя по центру
        Box(Modifier.fillMaxWidth().height(86.dp)) {
            LiquidGlass(CircleShape, Modifier.padding(start = 16.dp, top = 6.dp).size(44.dp).clickableNoRipple { s.goHome() },
                tint = Color(0x33505055), contentAlignment = Alignment.Center) {
                Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBackIos, "Назад", tint = Color.White,
                    modifier = Modifier.padding(start = 6.dp).size(20.dp))
            }
            Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(54.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color(0xFFE38A63), Color(0xFFC9623C)))), contentAlignment = Alignment.Center) { ClaudeBurst(32.dp) }
                Row(Modifier.padding(top = 4.dp).clip(CircleShape).background(Color(0xFF1C1C1E)).padding(horizontal = 10.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Claude", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowForwardIos, null, tint = Color(0x99EBEBF5),
                        modifier = Modifier.padding(start = 3.dp).size(9.dp))
                }
            }
            LiquidGlass(CircleShape, Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 6.dp).size(44.dp).clickableNoRipple { s.clearChat() },
                tint = Color(0x33505055), contentAlignment = Alignment.Center) {
                Icon(androidx.compose.material.icons.Icons.Rounded.Delete, "Очистить", tint = Color.White, modifier = Modifier.size(21.dp))
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (s.secrets.claudeKey.isBlank()) item {
                Text("Чтобы Claude отвечал, вставьте ключ: Настройки → Ключ Claude.", color = Color(0x99EBEBF5), fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                        .clickableNoRipple { s.open(Module.SETTINGS) })
            }
            if (s.chat.isEmpty()) item {
                Text("Claude видит статистику студии, задачи, почты и команду Beamox.", color = Color(0x99EBEBF5), fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
            }
            itemsIndexed(s.chat) { i, m ->
                val mine = m.role == "user"
                val lastInGroup = i == s.chat.lastIndex || s.chat[i + 1].role != m.role
                if (i > 0 && s.chat[i - 1].role != m.role) Spacer(Modifier.size(8.dp))
                Bubble(s, mine, m.text, tail = lastInGroup)
            }
            if (s.claudeBusy) item { Spacer(Modifier.size(8.dp)); TypingBubble() }
        }
        if (s.chat.isEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QUICK_PROMPTS.forEach { (label, prompt) ->
                Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.clip(CircleShape)
                    .border(1.dp, Color(0xFF3A3A3C), CircleShape).clickableNoRipple { s.sendClaude(prompt) }
                    .padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
        // Тот же вопрос — в официальные приложения Claude, ChatGPT, Gemini (ваши аккаунты, без ключей)
        AiAppsRow(s, s.claudeDraft) { s.claudeDraft = "" }
        // Строка ввода как в «Сообщениях»: «+», поле, микрофон / синяя стрелка
        Row(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom) {
            LiquidGlass(CircleShape, Modifier.size(40.dp), tint = Color(0x33505055), contentAlignment = Alignment.Center) {
                Icon(androidx.compose.material.icons.Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.size(8.dp))
            val canSend = s.claudeDraft.isNotBlank() && !s.claudeBusy
            Row(Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).border(1.dp, Color(0xFF3A3A3C), RoundedCornerShape(20.dp))
                .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).padding(vertical = 5.dp)) {
                    if (s.claudeDraft.isEmpty()) Text("Сообщение", color = Color(0x99EBEBF5), fontSize = 17.sp)
                    BasicTextField(s.claudeDraft, { s.claudeDraft = it }, maxLines = 6,
                        textStyle = TextStyle(color = Color.White, fontSize = 17.sp, fontFamily = Rounded, letterSpacing = (-0.4).sp),
                        cursorBrush = SolidColor(Color(0xFF0A84FF)), modifier = Modifier.fillMaxWidth())
                }
                if (canSend) Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0xFF0A84FF))
                    .clickableNoRipple { val t = s.claudeDraft; s.claudeDraft = ""; s.sendClaude(t) }, contentAlignment = Alignment.Center) {
                    Icon(androidx.compose.material.icons.Icons.Rounded.ArrowUpward, "Отправить", tint = Color.White, modifier = Modifier.size(20.dp))
                } else Icon(androidx.compose.material.icons.Icons.Rounded.Mic, "Голос", tint = Color(0x99EBEBF5),
                    modifier = Modifier.padding(end = 6.dp).size(24.dp).clickableNoRipple { s.startVoice() })
            }
        }
    }
}

@Composable
private fun Bubble(s: LauncherState, mine: Boolean, text: String, tail: Boolean) {
    Row(Modifier.fillMaxWidth().padding(start = if (mine) 64.dp else 0.dp, end = if (mine) 0.dp else 64.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(Modifier
            .background(if (mine) Color(0xFF0A84FF) else Color(0xFF262628), BubbleShape(mine, tail))
            .clickableNoRipple {
                val cm = s.ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Claude", text)); s.toast("Скопировано")
            }
            .padding(horizontal = 13.dp, vertical = 8.dp)) {
            Text(text.replace("**", ""), color = Color.White, fontSize = 17.sp, letterSpacing = (-0.4).sp, lineHeight = 22.sp)
            if (!mine && text.lowercase().contains("тема:")) {
                Text("Открыть как письмо", color = Color(0xFF64D2FF), fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 6.dp).clickableNoRipple { val (su, b) = s.splitLetter(text); s.composeMail(su, b) })
            }
        }
    }
}

/** «Печатает…» — три точки в сером пузыре, как в iMessage */
@Composable
private fun TypingBubble() {
    val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "typing")
    val ph by inf.animateFloat(0f, 3f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1200)), label = "ph")
    Row(Modifier.background(Color(0xFF262628), BubbleShape(false, true)).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { i ->
            val a = if (ph.toInt() == i) 0.9f else 0.35f
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White.copy(alpha = a)))
        }
    }
}
