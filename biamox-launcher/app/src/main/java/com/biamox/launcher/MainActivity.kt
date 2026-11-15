package com.biamox.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.biamox.launcher.core.BiamoxWallpaper
import com.biamox.launcher.core.Provisioning
import com.biamox.launcher.core.Sounds
import com.biamox.launcher.ui.BiamoxTheme
import com.biamox.launcher.ui.LauncherRoot
import com.biamox.launcher.ui.LauncherState
import com.biamox.launcher.voice.VoiceService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var state: LauncherState

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { state.appsRepo.invalidate(); state.reloadApps() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        state = LauncherState(this, lifecycleScope)
        Sounds.initUi(applicationContext)

        firstRunSetup()

        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, pkgReceiver, f, ContextCompat.RECEIVER_EXPORTED)

        if (state.prefs.handsFree && state.hasMic()) VoiceService.start(applicationContext)

        // Лаунчер не закрывается кнопкой «Назад»; разделы закрываются своими BackHandler (у них приоритет)
        onBackPressedDispatcher.addCallback(this) { }

        setContent { BiamoxTheme { LauncherRoot(state) } }
        handleIntent(intent, fresh = true)
    }

    /** Первый запуск / после install.sh: секреты, заставка, звуки. */
    private fun firstRunSetup() {
        val imported = Provisioning.importIfPresent(applicationContext, state.prefs, state.secrets)
        if (imported) { state.reloadConfig(); state.handsFree = state.prefs.handsFree }
        if (state.prefs.wallpaperApplied < BiamoxWallpaper.VERSION || imported) {
            lifecycleScope.launch {
                BiamoxWallpaper.apply(applicationContext, state.config.company, state.config.tagline)
                    .onSuccess { state.prefs.wallpaperApplied = BiamoxWallpaper.VERSION }
                Sounds.install(applicationContext)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent, fresh = false)
    }

    private fun handleIntent(intent: Intent?, fresh: Boolean) {
        val i = intent ?: return
        when (i.action) {
            // Голосовые команды принимаем только от своего VoiceService (секретный токен процесса),
            // чтобы чужое приложение не могло «приказать» лаунчеру позвонить или открыть что-то.
            ACTION_VOICE -> if (i.getStringExtra(EXTRA_TOKEN) == VOICE_TOKEN) {
                i.getStringExtra(EXTRA_VOICE_TEXT)?.let { state.handleVoiceText(it) }
            }
            Intent.ACTION_ASSIST, Intent.ACTION_VOICE_COMMAND -> state.startVoice()
            Intent.ACTION_MAIN -> if (!fresh && i.hasCategory(Intent.CATEGORY_HOME)) state.goHome()
        }
    }

    override fun onResume() {
        super.onResume()
        state.refreshDevice()
        // install.sh мог прислать новый provision.json, пока лаунчер работал
        if (Provisioning.importIfPresent(applicationContext, state.prefs, state.secrets)) {
            state.reloadConfig(); state.refreshStats(); state.refreshMail()
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pkgReceiver) }
        super.onDestroy()
    }

    companion object {
        const val ACTION_VOICE = "com.biamox.launcher.VOICE_COMMAND"
        const val EXTRA_VOICE_TEXT = "text"
        const val EXTRA_TOKEN = "token"
        /** Случайный токен, известный только этому процессу. */
        val VOICE_TOKEN: String = java.util.UUID.randomUUID().toString()
    }
}
