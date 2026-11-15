package com.biamox.launcher.ui

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
import kotlinx.coroutines.delay

@Composable
fun LauncherRoot(s: LauncherState) {
    // Фоновые обновления: статистика и почта
    LaunchedEffect(Unit) {
        s.reloadApps()
        while (true) {
            s.refreshStats()
            s.refreshMail()
            delay(s.config.statsRefreshMinutes * 60_000L)
        }
    }
    BackHandler(enabled = s.screen != null || s.searchOpen || s.controlOpen || s.voiceOpen) {
        when {
            s.voiceOpen -> s.closeVoice()
            s.controlOpen -> s.controlOpen = false
            s.searchOpen -> s.searchOpen = false
            else -> s.screen = null
        }
    }
    Box(Modifier.fillMaxSize()) {
        HomeScreen(s)
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
        AnimatedVisibility(s.controlOpen, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            ControlCenter(s) { s.controlOpen = false }
        }
        AnimatedVisibility(s.voiceOpen, enter = fadeIn(), exit = fadeOut()) { VoiceOverlay(s) }
    }
}
