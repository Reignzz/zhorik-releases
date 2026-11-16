package com.beamox.launcher.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import androidx.core.content.ContextCompat

/**
 * Одна капсула на весь телефон. Где она живёт:
 *  - включены «Спец. возможности → Beamox» — окно спец. возможностей, поверх строки состояния, как настоящий Dynamic Island;
 *  - иначе есть «Поверх других приложений» — обычное окно поверх приложений;
 *  - доступ к уведомлениям только наполняет капсулу (музыка, звонки, баннеры); без него она всё равно видна у камеры.
 */
object IslandHub {
    private var overlay: IslandOverlay? = null
    private var inAccess = false
    private var power: BroadcastReceiver? = null

    val current: IslandOverlay? get() = overlay

    fun ensure(ctx: Context) {
        val app = ctx.applicationContext
        if (!IslandService.enabled(app)) { drop(); return }
        val acc = BeamoxAccess.instance
        when {
            acc != null -> if (overlay == null || !inAccess) {
                drop()
                overlay = runCatching {
                    IslandOverlay(acc, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY).also { it.attach() }
                }.getOrNull()
                inAccess = overlay != null
            }
            Settings.canDrawOverlays(app) -> if (overlay == null) {
                overlay = runCatching { IslandOverlay(windowContext(app)).also { it.attach() } }.getOrNull()
                inAccess = false
            }
            else -> drop()
        }
        if (overlay != null) {
            registerPower(app)
            IslandService.instance?.replay()
        }
    }

    /** Пересоздать окно (например, чтобы капсула оказалась поверх полосы шторок). */
    fun reattach(ctx: Context) { drop(); ensure(ctx) }

    fun drop() {
        runCatching { overlay?.detach() }
        overlay = null; inAccess = false
    }

    fun onConfig(cfg: Configuration) { overlay?.onConfig(cfg) }

    /** Окно «поверх приложений» из Application-контекста: на Android 11+ нужен оконный контекст. */
    private fun windowContext(app: Context): Context = if (Build.VERSION.SDK_INT >= 30) runCatching {
        val d = app.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        app.createDisplayContext(d).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    }.getOrDefault(app) else app

    private fun registerPower(app: Context) {
        if (power != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action == Intent.ACTION_POWER_CONNECTED) {
                    val pct = (c.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    overlay?.showCharging(pct)
                }
            }
        }
        runCatching {
            ContextCompat.registerReceiver(app, r, IntentFilter(Intent.ACTION_POWER_CONNECTED), ContextCompat.RECEIVER_NOT_EXPORTED)
            power = r
        }
    }
}
