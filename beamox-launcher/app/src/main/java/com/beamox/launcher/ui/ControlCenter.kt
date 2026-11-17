package com.beamox.launcher.ui

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamox.launcher.core.AppRepository
import com.beamox.launcher.core.BeamoxWallpaper
import com.beamox.launcher.core.Dashboard
import com.beamox.launcher.core.Prefs
import com.beamox.launcher.island.BeamoxAccess
import com.beamox.launcher.island.IslandService
import com.beamox.launcher.island.NotifBus
import kotlinx.coroutines.delay
import java.io.File

/** Где открыт пункт управления: в лаунчере (есть [launcher]) или поверх всех приложений (шторка). */
class CcEnv(
    val ctx: Context,
    val start: (Intent) -> Unit,
    /** Открыть системную шторку Android (true — быстрые настройки) — только в шторке поверх приложений */
    val androidShade: ((Boolean) -> Unit)? = null,
    val launcher: LauncherState? = null,
    /** Закрыть шторку (перед системными действиями) */
    val close: () -> Unit = {},
) {
    fun openSys(a: SysApp) {
        launcher?.let { it.openSys(a); return }
        val pm = ctx.packageManager
        val i = a.intent()
        val alt = a.fallbackPackages().firstNotNullOfOrNull { runCatching { pm.getLaunchIntentForPackage(it) }.getOrNull() }
        start(if (i.resolveActivity(pm) == null && alt != null) alt else i)
    }

    /** Системное действие через «Спец. возможности» (меню питания, шторка Android). */
    fun global(action: Int): Boolean {
        val a = BeamoxAccess.instance ?: return false
        close()
        return a.performGlobalAction(action)
    }

    companion object { const val OPEN_MODULE = "com.beamox.launcher.OPEN_MODULE" }

    /** Открыть раздел лаунчера (например, статистику) */
    fun openModule(m: Module) {
        launcher?.let { it.open(m); return }
        start(Intent(OPEN_MODULE).setClassName(ctx.packageName, "com.beamox.launcher.MainActivity").putExtra("module", m.name))
    }
}

// ================================================================== фон шторок
/** Размытые обои (как фон пункта управления на iPhone): маленькая копия обоев, растянутая и размытая. */
private object Backdrop {
    private var key: Any? = null
    private var img: ImageBitmap? = null
    fun get(ctx: Context, wp: ImageBitmap?): ImageBitmap? {
        val k: Any = wp ?: "own"
        if (key == k && img != null) return img
        val src: Bitmap = runCatching {
            wp?.asAndroidBitmap() ?: run {
                val custom = File(ctx.getExternalFilesDir(null), "homescreen.png")
                if (custom.exists()) BitmapFactory.decodeFile(custom.path)
                else BeamoxWallpaper.draw(360, 780, "Beamox", "", big = false)
            }
        }.getOrNull() ?: return null
        val small = Bitmap.createScaledBitmap(src, (src.width / 18).coerceAtLeast(2), (src.height / 18).coerceAtLeast(2), true)
        key = k; img = small.asImageBitmap()
        return img
    }
}

/** Обои во весь экран для шторки поверх приложений (в лаунчере они уже есть в LocalWallpaper). */
object ShadeWallpaper {
    private var img: ImageBitmap? = null
    fun full(ctx: Context): ImageBitmap? {
        img?.let { return it }
        return runCatching {
            val dm = ctx.resources.displayMetrics
            val w = dm.widthPixels.coerceAtLeast(360)
            val h = (dm.heightPixels + (120 * dm.density).toInt()).coerceAtLeast(720)
            val custom = File(ctx.getExternalFilesDir(null), "homescreen.png")
            val bmp = if (custom.exists()) BitmapFactory.decodeFile(custom.path) else BeamoxWallpaper.draw(w, h, "Beamox", "", big = false)
            Bitmap.createScaledBitmap(bmp, w, h, true).asImageBitmap()
        }.getOrNull().also { img = it }
    }
}

@Composable
fun ShadeBackdrop(ctx: Context) {
    val wp = LocalWallpaper.current
    val img = remember(wp) { Backdrop.get(ctx, wp) }
    Box(Modifier.fillMaxSize().background(Color(0xFF0E0E10))) {
        if (img != null) Image(img, null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(30.dp) else Modifier))
        Box(Modifier.fillMaxSize().background(Color(0xB8000000)))
    }
}

