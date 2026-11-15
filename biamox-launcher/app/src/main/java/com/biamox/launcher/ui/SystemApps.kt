package com.biamox.launcher.ui

import android.content.Intent
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biamox.launcher.core.AppRepository

/**
 * Системные разделы телефона в едином iOS-стиле. Открывают стандартные приложения Moto/Google
 * (Телефон, Сообщения и т.д.) — иконки одинаковые, как на iPhone.
 */
enum class SysApp(val title: String, val emoji: String, val c1: Color, val c2: Color) {
    PHONE("Телефон", "📞", Color(0xFF5BE37D), Color(0xFF1FB64A)),
    MESSAGES("Сообщения", "💬", Color(0xFF5BE37D), Color(0xFF1FB64A)),
    CONTACTS("Контакты", "👥", Color(0xFFB8B8BE), Color(0xFF7C7C84)),
    CAMERA("Камера", "📷", Color(0xFF9A9AA2), Color(0xFF4A4A50)),
    PHOTOS("Фото", "🌼", Color(0xFFFFFFFF), Color(0xFFE9E9EF)),
    CLOCK("Часы", "⏰", Color(0xFF2C2C2E), Color(0xFF000000)),
    CALENDAR("Календарь", "📅", Color(0xFFFFFFFF), Color(0xFFEDEDF2)),
    CALCULATOR("Калькулятор", "🧮", Color(0xFF3A3A3C), Color(0xFF1C1C1E)),
    BROWSER("Браузер", "🧭", Color(0xFF6EC6FF), Color(0xFF1E7BFF)),
    MAPS("Карты", "🗺️", Color(0xFF8BE08B), Color(0xFF3FAF6A)),
    FILES("Файлы", "📁", Color(0xFF64B5FF), Color(0xFF1E6FE0)),
    MUSIC("Музыка", "🎵", Color(0xFFFF6B81), Color(0xFFE5294B)),
    SYSTEM_SETTINGS("Система", "🛠️", Color(0xFF9A9AA2), Color(0xFF5A5A62));

    val brush get() = Brush.linearGradient(listOf(c1, c2))

    fun intent(): Intent = when (this) {
        PHONE -> Intent(Intent.ACTION_DIAL)
        MESSAGES -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
        CONTACTS -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS)
        CAMERA -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        PHOTOS -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)
        CLOCK -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
        CALENDAR -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
        CALCULATOR -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)
        BROWSER -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
        MAPS -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS)
        FILES -> Intent(Intent.ACTION_MAIN).setClassName("com.google.android.documentsui", "com.android.documentsui.files.FilesActivity")
        MUSIC -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
        SYSTEM_SETTINGS -> Intent(Settings.ACTION_SETTINGS)
    }

    /** Запасные варианты, если основной intent не сработал. */
    fun fallbackPackages(): List<String> = when (this) {
        FILES -> listOf("com.google.android.apps.nbu.files", "com.android.documentsui", "com.google.android.documentsui")
        PHOTOS -> listOf("com.google.android.apps.photos")
        MESSAGES -> listOf("com.google.android.apps.messaging")
        CLOCK -> listOf("com.google.android.deskclock")
        CALCULATOR -> listOf("com.google.android.calculator")
        MUSIC -> listOf("com.google.android.apps.youtube.music")
        else -> emptyList()
    }

    companion object {
        /** Сетка «Телефон» на главном экране (как первая страница iPhone) */
        val homeRow = listOf(MESSAGES, CONTACTS, PHOTOS, CALENDAR, CLOCK, CALCULATOR, BROWSER, MAPS, FILES, SYSTEM_SETTINGS)
    }
}

fun LauncherState.openSys(a: SysApp) {
    val ok = try {
        activity.startActivity(a.intent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) { false }
    if (!ok && a.fallbackPackages().none { appsRepo.launchPackage(it) }) toast("Нет приложения «${a.title}»")
}

@Composable
fun SysIcon(a: SysApp, size: Dp = 60.dp) {
    Box(Modifier.size(size).clip(Squircle).background(a.brush), contentAlignment = Alignment.Center) {
        Text(a.emoji, fontSize = (size.value * 0.5f).sp)
    }
}

@Composable
fun SysCell(s: LauncherState, a: SysApp, label: Boolean = true) {
    Column(Modifier.clickable { s.haptic(); s.openSys(a) }, horizontalAlignment = Alignment.CenterHorizontally) {
        SysIcon(a)
        if (label) IconLabel(a.title)
    }
}
