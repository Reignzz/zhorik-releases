package com.beamox.launcher.core

import android.content.ContentValues
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import com.beamox.launcher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Фирменные звуки Beamox: рингтон, уведомление, сообщение, будильник + звуки интерфейса. */
object Sounds {
    private data class Tone(val res: Int, val file: String, val title: String, val type: Int, val dir: String)

    private val tones = listOf(
        Tone(R.raw.beamox_ring, "beamox_ring.wav", "Beamox Opening", RingtoneManager.TYPE_RINGTONE, Environment.DIRECTORY_RINGTONES),
        Tone(R.raw.beamox_notify, "beamox_notify.wav", "Beamox Glass", RingtoneManager.TYPE_NOTIFICATION, Environment.DIRECTORY_NOTIFICATIONS),
        Tone(R.raw.beamox_message, "beamox_message.wav", "Beamox Note", 0, Environment.DIRECTORY_NOTIFICATIONS),
        Tone(R.raw.beamox_alarm, "beamox_alarm.wav", "Beamox Morning", RingtoneManager.TYPE_ALARM, Environment.DIRECTORY_ALARMS),
    )

    fun canWriteSettings(ctx: Context) = Settings.System.canWrite(ctx)

    /**
     * Копирует звуки в системную медиатеку и (если есть разрешение «Изменение системных настроек»,
     * install.sh выдаёт его сам) ставит их по умолчанию.
     */
    suspend fun install(ctx: Context): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT < 29) error("Нужен Android 10+")
            val cr = ctx.contentResolver
            val col = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            var applied = 0
            for (t in tones) {
                // Удаляем старую копию, чтобы не плодить дубликаты
                cr.delete(col, "${MediaStore.MediaColumns.DISPLAY_NAME}=?", arrayOf(t.file))
                val v = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, t.file)
                    put(MediaStore.MediaColumns.TITLE, t.title)
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, t.dir + "/")
                    put(MediaStore.Audio.Media.IS_RINGTONE, t.type == RingtoneManager.TYPE_RINGTONE)
                    put(MediaStore.Audio.Media.IS_NOTIFICATION, t.dir == Environment.DIRECTORY_NOTIFICATIONS)
                    put(MediaStore.Audio.Media.IS_ALARM, t.type == RingtoneManager.TYPE_ALARM)
                    put(MediaStore.Audio.Media.IS_MUSIC, false)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri: Uri = cr.insert(col, v) ?: continue
                cr.openOutputStream(uri)?.use { out -> ctx.resources.openRawResource(t.res).use { it.copyTo(out) } }
                cr.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                if (t.type != 0 && canWriteSettings(ctx)) {
                    RingtoneManager.setActualDefaultRingtoneUri(ctx, t.type, uri); applied++
                }
            }
            if (applied > 0) "Звуки Beamox установлены и выбраны по умолчанию"
            else "Звуки добавлены. Выберите их в Настройки → Звук (или дайте разрешение «Изменение системных настроек»)"
        }
    }

    // ---- Звуки интерфейса
    private var pool: SoundPool? = null
    private var tapId = 0
    private var listenId = 0

    fun initUi(ctx: Context) {
        if (pool != null) return
        pool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build().also {
            tapId = it.load(ctx, R.raw.beamox_tap, 1)
            listenId = it.load(ctx, R.raw.beamox_listen, 1)
        }
    }

    fun tap() { pool?.play(tapId, 0.25f, 0.25f, 0, 0, 1f) }
    fun listen() { pool?.play(listenId, 0.6f, 0.6f, 1, 0, 1f) }
}