// ================================================================== пункт управления
/** «Пункт управления» как в iOS 26 (Liquid Glass) */
@Composable
fun ControlCenter(s: LauncherState, onClose: () -> Unit) =
    ControlCenterView(CcEnv(s.ctx, { AppRepository.safeStart(s.act, it) }, launcher = s, close = onClose), onClose)

private val Glass = Color(0x2EFFFFFF)
private val Mini = Color(0x33FFFFFF)

@Composable
fun ControlCenterView(e: CcEnv, onClose: () -> Unit) {
    val ctx = e.ctx
    val s = e.launcher
    val cr = ctx.contentResolver
    val am = remember { runCatching { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }.getOrNull() }
    val nm = remember { runCatching { ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }.getOrNull() }
    val pm = remember { runCatching { ctx.getSystemService(Context.POWER_SERVICE) as PowerManager }.getOrNull() }
    val lm = remember { runCatching { ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager }.getOrNull() }
    val bm = remember { runCatching { ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager }.getOrNull() }
    val carrier = remember { runCatching { (ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).networkOperatorName }.getOrNull().orEmpty() }
    fun g(name: String) = runCatching { Settings.Global.getInt(cr, name, 0) == 1 }.getOrDefault(false)

    // Состояния перечитываем раз в секунду: после панели Wi‑Fi и т.п. всё сразу верно
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    val airplane = remember(tick) { g(Settings.Global.AIRPLANE_MODE_ON) }
    val wifi = remember(tick) { g("wifi_on") }
    val bt = remember(tick) { g("bluetooth_on") }
    val cell = remember(tick) { g("mobile_data") }
    val nfc = remember(tick) { runCatching { NfcAdapter.getDefaultAdapter(ctx)?.isEnabled == true }.getOrDefault(false) }
    val geo = remember(tick) { runCatching { Build.VERSION.SDK_INT >= 28 && lm?.isLocationEnabled == true }.getOrDefault(false) }
    val saver = remember(tick) { pm?.isPowerSaveMode == true }
    val battery = remember(tick) { runCatching { bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) }.getOrNull() }
    var torch by remember { mutableStateOf(ControlState.torchOn) }
    var dnd by remember { mutableStateOf(runCatching { nm!!.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL }.getOrDefault(false)) }
    var ringer by remember { mutableStateOf(runCatching { am!!.ringerMode }.getOrDefault(AudioManager.RINGER_MODE_NORMAL)) }
    var rotationLocked by remember { mutableStateOf(runCatching { Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0) == 0 }.getOrDefault(true)) }
    var autoBright by remember { mutableStateOf(runCatching { Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == 1 }.getOrDefault(false)) }
    var brightness by remember { mutableFloatStateOf(runCatching { Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f }.getOrDefault(0.6f)) }
    val maxVol = remember { runCatching { am!!.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(15).coerceAtLeast(1) }
    var volume by remember { mutableFloatStateOf(runCatching { am!!.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVol.toFloat() }.getOrDefault(0.5f)) }

    fun start(i: Intent) = e.start(i)
    fun needWriteSettings(): Boolean {
        if (Settings.System.canWrite(ctx)) return false
        start(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")))
        return true
    }
    fun androidQuick() { if (!e.global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)) e.androidShade?.invoke(true) }

    Box(Modifier.fillMaxSize()
        .pointerInput(Unit) { detectTapGestures { onClose() } }
        .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy < -30) onClose() } }) {
        ShadeBackdrop(ctx)
        BoxWithConstraints(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp)) {
            val gap = 17.dp
            val c: Dp = (maxWidth - gap * 3) / 4          // одна клетка
            val big: Dp = c * 2 + gap                      // модуль 2×2
            val moduleShape = RoundedCornerShape(c * 0.46f)
            Column(Modifier.fillMaxWidth()) {
                // ── «+» и питание
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Circle(Icons.Rounded.Add, false, 40.dp, Color.White) { androidQuick() }
                    Spacer(Modifier.weight(1f))
                    Circle(Icons.Rounded.PowerSettingsNew, false, 40.dp, Color.White) {
                        if (!e.global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)) start(Intent(Settings.ACTION_SETTINGS))
                    }
                }
                // ── оператор и заряд
                Row(Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 22.dp, start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SignalCellularAlt, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("  " + carrier.ifBlank { "Нет сети" }, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (wifi) Icon(Icons.Rounded.Wifi, null, tint = Color.White, modifier = Modifier.padding(start = 6.dp).size(20.dp))
                    Spacer(Modifier.weight(1f))
                    if (battery != null) {
                        Text("$battery%", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(8.dp))
                        BatteryGlyph(battery, saver)
                    }
                }
                Column(Modifier.pointerInput(Unit) { detectTapGestures { } }, verticalArrangement = Arrangement.spacedBy(gap)) {
                    // ── Связь | Сейчас играет
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        LiquidGlass(moduleShape, Modifier.size(big), tint = Glass) {
                            val pad = c * 0.16f
                            val bigC = (big - pad * 3) / 2
                            Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.spacedBy(pad)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(pad)) {
                                    Circle(Icons.Rounded.AirplanemodeActive, airplane, bigC, Color(0xFFFF9F0A), glass = false) {
                                        start(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
                                    }
                                    Circle(Icons.Rounded.SignalCellularAlt, cell, bigC, Color(0xFF30D158), glass = false) {
                                        start(if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else Intent(Settings.ACTION_WIRELESS_SETTINGS))
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(pad)) {
                                    Circle(Icons.Rounded.Wifi, wifi, bigC, Color(0xFF0A84FF), glass = false) {
                                        start(if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS))
                                    }
                                    // четыре маленьких: Bluetooth, NFC, геопозиция, точка доступа
                                    val small = (bigC - c * 0.08f) / 2
                                    Column(Modifier.size(bigC), verticalArrangement = Arrangement.SpaceBetween) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Circle(Icons.Rounded.Bluetooth, bt, small, Color(0xFF0A84FF), glass = false) { start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                                            Circle(Icons.Rounded.Nfc, nfc, small, Color(0xFF0A84FF), glass = false) { start(Intent(Settings.ACTION_NFC_SETTINGS)) }
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Circle(Icons.Rounded.LocationOn, geo, small, Color(0xFF0A84FF), glass = false) { start(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                                            Circle(Icons.Rounded.WifiTethering, false, small, Color(0xFF30D158), glass = false) {
                                                start(Intent("android.settings.TETHER_SETTINGS").takeIf { it.resolveActivity(ctx.packageManager) != null }
                                                    ?: Intent(Settings.ACTION_WIRELESS_SETTINGS))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        LiquidGlass(moduleShape, Modifier.size(big), tint = Glass) {
                            NowPlaying(ctx, Modifier.fillMaxSize().padding(c * 0.2f), tick, c) {
                                start(if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_VOLUME) else Intent(Settings.ACTION_SOUND_SETTINGS))
                            }
                        }
                    }
                    // ── Поворот, трансляция, Фокус | яркость, громкость
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                Circle(Icons.Rounded.ScreenLockRotation, rotationLocked, c, Color.White, activeGlyph = Color(0xFFFF453A)) {
                                    if (!needWriteSettings()) {
                                        rotationLocked = !rotationLocked
                                        runCatching { Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, if (rotationLocked) 0 else 1) }
                                    }
                                }
                                Circle(Icons.Rounded.Cast, false, c, Color.White) { start(Intent(Settings.ACTION_CAST_SETTINGS)) }
                            }
                            // Фокус — капсула на две клетки
                            LiquidGlass(CircleShape, Modifier.width(big).height(c)
                                .pointerInput(dnd) {
                                    detectTapGestures {
                                        if (nm == null) return@detectTapGestures
                                        if (!nm.isNotificationPolicyAccessGranted) start(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                                        else { val on = !dnd; nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL); dnd = on }
                                    }
                                }, tint = Glass, contentAlignment = Alignment.CenterStart) {
                                Row(Modifier.padding(start = c * 0.12f), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(c * 0.74f).clip(CircleShape).background(if (dnd) Color(0xFF5E5CE6) else Mini),
                                        contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.DarkMode, null, tint = Color.White, modifier = Modifier.size(c * 0.4f))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(if (dnd) "Не беспокоить" else "Фокус", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Icon(Icons.Rounded.UnfoldMore, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        VSlider(Icons.Rounded.WbSunny, Color(0xFFFFC300), brightness, Modifier.width(c).height(big), c / 2) { v ->
                            if (!needWriteSettings()) {
                                brightness = v
                                if (autoBright) { autoBright = false; runCatching { Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) } }
                                runCatching { Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (v * 255).toInt().coerceIn(3, 255)) }
                            }
                        }
                        VSlider(Icons.AutoMirrored.Rounded.VolumeUp, Color(0xFF32ADE6), volume, Modifier.width(c).height(big), c / 2) { v ->
                            volume = v; runCatching { am!!.setStreamVolume(AudioManager.STREAM_MUSIC, (v * maxVol).toInt(), 0) }
                        }
                    }
                    // ── Beamox (вместо Apple TV) | фонарик, таймер, калькулятор, камера
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        BeamoxModule(ctx, s, Modifier.size(big), moduleShape, c) { e.openModule(Module.STATS) }
                        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                Circle(Icons.Rounded.FlashlightOn, torch, c, Color.White, activeGlyph = Color(0xFF0A84FF)) { torch = ControlState.setTorch(ctx, !torch) }
                                Circle(Icons.Rounded.Timer, false, c, Color.White) { start(Intent(AlarmClock.ACTION_SHOW_TIMERS)) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                Circle(Icons.Rounded.Calculate, false, c, Color.White) { e.openSys(SysApp.CALCULATOR) }
                                Circle(Icons.Rounded.PhotoCamera, false, c, Color.White) { e.openSys(SysApp.CAMERA) }
                            }
                        }
                    }
                    // ── QR, диктофон, будильник, автояркость
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Circle(Icons.Rounded.QrCodeScanner, false, c, Color.White) { e.openSys(SysApp.CAMERA) }
                        Circle(Icons.Rounded.GraphicEq, false, c, Color.White) { start(Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)) }
                        Circle(Icons.Rounded.Alarm, false, c, Color.White) { e.openSys(SysApp.CLOCK) }
                        Circle(Icons.Rounded.BrightnessAuto, autoBright, c, Color.White, activeGlyph = Color(0xFFFF9F0A)) {
                            if (!needWriteSettings()) {
                                autoBright = !autoBright
                                runCatching { Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                                    if (autoBright) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) }
                            }
                        }
                    }
                    // ── голос / шторка Android, энергосбережение, без звука, настройки
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        if (s != null) Circle(Icons.Rounded.Mic, s.handsFree, c, B.Violet) { s.setHandsFreeMode(!s.handsFree) }
                        else Circle(Icons.Rounded.Tune, false, c, Color.White) { androidQuick() }
                        Circle(Icons.Rounded.BatterySaver, saver, c, Color(0xFFFFD60A), activeGlyph = Color.Black) { start(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) }
                        Circle(if (ringer == AudioManager.RINGER_MODE_NORMAL) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff,
                            ringer != AudioManager.RINGER_MODE_NORMAL, c, Color(0xFFFF453A)) {
                            val next = if (ringer == AudioManager.RINGER_MODE_NORMAL) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
                            runCatching { am!!.ringerMode = next; ringer = next }
                        }
                        Circle(Icons.Rounded.Settings, false, c, Color.White) { start(Intent(Settings.ACTION_SETTINGS)) }
                    }
                }
            }
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

