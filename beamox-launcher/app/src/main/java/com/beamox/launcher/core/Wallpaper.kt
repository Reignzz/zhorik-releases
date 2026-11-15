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
    const val VERSION = 4

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
            Unit
        }
    }

    fun draw(w: Int, h: Int, company: String, tagline: String, big: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // «Титан»: тёмно-серый фон с мягким диагональным бликом и мелким зерном (как обои iPhone)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(Color.parseColor("#3A3B40"), Color.parseColor("#26272B"), Color.parseColor("#1B1C1F"), Color.parseColor("#2C2D31")),
            floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        // диагональные световые полосы
        fun band(x0: Float, y0: Float, x1: Float, y1: Float, color: String) {
            p.shader = LinearGradient(x0, y0, x1, y1, intArrayOf(Color.TRANSPARENT, Color.parseColor(color), Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
        band(w * 0.25f, h * 0.05f, w * 0.75f, h * 0.30f, "#22FFFFFF")
        band(w * 0.10f, h * 0.55f, w * 0.85f, h * 0.80f, "#14FFFFFF")
        p.shader = null
        // зерно
        val rnd = java.util.Random(7)
        val dot = Paint()
        repeat(w * h / 180) {
            dot.color = Color.argb(rnd.nextInt(26) + 6, 255, 255, 255)
            c.drawPoint(rnd.nextFloat() * w, rnd.nextFloat() * h, dot)
        }
        if (big) {
            // Знак-лучи и надпись BEAMOX в нижней трети (часы Android остаются сверху)
            val cx = w / 2f; val cy = h * 0.70f; val s = w * 0.0042f
            p.color = Color.parseColor("#E8794F"); p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = 4f * s
            for (k in 0 until 12) {
                val a = Math.PI / 6 * k + .13; val r1 = 4.5f * s; val r2 = (if (k % 2 == 1) 15f else 18f) * s
                c.drawLine(cx + r1 * Math.cos(a).toFloat(), cy + r1 * Math.sin(a).toFloat(),
                    cx + r2 * Math.cos(a).toFloat(), cy + r2 * Math.sin(a).toFloat(), p)
            }
            c.drawCircle(cx, cy, 5f * s, p)
            p.color = Color.parseColor("#F6F3ED")
            p.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            p.textAlign = Paint.Align.CENTER
            p.textSize = w * 0.075f
            p.letterSpacing = 0.32f
            c.drawText(company.uppercase(), cx, cy + w * 0.17f, p)
            if (tagline.isNotBlank()) {
                p.textSize = w * 0.032f; p.letterSpacing = 0.25f; p.color = Color.parseColor("#8F8B85")
                p.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                c.drawText(tagline.uppercase(), cx, cy + w * 0.225f, p)
            }
        }
        return bmp
    }
}
