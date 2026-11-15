package com.biamox.launcher.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Голосовой ассистент: «светящийся шар» внизу экрана, как Siri. */
@Composable
fun VoiceOverlay(s: LauncherState) {
    val inf = rememberInfiniteTransition(label = "orb")
    val pulse by inf.animateFloat(0.9f, 1.1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
    val scale = if (s.voiceListening) pulse + s.voiceLevel * 0.35f else if (s.claudeBusy) pulse else 1f

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x33000000), Color(0xEE05060F))))
        .clickable { s.closeVoice() }) {
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(24.dp)
            .clickableNoRipple { }, horizontalAlignment = Alignment.CenterHorizontally) {
            if (s.voiceHeard.isNotBlank()) {
                Text("«${s.voiceHeard}»", color = Color.White, fontSize = 22.sp, textAlign = TextAlign.Center)
            } else if (s.voiceListening) {
                Text("Говорите…", color = B.Text2, fontSize = 22.sp)
            }
            if (s.voiceAnswer.isNotBlank()) {
                Text(s.voiceAnswer.replace("**", ""), color = B.Text2, fontSize = 17.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp).heightIn(max = 320.dp).verticalScroll(rememberScrollState()))
            }
            Box(Modifier.padding(top = 26.dp).size(86.dp).scale(scale).clip(CircleShape)
                .background(Brush.sweepGradient(listOf(B.Violet, B.Pink, B.Orange, B.Cyan, B.Blue, B.Violet)))
                .clickable { if (!s.voiceListening) s.startVoice() }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(70.dp).clip(CircleShape).background(Color(0x55000000)), contentAlignment = Alignment.Center) {
                    Text("🎙️", fontSize = 28.sp)
                }
            }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (s.voiceAnswer.isNotBlank() && s.chat.isNotEmpty()) Pill("Открыть в чате", B.Violet) { s.closeVoice(); s.open(Module.CLAUDE) }
                Pill("Закрыть", B.Gray) { s.closeVoice() }
            }
        }
    }
}
