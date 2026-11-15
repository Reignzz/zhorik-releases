package com.beamox.launcher.ui

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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Settings
import com.beamox.launcher.core.AppRepository

/**
 * Системные разделы телефона в едином iOS-стиле. Открывают стандартные приложения Moto/Google
 * (Телефон, Сообщения и т.д.) — иконки одинаковые, как на iPhone.
 */
enum class SysApp(val title: String, val icon: ImageVector?, val c1: Color, val c2: Color, val glyph: Color = Color.White) {
    PHONE("Телефон", Icons.Rounded.Phone, Color(0xFF6BE58A), Color(0xFF25B14C)),
    MESSAGES("Сообщения", Icons.Rounded.ChatBubble, Color(0xFF6BE58A), Color(0xFF25B14C)),
    CONTACTS("Контакты", Icons.Rounded.Person, Color(0xFFC9C9CF), Color(0xFF8E8E96)),
    CAMERA("Камера", Icons.Rounded.PhotoCamera, Color(0xFFD7D7DC), Color(0xFF9A9AA2), Color(0xFF2B2B30)),
    PHOTOS("Фото", null, Color(0xFFFFFFFF), Color(0xFFF1F1F4)),
    CLOCK("Часы", null, Color(0xFF1C1C1E), Color(0xFF000000)),
    CALENDAR("Календарь", null, Color(0xFFFFFFFF), Color(0xFFF2F2F5)),
    CALCULATOR("Калькулятор", Icons.Rounded.Calculate, Color(0xFF3A3A3C), Color(0xFF1C1C1E), Color(0xFFFF9F0A)),
    BROWSER("Браузер", Icons.Rounded.Explore, Color(0xFF7CC8FF), Color(0xFF1E7BFF)),
    MAPS("Карты", Icons.Rounded.Map, Color(0xFF8BE08B), Color(0xFF34B36A)),
    FILES("Файлы", Icons.Rounded.Folder, Color(0xFF7CC8FF), Color(0xFF1E6FE0)),
    MUSIC("Музыка", Icons.Rounded.MusicNote, Color(0xFFFF7A8E), Color(0xFFE5294B)),
    SYSTEM_SETTINGS("Система", Icons.Rounded.Settings, Color(0xFFA4A4AB), Color(0xFF66666E));

    val brush get() = Brush.verticalGradient(listOf(c1, c2))

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
        act.startActivity(a.intent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) { false }
    if (!ok && a.fallbackPackages().none { appsRepo.launchPackage(it) }) toast("Нет приложения «${a.title}»")
}

@Composable
fun SysIcon(a: SysApp, size: Dp = 64.dp) {
    when (a) {
        SysApp.CALENDAR -> GlyphIcon(a.c1, a.c2, null, size) { CalendarFace(size) }
        SysApp.CLOCK -> GlyphIcon(a.c1, a.c2, null, size) { ClockFace(size * 0.86f) }
        SysApp.PHOTOS -> GlyphIcon(a.c1, a.c2, null, size) { PhotosFlower(size * 0.78f) }
        else -> GlyphIcon(a.c1, a.c2, a.icon, size, glyphColor = a.glyph)
    }
}

/** Календарь как в iOS: день недели красным и большое число */
@Composable
private fun CalendarFace(size: Dp) {
    val now = java.util.Calendar.getInstance()
    val dow = java.text.SimpleDateFormat("EE", java.util.Locale("ru")).format(now.time).replaceFirstChar { it.uppercase() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(dow, color = Color(0xFFFF3B30), fontSize = (size.value * 0.19f).sp, fontWeight = FontWeight.Bold,
            fontFamily = Rounded, lineHeight = (size.value * 0.2f).sp)
        Text("${now.get(java.util.Calendar.DAY_OF_MONTH)}", color = Color(0xFF1C1C1E), fontSize = (size.value * 0.5f).sp,
            fontWeight = FontWeight.Normal, fontFamily = Rounded, lineHeight = (size.value * 0.52f).sp)
    }
}

/** Часы как в iOS: белый циферблат, стрелки показывают текущее время */
@Composable
private fun ClockFace(size: Dp) {
    val now = java.util.Calendar.getInstance()
    Canvas(Modifier.size(size)) {
        val c = center; val r = this.size.minDimension / 2
        drawCircle(Color.White, r, c)
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0)
            val p1 = Offset(c.x + (r * 0.80f) * kotlin.math.sin(a).toFloat(), c.y - (r * 0.80f) * kotlin.math.cos(a).toFloat())
            val p2 = Offset(c.x + (r * 0.90f) * kotlin.math.sin(a).toFloat(), c.y - (r * 0.90f) * kotlin.math.cos(a).toFloat())
            drawLine(Color(0xFF1C1C1E), p1, p2, strokeWidth = r * 0.05f, cap = StrokeCap.Round)
        }
        fun hand(angleDeg: Double, len: Float, w: Float, col: Color) {
            val a = Math.toRadians(angleDeg)
            drawLine(col, c, Offset(c.x + len * kotlin.math.sin(a).toFloat(), c.y - len * kotlin.math.cos(a).toFloat()), strokeWidth = w, cap = StrokeCap.Round)
        }
        val h = now.get(java.util.Calendar.HOUR) ; val m = now.get(java.util.Calendar.MINUTE)
        hand((h + m / 60.0) * 30, r * 0.5f, r * 0.09f, Color(0xFF1C1C1E))
        hand(m * 6.0, r * 0.72f, r * 0.07f, Color(0xFF1C1C1E))
        hand(now.get(java.util.Calendar.SECOND) * 6.0, r * 0.78f, r * 0.03f, Color(0xFFFF9500))
        drawCircle(Color(0xFFFF9500), r * 0.06f, c)
    }
}

/** «Фото»: разноцветный цветок из лепестков */
@Composable
private fun PhotosFlower(size: Dp) {
    val cols = listOf(0xFFFFCC00, 0xFFFF9500, 0xFFFF3B30, 0xFFFF2D92, 0xFFAF52DE, 0xFF5856D6, 0xFF007AFF, 0xFF34C759)
    Canvas(Modifier.size(size)) {
        val c = center; val r = this.size.minDimension / 2
        cols.forEachIndexed { i, col ->
            rotate(i * 45f, c) {
                drawOval(Color(col).copy(alpha = 0.85f), topLeft = Offset(c.x - r * 0.2f, c.y - r * 0.95f),
                    size = androidx.compose.ui.geometry.Size(r * 0.4f, r * 0.62f))
            }
        }
    }
}

@Composable
fun SysCell(s: LauncherState, a: SysApp, label: Boolean = true, size: Dp = 64.dp) {
    Column(Modifier.bounceClick { s.haptic(); s.openSys(a) }, horizontalAlignment = Alignment.CenterHorizontally) {
        SysIcon(a, size)
        if (label) IconLabel(a.title)
    }
}
