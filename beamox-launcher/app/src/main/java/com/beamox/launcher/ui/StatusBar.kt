package com.beamox.launcher.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Высота строки состояния в стиле iOS (системная скрыта, рисуем свою) */
val IosStatusBarHeight = 50.dp

/** Строка состояния как на iPhone: время слева, связь/Wi-Fi/батарея справа */
@Composable
fun IosStatusBar(s: LauncherState, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Date()) }
    var wifi by remember { mutableStateOf(true) }
    var cell by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            if (!s.preview) runCatching {
                val cm = s.ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                cell = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true || !wifi
            }
            s.refreshDevice()
            delay(15_000)
        }
    }
    Row(modifier.fillMaxWidth().height(IosStatusBarHeight).padding(start = 38.dp, end = 30.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(SimpleDateFormat("H:mm", Locale("ru")).format(now), color = Color.White, fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold, fontFamily = Rounded, letterSpacing = (-0.3).sp)
        Spacer(Modifier.weight(1f))
        // Сотовая связь: 4 столбика
        Canvas(Modifier.size(18.dp, 12.dp)) {
            val w = size.width / 4.6f
            for (i in 0 until 4) {
                val h = size.height * (0.4f + 0.2f * i)
                drawRoundRect(if (cell || i < 2) Color.White else Color(0x55FFFFFF),
                    Offset(i * w * 1.2f, size.height - h), Size(w, h), CornerRadius(w / 3))
            }
        }
        Spacer(Modifier.width(6.dp))
        if (wifi) {
            // Wi-Fi: три дуги и точка
            Canvas(Modifier.size(17.dp, 12.dp)) {
                val c = Offset(size.width / 2, size.height)
                listOf(1f, 0.66f, 0.33f).forEach { k ->
                    val r = size.height * 1.05f * k
                    drawArc(Color.White, 225f, 90f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2),
                        style = Stroke(size.height * 0.17f, cap = StrokeCap.Round))
                }
            }
            Spacer(Modifier.width(6.dp))
        }
        // Батарея с процентом внутри, как в iOS
        val pct = s.device.batteryPct
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(27.dp, 13.dp)) {
                val bw = size.width - 2.5f.dp.toPx()
                drawRoundRect(Color(0x66FFFFFF), Offset.Zero, Size(bw, size.height), CornerRadius(4.dp.toPx()))
                drawRoundRect(if (pct <= 20) Color(0xFFFF453A) else if (s.device.charging) Color(0xFF30D158) else Color.White,
                    Offset.Zero, Size(bw * (pct / 100f).coerceIn(0.08f, 1f), size.height), CornerRadius(4.dp.toPx()))
                drawRoundRect(Color(0x66FFFFFF), Offset(bw + 1.dp.toPx(), size.height * 0.32f),
                    Size(1.5f.dp.toPx(), size.height * 0.36f), CornerRadius(1.dp.toPx()))
            }
            Text("$pct", color = if (pct > 45) Color.Black else Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold,
                fontFamily = Rounded, modifier = Modifier.padding(end = 2.dp))
        }
    }
}
