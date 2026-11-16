package com.beamox.launcher.ui

import android.app.NotificationManager
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.foundation.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.beamox.launcher.island.IslandService
import com.beamox.launcher.island.NotifBus
import kotlinx.coroutines.delay
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamox.launcher.core.AppRepository

/** Где открыт пункт управления: в лаунчере (есть [launcher]) или поверх всех приложений (шторка). */
class CcEnv(
    val ctx: Context,
    val start: (Intent) -> Unit,
    /** Открыть системную шторку Android (true — быстрые настройки) — только в шторке поверх приложений */
    val androidShade: ((Boolean) -> Unit)? = null,
    val launcher: LauncherState? = null,
) {
    fun openSys(a: SysApp) {
        launcher?.let { it.openSys(a); return }
        val pm = ctx.packageManager
        val i = a.intent()
        val alt = a.fallbackPackages().firstNotNullOfOrNull { runCatching { pm.getLaunchIntentForPackage(it) }.getOrNull() }
        start(if (i.resolveActivity(pm) == null && alt != null) alt else i)
    }
}

/** «Пункт управления» как в iOS 26 */
@Composable
fun ControlCenter(s: LauncherState, onClose: () -> Unit) =
    ControlCenterView(CcEnv(s.ctx, { AppRepository.safeStart(s.act, it) }, launcher = s), onClose)

@Composable
fun ControlCenterView(e: CcEnv, onClose: () -> Unit) {
    val ctx = e.ctx
    val s = e.launcher
    val am = runCatching { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }.getOrNull()
    val nm = runCatching { ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }.getOrNull()
    var torch by remember { mutableStateOf(ControlState.torchOn) }
    var dnd by remember { mutableStateOf(runCatching { nm!!.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL }.getOrDefault(false)) }
    var ringer by remember { mutableStateOf(runCatching { am!!.ringerMode }.getOrDefault(AudioManager.RINGER_MODE_NORMAL)) }
    var rotationLocked by remember {
        mutableStateOf(runCatching { Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 0 }.getOrDefault(true))
    }
    var brightness by remember {
        mutableFloatStateOf(runCatching { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f }.getOrDefault(0.6f))
    }
    val maxVol = runCatching { am!!.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(15).coerceAtLeast(1)
    var volume by remember { mutableFloatStateOf(runCatching { am!!.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVol.toFloat() }.getOrDefault(0.5f)) }

    fun start(i: Intent) = e.start(i)
    fun needWriteSettings(): Boolean {
        if (Settings.System.canWrite(ctx)) return false
        start(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")))
        return true
    }

    Box(Modifier.fillMaxSize().background(Color(0xB30B0A0F))
        .pointerInput(Unit) { detectTapGestures { onClose() } }
        .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy < -30) onClose() } }) {
        Column(Modifier.padding(top = IosStatusBarHeight).padding(horizontal = 22.dp).padding(top = 30.dp).fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures { } }, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // Связь: 4 круглые кнопки в одной плитке
                Column(Modifier.weight(1f).aspectRatio(1f).ccTile().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Round(Icons.Rounded.AirplanemodeActive, false, Color(0xFFFF9500)) { start(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) }
                        Round(Icons.Rounded.SignalCellularAlt, true, Color(0xFF30D158)) {
                            start(if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else Intent(Settings.ACTION_WIRELESS_SETTINGS))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Round(Icons.Rounded.Wifi, true, Color(0xFF0A84FF)) {
                            start(if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS))
                        }
                        Round(Icons.Rounded.Bluetooth, true, Color(0xFF0A84FF)) { start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                    }
                }
                if (s != null) {
                    // Голос Beamox
                    Column(Modifier.weight(1f).aspectRatio(1f).ccTile(if (s.handsFree) B.Violet else null)
                        .pointerInput(s.handsFree) { detectTapGestures { s.setHandsFreeMode(!s.handsFree) } }.padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween) {
                        Icon(Icons.Rounded.Mic, null, tint = Color.White, modifier = Modifier.size(30.dp))
                        Column {
                            Text("«Привет, Бимокс»", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                            Text(if (s.handsFree) "Слушаю" else "Выключено", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else NowPlayingTile(ctx, Modifier.weight(1f).aspectRatio(1f).ccTile())
            }
            Row(Modifier.height(170.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Wide(Icons.Rounded.DarkMode, "Фокус", dnd, Color(0xFF5E5CE6), Modifier.weight(1f)) {
                        if (nm == null) return@Wide
                        if (!nm.isNotificationPolicyAccessGranted) start(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                        else { val on = !dnd; nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL); dnd = on }
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Square(Icons.Rounded.ScreenLockRotation, rotationLocked, Color(0xFFFF9500), Modifier.weight(1f), square = false) {
                            if (!needWriteSettings()) {
                                rotationLocked = !rotationLocked
                                Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (rotationLocked) 0 else 1)
                            }
                        }
                        Square(when (ringer) { AudioManager.RINGER_MODE_SILENT -> Icons.Rounded.NotificationsOff; AudioManager.RINGER_MODE_VIBRATE -> Icons.Rounded.Vibration; else -> Icons.Rounded.Notifications },
                            ringer != AudioManager.RINGER_MODE_NORMAL, Color(0xFFFF453A), Modifier.weight(1f), square = false) {
                            val next = if (ringer == AudioManager.RINGER_MODE_NORMAL) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
                            runCatching { am!!.ringerMode = next; ringer = next }
                        }
                    }
                }
                VSlider(Icons.Rounded.LightMode, brightness, Modifier.weight(0.5f)) { v ->
                    if (!needWriteSettings()) {
                        brightness = v
                        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (v * 255).toInt().coerceIn(5, 255))
                    }
                }
                VSlider(Icons.AutoMirrored.Rounded.VolumeUp, volume, Modifier.weight(0.5f)) { v ->
                    volume = v; runCatching { am!!.setStreamVolume(AudioManager.STREAM_MUSIC, (v * maxVol).toInt(), 0) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Square(Icons.Rounded.FlashlightOn, torch, Color.White, Modifier.weight(1f), activeGlyph = Color.Black) { torch = ControlState.setTorch(ctx, !torch) }
                Square(Icons.Rounded.Alarm, false, Color.White, Modifier.weight(1f)) { e.openSys(SysApp.CLOCK) }
                Square(Icons.Rounded.Calculate, false, Color.White, Modifier.weight(1f)) { e.openSys(SysApp.CALCULATOR) }
                Square(Icons.Rounded.PhotoCamera, false, Color.White, Modifier.weight(1f)) { e.openSys(SysApp.CAMERA) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Square(Icons.Rounded.BatterySaver, false, Color(0xFFFFD60A), Modifier.weight(1f)) { start(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) }
                Spacer(Modifier.weight(3f))
            }
            e.androidShade?.let { sys ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Wide(Icons.Rounded.Tune, "Шторка Android", false, Color.Gray, Modifier.weight(1f).height(56.dp)) { sys(true) }
                    Wide(Icons.Rounded.Settings, "Настройки", false, Color.Gray, Modifier.weight(1f).height(56.dp)) { start(Intent(Settings.ACTION_SETTINGS)) }
                }
            }
        }
    }
}

/** Плитка «Сейчас играет» (в шторке поверх приложений вместо голоса). Нужен доступ к уведомлениям. */
@Composable
private fun NowPlayingTile(ctx: Context, modifier: Modifier) {
    val ver = NotifBus.version
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1500); tick++ } }
    val mc = remember(ver, tick) {
        runCatching {
            ctx.getSystemService(MediaSessionManager::class.java)
                .getActiveSessions(ComponentName(ctx, IslandService::class.java)).firstOrNull()
        }.getOrNull()
    }
    val md = mc?.metadata
    val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
    val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
    val art = remember(title, mc?.packageName) {
        runCatching { (md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART))?.asImageBitmap() }.getOrNull()
    }
    val playing = mc?.playbackState?.state == PlaybackState.STATE_PLAYING
    Column(modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x40FFFFFF))
            .pointerInput(mc) { detectTapGestures { runCatching { mc?.sessionActivity?.send() } } },
            contentAlignment = Alignment.Center) {
            if (art != null) Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Column {
            Text(title ?: "Не играет", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (!artist.isNullOrBlank()) Text(artist, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            val t = mc?.transportControls
            Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(30.dp)
                .pointerInput(mc) { detectTapGestures { t?.skipToPrevious(); tick++ } })
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(34.dp)
                .pointerInput(mc, playing) { detectTapGestures { if (playing) t?.pause() else t?.play(); tick++ } })
            Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(30.dp)
                .pointerInput(mc) { detectTapGestures { t?.skipToNext(); tick++ } })
        }
    }
}

