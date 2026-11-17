package com.beamox.launcher.ui

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.Build
import android.service.notification.StatusBarNotification
import android.text.format.DateUtils
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.beamox.launcher.island.IslandService
import com.beamox.launcher.island.NotifBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Уведомление для шторки */
class NcItem(
    val key: String, val pkg: String, val app: String, val icon: ImageBitmap?, val title: String, val text: String,
    val time: Long, val intent: PendingIntent?, val clearable: Boolean,
)

private object NcIcons {
    private val cache = HashMap<String, ImageBitmap?>()
    fun app(ctx: Context, pkg: String): ImageBitmap? = cache.getOrPut(pkg) {
        runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    fun label(ctx: Context, pkg: String): String = runCatching {
        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}

/** Текущие уведомления; null — нет доступа к уведомлениям. */
private fun loadNotifications(ctx: Context): List<NcItem>? {
    val svc = IslandService.instance ?: return null
    val list: Array<StatusBarNotification> = runCatching { svc.activeNotifications }.getOrNull() ?: return emptyList()
    return list.asSequence()
        .filter { it.notification != null && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
        .mapNotNull { sbn ->
            val n = sbn.notification
            val ex = n.extras
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()
            if (title.isEmpty() && text.isEmpty()) return@mapNotNull null
            NcItem(sbn.key, sbn.packageName, NcIcons.label(ctx, sbn.packageName), NcIcons.app(ctx, sbn.packageName),
                title, text, sbn.postTime, n.contentIntent, sbn.isClearable)
        }
        .sortedByDescending { it.time }
        .take(60)
        .toList()
}

private fun openItem(ctx: Context, it: NcItem) {
    val pi = it.intent ?: ctx.packageManager.getLaunchIntentForPackage(it.pkg)?.let { i ->
        PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    } ?: return
    val opts = if (Build.VERSION.SDK_INT >= 34) ActivityOptions.makeBasic()
        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle() else null
    runCatching { pi.send(ctx, 0, null, null, null, null, opts) }
    if (it.clearable) runCatching { IslandService.instance?.cancelNotification(it.key) }
}

/** «Центр уведомлений» как в iOS: часы и дата сверху, карточки уведомлений; смахнуть вверх — закрыть. */
@Composable
fun NotificationCenterView(e: CcEnv, onClose: () -> Unit) {
    val ctx = e.ctx
    val ver = NotifBus.version
    val list = remember(ver) { loadNotifications(ctx) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = System.currentTimeMillis() } }
    val ru = Locale("ru")
    val closeOnSwipeUp = Modifier.pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy < -25) onClose() } }

    Box(Modifier.fillMaxSize()) {
        ShadeBackdrop(ctx)
        Column(Modifier.fillMaxSize().padding(top = IosStatusBarHeight)) {
            // Часы и дата — как на экране блокировки
            Column(Modifier.fillMaxWidth().then(closeOnSwipeUp).pointerInput(Unit) { detectTapGestures { } }.padding(top = 14.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(SimpleDateFormat("EEEE, d MMMM", ru).format(Date(now)).replaceFirstChar { it.titlecase(ru) },
                    color = Color.White.copy(alpha = 0.85f), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(SimpleDateFormat("H:mm", ru).format(Date(now)), color = Color.White, fontSize = 86.sp, fontWeight = FontWeight.Bold,
                    lineHeight = 90.sp)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Уведомления", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (!list.isNullOrEmpty() && list.any { it.clearable }) {
                    Text("Очистить", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(CircleShape).background(Color(0x33FFFFFF))
                            .pointerInput(Unit) { detectTapGestures { runCatching { IslandService.instance?.cancelAllNotifications() } } }
                            .padding(horizontal = 14.dp, vertical = 7.dp))
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    list == null -> Column(Modifier.padding(18.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0x33FFFFFF))
                        .pointerInput(Unit) { detectTapGestures { e.start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } }
                        .padding(16.dp)) {
                        Text("Нужен доступ к уведомлениям", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Нажмите и включите «Островок Beamox». Если переключатель серый: «О приложении» Beamox → ⋮ → «Разрешить ограниченные настройки».",
                            color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                    }
                    list.isEmpty() -> Text("Нет уведомлений", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp))
                    else -> LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(list, key = { it.key }) { n ->
                            NotifCard(n, now,
                                onOpen = { onClose(); openItem(ctx, n) },
                                onDismiss = { runCatching { IslandService.instance?.cancelNotification(n.key) } })
                        }
                    }
                }
            }
            // Полоска снизу: смахнуть вверх или нажать — закрыть
            Box(Modifier.fillMaxWidth().height(44.dp).then(closeOnSwipeUp).pointerInput(Unit) { detectTapGestures { onClose() } },
                contentAlignment = Alignment.Center) {
                Box(Modifier.width(134.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.8f)))
            }
        }
    }
}

@Composable
private fun NotifCard(n: NcItem, now: Long, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val off = remember(n.key) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LiquidGlass(RoundedCornerShape(22.dp), Modifier.fillMaxWidth()
        .offset { IntOffset(off.value.roundToInt(), 0) }
        .alpha((1f - abs(off.value) / 900f).coerceIn(0.2f, 1f))
        .pointerInput(n.key) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    scope.launch {
                        if (n.clearable && abs(off.value) > size.width * 0.33f) {
                            off.animateTo(if (off.value > 0) size.width * 1.2f else -size.width * 1.2f)
                            onDismiss()
                        } else off.animateTo(0f)
                    }
                },
                onDragCancel = { scope.launch { off.animateTo(0f) } },
            ) { ch, dx -> ch.consume(); scope.launch { off.snapTo(off.value + dx) } }
        }
        .pointerInput(n.key) { detectTapGestures { onOpen() } }, tint = Color(0x1FFFFFFF)) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
        if (n.icon != null) Image(n.icon, null, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)))
        else Box(Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(Color(0x55FFFFFF)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n.app.uppercase(), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(ago(n.time, now), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
            if (n.title.isNotEmpty()) Text(n.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (n.text.isNotEmpty()) Text(n.text, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
    }
}

private fun ago(t: Long, now: Long): String {
    val m = (now - t) / 60_000
    return when {
        m < 1 -> "сейчас"
        m < 60 -> "$m мин"
        DateUtils.isToday(t) -> SimpleDateFormat("H:mm", Locale("ru")).format(Date(t))
        else -> SimpleDateFormat("d MMM", Locale("ru")).format(Date(t))
    }
}
