package com.beamox.launcher.island

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Доступ к уведомлениям: наполняет «островок» (музыка, звонки, баннеры) и шторку уведомлений.
 * Саму капсулу держит [IslandHub].
 */
class IslandService : NotificationListenerService() {
    /** Последнее показанное содержимое по ключу уведомления — чтобы обновления одного и того же не мигали */
    private val shown = HashMap<String, String>()

    override fun onListenerConnected() {
        instance = this
        IslandHub.ensure(this)
        NotifBus.bump()
    }

    override fun onListenerDisconnected() { cleanup() }
    override fun onDestroy() { cleanup(); super.onDestroy() }

    private fun cleanup() {
        if (instance === this) instance = null
        NotifBus.bump()
    }

    /** Уже идущие музыка/звонок — сразу в капсулу, без баннеров */
    fun replay() { runCatching { activeNotifications?.forEach { handle(it, initial = true) } } }

    override fun onNotificationPosted(sbn: StatusBarNotification) { handle(sbn, initial = false); NotifBus.bump() }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        shown.remove(sbn.key)
        IslandHub.current?.removeLive(sbn.key)
        NotifBus.bump()
    }

    private fun handle(sbn: StatusBarNotification, initial: Boolean) {
        val o = IslandHub.current ?: return
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

    companion object {
        @Volatile var instance: IslandService? = null
        private fun prefs(ctx: Context) = ctx.getSharedPreferences("island", Context.MODE_PRIVATE)
        fun enabled(ctx: Context) = prefs(ctx).getBoolean("on", true)
        fun setEnabled(ctx: Context, on: Boolean) {
            prefs(ctx).edit().putBoolean("on", on).apply()
            refresh(ctx)
        }
        /** После выдачи разрешений: поднять капсулу (или попросить систему переподключить службу). */
        fun refresh(ctx: Context) {
            IslandHub.ensure(ctx)
            if (instance == null) runCatching { requestRebind(ComponentName(ctx, IslandService::class.java)) }
        }
    }
}

/** Счётчик изменений уведомлений — шторка перечитывает список, когда он меняется. */
object NotifBus {
    var version by mutableIntStateOf(0)
        private set
    fun bump() { version++ }
}