/** Круглая кнопка iOS 26: стекло; включена — цветная (или белая с цветным значком). */
@Composable
private fun Circle(icon: ImageVector, active: Boolean, size: Dp, activeBg: Color, activeGlyph: Color = Color.White,
                   glass: Boolean = true, onClick: () -> Unit) {
    val m = Modifier.size(size).pointerInput(active) { detectTapGestures { onClick() } }
    val glyph: @Composable () -> Unit = {
        Icon(icon, null, tint = if (active) activeGlyph else Color.White, modifier = Modifier.size(size * 0.44f))
    }
    if (glass) LiquidGlass(CircleShape, m, tint = if (active) activeBg else Glass, contentAlignment = Alignment.Center) { glyph() }
    else Box(m.clip(CircleShape).background(if (active) activeBg else Mini), contentAlignment = Alignment.Center) { glyph() }
}

/** Батарейка как в статус-баре iOS */
@Composable
private fun BatteryGlyph(pct: Int, saver: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(30.dp).height(15.dp).clip(RoundedCornerShape(4.5.dp)).background(Color.White.copy(alpha = 0.35f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((pct / 100f).coerceIn(0.05f, 1f))
                .background(if (saver) Color(0xFFFFD60A) else Color.White))
        }
        Box(Modifier.padding(start = 1.5.dp).width(2.dp).height(5.dp).clip(RoundedCornerShape(1.dp)).background(Color.White.copy(alpha = 0.4f)))
    }
}

