package com.beamox.launcher.island

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import com.beamox.launcher.ui.BeamoxTheme
import com.beamox.launcher.ui.LocalWallpaper
import com.beamox.launcher.ui.ShadeWallpaper
import com.beamox.launcher.ui.CcEnv
import com.beamox.launcher.ui.ControlCenterView
import com.beamox.launcher.ui.NotificationCenterView

enum class ShadeMode { NOTIFICATIONS, CONTROL }

/**
 * «Спец. возможности → Beamox: островок и шторки».
 * Экран не читает и ничего за владельца не нажимает: только рисует свои окна поверх всего —
 * капсулу у камеры и шторки как у iPhone (свайп сверху: слева — уведомления, справа — пункт управления).
 */
class BeamoxAccess : AccessibilityService() {
    private val h = Handler(Looper.getMainLooper())
    private val wm by lazy { getSystemService(WindowManager::class.java) }
    private var edge: View? = null
    private val shade by lazy { ShadeWindow(this) }

    override fun onServiceConnected() {
        instance = this
        addEdge()
        IslandHub.reattach(this) // капсула — поверх полосы шторок и строки состояния
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean { cleanup(); return super.onUnbind(intent) }
    override fun onDestroy() { cleanup(); super.onDestroy() }

    private fun cleanup() {
        if (instance !== this) return
        instance = null
        shade.hide(now = true)
        removeEdge()
        IslandHub.reattach(this) // переедет в обычное окно «поверх приложений», если оно разрешено
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // В альбомной (видео, игры) полосу у края убираем — чтобы не мешать кнопкам приложений
        edge?.visibility = if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) View.GONE else View.VISIBLE
    }

    // ---------- полоса у верхнего края ----------
    private fun statusBarPx(): Int {
        if (Build.VERSION.SDK_INT >= 30) runCatching {
            val t = wm.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
            if (t > 0) return t
        }
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else (28 * resources.displayMetrics.density).toInt()
    }

    fun addEdge() {
        if (edge != null || !shadeEnabled(this)) return
        val d = resources.displayMetrics.density
        val v = View(this)
        var downX = 0f; var downY = 0f; var fired = false
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; fired = false }
                MotionEvent.ACTION_MOVE -> if (!fired && e.rawY - downY > 14 * d) {
                    fired = true
                    open(if (downX > view.width * 0.62f) ShadeMode.CONTROL else ShadeMode.NOTIFICATIONS)
                }
                MotionEvent.ACTION_UP -> if (!fired && e.rawY - downY > 6 * d)
                    open(if (downX > view.width * 0.62f) ShadeMode.CONTROL else ShadeMode.NOTIFICATIONS)
            }
            true
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, statusBarPx(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "BeamoxShadeEdge"
            if (Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (runCatching { wm.addView(v, lp) }.isSuccess) edge = v
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) v.visibility = View.GONE
    }

    fun removeEdge() { edge?.let { runCatching { wm.removeView(it) } }; edge = null }

    fun open(mode: ShadeMode) { h.post { shade.show(mode) } }

    /** Системная шторка Android — на случай, если нужна её плитка */
    fun androidShade(quick: Boolean) {
        shade.hide(now = true)
        performGlobalAction(if (quick) GLOBAL_ACTION_QUICK_SETTINGS else GLOBAL_ACTION_NOTIFICATIONS)
    }

    companion object {
        @Volatile var instance: BeamoxAccess? = null
        private fun prefs(ctx: Context) = ctx.getSharedPreferences("island", Context.MODE_PRIVATE)
        fun shadeEnabled(ctx: Context) = prefs(ctx).getBoolean("shade", true)
        fun setShadeEnabled(ctx: Context, on: Boolean) {
            prefs(ctx).edit().putBoolean("shade", on).apply()
            val s = instance ?: return
            if (on) { s.addEdge(); IslandHub.reattach(s) } else s.removeEdge()
        }
        fun isEnabled(ctx: Context): Boolean {
            val me = ComponentName(ctx, BeamoxAccess::class.java)
            val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return list.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}

/** Окно шторок: Compose поверх всего, назад/смахивание вверх — закрыть. */
private class ShadeWindow(private val svc: BeamoxAccess) : LifecycleOwner, SavedStateRegistryOwner {
    private val wm = svc.getSystemService(WindowManager::class.java)
    private val h = Handler(Looper.getMainLooper())
    private val registry = LifecycleRegistry(this)
    private val ssc = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = ssc.savedStateRegistry

    private var root: FrameLayout? = null
    private var mode by mutableStateOf(ShadeMode.NOTIFICATIONS)
    private val visible = MutableTransitionState(false)
    private val removeLater = Runnable { remove() }

    init {
        ssc.performAttach()
        ssc.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun show(m: ShadeMode) {
        h.removeCallbacks(removeLater)
        mode = m
        if (root == null) create()
        registry.currentState = Lifecycle.State.RESUMED
        visible.targetState = true
    }

    fun hide(now: Boolean = false) {
        visible.targetState = false
        h.removeCallbacks(removeLater)
        if (now) remove() else h.postDelayed(removeLater, 320)
    }

    private fun remove() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        visible.targetState = false
        if (registry.currentState.isAtLeast(Lifecycle.State.STARTED)) registry.currentState = Lifecycle.State.CREATED
    }

    private fun create() {
        val frame = object : FrameLayout(svc) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) hide()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        frame.setViewTreeLifecycleOwner(this)
        frame.setViewTreeSavedStateRegistryOwner(this)
        val env = CcEnv(
            ctx = svc,
            start = { i -> hide(now = true); runCatching { svc.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
            androidShade = { quick -> svc.androidShade(quick) },
            close = { hide(now = true) },
        )
        val cv = ComposeView(svc).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                BeamoxTheme { CompositionLocalProvider(LocalWallpaper provides ShadeWallpaper.full(svc)) {
                    AnimatedVisibility(visible, enter = fadeIn(tween(200)) + scaleIn(tween(240), initialScale = 0.94f), exit = fadeOut(tween(170)) + scaleOut(tween(170), targetScale = 0.96f)) {
                        when (mode) {
                            ShadeMode.CONTROL -> ControlCenterView(env) { hide() }
                            ShadeMode.NOTIFICATIONS -> NotificationCenterView(env) { hide() }
                        }
                    }
                } }
            }
        }
        frame.addView(cv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "BeamoxShade"
            if (Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            if (Build.VERSION.SDK_INT >= 31 && wm.isCrossWindowBlurEnabled) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = 70
            }
        }
        if (runCatching { wm.addView(frame, lp) }.isSuccess) {
            root = frame
            frame.isFocusableInTouchMode = true
            frame.requestFocus()
        }
    }
}
