package com.beamox.launcher.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamox.launcher.core.ClaudeSession
import com.beamox.launcher.core.Dashboard
import java.text.SimpleDateFormat
import java.util.Date

/** Что раскрыть по нажатию на цифру (как в «Здоровье»/«Экранном времени» на iPhone). */
sealed class StatDetail(val title: String) {
    object Reach : StatDetail("Охват")
    object Er : StatDetail("Вовлечённость")
    object Week : StatDetail("Просмотры за 7 дней")
    object Comments : StatDetail("Комментарии")
    class Platform(val name: String) : StatDetail(name)
    object Limits : StatDetail("Лимиты Claude")
    object Machines : StatDetail("Mac и сервер")
    class Session(val s: ClaudeSession) : StatDetail(s.title)
    object Job : StatDetail("Студия сейчас")
}

/** Куда отдать нажатие на цифру — задаёт Root (открывает карточку подробностей). */
val LocalPick = staticCompositionLocalOf<(StatDetail) -> Unit> { {} }

private val ru = java.util.Locale("ru")
private val Sheet = Color(0xFF1C1C1E)
private val Row2 = Color(0xFF2C2C2E)

@Composable
fun StatDetailSheet(s: LauncherState, det: StatDetail, onClose: () -> Unit) {
    val d = s.dash
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onClose)) {
        Column(Modifier.fillMaxSize().padding(top = 56.dp)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Sheet)
            .pointerInput(Unit) { androidx.compose.foundation.gestures.detectTapGestures { } }) {
            // ручка и заголовок: смахнуть вниз — закрыть
            Column(Modifier.fillMaxWidth().pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy > 30) onClose() } }) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(38.dp).height(5.dp).clip(CircleShape).background(Color(0xFF5A5A5E)))
                }
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(det.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Row2).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, null, tint = Color(0xFFAEAEB2), modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (d == null) {
                Text("Нет данных панели", color = B.Faint, fontSize = 16.sp, modifier = Modifier.padding(20.dp))
                return@Column
            }
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { DetailBody(s, d, det) }
            }
        }
    }
}

