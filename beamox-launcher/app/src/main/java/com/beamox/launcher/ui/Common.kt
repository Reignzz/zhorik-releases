package com.beamox.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** Разделы Beamox внутри лаунчера */
enum class Module(val title: String, val icon: ImageVector?, val c1: Color, val c2: Color) {
    CLAUDE("Claude", null, Color(0xFFE38A63), Color(0xFFC9623C)),
    STATS("Статистика", Icons.Rounded.BarChart, Color(0xFF5EE6A8), Color(0xFF1FA971)),
    STUDIO("Студия", Icons.Rounded.Movie, Color(0xFFFF8A7A), Color(0xFFFF3D5A)),
    OFFICE("Офис", Icons.Rounded.BusinessCenter, Color(0xFF7AA2FF), Color(0xFF4A63E8)),
    MAIL("Почта", Icons.Rounded.Email, Color(0xFF5AC8FA), Color(0xFF1E7BEF)),
    VOICE("Голос", Icons.Rounded.GraphicEq, Color(0xFFB794FF), Color(0xFF7448F0)),
    SETTINGS("Настройки", Icons.Rounded.Settings, Color(0xFF9C9CA3), Color(0xFF5E5E66));

    val brush get() = Brush.verticalGradient(listOf(c1, c2))
    val emoji get() = "" // совместимость
}

/** Иконка в стиле iOS 26: цветной суперэллипс, мягкий блик сверху, белый символ */
@Composable
fun GlyphIcon(
    c1: Color, c2: Color, icon: ImageVector?, size: Dp = 60.dp,
    glyphColor: Color = Color.White, glyphScale: Float = 0.52f,
    custom: (@Composable () -> Unit)? = null,
) {
    Box(
        Modifier.size(size).clip(Squircle).background(Brush.verticalGradient(listOf(c1, c2)))
            .drawWithContent {
                drawContent()
                // блик «жидкого стекла»
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), endY = this.size.height * 0.55f))
            }
            .border(0.6.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.05f))), Squircle),
        contentAlignment = Alignment.Center,
    ) {
        if (custom != null) custom()
        else if (icon != null) Icon(icon, null, tint = glyphColor, modifier = Modifier.size(size * glyphScale))
    }
}

/** Знак Claude — коралловые лучи (как в панели Beamox) */
@Composable
fun ClaudeBurst(size: Dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val s = this.size.minDimension / 40f
        for (k in 0 until 12) {
            val a = Math.PI / 6 * k + .13
            val r1 = 4.5f * s; val r2 = (if (k % 2 == 1) 15f else 18f) * s
            drawLine(color, Offset(c.x + r1 * cos(a).toFloat(), c.y + r1 * sin(a).toFloat()),
                Offset(c.x + r2 * cos(a).toFloat(), c.y + r2 * sin(a).toFloat()), strokeWidth = 3.6f * s, cap = StrokeCap.Round)
        }
        drawCircle(color, 4.6f * s, c)
    }
}

@Composable
fun ModuleIcon(m: Module, size: Dp = 64.dp) {
    if (m == Module.CLAUDE) GlyphIcon(m.c1, m.c2, null, size) { ClaudeBurst(size * 0.62f) }
    else GlyphIcon(m.c1, m.c2, m.icon, size)
}

val LabelStyle = TextStyle(
    fontFamily = Rounded, fontSize = 12.5.sp, fontWeight = FontWeight.Normal, color = Color.White, letterSpacing = (-0.1).sp,
    shadow = Shadow(Color(0x40000000), Offset(0f, 1f), 3f),
)

