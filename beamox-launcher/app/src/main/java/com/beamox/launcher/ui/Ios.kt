package com.beamox.launcher.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Переключатель iOS 26: широкая «пилюля»-ручка, зелёный трек */
@Composable
fun IosSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val track by animateColorAsState(if (checked) Color(0xFF34C759) else Color(0xFF39393D), label = "track")
    val x by animateDpAsState(if (checked) 25.dp else 2.dp, label = "knob")
    Box(Modifier.width(64.dp).height(28.dp).clip(CircleShape).background(track)
        .toggleable(checked, remember { MutableInteractionSource() }, null, role = Role.Switch, onValueChange = onChange),
        contentAlignment = Alignment.CenterStart) {
        Box(Modifier.offset(x = x).width(37.dp).height(24.dp).shadow(3.dp, CircleShape).clip(CircleShape).background(Color.White))
    }
}

/** Шеврон «›» как в списках iOS */
@Composable
fun IosChevron() {
    Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, tint = Color(0x4DEBEBF5),
        modifier = Modifier.padding(start = 10.dp).size(15.dp))
}

/** Пузырь iMessage с «хвостиком» внизу (справа — мои сообщения, слева — ответы) */
class BubbleShape(private val mine: Boolean, private val tail: Boolean = true) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { 18.dp.toPx() }.coerceAtMost(size.height / 2)
        val t = with(density) { 5.dp.toPx() }
        val w = size.width; val h = size.height
        val body = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, w, h, androidx.compose.ui.geometry.CornerRadius(r)))
        }
        if (!tail) return Outline.Generic(body)
        // «Хвостик» iMessage: выходит из нижнего угла и загибается наружу
        val tp = Path()
        if (mine) {
            tp.moveTo(w - r * 1.2f, h - r)
            tp.lineTo(w - 1f, h - r * 0.9f)
            tp.quadraticBezierTo(w - 1f, h - t * 0.3f, w + t, h)
            tp.quadraticBezierTo(w - t * 1.6f, h + t * 0.15f, w - r * 1.3f, h - t * 0.2f)
            tp.close()
        } else {
            tp.moveTo(r * 1.2f, h - r)
            tp.lineTo(1f, h - r * 0.9f)
            tp.quadraticBezierTo(1f, h - t * 0.3f, -t, h)
            tp.quadraticBezierTo(t * 1.6f, h + t * 0.15f, r * 1.3f, h - t * 0.2f)
            tp.close()
        }
        return Outline.Generic(Path.combine(androidx.compose.ui.graphics.PathOperation.Union, body, tp))
    }
}
