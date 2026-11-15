package com.beamox.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamox.launcher.core.Dashboard
import com.beamox.launcher.core.Kpi
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ru = Locale("ru")

fun hm(t: Long) = SimpleDateFormat("HH:mm", ru).format(Date(t))
fun dur(ms: Long): String {
    val m = maxOf(0, Math.round(ms / 60000.0)).toInt()
    if (m < 60) return "$m мин"
    val h = m / 60
    return "$h ч" + if (m % 60 > 0) " ${m % 60} мин" else ""
}
fun plural(n: Int, a: String, b: String, c: String): String {
    val x = n % 10; val y = n % 100
    return if (x == 1 && y != 11) a else if (x in 2..4 && (y < 12 || y > 14)) b else c
}
fun fx(v: Double?) = v?.let { (Math.round(it * 10) / 10.0).toString().replace(".0", "").replace(".", ",") } ?: "—"
fun num(v: Int?) = v?.let { Dashboard.formatNum(it) } ?: "—"

// ================================================================== Графика панели
/** Кольцо прогресса как в Apple Fitness / панели Beamox */
@Composable
fun Ring(frac: Float, colors: List<Color>, value: String, label: String, sub: String, size: Dp = 92.dp) {
    val f = frac.coerceIn(0f, 1f)
    val g = if (f >= 0.9f) B.GHot else colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = this.size.minDimension * 0.105f
                val tl = Offset(sw / 2, sw / 2); val sz = Size(this.size.width - sw, this.size.height - sw)
                drawArc(Color(0x14FFFFFF), 0f, 360f, false, tl, sz, style = Stroke(sw))
                if (f > 0.004f) drawArc(Brush.linearGradient(g), -90f, 360f * f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
            }
            Text(value, color = B.Text, fontSize = (size.value * 0.2f).sp, fontWeight = FontWeight.ExtraBold)
        }
        Text(label, color = B.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        Text(sub, color = B.Faint, fontSize = 11.5.sp, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
fun GradientBar(frac: Float, colors: List<Color>, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color(0x14FFFFFF))) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(8.dp).clip(CircleShape).background(Brush.horizontalGradient(colors)))
    }
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
        drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 3.5.dp.toPx(), pt(points.size - 1))
    }
}