@Composable
fun IconLabel(text: String) {
    Text(text, style = LabelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
}

/** Полноэкранный раздел с большим заголовком как в iOS */
@Composable
fun ModuleSheet(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Color.Black).padding(top = IosStatusBarHeight)) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.clip(CircleShape).clickable { onBack() }.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, "Назад", tint = B.Accent, modifier = Modifier.size(20.dp))
                Text("Назад", color = B.Accent, fontSize = 17.sp, fontWeight = FontWeight.Normal)
            }
            Spacer(Modifier.weight(1f))
            actions()
        }
        Text(title, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = B.Text, letterSpacing = (-0.6).sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp))
        content()
    }
}

/** Свечение панели: коралл справа сверху, розовый слева снизу */
val PanelGlow: Brush
    get() = Brush.radialGradient(listOf(Color(0x38FF7A50), Color.Transparent), center = Offset(1400f, 0f), radius = 1300f)

@Composable
fun SectionTitle(t: String) {
    Text(t.uppercase(), color = B.Faint, fontSize = 13.sp, fontWeight = FontWeight.Normal,
        modifier = Modifier.padding(start = 34.dp, top = 24.dp, bottom = 7.dp))
}

/** Группа строк как в Настройках iOS */
@Composable
fun Group(content: @Composable ColumnScope.() -> Unit) {
    // Карточка как в Настройках iOS 26; разделитель над первой строкой закрываем
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(B.Group)
        .drawWithContent { drawContent(); drawRect(B.Group, size = androidx.compose.ui.geometry.Size(size.width, 1.5.dp.toPx())) },
        content = content)
}

/** Поисковый запрос экрана настроек — строки, не подходящие под него, скрываются */
val LocalRowFilter = androidx.compose.runtime.compositionLocalOf { "" }

/**
 * Строка как в Настройках iOS: цветной квадрат с символом, заголовок, значение, шеврон.
 * emoji оставлен для совместимости, но вместо него лучше icon.
 */
@Composable
fun RowItem(
    title: String,
    subtitle: String? = null,
    emoji: String? = null,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
    iconColor: Color = B.Coral,
    end: @Composable (() -> Unit)? = null,
) {
    val q = LocalRowFilter.current
    if (q.isNotBlank() && !title.contains(q, true) && subtitle?.contains(q, true) != true) return
    val hasIcon = icon != null || emoji != null
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            // разделитель iOS: от начала текста до правого края
            .drawBehind {
                val x = if (hasIcon) 57.dp.toPx() else 16.dp.toPx()
                drawLine(Color(0xFF38383A), androidx.compose.ui.geometry.Offset(x, 0f),
                    androidx.compose.ui.geometry.Offset(size.width, 0f), strokeWidth = 0.5.dp.toPx())
            }
            .padding(start = 14.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(28.dp).clip(SuperellipseShape(4.2)).background(iconColor),
                contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp)) }
            Spacer(Modifier.width(15.dp))
        } else if (emoji != null) {
            val (ic, col) = emojiIcon(emoji)
            Box(Modifier.size(28.dp).clip(SuperellipseShape(4.2)).background(col),
                contentAlignment = Alignment.Center) { Icon(ic, null, tint = Color.White, modifier = Modifier.size(19.dp)) }
            Spacer(Modifier.width(15.dp))
        } else Spacer(Modifier.width(2.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = B.Text, fontSize = 17.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.4).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = Color(0x99EBEBF5), fontSize = 14.sp, letterSpacing = (-0.2).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) Text(trailing, color = Color(0x99EBEBF5), fontSize = 17.sp, letterSpacing = (-0.4).sp,
            maxLines = 1, modifier = Modifier.padding(start = 8.dp))
        end?.invoke()
        if (onClick != null && end == null) IosChevron()
    }
}

fun Color.darken(f: Float = 0.78f) = Color(red * f, green * f, blue * f, alpha)

@Composable
fun Pill(text: String, color: Color = B.Coral, onClick: () -> Unit) {
    Box(Modifier.clip(CircleShape).background(color.copy(alpha = 0.18f)).clickable { onClick() }
        .padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(text, color = color.lighten(0.15f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

fun Color.lighten(f: Float = 0.35f) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)

/** Статус-«пилюля» как в панели */
@Composable
fun StatusPill(text: String, color: Color) {
    Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.17f)).padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, color = B.Faint, fontSize = 13.5.sp, modifier = modifier.padding(horizontal = 34.dp, vertical = 6.dp))
}

