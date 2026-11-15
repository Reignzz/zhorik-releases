package com.biamox.launcher.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val chartColors = listOf(B.Blue, B.Green, B.Orange, B.Pink, B.Violet, B.Cyan, B.Yellow)

@Composable
fun StatsScreen(s: LauncherState) {
    val st = s.stats
    ModuleSheet("Статистика", onBack = { s.goHome() }, actions = {
        if (s.statsLoading) CircularProgressIndicator(Modifier.size(22.dp).padding(end = 4.dp), strokeWidth = 2.dp)
        IconButton(onClick = { s.refreshStats() }) { Icon(Icons.Default.Refresh, "Обновить", tint = B.Blue) }
    }) {
        LazyColumn(contentPadding = ListPadding) {
            item {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Обновлено ${st.updated}", color = B.Gray, fontSize = 13.sp)
                    Spacer(Modifier.width(8.dp))
                    if (st.demo) DemoBadge()
                }
                st.error?.let { Hint("⚠️ $it") }
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    st.kpis.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { k -> Box(Modifier.weight(1f).glass(RoundedCornerShape(18.dp)).padding(14.dp)) { KpiCell(k) } }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            items(st.series) { ser ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(14.dp)) {
                    Text(ser.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    BarChart(ser.points, Modifier.fillMaxWidth().height(130.dp).padding(top = 10.dp))
                    Row(Modifier.fillMaxWidth()) {
                        ser.labels.forEach { Text(it, color = B.Gray, fontSize = 11.sp, modifier = Modifier.weight(1f)) }
                    }
                }
            }
            if (st.breakdown.isNotEmpty()) item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(14.dp)) {
                    Text("Источники", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                        Donut(st.breakdown.map { it.value }, Modifier.size(120.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            val total = st.breakdown.sumOf { it.value.toDouble() }.takeIf { it > 0 } ?: 1.0
                            st.breakdown.forEachIndexed { i, b ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(chartColors[i % chartColors.size]))
                                    Spacer(Modifier.width(8.dp))
                                    Text("${b.title} — ${"%.0f".format(b.value / total * 100)}%", color = Color.White, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
            if (st.notes.isNotEmpty()) item {
                SectionTitle("Заметки")
                Group { st.notes.forEach { RowItem(it, emoji = "📝") } }
            }
            item {
                SectionTitle("Анализ")
                Group {
                    RowItem("Спросить Claude про эти цифры", "Сводка, причины, что делать", emoji = "✳️", onClick = {
                        s.claudeDraft = "Проанализируй статистику Biamox: что изменилось, почему и 3 конкретных действия на сегодня."
                        s.open(Module.CLAUDE)
                    })
                }
            }
        }
    }
}

@Composable
fun BarChart(points: List<Float>, modifier: Modifier) {
    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val max = (points.maxOrNull() ?: 1f).takeIf { it > 0f } ?: 1f
        val slot = size.width / points.size
        val w = slot * 0.55f
        points.forEachIndexed { i, v ->
            val h = v / max * size.height
            val last = i == points.size - 1
            drawRoundRect(
                color = if (last) B.Green else B.Blue.copy(alpha = 0.75f),
                topLeft = Offset(i * slot + (slot - w) / 2, size.height - h),
                size = Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
            )
        }
    }
}

@Composable
fun Donut(values: List<Float>, modifier: Modifier) {
    Canvas(modifier) {
        val total = values.sum().takeIf { it > 0f } ?: 1f
        var start = -90f
        val stroke = size.minDimension * 0.18f
        values.forEachIndexed { i, v ->
            val sweep = v / total * 360f
            drawArc(chartColors[i % chartColors.size], start, sweep - 2f, false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
            start += sweep
        }
    }
}
