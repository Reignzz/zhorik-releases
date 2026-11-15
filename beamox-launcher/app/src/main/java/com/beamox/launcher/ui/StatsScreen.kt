package com.beamox.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamox.launcher.core.Dashboard
import kotlinx.coroutines.delay

@Composable
fun StatsScreen(s: LauncherState) {
    // Пока экран открыт — обновляем каждые 15 секунд, как панель
    LaunchedEffect(Unit) { while (true) { s.refreshStats(); delay(15_000) } }
    val d = s.dash
    ModuleSheet(if (d != null) "Генератор охватов" else "Статистика", onBack = { s.goHome() }, actions = {
        if (s.statsLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = B.Coral)
        IconButton(onClick = { s.refreshStats() }) { Icon(Icons.Rounded.Refresh, "Обновить", tint = B.Coral) }
    }) {
        if (d == null) {
            LazyColumn(contentPadding = ListPadding) { item { ConnectPanelCard(s) }; item { LegacyKpis(s) } }
        } else DashboardView(s, d)
    }
}

@Composable
private fun DashboardView(s: LauncherState, d: Dashboard) {
    LazyColumn(contentPadding = ListPadding) {
        item {
            Row(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("обновлено ${hm(d.at)}", color = B.Faint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                when {
                    d.waiting.isNotEmpty() -> StatusPill("✋ ждёт подтверждения", B.Amber)
                    d.working > 0 -> StatusPill("● ${d.working} ${plural(d.working, "работает", "работают", "работают")}", B.Green)
                    else -> StatusPill("свободен", B.Dim)
                }
            }
            s.dashError?.let { Hint("⚠️ $it") }
        }
        item {
            PanelCard("Лимиты", d.budgetMode?.let { when (it) { "bots" -> "только боты"; "saving" -> "экономия"; else -> "обычный" } } ?: "") {
                LimitRings(d)
            }
        }
        item { PanelCard("Сейчас", if (d.sessions.isNotEmpty()) "${d.sessions.size} ${plural(d.sessions.size, "сессия", "сессии", "сессий")}" else "") { SessionsList(d, 10) } }
        item {
            PanelCard("Студия роликов", d.studioName) {
                StudioNumbers(d)
                StudioJob(d)
                PlatformTiles(d)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    listOf("${d.madeToday}" to "собрано сегодня", "${d.postedToday}" to "выложено", "${d.readyClips}" to "в запасе",
                        "${d.topicsNew}" to "новых тем").forEach { (v, l) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(v, color = B.Text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                            Text(l, color = B.Faint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        if (d.days.isNotEmpty()) item {
            PanelCard("Просмотры по дням", "${num(d.v7)} за 7 дней") {
                StackedBars(d)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    d.viewsByPlatform.keys.forEach { name ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Brush.linearGradient(B.platform[name] ?: B.GBlue)))
                            Text(" $name", color = B.Faint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        if (d.top.isNotEmpty()) item {
            PanelCard("Лучшие ролики") {
                d.top.take(8).forEachIndexed { i, v ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", color = B.Faint, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(22.dp))
                        Column(Modifier.weight(1f)) {
                            Text(v.title, color = B.Text, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(v.platform + (v.er?.let { " · ER ${fx(it)}%" } ?: ""), color = B.Faint, fontSize = 12.sp)
                        }
                        Text(num(v.views), color = B.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        if (d.events.isNotEmpty()) item {
            PanelCard("События", if (d.errorsToday > 0) "ошибок сегодня ${d.errorsToday}" else "") {
                d.events.take(12).forEach { e ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                        Text(hm(e.at), color = B.Faint, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
                        Box(Modifier.padding(top = 5.dp, end = 8.dp).size(7.dp).clip(CircleShape).background(if (e.ok) B.Green else B.Red))
                        Text(e.text, color = B.Dim, fontSize = 13.5.sp)
                    }
                }
            }
        }
        if (d.services.isNotEmpty()) item {
            PanelCard("Сервер", d.serverDiskPct?.let { "диск $it%" } ?: "") {
                d.services.forEach { sv ->
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(if (sv.active) B.Green else B.Faint))
                        Text("  ${sv.name}", color = B.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(if (sv.active) "работает" else "остановлен", color = if (sv.active) B.Green else B.Faint, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Group {
                RowItem("Спросить Claude про эти цифры", "Что изменилось и что сделать сегодня", onClick = {
                    s.claudeDraft = "Проанализируй студию Beamox по живым данным: что работает, что нет, и 3 конкретных действия на сегодня."
                    s.open(Module.CLAUDE)
                }, icon = null)
            }
        }
    }
}

/** Столбики по дням, сложенные по площадкам */
@Composable
private fun StackedBars(d: Dashboard) {
    val totals = d.viewsPerDay
    val max = (totals.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val slot = size.width / totals.size
        val bw = slot * 0.62f
        totals.indices.forEach { i ->
            var y = size.height
            // пустой «трек»
            drawRoundRect(Color(0x0FFFFFFF), Offset(i * slot + (slot - bw) / 2, 0f), Size(bw, size.height), CornerRadius(bw / 2))
            d.viewsByPlatform.forEach { (name, arr) ->
                val v = arr.getOrElse(i) { 0 }
                if (v > 0) {
                    val h = maxOf(4f, v.toFloat() / max * size.height)
                    y -= h
                    drawRoundRect(Brush.verticalGradient(B.platform[name] ?: B.GBlue, startY = y, endY = y + h),
                        Offset(i * slot + (slot - bw) / 2, y), Size(bw, h), CornerRadius(bw / 2))
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        d.days.forEachIndexed { i, day ->
            Text(if (i % 2 == 1 || i == d.days.size - 1) day.takeLast(2) else "", color = B.Faint, fontSize = 10.sp,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

/** Старый вид (если панель не подключена, а задан свой сервер статистики) */
@Composable
private fun LegacyKpis(s: LauncherState) {
    val st = s.stats
    PanelCard("Показатели", if (st.demo) "демо" else st.updated) {
        st.kpis.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                row.forEach { k -> KpiCell(k, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