val ListPadding = PaddingValues(bottom = 48.dp)
val Gap = Arrangement.spacedBy(10.dp)

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

/** Карточка панели: стекло, скругление 24, заголовок капсом */
@Composable
fun PanelCard(title: String, right: String = "", modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
              content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
        .clip(RoundedCornerShape(26.dp)).background(B.Group)
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
        .padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 2.dp, bottom = 12.dp)) {
            Text(title, color = B.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
            Spacer(Modifier.weight(1f))
            if (right.isNotBlank()) Text(right, color = B.Faint, fontSize = 15.sp)
        }
        content()
    }
}

/** Нажатие как в iOS: иконка слегка «проседает», без серой ряби */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Modifier.bounceClick(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier {
    val src = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed = src.collectIsPressedAsState()
    val sc = androidx.compose.animation.core.animateFloatAsState(if (pressed.value) 0.88f else 1f, label = "bounce")
    return this
        .graphicsLayer { scaleX = sc.value; scaleY = sc.value; alpha = if (pressed.value) 0.85f else 1f }
        .combinedClickable(interactionSource = src, indication = null, onClick = onClick, onLongClick = onLongClick)
}

/** Эмодзи из конфигурации → символ iOS-стиля и цвет */
fun emojiIcon(e: String): Pair<ImageVector, Color> = when (e) {
    "✉️" -> androidx.compose.material.icons.Icons.Rounded.Email to Color(0xFF0A84FF)
    "👤" -> androidx.compose.material.icons.Icons.Rounded.Person to Color(0xFF8E8E93)
    "🎥" -> androidx.compose.material.icons.Icons.Rounded.Videocam to Color(0xFFFF453A)
    "🖼" -> androidx.compose.material.icons.Icons.Rounded.PhotoLibrary to Color(0xFFFF9500)
    "✳️" -> androidx.compose.material.icons.Icons.Rounded.AutoAwesome to B.Coral
    "✏️" -> androidx.compose.material.icons.Icons.Rounded.Edit to Color(0xFFFF9500)
    "📮" -> androidx.compose.material.icons.Icons.Rounded.Inbox to Color(0xFFFF453A)
    "🎉" -> androidx.compose.material.icons.Icons.Rounded.Celebration to Color(0xFF30D158)
    "📝" -> androidx.compose.material.icons.Icons.Rounded.Notes to Color(0xFFFFD60A)
    "🌐" -> androidx.compose.material.icons.Icons.Rounded.Language to Color(0xFF40C8E0)
    "📁" -> androidx.compose.material.icons.Icons.Rounded.Folder to Color(0xFF0A84FF)
    "📅" -> androidx.compose.material.icons.Icons.Rounded.CalendarMonth to Color(0xFFFF453A)
    "📸" -> androidx.compose.material.icons.Icons.Rounded.CameraAlt to Color(0xFFE1306C)
    "🎵" -> androidx.compose.material.icons.Icons.Rounded.MusicNote to Color(0xFF111111)
    "▶️" -> androidx.compose.material.icons.Icons.Rounded.PlayArrow to Color(0xFFFF2D2D)
    "📘" -> androidx.compose.material.icons.Icons.Rounded.ThumbUp to Color(0xFF2D74F5)
    "✈️" -> androidx.compose.material.icons.Icons.AutoMirrored.Rounded.Send to Color(0xFF2AA3EF)
    "💼" -> androidx.compose.material.icons.Icons.Rounded.Work to Color(0xFF0A66C2)
    else -> androidx.compose.material.icons.Icons.Rounded.Circle to Color(0xFF8E8E93)
}