@Composable
fun KpiCell(k: Kpi, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(k.value, color = B.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(k.title, color = B.Faint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        k.delta?.let { Delta(it) }
    }
}

@Composable
fun Delta(d: Double) {
    val up = d >= 0
    Text((if (up) "▲ " else "▼ ") + "%.1f%%".format(kotlin.math.abs(d)),
        color = if (up) B.Green else B.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun DemoBadge() { StatusPill("демо", B.Amber) }

/** Четыре цифры студии в ряд (как в панели) */
@Composable
fun StudioNumbers(d: Dashboard) {
    Row(Modifier.fillMaxWidth()) {
        listOf(num(d.reach) to "Охват", (d.er?.let { fx(it) + "%" } ?: "—") to "ER",
            num(d.v7) to "За 7 дней", num(d.comments) to "Коммент.").forEach { (v, l) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(v, color = B.Text, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(l, color = B.Faint, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

/** Плитки площадок с градиентной полосой */
@Composable
fun PlatformTiles(d: Dashboard, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        d.platforms.forEach { p ->
            val g = B.platform[p.name] ?: listOf(Color.Gray, Color.DarkGray)
            Column(Modifier.width(96.dp).clip(RoundedCornerShape(18.dp)).background(Color(0x0FFFFFFF))
                .clickable { onClick() }.padding(12.dp)) {
                Box(Modifier.width(26.dp).height(5.dp).clip(CircleShape).background(Brush.horizontalGradient(g)))
                Text(p.name, color = B.Dim, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Text("${p.total}", color = B.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (p.today > 0) "+${p.today} сегодня" else "сегодня 0",
                    color = if (p.today > 0) B.Green else B.Faint, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Текущая работа студии: «плановый цикл 3/10» или «отдыхает до 12:45» */
@Composable
fun StudioJob(d: Dashboard) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp).clip(RoundedCornerShape(18.dp)).background(Color(0x0FFFFFFF)).padding(14.dp)) {
        if (d.jobWhat != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.jobWhat.replaceFirstChar { it.uppercase() }, color = B.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (d.progressDone != null && d.progressOf != null)
                    Text("${d.progressDone}/${d.progressOf}", color = B.Text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text((d.stepWhat?.let { "сейчас: $it · " } ?: "") + (d.jobSince?.let { "идёт ${dur(System.currentTimeMillis() - it)}" } ?: ""),
                color = B.Faint, fontSize = 12.5.sp, modifier = Modifier.padding(bottom = 8.dp))
            val frac = if (d.progressDone != null && d.progressOf != null && d.progressOf > 0) maxOf(0.04f, d.progressDone.toFloat() / d.progressOf) else 0.5f
            GradientBar(frac, B.GMint)
        } else {
            Text(d.nextRun?.let { "Отдыхает до ${hm(it)}" } ?: "Отдыхает", color = B.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("сегодня собрано ${d.madeToday} · выложено ${d.postedToday}", color = B.Faint, fontSize = 12.5.sp)
        }
    }
}

/** Сессии Claude (Mac и сервер) */
@Composable
fun SessionsList(d: Dashboard, max: Int = 4) {
    if (d.sessions.isEmpty()) {
        Text("Всё тихо — Claude отдыхает", color = B.Faint, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
        return
    }
    d.sessions.take(max).forEach { s ->
        val col = when (s.state) { "waiting" -> B.Amber; "working" -> B.Green; else -> B.Faint }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(col))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title, color = B.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(s.where, if (s.state == "idle") "ждёт вас" else s.doing, s.model).filter { it.isNotBlank() }.joinToString(" · "),
                    color = B.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val since = if (s.state == "idle") s.updated else s.since
            if (since > 0) Text(dur(System.currentTimeMillis() - since), color = B.Dim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun LimitRings(d: Dashboard, size: Dp = 88.dp) {
    val now = System.currentTimeMillis()
    val fiveActive = d.fiveReset != null && d.fiveReset > now
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Ring(((d.weekPct ?: 0.0) / 100).toFloat(), B.GWeek, d.weekPct?.let { "${it.toInt()}%" } ?: "—", "Неделя",
            d.weekReset?.let { "сброс " + SimpleDateFormat("EE HH:mm", ru).format(Date(it)) } ?: "", size)
        Ring(if (fiveActive) ((d.fivePct ?: 0.0) / 100).toFloat() else 0f, B.GFive,
            if (fiveActive) "${d.fivePct?.toInt() ?: 0}%" else "0%", "5 часов",
            if (fiveActive) "сброс через ${dur(d.fiveReset!! - now)}" else "новое окно", size)
        val mf = if (d.macRamUsed != null && d.macRamLimit != null && d.macRamLimit > 0) (d.macRamUsed / d.macRamLimit).toFloat() else 0f
        Ring(mf, B.GMint, d.macRamUsed?.let { fx(it) } ?: "—", "Память Mac",
            if (d.macOnline) "из ${fx(d.macRamLimit)} ГБ" + (d.macCpu?.let { " · ЦП $it%" } ?: "") else "Mac спит", size)
    }
}

// ================================================================== Страница «Сегодня»
@Composable
fun TodayPage(s: LauncherState) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) { now = Date(); s.refreshDevice(); delay(60_000L - System.currentTimeMillis() % 60_000L) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 8.dp)) {
            Box(Modifier.fillMaxWidth().bounceClick { s.searchOpen = true }) {
                Row(Modifier.fillMaxWidth().glass(CircleShape, 0.10f).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(androidx.compose.material.icons.Icons.Rounded.Search, null, tint = B.Dim, modifier = Modifier.size(18.dp))
                    Text("  Поиск", color = B.Dim, fontSize = 17.sp)
                }
            }
            Text(SimpleDateFormat("EEEE, d MMMM", ru).format(now).replaceFirstChar { it.uppercase() },
                color = B.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp,
                modifier = Modifier.padding(start = 4.dp, top = 18.dp))
        }
        val d = s.dash
        if (d == null) ConnectPanelCard(s)
        else {
            PanelCard("Студия роликов", d.studioName, onClick = { s.open(Module.STATS) }) {
                StudioNumbers(d)
                StudioJob(d)
                PlatformTiles(d) { s.open(Module.STATS) }
            }
            PanelCard("Claude", if (d.sessions.isNotEmpty()) "${d.sessions.size} ${plural(d.sessions.size, "сессия", "сессии", "сессий")}" else "",
                onClick = { s.open(Module.STATS) }) {
                if (d.waiting.isNotEmpty()) {
                    Text("✋ Ждёт подтверждения: " + d.waiting.joinToString { it.title }, color = B.Amber, fontSize = 14.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(B.Amber.copy(alpha = 0.12f)).padding(12.dp))
                    Spacer(Modifier.height(8.dp))
                }
                SessionsList(d, 3)
                Spacer(Modifier.height(12.dp))
                LimitRings(d, 80.dp)
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallWidget(Modifier.weight(1f), "Почта", B.Accent, { s.open(Module.MAIL) }) {
                val u = s.unreadTotal
                Text(u?.toString() ?: "—", color = B.Text, fontSize = 42.sp, fontWeight = FontWeight.Light)
                Text(if (u == null) "Добавьте пароли почт в Настройках" else "непрочитанных", color = B.Faint, fontSize = 12.5.sp)
            }
            SmallWidget(Modifier.weight(1f), "Задачи", B.Amber, { s.open(Module.OFFICE) }) {
                val open = s.tasks.filter { !it.done }
                Text("${open.size}", color = B.Text, fontSize = 42.sp, fontWeight = FontWeight.Light)
                Text(if (open.isEmpty()) "всё сделано" else open.first().text, color = B.Faint, fontSize = 12.5.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        ClaudeWidget(s)
        SocialsWidget(s)
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
fun ConnectPanelCard(s: LauncherState) {
    PanelCard("Панель Beamox") {
        Text("Подключите живую статистику студии, площадок и сессий Claude: скопируйте ссылку на панель («Генератор охватов») и нажмите кнопку.",
            color = B.Dim, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.clip(CircleShape).background(Brush.horizontalGradient(B.GCoral)).clickable { s.pastePanelLink() }
            .padding(horizontal = 18.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Link, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Вставить ссылку из буфера", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun SmallWidget(modifier: Modifier, title: String, accent: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier.height(158.dp).clip(RoundedCornerShape(26.dp)).background(B.Group)
        .clickable { onClick() }.padding(16.dp)) {
        Text(title, color = accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

// ================================================================== Квадратные виджеты главного экрана
/** Квадратный виджет (2×2 иконки), как погода/Revolut на iPhone */
@Composable
fun SquareWidget(modifier: Modifier, bg: Brush, label: String = "", sheen: Boolean = false, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).bounceClick(onClick = onClick).clip(RoundedCornerShape(30.dp)).background(bg)
            .then(if (sheen) Modifier.background(Brush.linearGradient(
                0.0f to Color.Transparent, 0.55f to Color.Transparent, 0.56f to Color.White.copy(alpha = 0.07f), 1f to Color.White.copy(alpha = 0.02f)))
            else Modifier.background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent))))
            .border(0.6.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(30.dp))
            .padding(18.dp)) { content() }
        if (label.isNotBlank()) Text(label, style = LabelStyle, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun StudioSquare(s: LauncherState, modifier: Modifier) {
    val d = s.dash
    SquareWidget(modifier, Brush.verticalGradient(listOf(Color(0xE6E8794F), Color(0xD9C2456A))), "Студия", false,
        { s.open(if (d == null) Module.SETTINGS else Module.STATS) }) {
        Column(Modifier.fillMaxSize()) {
            Text(d?.studioName ?: "Студия", color = B.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(num(d?.reach), color = B.Text, fontSize = 44.sp, fontWeight = FontWeight.Light, letterSpacing = (-1.5).sp, lineHeight = 48.sp)
            Spacer(Modifier.weight(1f))
            Text(if (d != null) "охват · +${d.postedToday} сегодня" else "Подключить панель", color = Color.White,
                fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, lineHeight = 16.sp)
        }
    }
}

@Composable
fun ClaudeSquare(s: LauncherState, modifier: Modifier) {
    val d = s.dash
    SquareWidget(modifier, Brush.verticalGradient(listOf(Color(0xFF111113), Color(0xFF050505))), "Claude", true, { s.open(Module.CLAUDE) }) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClaudeBurst(18.dp, B.Coral)
                Text("  Claude", color = B.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            if (d != null && d.weekPct != null) {
                val fiveOn = d.fiveReset != null && d.fiveReset > System.currentTimeMillis()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MiniRing((d.weekPct / 100).toFloat(), B.GWeek, "${d.weekPct.toInt()}%", "неделя")
                    MiniRing(if (fiveOn) ((d.fivePct ?: 0.0) / 100).toFloat() else 0f, B.GFive, if (fiveOn) "${d.fivePct?.toInt()}%" else "0%", "5 ч")
                }
            } else {
                Text("Спросить…", color = B.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("статистика, письма, идеи", color = B.Faint, fontSize = 12.5.sp)
            }
        }
    }
}

@Composable
private fun MiniRing(f: Float, g: List<Color>, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 6.dp.toPx(); val tl = Offset(sw / 2, sw / 2); val sz = Size(size.width - sw, size.height - sw)
                drawArc(Color(0x1FFFFFFF), 0f, 360f, false, tl, sz, style = Stroke(sw))
                if (f > 0.004f) drawArc(Brush.linearGradient(if (f >= .9f) B.GHot else g), -90f, 360f * f.coerceAtMost(1f), false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
            }
            Text(value, color = B.Text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text(label, color = B.Faint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ================================================================== Claude и соцсети
@Composable
fun ClaudeWidget(s: LauncherState) {
    PanelCard("Ассистент") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).clip(CircleShape).background(Color(0x14FFFFFF))
                .clickable { s.open(Module.CLAUDE) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Спросить Claude…", color = B.Faint, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(B.GCoral)).clickable { s.startVoice() },
                contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Mic, "Голос", tint = Color.White) }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QUICK_PROMPTS.forEach { (label, prompt) ->
                Pill(label, B.Coral) { s.claudeDraft = prompt; s.open(Module.CLAUDE) }
            }
        }
    }
}

val QUICK_PROMPTS = listOf(
    "Сводка дня" to "Сделай короткую сводку по студии и статистике Beamox за сегодня: что хорошо, что плохо, что сделать.",
    "Что выложить" to "Посмотри лучшие ролики и площадки студии: что и куда выложить сегодня, чтобы поднять охват?",
    "План дня" to "Составь мне план рабочего дня по открытым задачам — по приоритету.",
    "Письмо клиенту" to "Напиши вежливое письмо клиенту с предложением сотрудничества от Beamox. Первая строка — «Тема: …».",
    "Идеи роликов" to "Предложи 3 идеи коротких роликов для соцсетей Beamox с хуком в первые 3 секунды.",
)

@Composable
fun SocialsWidget(s: LauncherState) {
    if (s.config.socials.isEmpty()) return
    PanelCard("Соцсети ${s.config.company}") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            s.config.socials.forEach { so ->
                Column(Modifier.bounceClick { s.openSocial(so) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    val app = s.apps.firstOrNull { it.pkg == so.pkg }
                    if (app != null) Image(app.icon, so.title, Modifier.size(50.dp).clip(Squircle))
                    else Box(Modifier.size(50.dp).clip(Squircle).background(Brush.verticalGradient(B.platform[so.title] ?: B.GBlue)),
                        contentAlignment = Alignment.Center) { Text(so.title.take(1), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) }
                    Text(so.title, color = B.Dim, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
    }
}
