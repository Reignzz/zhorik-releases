package com.beamox.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.beamox.launcher.core.AppEntry
import com.beamox.launcher.core.BeamoxWallpaper
import com.beamox.launcher.core.Dashboard
import com.beamox.launcher.ui.BeamoxTheme
import com.beamox.launcher.ui.LauncherRoot
import com.beamox.launcher.ui.LauncherState
import com.beamox.launcher.ui.Module
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/** Скриншоты экранов лаунчера (рендер без телефона). Запуск: ./gradlew :app:recordPaparazziDebug */
class ScreensTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(softButtons = false),
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 100.0,
    )

    private fun fakeIcon(color: Int, letter: String): androidx.compose.ui.graphics.ImageBitmap {
        val b = Bitmap.createBitmap(168, 168, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(color)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE; textSize = 92f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        c.drawText(letter, 84f, 116f, p)
        return b.asImageBitmap()
    }

    private val apps by lazy {
        listOf(
            Triple("Gmail", "com.google.android.gm", 0xFFEA4335), Triple("Chrome", "com.android.chrome", 0xFF4285F4),
            Triple("Telegram", "org.telegram.messenger", 0xFF2AA3EF), Triple("WhatsApp", "com.whatsapp", 0xFF25D366),
            Triple("Viber", "com.viber.voip", 0xFF7360F2), Triple("Instagram", "com.instagram.android", 0xFFE1306C),
            Triple("Диск", "com.google.android.apps.docs", 0xFF1FA463), Triple("Play Маркет", "com.android.vending", 0xFF01875F),
            Triple("TikTok", "com.zhiliaoapp.musically", 0xFF111111), Triple("YouTube", "com.google.android.youtube", 0xFFFF0000),
        ).map { (l, p, c) -> AppEntry(l, p, "$p.Main", fakeIcon(c.toInt(), l.take(1))) }
    }

    @Composable
    private fun Screen(page: Int = 1, module: Module? = null, setup: (LauncherState) -> Unit = {}) {
        val scope = rememberCoroutineScope()
        val s = LauncherState(null, paparazzi.context, scope, preview = true)
        javaClass.classLoader?.getResourceAsStream("sample_dashboard.json")?.bufferedReader()?.readText()?.let {
            s.dash = Dashboard.parse(JSONObject(it)); s.stats = s.dash!!.toStats()
        }
        s.apps = apps
        s.screen = module
        s.previewPage = page
        setup(s)
        val wp = BeamoxWallpaper.draw(1080, 2340, "Beamox", "Офис в кармане", big = false).asImageBitmap()
        BeamoxTheme {
            Box(Modifier.fillMaxSize()) {
                Image(wp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                LauncherRoot(s)
            }
        }
    }

    @Test fun home() = paparazzi.snapshot { Screen(1) }
    @Test fun today() = paparazzi.snapshot { Screen(0) }
    @Test fun stats() = paparazzi.snapshot { Screen(module = Module.STATS) }
    @Test fun settings() = paparazzi.snapshot { Screen(module = Module.SETTINGS) }
    @Test fun control() = paparazzi.snapshot { Screen(1) { it.controlOpen = true } }
    @Test fun claude() = paparazzi.snapshot { Screen(module = Module.CLAUDE) {
        it.chat.add(com.beamox.launcher.core.ChatMessage("user", "Сделай сводку дня"))
        it.chat.add(com.beamox.launcher.core.ChatMessage("assistant", "Сегодня выложено 10 роликов, охват 390. Лучше всего зашёл Instagram — 371 просмотр. Facebook Reels недоступны: стоит проверить подключение."))
    } }
    @Test fun office() = paparazzi.snapshot { Screen(module = Module.OFFICE) { it.tasks.add(com.beamox.launcher.core.OfficeTask(1, "Позвонить клиенту", false)) } }
    @Test fun mail() = paparazzi.snapshot { Screen(module = Module.MAIL) }
    @Test fun voice() = paparazzi.snapshot { Screen(1) { it.voiceOpen = true; it.voiceHeard = "открой офис"; it.voiceAnswer = "Открываю офис" } }
    @Test fun library() = paparazzi.snapshot { Screen(2) }
    @Test fun lockscreen() = paparazzi.snapshot {
        Image(BeamoxWallpaper.draw(1080, 2340, "Beamox", "Офис в кармане", big = true).asImageBitmap(), null,
            Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
