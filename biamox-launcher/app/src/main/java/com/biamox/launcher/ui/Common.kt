package com.biamox.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Разделы Biamox внутри лаунчера */
enum class Module(val title: String, val emoji: String, val c1: Color, val c2: Color) {
    CLAUDE("Claude", "✳️", Color(0xFFD97757), Color(0xFFB4532F)),
    STATS("Статистика", "📊", Color(0xFF34C759), Color(0xFF0A8F3C)),
    STUDIO("Студия", "🎬", Color(0xFFFF375F), Color(0xFFB0124A)),
    OFFICE("Офис", "🏢", Color(0xFF2F80FF), Color(0xFF1846B8)),
    MAIL("Почта", "✉️", Color(0xFF32D4F5), Color(0xFF1479E6)),
    VOICE("Голос", "🎙️", Color(0xFF8E5CFF), Color(0xFF4B23C9)),
    SETTINGS("Настройки", "⚙️", Color(0xFF8E8E93), Color(0xFF48484A));

    val brush get() = Brush.linearGradient(listOf(c1, c2))
}

@Composable
fun ModuleIcon(m: Module, size: Dp = 60.dp) {
    Box(
        Modifier.size(size).clip(Squircle).background(m.brush),
        contentAlignment = Alignment.Center,
    ) { Text(m.emoji, fontSize = (size.value * 0.48f).sp) }
}

@Composable
fun IconLabel(text: String) {
    Text(
        text, color = Color.White, fontSize = 11.5.sp, maxLines = 1,
        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp),
    )
}

/** Полноэкранный «лист» раздела с заголовком как в iOS. */
@Composable
fun ModuleSheet(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1030), Color(0xFF05070F))))
            .statusBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = B.Blue) }
            Spacer(Modifier.weight(1f))
            actions()
        }
        Text(title, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
        content()
    }
}

@Composable
fun SectionTitle(t: String) {
    Text(t.uppercase(), color = B.Gray, fontSize = 12.sp, letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp))
}

/** Группа строк как в настройках iOS */
@Composable
fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(Color(0xFF1C1F33)), content = content)
}

@Composable
fun RowItem(
    title: String,
    subtitle: String? = null,
    emoji: String? = null,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
    end: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (emoji != null) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x22FFFFFF)),
                contentAlignment = Alignment.Center) { Text(emoji, fontSize = 16.sp) }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = B.Gray, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) Text(trailing, color = B.Gray, fontSize = 15.sp)
        end?.invoke()
    }
}

@Composable
fun Pill(text: String, color: Color = B.Blue, onClick: () -> Unit) {
    Box(Modifier.clip(CircleShape).background(color.copy(alpha = 0.22f)).clickable { onClick() }
        .padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(text, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, color = B.Gray, fontSize = 13.sp, modifier = modifier.padding(horizontal = 20.dp, vertical = 6.dp))
}

val ListPadding = PaddingValues(bottom = 40.dp)
val Gap = Arrangement.spacedBy(10.dp)

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))
