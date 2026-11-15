package com.beamox.launcher.core

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Фирменные обои Beamox.
 * Если положить свою картинку в /sdcard/Android/data/com.beamox.launcher/files/lockscreen.png
 * (install.sh делает это из scripts/mac/brand/lockscreen.png) — будет использована она.
 */
object BeamoxWallpaper {
    const val VERSION = 1

    suspend fun apply(ctx: Context, company: String, tagline: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val wm = WallpaperManager.getInstance(ctx)
            val dm = ctx.resources.displayMetrics
            val w = minOf(dm.widthPixels, dm.heightPixels).coerceAtLeast(720)
            val h = maxOf(dm.widthPixels, dm.heightPixels).coerceAtLeast(1600)

            val custom = File(ctx.getExternalFilesDir(null), "lockscreen.png")
            val lock = if (custom.exists()) BitmapFactory.decodeFile(custom.path) else draw(w, h, company, tagline, big = true)
            val customHome = File(ctx.getExternalFilesDir(null), "homescreen.png")
            val home = if (customHome.exists()) BitmapFactory.decodeFile(customHome.path) else draw(w, h, company, tagline, big = false)

            wm.setBitmap(home, null, true, WallpaperManager.FLAG_SYSTEM)
            wm.setBitmap(lock, null, true, WallpaperManager.FLAG_LOCK)
        }
    }

    fun draw(w: Int, h: Int, company: String, tagline: String, big: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Глубокий фон «как в iOS»: тёмно-синий → фиолетовый → синий
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(Color.parseColor("#070B1C"), Color.parseColor("#1A1450"), Color.parseColor("#0E2A6B")),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)

        // Мягкие световые пятна
        fun glow(x: Float, y: Float, r: Float, color: String) {
            p.shader = RadialGradient(x, y, r, Color.parseColor(color), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, p)
        }
        glow(w * 0.15f, h * 0.22f, w * 0.85f, "#995B3FD9")
        glow(w * 0.95f, h * 0.62f, w * 0.9f, "#992F80FF")
        glow(w * 0.4f, h * 1.02f, w * 0.8f, "#8834C7A5")
        p.shader = null

        if (big) {
            // Логотип-знак: скруглённый квадрат с «B»
            val s = w * 0.26f
            val cx = w / 2f
            val top = h * 0.56f
            val rect = RectF(cx - s / 2, top, cx + s / 2, top + s)
            p.color = Color.argb(46, 255, 255, 255)
            c.drawRoundRect(rect, s * 0.24f, s * 0.24f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = w * 0.004f; p.color = Color.argb(90, 255, 255, 255)
            c.drawRoundRect(rect, s * 0.24f, s * 0.24f, p)
            p.style = Paint.Style.FILL
            p.color = Color.WHITE
            p.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            p.textAlign = Paint.Align.CENTER
            p.textSize = s * 0.62f
            c.drawText("B", cx, top + s * 0.72f, p)

            // Надпись BEAMOX
            p.textSize = w * 0.13f
            p.letterSpacing = 0.18f
            p.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            c.drawText(company.uppercase(), cx, top + s + w * 0.17f, p)
            if (tagline.isNotBlank()) {
                p.textSize = w * 0.038f
                p.letterSpacing = 0.3f
                p.color = Color.argb(170, 255, 255, 255)
                c.drawText(tagline.uppercase(), cx, top + s + w * 0.245f, p)
            }
        } else {
            p.color = Color.argb(28, 255, 255, 255)
            p.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            p.textAlign = Paint.Align.CENTER
            p.textSize = w * 0.2f
            p.letterSpacing = 0.2f
            c.drawText(company.uppercase(), w / 2f, h * 0.62f, p)
        }
        return bmp
    }
}
