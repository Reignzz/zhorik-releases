package com.beamox.launcher.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * «Непрерывные» углы как у иконок iOS: суперэллипс (кривая Ламе, n≈5),
 * а не обычное скругление — угол переходит в сторону плавно.
 */
class SuperellipseShape(private val n: Double = 5.0) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val a = size.width / 2.0; val b = size.height / 2.0
        val p = Path()
        val steps = 96
        for (i in 0..steps) {
            val t = 2 * Math.PI * i / steps
            val c = cos(t); val s = sin(t)
            val x = a + a * sign(c) * abs(c).pow(2.0 / n)
            val y = b + b * sign(s) * abs(s).pow(2.0 / n)
            if (i == 0) p.moveTo(x.toFloat(), y.toFloat()) else p.lineTo(x.toFloat(), y.toFloat())
        }
        p.close()
        return Outline.Generic(p)
    }
}

/** Обои, которые лаунчер рисует сам — чтобы стекло могло их размывать */
val LocalWallpaper = compositionLocalOf<ImageBitmap?> { null }

/**
 * Liquid Glass (iOS 26): под панелью — размытые обои, лёгкий тон, яркий блик по краю
 * (сверху-слева и снизу-справа) и мягкое внутреннее свечение.
 */
@Composable
fun LiquidGlass(
    shape: Shape,
    modifier: Modifier = Modifier,
    tint: Color = Color(0x26FFFFFF),
    blurRadius: Float = 60f,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val wp = LocalWallpaper.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.onGloballyPositioned { pos = it.positionInRoot() }.clip(shape), contentAlignment = contentAlignment) {
        if (wp != null && Build.VERSION.SDK_INT >= 31) {
            Canvas(Modifier.matchParentSize().graphicsLayer {
                renderEffect = BlurEffect(blurRadius, blurRadius, TileMode.Clamp)
                clip = true
            }) {
                drawImage(wp, srcOffset = IntOffset.Zero, srcSize = IntSize(wp.width, wp.height),
                    dstOffset = IntOffset(-pos.x.toInt(), -pos.y.toInt()), dstSize = IntSize(wp.width, wp.height))
            }
        } else {
            Box(Modifier.matchParentSize().background(Color(0x99303035)))
        }
        // тон и внутреннее свечение сверху
        Box(Modifier.matchParentSize().background(tint)
            .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.10f), 0.35f to Color.Transparent)))
        // световой обод: ярче сверху-слева и снизу-справа, как у стекла iOS 26
        Box(Modifier.matchParentSize().border(1.dp, Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.55f), 0.3f to Color.White.copy(alpha = 0.10f),
            0.7f to Color.White.copy(alpha = 0.06f), 1f to Color.White.copy(alpha = 0.35f)), shape))
        content()
    }
}