/** Вертикальный ползунок как в iOS 26: капсула, белая заливка снизу, цветной значок. Тянуть от текущего значения. */
@Composable
private fun VSlider(icon: ImageVector, iconColor: Color, value: Float, modifier: Modifier, corner: Dp, onChange: (Float) -> Unit) {
    val cur by rememberUpdatedState(value)
    LiquidGlass(RoundedCornerShape(corner), modifier.pointerInput(Unit) {
        detectVerticalDragGestures { ch, dy -> ch.consume(); onChange((cur - dy / size.height).coerceIn(0f, 1f)) }
    }, tint = Glass, contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(value.coerceIn(0f, 1f)).background(Color.White))
        Icon(icon, null, tint = if (value > 0.16f) iconColor else Color.White,
            modifier = Modifier.padding(bottom = 22.dp).size(30.dp))
    }
}

/** Модуль «Сейчас играет» 2×2. Нужен доступ к уведомлениям (через него видны плееры). */
@Composable
private fun NowPlaying(ctx: Context, modifier: Modifier, tick: Int, c: Dp, onOutput: () -> Unit) {
    val ver = NotifBus.version
    var poke by remember { mutableIntStateOf(0) }
    val mc = remember(ver, tick, poke) {
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
    Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(c * 0.84f).clip(RoundedCornerShape(c * 0.2f)).background(Mini)
                .pointerInput(mc) { detectTapGestures { runCatching { mc?.sessionActivity?.send() } } },
                contentAlignment = Alignment.Center) {
                if (art != null) Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(c * 0.64f).clip(CircleShape).background(Mini)
                .pointerInput(Unit) { detectTapGestures { onOutput() } }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Cast, null, tint = Color.White, modifier = Modifier.size(c * 0.32f))
            }
        }
        Column {
            Text(title ?: "Не исполняется", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!artist.isNullOrBlank()) Text(artist, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            val t = mc?.transportControls
            val dim = if (mc == null) 0.45f else 1f
            Icon(Icons.Rounded.FastRewind, null, tint = Color.White.copy(alpha = dim), modifier = Modifier.size(32.dp)
                .pointerInput(mc) { detectTapGestures { t?.skipToPrevious(); poke++ } })
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White,
                modifier = Modifier.size(42.dp).pointerInput(mc, playing) { detectTapGestures { if (playing) t?.pause() else t?.play(); poke++ } })
            Icon(Icons.Rounded.FastForward, null, tint = Color.White.copy(alpha = dim), modifier = Modifier.size(32.dp)
                .pointerInput(mc) { detectTapGestures { t?.skipToNext(); poke++ } })
        }
    }
}