@Composable
private fun DetailBody(s: LauncherState, d: Dashboard, det: StatDetail) {
    val days = d.days.map { it.takeLast(5) }
    fun open(url: String) { if (url.isNotBlank()) runCatching { s.act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when (det) {
            StatDetail.Reach -> {
                Big(num(d.reach), "столько людей увидели ролики на всех площадках")
                Chart("Просмотры по дням", d.viewsPerDay.takeLast(14), days.takeLast(14), B.GCoral.first())
                DGroup("Площадки") {
                    d.platforms.forEach { p -> Line(p.name, "${num(p.total)} · " + (if (p.today > 0) "+${p.today} сегодня" else "сегодня 0")) { s.detail = StatDetail.Platform(p.name) } }
                }
            }
            StatDetail.Er -> {
                Big(d.er?.let { fx(it) + "%" } ?: "—", "доля лайков и комментариев от просмотров")
                DGroup("Ролики с лучшей вовлечённостью") {
                    d.top.filter { it.er != null }.sortedByDescending { it.er }.take(10).forEach { v ->
                        Line(v.title, "${fx(v.er)}% · ${v.platform}") { open(v.url) }
                    }
                }
            }
            StatDetail.Week -> {
                Big(num(d.v7), d.v30?.let { "за 30 дней — ${num(it)}" } ?: "просмотры за неделю")
                Chart("Последние 7 дней", d.viewsPerDay.takeLast(7), days.takeLast(7), B.GMint.first())
                DGroup("По площадкам") {
                    d.viewsByPlatform.forEach { (name, vals) -> Line(name, num(vals.takeLast(7).sum())) { s.detail = StatDetail.Platform(name) } }
                }
            }
            StatDetail.Comments -> {
                Big(num(d.comments), "комментариев · лайков ${num(d.likes)}")
                DGroup("Самые просматриваемые ролики") {
                    d.top.sortedByDescending { it.views }.take(10).forEach { v ->
                        Line(v.title, "${num(v.views)} просм. · ${num(v.likes)} ♥") { open(v.url) }
                    }
                }
            }
            is StatDetail.Platform -> {
                val p = d.platforms.firstOrNull { it.name == det.name }
                Big(num(p?.total), p?.let { (if (it.today > 0) "+${it.today} сегодня" else "сегодня 0") + " · подписчиков ${num(it.followers)}" } ?: "")
                d.viewsByPlatform[det.name]?.let { Chart("Просмотры по дням", it.takeLast(14), days.takeLast(14), (B.platform[det.name] ?: B.GMint).first()) }
                if (p != null) DGroup("Показатели") {
                    Line("Просмотры", num(p.views)); Line("За 7 дней", num(p.v7)); Line("ER", p.er?.let { fx(it) + "%" } ?: "—")
                }
                val vids = d.top.filter { it.platform == det.name }
                if (vids.isNotEmpty()) DGroup("Лучшие ролики") {
                    vids.take(10).forEach { v -> Line(v.title, "${num(v.views)} просм.") { open(v.url) } }
                }
            }
            StatDetail.Limits -> {
                Big(d.weekPct?.let { "${it.toInt()}%" } ?: "—", "недельного лимита израсходовано")
                DGroup("Лимиты") {
                    Line("Неделя", (d.weekPct?.let { "${it.toInt()}%" } ?: "—") + (d.weekReset?.let { " · сброс " + SimpleDateFormat("EE HH:mm", ru).format(Date(it)) } ?: ""))
                    val now = System.currentTimeMillis()
                    val five = d.fiveReset != null && d.fiveReset > now
                    Line("5 часов", if (five) "${d.fivePct?.toInt() ?: 0}% · сброс через ${dur(d.fiveReset!! - now)}" else "новое окно")
                    d.budgetMode?.let { Line("Режим", it) }
                    Line("На сутки", "≈14,3% недели")
                }
            }
            StatDetail.Machines -> {
                Big(d.macRamUsed?.let { fx(it) + " ГБ" } ?: "—", "память Mac" + (d.macRamLimit?.let { " из ${fx(it)} ГБ" } ?: ""))
                DGroup("Mac") {
                    Line("Состояние", if (d.macOnline) "в сети" else "спит")
                    d.macCpu?.let { Line("Процессор", "$it%") }
                    d.macBattery?.let { Line("Батарея", "$it%") }
                }
                DGroup("Сервер") {
                    Line("Состояние", if (d.serverOnline) "в сети" else "нет связи")
                    d.serverDiskPct?.let { Line("Диск", "$it%") }
                    d.services.forEach { sv -> Line(sv.name, if (sv.active) "работает" else "остановлена", if (sv.active) B.Green else B.Red) }
                }
            }
            is StatDetail.Session -> {
                val x = det.s
                Big(when (x.state) { "working" -> "Работает"; "waiting" -> "Ждёт ответа"; else -> "Ждёт вас" }, x.where)
                DGroup("Сессия") {
                    if (x.doing.isNotBlank()) Line("Сейчас", x.doing)
                    if (x.model.isNotBlank()) Line("Модель", x.model)
                    if (x.agents > 0) Line("Агентов", "${x.agents}")
                    if (x.since > 0) Line("Идёт", dur(System.currentTimeMillis() - x.since))
                    if (x.updated > 0) Line("Обновлено", hm(x.updated))
                }
            }
            StatDetail.Job -> {
                Big(d.jobWhat?.replaceFirstChar { it.uppercase() } ?: (d.nextRun?.let { "Отдыхает до ${hm(it)}" } ?: "Отдыхает"),
                    "собрано сегодня ${d.madeToday} · выложено ${d.postedToday} · готово ${d.readyClips}")
                DGroup("Сегодня") {
                    if (d.progressDone != null && d.progressOf != null) Line("Прогресс", "${d.progressDone}/${d.progressOf}")
                    d.stepWhat?.let { Line("Шаг", it) }
                    Line("Ошибок", "${d.errorsToday}", if (d.errorsToday > 0) B.Red else B.Green)
                    Line("Темы", "новых ${d.topicsNew} · сделано ${d.topicsMade}")
                }
                if (d.events.isNotEmpty()) DGroup("События") {
                    d.events.take(20).forEach { ev -> Line((if (ev.ok) "✓ " else "✗ ") + ev.text, hm(ev.at), if (ev.ok) B.Faint else B.Red) }
                }
            }
        }
    }
}

@Composable
private fun Big(value: String, caption: String) {
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(value, color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (caption.isNotBlank()) Text(caption, color = B.Faint, fontSize = 15.sp)
    }
}

@Composable
private fun DGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title.uppercase(), color = B.Faint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Row2)) { content() }
    }
}

@Composable
private fun Line(left: String, right: String, rightColor: Color = B.Faint, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(left, color = Color.White, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text(right, color = rightColor, fontSize = 15.sp, maxLines = 1)
        if (onClick != null) Text("  ›", color = Color(0xFF5A5A5E), fontSize = 18.sp)
    }
}

/** Столбики по дням (как в «Экранном времени») */
@Composable
private fun Chart(title: String, values: List<Int>, labels: List<String>, color: Color) {
    if (values.isEmpty()) return
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Row2).padding(16.dp)) {
        Text(title, color = B.Faint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
            values.forEachIndexed { i, v ->
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (values.size <= 8) Text(short(v), color = B.Faint, fontSize = 10.sp, maxLines = 1)
                    Box(Modifier.fillMaxWidth().fillMaxHeight((v / max.toFloat()).coerceAtLeast(0.02f) * 0.74f)
                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                        .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.55f)))))
                    Text(labels.getOrNull(i).orEmpty().let { if (values.size > 8) it.take(2) else it }, color = B.Faint, fontSize = 9.sp, maxLines = 1)
                }
            }
        }
    }
}

private fun short(v: Int) = when {
    v >= 1_000_000 -> "%.1fM".format(v / 1e6).replace(".0", "")
    v >= 1_000 -> "%.1fK".format(v / 1e3).replace(".0", "")
    else -> "$v"
}
