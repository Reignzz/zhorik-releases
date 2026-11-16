package com.beamox.launcher.island

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat

/**
 * «Островок» как Dynamic Island на iPhone: чёрная капсула вокруг камеры поверх всех приложений.
 * Живёт в службе доступа к уведомлениям (её держит сама система), рисуется окном «поверх других приложений».
 * Нужны два разрешения владельца: «Поверх других приложений» и «Доступ к уведомлениям».
 */
class IslandService : NotificationListenerService() {
    private var overlay: IslandOverlay? = null
    private var power: BroadcastReceiver? = null
    /** Последнее показанное содержимое по ключу уведомления — чтобы обновления одного и того же не мигали */
    private val shown = HashMap<String, String>()

    override fun onListenerConnected() {
        instance = this
        attach()
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action == Intent.ACTION_POWER_CONNECTED) overlay?.showCharging(batteryPct())
            }
        }
        ContextCompat.registerReceiver(this, r, IntentFilter(Intent.ACTION_POWER_CONNECTED), ContextCompat.RECEIVER_NOT_EXPORTED)
        power = r
        // Уже идущие музыка/звонок — сразу в капсулу, без баннеров
        runCatching { activeNotifications?.forEach { handle(it, initial = true) } }
    }

    override fun onListenerDisconnected() { cleanup() }
    override fun onDestroy() { cleanup(); super.onDestroy() }

    private fun cleanup() {
        detach()
        power?.let { runCatching { unregisterReceiver(it) } }; power = null
        if (instance === this) instance = null
    }

    fun attach() {
        if (overlay != null || !enabled(this) || !Settings.canDrawOverlays(this)) return
        overlay = runCatching { IslandOverlay(this).also { it.attach() } }.getOrNull()
        runCatching { activeNotifications?.forEach { handle(it, initial = true) } }
    }

    fun detach() { overlay?.detach(); overlay = null }

    override fun onNotificationPosted(sbn: StatusBarNotification) { handle(sbn, initial = false) }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        shown.remove(sbn.key)
        overlay?.removeLive(sbn.key)
    }

    private fun handle(sbn: StatusBarNotification, initial: Boolean) {
        val o = overlay ?: return
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()

        // Музыка и видео: идёт воспроизведение — капсула с обложкой и эквалайзером
        @Suppress("DEPRECATION")
        val token = if (Build.VERSION.SDK_INT >= 33) ex.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
            else ex.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
        if (token != null) {
            val playing = runCatching { MediaController(this, token).playbackState?.state == PlaybackState.STATE_PLAYING }.getOrDefault(false)
            if (playing) o.setLive(IslandItem(sbn.key, sbn.packageName, icon(sbn), title, text, n.contentIntent, IslandKind.MEDIA))
            else o.removeLive(sbn.key)
            return
        }
        // Идущий звонок — зелёная капсула
        if (n.category == Notification.CATEGORY_CALL && sbn.isOngoing) {
            o.setLive(IslandItem(sbn.key, sbn.packageName, icon(sbn), title, text, n.contentIntent, IslandKind.CALL))
            return
        }
        if (initial || sbn.isOngoing) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (title.isEmpty() && text.isEmpty()) return
        // Беззвучные уведомления (важность ниже обычной) не раскрываем
        val rk = Ranking()
        if (runCatching { currentRanking.getRanking(sbn.key, rk) }.getOrDefault(false) &&
            rk.importance in 1 until android.app.NotificationManager.IMPORTANCE_DEFAULT) return
        val sig = "$title|$text"
        if (shown[sbn.key] == sig) return
        if (shown.containsKey(sbn.key) && n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0) { shown[sbn.key] = sig; return }
        shown[sbn.key] = sig
        if (shown.size > 200) shown.keys.take(100).toList().forEach { shown.remove(it) }
        o.showBanner(IslandItem(sbn.key, sbn.packageName, icon(sbn), title.ifEmpty { appLabel(sbn.packageName) }, text, n.contentIntent, IslandKind.NOTIFY))
    }

    private fun icon(sbn: StatusBarNotification) = runCatching {
        sbn.notification.getLargeIcon()?.loadDrawable(this)
    }.getOrNull() ?: runCatching { packageManager.getApplicationIcon(sbn.packageName) }.getOrNull()

    private fun appLabel(pkg: String) = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun batteryPct() = (getSystemService(BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    companion object {
        @Volatile var instance: IslandService? = null
        private fun prefs(ctx: Context) = ctx.getSharedPreferences("island", Context.MODE_PRIVATE)
        fun enabled(ctx: Context) = prefs(ctx).getBoolean("on", true)
        fun setEnabled(ctx: Context, on: Boolean) {
            prefs(ctx).edit().putBoolean("on", on).apply()
            instance?.let { if (on) it.attach() else it.detach() } ?: refresh(ctx)
        }
        /** После выдачи разрешений: поднять капсулу (или попросить систему переподключить службу). */
        fun refresh(ctx: Context) {
            val s = instance
            if (s != null) { s.attach(); return }
            runCatching { requestRebind(ComponentName(ctx, IslandService::class.java)) }
        }
    }
}
