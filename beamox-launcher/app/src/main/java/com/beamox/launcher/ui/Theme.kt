package com.beamox.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamox.launcher.R

/** Шрифт интерфейса: Inter — ближайший свободный аналог SF Pro (системный шрифт iOS) */
val Rounded = FontFamily(
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

/** Палитра: системные цвета iOS (тёмная тема) + акценты Beamox */
object B {
    val Bg1 = Color(0xFF000000)
    val Bg2 = Color(0xFF000000)
    val Text = Color(0xFFFFFFFF)
    val Dim = Color(0x99EBEBF5)      // secondaryLabel
    val Faint = Color(0xFF8E8E93)    // systemGray
    val Accent = Color(0xFF0A84FF)   // systemBlue (dark)
    val Green = Color(0xFF4CD787)
    val Amber = Color(0xFFF5B841)
    val Red = Color(0xFFFF5F57)
    val Coral = Color(0xFFE8794F)
    val Blue = Color(0xFF6EA8FF)
    val Violet = Color(0xFF8B7BFF)
    val Pink = Color(0xFFFF6FB1)
    val Cyan = Color(0xFF2EC4D6)

    // старые имена — для совместимости
    val Gray = Faint
    val Text2 = Dim
    val Orange = Amber
    val Yellow = Color(0xFFFFD36B)
    val Navy = Bg2

    /** Карточки-группы (как в Настройках iOS) */
    val Group = Color(0xFF1C1C1E)    // secondarySystemGroupedBackground
    val Separator = Color(0x5454589E)

    // Градиенты колец/полос (как в панели)
    val GWeek = listOf(Color(0xFFFFB36B), Color(0xFFFF6B4A))
    val GFive = listOf(Color(0xFFB794FF), Color(0xFFFF6FB1))
    val GMint = listOf(Color(0xFF5EE6A8), Color(0xFF2EC4D6))
    val GBlue = listOf(Color(0xFF7AA2FF), Color(0xFF8B7BFF))
    val GGold = listOf(Color(0xFFFFD36B), Color(0xFFFF9F43))
    val GHot = listOf(Color(0xFFFF8A7A), Color(0xFFFF3D5A))
    val GCoral = listOf(Color(0xFFFF9A6B), Color(0xFFE8613C))

    val platform = mapOf(
        "YouTube" to listOf(Color(0xFFFF5A4E), Color(0xFFFF2D2D)),
        "TikTok" to listOf(Color(0xFF25F4EE), Color(0xFFFE2C55)),
        "Instagram" to listOf(Color(0xFFF9A03F), Color(0xFFE1306C)),
        "Facebook" to listOf(Color(0xFF6EA8FF), Color(0xFF2D74F5)),
        "Telegram" to listOf(Color(0xFF5AC8FA), Color(0xFF2AA3EF)),
    )
}

/** iOS-«суперэллипс» иконок */
val Squircle: androidx.compose.ui.graphics.Shape = SuperellipseShape(5.0)
val CardShape = RoundedCornerShape(26.dp)

/** Стекло iOS 26: полупрозрачный слой + световой блик + тонкая рамка */
fun Modifier.glass(shape: Shape = CardShape, alpha: Float = 0.08f): Modifier =
    this.clip(shape)
        .background(Color(0x66140F18))
        .background(Brush.linearGradient(listOf(Color.White.copy(alpha = alpha + 0.05f), Color.White.copy(alpha = alpha * 0.5f)),
            start = Offset.Zero, end = Offset(0f, Float.POSITIVE_INFINITY)))
        .border(0.8.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.14f))), shape)

/** Фон экранов разделов — как фон панели Beamox */
val ScreenBg: Brush get() = Brush.verticalGradient(listOf(B.Bg1, B.Bg2))

@Composable
fun BeamoxTheme(content: @Composable () -> Unit) {
    val t = Typography()
    fun TextStyle.r() = copy(fontFamily = Rounded)
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = B.Coral, secondary = B.Violet, tertiary = B.Cyan,
            background = B.Bg2, surface = B.Group, onPrimary = Color.White, surfaceContainerHigh = Color(0xFF2A2330),
            onBackground = B.Text, onSurface = B.Text, error = B.Red,
        ),
        typography = Typography(
            displayLarge = t.displayLarge.r(), displayMedium = t.displayMedium.r(), displaySmall = t.displaySmall.r(),
            headlineLarge = t.headlineLarge.r(), headlineMedium = t.headlineMedium.r(), headlineSmall = t.headlineSmall.r(),
            titleLarge = t.titleLarge.r().copy(fontWeight = FontWeight.ExtraBold, fontSize = 30.sp),
            titleMedium = t.titleMedium.r(), titleSmall = t.titleSmall.r(),
            bodyLarge = t.bodyLarge.r(), bodyMedium = t.bodyMedium.r(), bodySmall = t.bodySmall.r(),
            labelLarge = t.labelLarge.r().copy(fontWeight = FontWeight.SemiBold), labelMedium = t.labelMedium.r(), labelSmall = t.labelSmall.r(),
        ),
        content = content,
    )
}
