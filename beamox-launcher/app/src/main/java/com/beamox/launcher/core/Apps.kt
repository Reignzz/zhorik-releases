package com.beamox.launcher.core

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(
    val label: String,
    val pkg: String,
    val activity: String,
    val icon: ImageBitmap,
)

class AppRepository(private val ctx: Context) {
    private val iconCache = HashMap<String, ImageBitmap>()

    suspend fun load(pack: IconPack? = null): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map { ri ->
                val key = ri.activityInfo.packageName + "/" + ri.activityInfo.name
                val icon = pack?.icon(ComponentName(ri.activityInfo.packageName, ri.activityInfo.name), 192)
                    ?: iconCache.getOrPut(key) { ri.loadIcon(pm).toSquareBitmap(168).asImageBitmap() }
                AppEntry(ri.loadLabel(pm).toString(), ri.activityInfo.packageName, ri.activityInfo.name, icon)
            }
            .distinctBy { it.pkg + it.activity }
            .sortedBy { it.label.lowercase() }
    }

    fun invalidate() = iconCache.clear()

    fun launch(app: AppEntry) {
        val i = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(app.pkg, app.activity))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        safeStart(ctx, i)
    }

    fun launchPackage(pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return safeStart(ctx, i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun appInfo(pkg: String) = safeStart(ctx,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun uninstall(pkg: String) = safeStart(ctx,
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    companion object {
        val dialIntent get() = Intent(Intent.ACTION_DIAL)
        val cameraIntent get() = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)

        fun safeStart(ctx: Context, i: Intent): Boolean = try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "Нет приложения для этого действия", Toast.LENGTH_SHORT).show(); false
        } catch (e: SecurityException) {
            Toast.makeText(ctx, "Нет разрешения", Toast.LENGTH_SHORT).show(); false
        }

        /** Иконка «на весь квадрат» — чтобы обрезать её в iOS-скруглённый квадрат. */
        fun Drawable.toSquareBitmap(size: Int): Bitmap {
            if (this is AdaptiveIconDrawable) {
                val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                val pad = size / 4
                background?.let { it.setBounds(-pad, -pad, size + pad, size + pad); it.draw(c) }
                foreground?.let { it.setBounds(-pad, -pad, size + pad, size + pad); it.draw(c) }
                return bmp
            }
            return toBitmap(size, size, Bitmap.Config.ARGB_8888)
        }
    }
}
