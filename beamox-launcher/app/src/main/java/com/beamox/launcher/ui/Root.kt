package com.beamox.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay

@Composable
fun LauncherRoot(s: LauncherState) {
    // Фоновые обновления: статистика и почта
    LaunchedEffect(Unit) {
        if (s.preview) return@LaunchedEffect
        s.reloadApps()
        while (true) {
            s.refreshStats()
            s.refreshMail()
            delay(s.config.statsRefreshMinutes * 60_000L)
        }
    }
    BackHandler(enabled = s.detail != null || s.screen != null || s.searchOpen || s.controlOpen || s.voiceOpen) {
        when {
            s.detail != null -> s.detail = null
            s.voiceOpen -> s.closeVoice()
            s.controlOpen -> s.controlOpen = false
            s.searchOpen -> s.searchOpen = false
            else -> s.screen = null
        }
    }
    // Обои рисует сам лаунчер (те же, что на системном экране) — чтобы стекло могло их размывать
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val dens = androidx.compose.ui.platform.LocalDensity.current.density
    val wpW = (cfg.screenWidthDp * dens).toInt().coerceAtLeast(360)
    val wpH = ((cfg.screenHeightDp + 120) * dens).toInt().coerceAtLeast(720)
    val wallpaper = androidx.compose.runtime.remember(wpW, wpH) {
        val custom = runCatching { java.io.File(s.ctx.getExternalFilesDir(null), "homescreen.png") }.getOrNull()
        val bmp = if (custom != null && custom.exists()) android.graphics.BitmapFactory.decodeFile(custom.path)
        else com.beamox.launcher.core.BeamoxWallpaper.draw(wpW, wpH, s.config.company, s.config.tagline, big = false)
        android.graphics.Bitmap.createScaledBitmap(bmp, wpW, wpH, true).asImageBitmap()
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalWallpaper provides wallpaper, LocalPick provides { d: StatDetail -> s.detail = d }) {
    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.Image(wallpaper, null, Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop, alignment = androidx.compose.ui.Alignment.TopStart)
        val overlay = s.controlOpen || s.voiceOpen || s.screen != null
        val blur by androidx.compose.animation.core.animateDpAsState(if (overlay) 28.dp else 0.dp, label = "blur")
        Box(Modifier.fillMaxSize().blur(blur)) { HomeScreen(s) }
        Module.entries.forEach { m ->
            AnimatedVisibility(s.screen == m,
                enter = scaleIn(initialScale = 0.85f) + fadeIn(),
                exit = scaleOut(targetScale = 0.85f) + fadeOut()) {
                when (m) {
                    Module.CLAUDE -> ClaudeScreen(s)
                    Module.STATS -> StatsScreen(s)
                    Module.STUDIO -> StudioScreen(s)
                    Module.OFFICE -> OfficeScreen(s)
                    Module.MAIL -> MailScreen(s)
                    Module.SETTINGS -> SettingsScreen(s)
                    Module.VOICE -> Unit
                }
            }
        }
        AnimatedVisibility(s.controlOpen, enter = fadeIn() + scaleIn(initialScale = 0.94f), exit = fadeOut() + scaleOut(targetScale = 0.96f)) {
            ControlCenter(s) { s.controlOpen = false }
        }
        // Подробности по нажатию на цифру статистики — лист снизу, как в iOS
        val lastDetail = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<StatDetail?>(null) }
        s.detail?.let { lastDetail.value = it }
        AnimatedVisibility(s.detail != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            lastDetail.value?.let { StatDetailSheet(s, it) { s.detail = null } }
        }
        AnimatedVisibility(s.voiceOpen, enter = fadeIn(), exit = fadeOut()) { VoiceOverlay(s) }
        if (!s.controlOpen) IosStatusBar(s)
    }
    }
}
