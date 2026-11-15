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
    const val VERSION = 2

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
        // Фон панели Beamox: тёплый тёмный градиент
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
            intArrayOf(Color.parseColor("#1E1624"), Color.parseColor("#120F17"), Color.parseColor("#0E0F13")),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        fun glow(x: Float, y: Float, r: Float, color: String) {
            p.shader = RadialGradient(x, y, r, Color.parseColor(color), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
        glow(w * 1.0f, 0f, w * 1.25f, "#5CFF7A50")      // коралл справа сверху
        glow(0f, h * 1.0f, w * 1.1f, "#3DFF3D7F")         // розовый слева снизу
        glow(w * 0.2f, h * 0.45f, w * 0.7f, "#1F8B7BFF")  // лёгкий фиолет
        p.shader = null
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