/** Модуль Beamox 2×2 (на месте Apple TV): студия и лимит Claude из последних данных панели. */
@Composable
private fun BeamoxModule(ctx: Context, s: LauncherState?, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape, c: Dp, onClick: () -> Unit) {
    val cached = remember { runCatching { Prefs(ctx).lastDashJson?.let { Dashboard.parse(org.json.JSONObject(it)) } }.getOrNull() }
    val d: Dashboard? = s?.dash ?: cached
    LiquidGlass(shape, modifier.pointerInput(Unit) { detectTapGestures { onClick() } }, tint = Glass) {
        Column(Modifier.fillMaxSize().padding(c * 0.24f), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(c * 0.62f).clip(RoundedCornerShape(c * 0.16f))
                .background(Brush.linearGradient(listOf(Color(0xFF5B3BE8), Color(0xFF3A7BF0))))
                .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(c * 0.16f)),
                contentAlignment = Alignment.Center) {
                Text("B", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column {
                Text(d?.studioName?.ifBlank { null } ?: "Студия", color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(d?.reach?.let { "Охват ${num(it)}" } ?: "Beamox", color = Color.White, fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(d?.weekPct?.let { "Claude: ${it.toInt()}% недели" } ?: "Статистика", color = Color.White.copy(alpha = 0.6f),
                    fontSize = 15.sp, maxLines = 1)
            }
        }
    }
}
