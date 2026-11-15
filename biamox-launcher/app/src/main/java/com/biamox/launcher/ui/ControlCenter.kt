package com.biamox.launcher.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biamox.launcher.core.AppRepository

/** «Пункт управления» как в iOS. */
@Composable
fun ControlCenter(s: LauncherState, onClose: () -> Unit) {
    val ctx = s.ctx
    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    var torch by remember { mutableStateOf(ControlState.torchOn) }
    var dnd by remember { mutableStateOf(nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) }
    var ringer by remember { mutableStateOf(am.ringerMode) }
    var rotation by remember { mutableStateOf(Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1) }
    var brightness by remember {
        mutableFloatStateOf(Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
    }
    val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    var volume by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVol.toFloat()) }

    fun start(i: Intent) = AppRepository.safeStart(s.activity, i)
    fun needWriteSettings(): Boolean {
        if (Settings.System.canWrite(ctx)) return false
        start(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")))
        return true
    }

    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(onClick = onClose)) {
        Column(Modifier.statusBarsPadding().padding(16.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .clickableNoRipple {}) {
            Text("Пункт управления", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Связь
                Column(Modifier.weight(1f).glass(RoundedCornerShape(26.dp), 0.18f).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Round("✈️", false) { start(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) }
                        Round("📶", true, B.Green) {
                            start(if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else Intent(Settings.ACTION_WIRELESS_SETTINGS))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Round("📡", true, B.Blue) {
                            start(if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS))
                        }
                        Round("🔵", true, B.Blue) { start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                    }
                }
                // Голос + звук
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile("🎙️", if (s.handsFree) "«Привет, Биамокс» вкл." else "Голос выкл.", s.handsFree, B.Violet) {
                        s.setHandsFreeMode(!s.handsFree)
                    }
                    Tile(when (ringer) { AudioManager.RINGER_MODE_SILENT -> "🔕"; AudioManager.RINGER_MODE_VIBRATE -> "📳"; else -> "🔔" },
                        when (ringer) { AudioManager.RINGER_MODE_SILENT -> "Без звука"; AudioManager.RINGER_MODE_VIBRATE -> "Вибрация"; else -> "Звонок" },
                        ringer != AudioManager.RINGER_MODE_NORMAL, B.Red) {
                        val next = if (ringer == AudioManager.RINGER_MODE_NORMAL) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
                        runCatching { am.ringerMode = next; ringer = next }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("🌙", "Не беспокоить", dnd, B.Violet, Modifier.weight(1f)) {
                    if (!nm.isNotificationPolicyAccessGranted) start(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    else {
                        val on = !dnd
                        nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
                        dnd = on
                    }
                }
                Tile("🔒", if (rotation) "Поворот вкл." else "Поворот выкл.", !rotation, B.Orange, Modifier.weight(1f)) {
                    if (!needWriteSettings()) {
                        rotation = !rotation
                        Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (rotation) 1 else 0)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // Слайдеры яркости и громкости
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SliderTile("☀️ Яркость", brightness, Modifier.weight(1f)) { v ->
                    if (!needWriteSettings()) {
                        brightness = v
                        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (v * 255).toInt().coerceIn(5, 255))
                    }
                }
                SliderTile("🔊 Громкость", volume, Modifier.weight(1f)) { v ->
                    volume = v
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, (v * maxVol).toInt(), 0)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Round("🔦", torch, Color.White) {
                    torch = ControlState.setTorch(ctx, !torch)
                }
                Round("⏰", false) { s.openSys(SysApp.CLOCK) }
                Round("🧮", false) { s.openSys(SysApp.CALCULATOR) }
                Round("📷", false) { s.openSys(SysApp.CAMERA) }
                Round("🔋", false) { start(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) }
            }
            Spacer(Modifier.height(16.dp))
            Text("Закрыть", color = B.Blue, fontSize = 16.sp, modifier = Modifier.align(Alignment.CenterHorizontally)
                .clickable(onClick = onClose).padding(12.dp))
        }
    }
}

object ControlState {
    var torchOn = false
    fun setTorch(ctx: Context, on: Boolean): Boolean = try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.first { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        cm.setTorchMode(id, on); torchOn = on; on
    } catch (e: Exception) { torchOn }
}

@Composable
private fun Round(emoji: String, active: Boolean, color: Color = B.Blue, onClick: () -> Unit) {
    Box(Modifier.size(58.dp).clip(CircleShape).background(if (active) color else Color(0x40FFFFFF)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { Text(emoji, fontSize = 24.sp) }
}

@Composable
private fun Tile(emoji: String, label: String, active: Boolean, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(if (active) color.copy(alpha = 0.85f) else Color(0x40FFFFFF))
        .clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.size(8.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2)
    }
}

@Composable
private fun SliderTile(label: String, value: Float, modifier: Modifier, onChange: (Float) -> Unit) {
    Column(modifier.glass(RoundedCornerShape(22.dp), 0.18f).padding(12.dp)) {
        Text(label, color = Color.White, fontSize = 13.sp)
        Slider(value, onChange, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White,
            inactiveTrackColor = Color(0x55FFFFFF)))
    }
}
