package com.beamox.launcher.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.beamox.launcher.MainActivity
import com.beamox.launcher.core.BeamoxConfig
import com.beamox.launcher.core.Prefs

/**
 * Режим «без рук»: постоянно слушает фразу «Привет, Бимокс» (как «Привет, Siri»),
 * затем передаёт команду лаунчеру. Работает при включённом экране, на выключенном — спит.
 */
class VoiceService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var engine: VoiceEngine
    private var wakeWords: List<String> = emptyList()
    private var running = false
    private var paused = false
    private var screenOn = true
    private var errorStreak = 0

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            screenOn = i.action != Intent.ACTION_SCREEN_OFF
            if (screenOn) scheduleListen(400) else engine.cancelListening()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        engine = VoiceEngine(this)
        wakeWords = BeamoxConfig.load(this).wakeWords
        val f = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) }
        ContextCompat.registerReceiver(this, screenReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs(this).handsFree = false
            stopSelf()
            return START_NOT_STICKY
        }
        val n = notification()
        try {
            if (Build.VERSION.SDK_INT >= 30) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(1, n)
        } catch (e: Exception) {
            // Android 14+: микрофон нельзя включать из фона (например, при перезапуске системой) —
            // режим включится снова, когда откроется лаунчер.
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) { running = true; scheduleListen(300) }
        return START_STICKY
    }

    private fun scheduleListen(delay: Long) {
        main.removeCallbacksAndMessages(null)
        main.postDelayed({ listenLoop() }, delay)
    }

    private fun listenLoop() {
        if (!running || paused || !screenOn || engine.speaking) {
            if (running && !paused && screenOn) scheduleListen(800)
            return
        }
        engine.listenOnce { text, err ->
            if (text != null) {
                errorStreak = 0
                val cmd = VoiceEngine.stripWakeWord(text, wakeWords)
                when {
                    cmd == null -> scheduleListen(150)
                    cmd.length >= 2 -> { deliver(cmd); scheduleListen(2500) }
                    else -> {
                        // Только «Привет, Бимокс» — отвечаем и ждём команду
                        engine.speak("Слушаю") {
                            engine.listenOnce(longSilence = true) { t2, _ ->
                                if (!t2.isNullOrBlank()) deliver(t2)
                                scheduleListen(2500)
                            }
                        }
                    }
                }
            } else {
                // Тишина — нормальная ситуация; частые ошибки — увеличиваем паузу, чтобы не грузить батарею
                val benign = err == SpeechRecognizer.ERROR_NO_MATCH || err == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                errorStreak = if (benign) 0 else errorStreak + 1
                scheduleListen(if (benign) 150L else minOf(30_000L, 1000L * errorStreak * errorStreak))
            }
        }
    }

    private fun deliver(command: String) {
        val i = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_VOICE)
            .putExtra(MainActivity.EXTRA_VOICE_TEXT, command)
            .putExtra(MainActivity.EXTRA_TOKEN, MainActivity.VOICE_TOKEN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try { startActivity(i) } catch (_: Exception) { }
    }

    fun pause() { paused = true; main.removeCallbacksAndMessages(null); engine.cancelListening() }
    fun resume() { paused = false; scheduleListen(600) }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CH, "Голосовой ассистент Beamox", NotificationManager.IMPORTANCE_MIN))
        }
        val stop = PendingIntent.getService(this, 1,
            Intent(this, VoiceService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 2,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Beamox слушает")
            .setContentText("Скажите: «Привет, Бимокс, открой офис»")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Выключить", stop).build())
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenReceiver) }
        engine.release()
        instance = null
        super.onDestroy()
    }

    companion object {
        private const val CH = "voice"
        const val ACTION_STOP = "com.beamox.launcher.VOICE_STOP"
        @Volatile var instance: VoiceService? = null

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, VoiceService::class.java))
        }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, VoiceService::class.java)) }
    }
}
