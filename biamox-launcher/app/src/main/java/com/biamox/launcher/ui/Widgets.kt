package com.biamox.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biamox.launcher.core.Kpi
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ru = Locale("ru")

// ------------------------------------------------------------------ Страница «Сегодня»
@Composable
fun TodayPage(s: LauncherState) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) { now = Date(); s.refreshDevice(); delay(60_000L - System.currentTimeMillis() % 60_000L) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        // Часы как на экране блокировки iOS
        Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(SimpleDateFormat("EEEE, d MMMM", ru).format(now).replaceFirstChar { it.uppercase() },
                color = B.Text2, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(SimpleDateFormat("HH:mm", ru).format(now), color = Color.White, fontSize = 86.sp,
                fontWeight = FontWeight.Light, letterSpacing = (-2).sp)
            Text(s.config.company.uppercase(), color = B.Text2, fontSize = 12.sp, letterSpacing = 4.sp)
        }

        StatsWidget(s)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallWidget(Modifier.weight(1f), "✉️ Почта", B.Cyan, { s.open(Module.MAIL) }) {
                val u = s.unreadTotal
                Text(u?.toString() ?: "—", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
                Text(if (u == null) "Введите пароли почт в Настройках" else "непрочитанных", color = B.Text2, fontSize = 13.sp)
                s.mailStatus.take(3).forEach {
                    Text("${it.mailbox.name}: ${it.unread ?: "!"}", color = B.Text2, fontSize = 12.sp, maxLines = 1)
                }
            }
            SmallWidget(Modifier.weight(1f), "✅ Задачи", B.Orange, { s.open(Module.OFFICE) }) {
                val open = s.tasks.filter { !it.done }
                Text("${open.size}", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
                Text("открыто", color = B.Text2, fontSize = 13.sp)
                open.take(3).forEach { Text("• ${it.text}", color = B.Text2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        Spacer(Modifier.height(12.dp))
        ClaudeWidget(s)
        Spacer(Modifier.height(12.dp))
        SocialsWidget(s)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallWidget(Modifier.weight(1f), "🎬 Студия", B.Pink, { s.open(Module.STUDIO) }) {
                val views = s.stats.kpis.firstOrNull { it.id == "views" }
                Text(views?.value ?: "Ролики", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Text(if (views != null) "просмотров" else "Открыть студию", color = B.Text2, fontSize = 13.sp)
            }
            SmallWidget(Modifier.weight(1f), "📱 Телефон", B.Green, { s.open(Module.SETTINGS) }) {
                val d = s.device
                Text("${d.batteryPct}%${if (d.charging) " ⚡" else ""}", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text("Память: ${"%.0f".format(d.storageFreeGb)} из ${"%.0f".format(d.storageTotalGb)} ГБ", color = B.Text2, fontSize = 12.sp)
                Text("ОЗУ своб.: ${"%.1f".format(d.ramFreeGb)} ГБ", color = B.Text2, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SmallWidget(
    modifier: Modifier, title: String, accent: Color, onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier.height(158.dp).glass().clickable { onClick() }.padding(14.dp)) {
        Text(title, color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

// ------------------------------------------------------------------ Статистика
@Composable
fun StatsWidget(s: LauncherState) {
    val st = s.stats
    Column(Modifier.fillMaxWidth().glass().clickable { s.open(Module.STATS) }.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📊 ${s.config.company} · сегодня", color = B.Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (st.demo) DemoBadge()
            Text(st.updated, color = B.Text2, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp))
        }
        Spacer(Modifier.height(10.dp))
        st.kpis.take(4).chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                row.forEach { k -> KpiCell(k, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        st.series.firstOrNull()?.let { ser ->
            Text(ser.title, color = B.Text2, fontSize = 12.sp)
            Sparkline(ser.points, Modifier.fillMaxWidth().height(46.dp).padding(top = 4.dp), B.Green)
        }
    }
}

/** Компактная строка статистики на главном экране */
@Composable
fun CompactStatsWidget(s: LauncherState) {
    val st = s.stats
    Row(Modifier.fillMaxWidth().glass().clickable { s.open(Module.STATS) }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        st.kpis.take(3).forEach { k ->
            Column(Modifier.weight(1f)) {
                Text(k.title, color = B.Text2, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(k.value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                k.delta?.let { Delta(it) }
            }
        }
        if (st.demo) DemoBadge()
    }
}

@Composable
fun KpiCell(k: Kpi, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("${k.emoji} ${k.title}", color = B.Text2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(k.value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        k.delta?.let { Delta(it) }
    }
}

@Composable
fun Delta(d: Double) {
    val up = d >= 0
    Text((if (up) "▲ " else "▼ ") + "%.1f%%".format(kotlin.math.abs(d)),
        color = if (up) B.Green else B.Red, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun DemoBadge() {
    Text("ДЕМО", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(B.Yellow).padding(horizontal = 6.dp, vertical = 2.dp))
}

@Composable
fun Sparkline(points: List<Float>, modifier: Modifier, color: Color, fill: Boolean = true) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val max = points.max(); val min = points.min()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (points.size - 1)
        fun pt(i: Int) = Offset(i * stepX, size.height - (points[i] - min) / range * size.height * 0.9f - size.height * 0.05f)
        val line = Path().apply { moveTo(pt(0).x, pt(0).y); for (i in 1 until points.size) lineTo(pt(i).x, pt(i).y) }
        if (fill) {
            val area = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        }
        drawPath(line, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 4.dp.toPx(), pt(points.size - 1))
    }
}

// ------------------------------------------------------------------ Claude
@Composable
fun ClaudeWidget(s: LauncherState) {
    Column(Modifier.fillMaxWidth().glass().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModuleIcon(Module.CLAUDE, 28.dp)
            Spacer(Modifier.width(8.dp))
            Text("Claude · ассистент Biamox", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(Color(0x22FFFFFF))
                .clickable { s.open(Module.CLAUDE) }.padding(horizontal = 14.dp, vertical = 11.dp)) {
                Text("Спросить Claude…", color = B.Text2, fontSize = 15.sp)
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(42.dp).clip(CircleShape).background(Module.VOICE.brush).clickable { s.startVoice() },
                contentAlignment = Alignment.Center) { Text("🎙️", fontSize = 20.sp) }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QUICK_PROMPTS.forEach { (label, prompt) ->
                Pill(label, B.Violet) { s.claudeDraft = prompt; s.open(Module.CLAUDE) }
            }
        }
    }
}

val QUICK_PROMPTS = listOf(
    "📊 Сводка дня" to "Сделай короткую сводку по статистике Biamox за сегодня: что хорошо, что плохо, что сделать.",
    "🗓 План дня" to "Составь мне план рабочего дня по открытым задачам — по приоритету.",
    "✉️ Письмо клиенту" to "Напиши вежливое письмо клиенту с предложением сотрудничества от Biamox. Первая строка — «Тема: …».",
    "🎬 Идея ролика" to "Предложи 3 идеи коротких роликов для соцсетей Biamox с хуком в первые 3 секунды.",
    "📣 Пост" to "Напиши пост для Instagram и Telegram компании Biamox на основе сегодняшних успехов.",
)

// ------------------------------------------------------------------ Соцсети
@Composable
fun SocialsWidget(s: LauncherState) {
    if (s.config.socials.isEmpty()) return
    Column(Modifier.fillMaxWidth().glass().padding(14.dp)) {
        Text("🌐 Соцсети ${s.config.company}", color = B.Cyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            s.config.socials.forEach { so ->
                Column(Modifier.clickable { s.openSocial(so) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    val app = s.apps.firstOrNull { it.pkg == so.pkg }
                    if (app != null) androidx.compose.foundation.Image(app.icon, so.title, Modifier.size(48.dp).clip(Squircle))
                    else Box(Modifier.size(48.dp).clip(Squircle).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
                        Text(so.emoji, fontSize = 22.sp)
                    }
                    Text(so.title, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