private fun Modifier.ccTile(active: Color? = null) = this.clip(RoundedCornerShape(30.dp))
    .background(active ?: Color(0x33FFFFFF))

object ControlState {
    var torchOn = false
    fun setTorch(ctx: Context, on: Boolean): Boolean = try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.first { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        cm.setTorchMode(id, on); torchOn = on; on
    } catch (e: Exception) { torchOn }
}

@Composable
private fun Round(icon: ImageVector, active: Boolean, color: Color, onClick: () -> Unit) {
    Box(Modifier.size(54.dp).clip(CircleShape).background(if (active) color else Color(0x40FFFFFF))
        .pointerInput(Unit) { detectTapGestures { onClick() } }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun Square(icon: ImageVector, active: Boolean, color: Color, modifier: Modifier, activeGlyph: Color = Color.White,
                   square: Boolean = true, onClick: () -> Unit) {
    Box(modifier.then(if (square) Modifier.aspectRatio(1f) else Modifier.fillMaxHeight()).ccTile(if (active) color else null).pointerInput(active) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (active) activeGlyph else Color.White, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun Wide(icon: ImageVector, label: String, active: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().ccTile().pointerInput(active) { detectTapGestures { onClick() } }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(if (active) color else Color(0x40FFFFFF)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Text("  $label", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Вертикальный ползунок как в iOS (яркость/громкость) */
@Composable
private fun VSlider(icon: ImageVector, value: Float, modifier: Modifier, onChange: (Float) -> Unit) {
    Box(modifier.fillMaxHeight().clip(RoundedCornerShape(30.dp)).background(Color(0x33FFFFFF))
        .pointerInput(Unit) {
            detectVerticalDragGestures { change, _ ->
                onChange((1f - change.position.y / size.height).coerceIn(0f, 1f))
            }
        }, contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(value.coerceIn(0f, 1f)).background(Color.White))
        Icon(icon, null, tint = if (value > 0.15f) Color(0xFF8E8E93) else Color.White,
            modifier = Modifier.padding(bottom = 16.dp).size(26.dp))
    }
}
