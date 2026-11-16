package com.beamox.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamox.launcher.core.AiApp
import com.beamox.launcher.core.PhoneSetup

/** Плашка на главном экране, пока Beamox не выбран главным экраном. */
@Composable
fun HomeDefaultBanner(s: LauncherState, modifier: Modifier = Modifier) {
    LiquidGlass(CircleShape, modifier.height(40.dp).bounceClick { PhoneSetup.requestDefaultHome(s.activity, s.ctx) },
        tint = Color(0x55F59E0B), contentAlignment = Alignment.Center) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Home, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Сделать Beamox главным экраном", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Карточка «Настройка телефона» на странице «Сегодня»: что осталось включить, по кнопке на пункт. */
@Composable
fun SetupCard(s: LauncherState) {
    val st = s.setup
    PanelCard("Настройка телефона", "осталось ${st.missing}") {
        SetupStep("Главный экран — Beamox", "Чтобы кнопка «Домой» открывала Beamox", st.defaultHome) {
            PhoneSetup.requestDefaultHome(s.activity, s.ctx)
        }
        SetupStep("Навигация жестами", "Внизу одна полоска, как на iPhone: «Навигация жестами»", st.gestures) {
            PhoneSetup.openGestureSettings(s.act)
        }
        SetupStep("Островок: поверх приложений", "Капсула у камеры как на iPhone — включите «Разрешить»", st.overlay) {
            PhoneSetup.openOverlaySettings(s.act)
        }
        SetupStep("Островок: доступ к уведомлениям",
            "Включите «Островок Beamox». Если серый — «О приложении» → ⋮ → «Разрешить ограниченные настройки»",
            st.notifications) { PhoneSetup.openNotificationAccess(s.act) }
        if (!st.notifications) Text("Открыть «О приложении»", color = B.Accent, fontSize = 14.sp,
            modifier = Modifier.padding(start = 34.dp, bottom = 6.dp).clickable { PhoneSetup.openAppInfo(s.act) })
        SetupStep("Панель Beamox", "Откройте ссылку настройки из Telegram на этом телефоне", st.panel) {
            s.pastePanelLink()
        }
    }
}

@Composable
private fun SetupStep(title: String, hint: String, done: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).then(if (done) Modifier else Modifier.clickable { onClick() })
        .padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        if (done) Icon(Icons.Rounded.CheckCircle, null, tint = B.Green, modifier = Modifier.size(24.dp))
        else Box(Modifier.size(24.dp).clip(CircleShape).border(2.dp, B.Amber, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (done) B.Dim else B.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (!done) Text(hint, color = B.Dim, fontSize = 13.sp)
        }
        if (!done) Text("Включить", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(CircleShape).background(B.Accent).padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

/** Кнопки «спросить в приложении»: Claude, ChatGPT, Gemini — под вашими аккаунтами, без ключей. */
@Composable
fun AiAppsRow(s: LauncherState, text: String, onSent: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(if (text.isBlank()) "Открыть:" else "Спросить в:", color = B.Dim, fontSize = 13.sp)
        AiApp.entries.forEach { a ->
            val col = when (a) { AiApp.CLAUDE -> Color(0xFFD97757); AiApp.CHATGPT -> Color(0xFF10A37F); AiApp.GEMINI -> Color(0xFF4F7DF3) }
            Row(Modifier.clip(CircleShape).border(1.dp, Color(0xFF3A3A3C), CircleShape)
                .clickableNoRipple { s.askAi(a, text); if (text.isNotBlank()) onSent() }
                .padding(horizontal = 11.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(col))
                Spacer(Modifier.width(6.dp))
                Text(a.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
