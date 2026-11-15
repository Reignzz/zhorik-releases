package com.biamox.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object B {
    val Navy = Color(0xFF070B1C)
    val Deep = Color(0xFF111735)
    val Violet = Color(0xFF6E56FF)
    val Blue = Color(0xFF2F80FF)
    val Cyan = Color(0xFF32D4F5)
    val Green = Color(0xFF34C759)
    val Red = Color(0xFFFF453A)
    val Orange = Color(0xFFFF9F0A)
    val Yellow = Color(0xFFFFD60A)
    val Pink = Color(0xFFFF375F)
    val Gray = Color(0xFF8E8E93)
    val Text2 = Color(0xB3FFFFFF)
    val Sheet = Color(0xF20D1230)
    val Card = Color(0x1FFFFFFF)
    val Stroke = Color(0x33FFFFFF)

    val Wallpaper = Brush.linearGradient(listOf(Color(0xFF070B1C), Color(0xFF1A1450), Color(0xFF0E2A6B)))
}

/** iOS-«скруглённый квадрат» иконок */
val Squircle = RoundedCornerShape(percent = 23)
val CardShape = RoundedCornerShape(24.dp)

/** «Стекло»: полупрозрачная карточка с тонкой светлой рамкой (как виджеты iOS). */
fun Modifier.glass(shape: Shape = CardShape, alpha: Float = 0.14f): Modifier =
    this.clip(shape)
        .background(Color.White.copy(alpha = alpha))
        .border(0.7.dp, Color.White.copy(alpha = 0.18f), shape)

@Composable
fun BiamoxTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = B.Blue, secondary = B.Violet, tertiary = B.Cyan,
            background = B.Navy, surface = B.Deep, onPrimary = Color.White,
            onBackground = Color.White, onSurface = Color.White, error = B.Red,
        ),
        typography = base.copy(
            displayLarge = TextStyle(fontSize = 84.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp),
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 30.sp),
        ),
        content = content,
    )
}
