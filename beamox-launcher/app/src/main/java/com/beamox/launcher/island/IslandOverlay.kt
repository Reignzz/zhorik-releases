package com.beamox.launcher.island

import android.animation.ValueAnimator
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class IslandKind { NOTIFY, CHARGE, MEDIA, CALL }

data class IslandItem(
    val key: String, val pkg: String, val icon: Drawable?, val title: String, val text: String,
    val intent: PendingIntent?, val kind: IslandKind,
)

/**
 * Окно капсулы. В покое оно размером с капсулу и не принимает касаний (под ним строка состояния),
 * в раскрытом виде — во всю ширину и нажимается: нажатие открывает уведомление, смахивание вверх — закрывает.
 */
class IslandOverlay(
    private val ctx: Context,
    /** TYPE_ACCESSIBILITY_OVERLAY (из службы спец. возможностей) — поверх строки состояния; иначе обычное «поверх приложений» */
    windowType: Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val h = Handler(Looper.getMainLooper())
    private val d = ctx.resources.displayMetrics.density
    private fun dp(v: Float) = v * d

    private val view = IslandView(ctx, this)
    private val lp = WindowManager.LayoutParams(
        1, 1,
        windowType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "BeamoxIsland"
        if (Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        else if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
    private var attached = false

    // Геометрия (в координатах экрана)
    var screenW = 0; private set
    var centerX = 0f; private set
    var pillTop = 0f; private set
    var idleW = 0f; private set
    var idleH = 0f; private set
    val liveW get() = idleW + dp(84f)
    val bannerW get() = screenW - dp(20f)
    val bannerH get() = dp(78f)

    /** Музыка/звонок — живые занятия капсулы (звонок важнее) */
    private val live = LinkedHashMap<String, IslandItem>()
    var banner: IslandItem? = null; private set
    var chargePct = -1; private set
    private val collapse = Runnable { hideBanner() }

    fun attach() {
        measure()
        view.snapTo(compactRect(), idleH / 2)
        applyWindow(expanded = false)
        wm.addView(view, lp)
        attached = true
    }

    fun detach() {
        h.removeCallbacksAndMessages(null)
        if (attached) runCatching { wm.removeView(view) }
        attached = false
    }

    /** Где камера: вырез сверху; нет выреза — по центру под краем. */
    private fun measure() {
        var cut: Rect? = null
        var sb = dp(28f).toInt()
        if (Build.VERSION.SDK_INT >= 30) {
            val m = wm.currentWindowMetrics
            screenW = m.bounds.width()
            val ins = m.windowInsets
            cut = ins.displayCutout?.boundingRectTop?.takeIf { !it.isEmpty }
            sb = ins.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top.takeIf { it > 0 } ?: sb
        } else {
            screenW = ctx.resources.displayMetrics.widthPixels
        }
        val cutH = cut?.height()?.toFloat() ?: 0f
        idleH = max(cutH + dp(10f), dp(32f)).coerceAtMost(max(sb.toFloat(), dp(32f)) + dp(6f))
        idleW = max(idleH * 3.4f, (cut?.width() ?: 0) + dp(60f))
        centerX = cut?.exactCenterX() ?: (screenW / 2f)
        pillTop = if (cut != null) max(dp(2f), cut.exactCenterY() - idleH / 2f) else max(dp(2f), (sb - idleH) / 2f)
    }

    fun compactRect(): RectF {
        val w = when { live.isNotEmpty() -> liveW; else -> idleW }
        return RectF(centerX - w / 2, pillTop, centerX + w / 2, pillTop + idleH)
    }

    private fun bannerRect(): RectF {
        val w = bannerW
        val cx = screenW / 2f
        return RectF(cx - w / 2, pillTop, cx + w / 2, pillTop + bannerH)
    }

    /** Размер окна под состояние: компактное — с капсулу (касания проходят), раскрытое — во всю ширину. */
    private fun applyWindow(expanded: Boolean) {
        if (expanded) {
            lp.x = 0; lp.width = screenW; lp.height = (pillTop + bannerH + dp(10f)).toInt()
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            val w = (liveW + dp(8f)).toInt()
            lp.x = (centerX - w / 2f).toInt(); lp.width = w; lp.height = (pillTop + idleH + dp(4f)).toInt()
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        lp.y = 0
        view.originX = lp.x.toFloat()
        if (attached) runCatching { wm.updateViewLayout(view, lp) }
    }

    // ---------- события ----------
    fun setLive(item: IslandItem) {
        val had = live.containsKey(item.key)
        live[item.key] = item
        if (item.kind == IslandKind.CALL) { live.remove(item.key); val rest = LinkedHashMap(live); live.clear(); live[item.key] = item; live.putAll(rest) }
        view.liveItem = currentLive()
        if (banner == null) view.animateTo(compactRect(), idleH / 2)
        else if (!had) view.invalidate()
    }

    fun removeLive(key: String) {
        if (live.remove(key) == null) return
        view.liveItem = currentLive()
        if (banner == null) view.animateTo(compactRect(), idleH / 2)
    }

    private fun currentLive(): IslandItem? = live.values.firstOrNull { it.kind == IslandKind.CALL } ?: live.values.firstOrNull()

    fun showBanner(item: IslandItem) {
        if (!attached) return
        banner = item; chargePct = -1
        expand(4200L)
    }

    fun showCharging(pct: Int) {
        if (!attached) return
        banner = IslandItem("charge", ctx.packageName, null, "Зарядка", "", null, IslandKind.CHARGE)
        chargePct = pct
        expand(2600L)
    }

    private fun expand(ms: Long) {
        h.removeCallbacks(collapse)
        applyWindow(expanded = true)
        view.bannerItem = banner; view.chargePct = chargePct
        view.animateTo(bannerRect(), min(bannerH / 2, dp(36f)))
        h.postDelayed(collapse, ms)
    }

    fun hideBanner() {
        h.removeCallbacks(collapse)
        if (banner == null) return
        banner = null
        view.bannerItem = null
        view.animateTo(compactRect(), idleH / 2) { if (banner == null) applyWindow(expanded = false) }
    }

    fun openBanner() {
        val b = banner
        hideBanner()
        val pi = b?.intent ?: (if (b != null && b.kind != IslandKind.CHARGE)
            ctx.packageManager.getLaunchIntentForPackage(b.pkg)?.let {
                PendingIntent.getActivity(ctx, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            } else null) ?: return
        val opts = if (Build.VERSION.SDK_INT >= 34) ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle() else null
        runCatching { pi.send(ctx, 0, null, null, null, null, opts) }
    }

    /** Поворот экрана / смена размера: пересчитать; в альбомной ориентации капсулу прячем. */
    fun onConfig(cfg: Configuration) {
        if (!attached) return
        view.visibility = if (cfg.orientation == Configuration.ORIENTATION_LANDSCAPE) View.GONE else View.VISIBLE
        measure()
        val expanded = banner != null
        applyWindow(expanded)
        view.snapTo(if (expanded) bannerRect() else compactRect(), if (expanded) min(bannerH / 2, dp(36f)) else idleH / 2)
    }
}

/** Рисует капсулу и её содержимое. Координаты капсулы — экранные, originX — сдвиг окна. */
class IslandView(ctx: Context, private val o: IslandOverlay) : View(ctx) {
    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d
    var originX = 0f
    var liveItem: IslandItem? = null
        set(v) { field = v; invalidate() }
    var bannerItem: IslandItem? = null
        set(v) { field = v; invalidate() }
    var chargePct = -1

    private val cur = RectF(); private var curR = 0f
    private val from = RectF(); private var fromR = 0f
    private val to = RectF(); private var toR = 0f
    private var anim: ValueAnimator? = null

    private val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val green = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(48, 209, 88) }
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val titleP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = dp(15f); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val textP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 235, 235, 245); textSize = dp(13.5f) }
    private val smallP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = dp(13f); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private var downY = 0f

    fun snapTo(r: RectF, radius: Float) { anim?.cancel(); cur.set(r); curR = radius; invalidate() }

    fun animateTo(r: RectF, radius: Float, end: (() -> Unit)? = null) {
        anim?.cancel()
        from.set(cur); fromR = curR; to.set(r); toR = radius
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420
            interpolator = OvershootInterpolator(0.9f)
            addUpdateListener {
                val t = it.animatedValue as Float
                cur.set(lerp(from.left, to.left, t), lerp(from.top, to.top, t), lerp(from.right, to.right, t), lerp(from.bottom, to.bottom, t))
                curR = max(0f, lerp(fromR, toR, t))
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { end?.invoke() }
            })
            start()
        }
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        o.onConfig(newConfig)
    }

    override fun onDraw(c: Canvas) {
        c.save()
        c.translate(-originX, 0f)
        val r = RectF(cur)
        c.drawRoundRect(r, curR, curR, black)
        val b = bannerItem
        val grown = r.height() > o.idleH * 1.6f
        when {
            b != null && grown -> drawBanner(c, r, b)
            b == null -> liveItem?.let { drawLive(c, r, it) }
        }
        c.restore()
        // Эквалайзер двигается ~15 раз в секунду (а не 60) — бережём батарею
        if (bannerItem == null && liveItem?.kind == IslandKind.MEDIA) postInvalidateDelayed(66)
    }

    private fun drawIcon(c: Canvas, dr: Drawable?, l: Float, t: Float, size: Float) {
        if (dr == null) return
        c.save()
        val path = android.graphics.Path().apply { addRoundRect(RectF(l, t, l + size, t + size), size * 0.28f, size * 0.28f, android.graphics.Path.Direction.CW) }
        c.clipPath(path)
        dr.setBounds(l.toInt(), t.toInt(), (l + size).toInt(), (t + size).toInt())
        dr.draw(c)
        c.restore()
    }

    private fun drawBanner(c: Canvas, r: RectF, b: IslandItem) {
        val pad = dp(14f)
        if (b.kind == IslandKind.CHARGE) {
            // Батарейка слева, процент справа — как на iPhone при подключении зарядки
            val bw = dp(34f); val bh = dp(17f)
            val bl = r.left + pad + dp(6f); val bt = r.centerY() - bh / 2
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = dp(1.5f) }
            c.drawRoundRect(RectF(bl, bt, bl + bw, bt + bh), dp(5f), dp(5f), stroke)
            c.drawRoundRect(RectF(bl + bw + dp(2f), bt + bh * 0.3f, bl + bw + dp(4.5f), bt + bh * 0.7f), dp(1.5f), dp(1.5f), stroke)
            val f = (chargePct.coerceIn(0, 100) / 100f)
            c.drawRoundRect(RectF(bl + dp(2.5f), bt + dp(2.5f), bl + dp(2.5f) + (bw - dp(5f)) * f, bt + bh - dp(2.5f)), dp(3f), dp(3f), green)
            c.drawText("Зарядка", bl + bw + dp(16f), r.centerY() + dp(5f), titleP)
            val pct = if (chargePct >= 0) "$chargePct %" else ""
            val gp = TextPaint(titleP).apply { color = green.color; textSize = dp(17f) }
            c.drawText(pct, r.right - pad - dp(6f) - gp.measureText(pct), r.centerY() + dp(6f), gp)
            return
        }
        val ic = dp(46f)
        drawIcon(c, b.icon, r.left + pad, r.centerY() - ic / 2, ic)
        val tx = r.left + pad + ic + dp(12f)
        val tw = r.right - pad - tx
        val title = TextUtils.ellipsize(b.title, titleP, tw, TextUtils.TruncateAt.END).toString()
        val text = TextUtils.ellipsize(b.text.replace('\n', ' '), textP, tw, TextUtils.TruncateAt.END).toString()
        if (text.isEmpty()) c.drawText(title, tx, r.centerY() + dp(5f), titleP)
        else {
            c.drawText(title, tx, r.centerY() - dp(3f), titleP)
            c.drawText(text, tx, r.centerY() + dp(16f), textP)
        }
    }

    private fun drawLive(c: Canvas, r: RectF, it: IslandItem) {
        if (r.width() < o.idleW + dp(20f)) return
        val s = r.height() - dp(10f)
        if (it.kind == IslandKind.CALL) {
            c.drawCircle(r.left + dp(5f) + s / 2, r.centerY(), s / 2, green)
            val ph = TextPaint(smallP).apply { textSize = s * 0.55f; textAlign = Paint.Align.CENTER }
            c.drawText("✆", r.left + dp(5f) + s / 2, r.centerY() + s * 0.2f, ph)
            val name = TextUtils.ellipsize(it.title, smallP, dp(64f), TextUtils.TruncateAt.END).toString()
            val gp = TextPaint(smallP).apply { color = green.color }
            c.drawText(name, r.right - dp(10f) - gp.measureText(name), r.centerY() + dp(4.5f), gp)
            return
        }
        // Музыка: обложка слева, «эквалайзер» справа
        drawIcon(c, it.icon, r.left + dp(5f), r.centerY() - s / 2, s)
        val t = (System.currentTimeMillis() % 100_000L) / 1000.0
        val bw = dp(3f); val gap = dp(2.5f)
        val right = r.right - dp(12f)
        for (i in 0 until 4) {
            val hh = s * (0.3f + 0.55f * abs(sin(t * (2.1 + i * 0.7) + i)).toFloat())
            val x = right - (4 - i) * (bw + gap)
            c.drawRoundRect(RectF(x, r.centerY() - hh / 2, x + bw, r.centerY() + hh / 2), bw / 2, bw / 2, white)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (bannerItem == null) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downY = e.rawY; return true }
            MotionEvent.ACTION_UP -> {
                val inside = RectF(cur).apply { offset(-originX, 0f) }.contains(e.x, e.y)
                if (downY - e.rawY > dp(24f) || !inside) o.hideBanner() else o.openBanner()
                return true
            }
        }
        return true
    }
}
