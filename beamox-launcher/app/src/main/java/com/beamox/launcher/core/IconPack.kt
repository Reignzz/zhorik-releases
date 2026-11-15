package com.beamox.launcher.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.res.ResourcesCompat
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Наборы иконок из Google Play (стандартный формат ADW/Nova: appfilter.xml).
 * Пользователь ставит любой набор в стиле iOS — лаунчер применяет его ко всем приложениям.
 */
class IconPack(private val ctx: Context, val pkg: String) {
    data class Info(val pkg: String, val label: String)

    private var res: Resources? = null
    private val byComponent = HashMap<String, String>()   // "pkg/cls" -> drawable
    private val byPackage = HashMap<String, String>()     // "pkg" -> drawable (первое попадание)
    private val cache = HashMap<String, ImageBitmap?>()

    fun load(): Boolean = try {
        val r = ctx.packageManager.getResourcesForApplication(pkg)
        res = r
        val parser: XmlPullParser = r.getIdentifier("appfilter", "xml", pkg).takeIf { it != 0 }?.let { r.getXml(it) }
            ?: XmlPullParserFactory.newInstance().newPullParser().apply { setInput(r.assets.open("appfilter.xml"), "utf-8") }
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                val comp = parser.getAttributeValue(null, "component") ?: ""
                val dr = parser.getAttributeValue(null, "drawable") ?: ""
                val inner = comp.substringAfter("ComponentInfo{", "").substringBefore("}")
                if (inner.contains("/") && dr.isNotBlank()) {
                    val (p, c) = inner.split("/", limit = 2)
                    val cls = if (c.startsWith(".")) p + c else c
                    byComponent.putIfAbsent("$p/$cls", dr)
                    byPackage.putIfAbsent(p, dr)
                }
            }
            ev = parser.next()
        }
        byComponent.isNotEmpty()
    } catch (e: Exception) { false }

    /** Иконка из набора для компонента (или для пакета, если точного совпадения нет) */
    fun icon(component: ComponentName, sizePx: Int): ImageBitmap? {
        val key = component.packageName + "/" + component.className
        return cache.getOrPut(key) {
            val name = byComponent[key] ?: byPackage[component.packageName] ?: return@getOrPut null
            val r = res ?: return@getOrPut null
            val id = r.getIdentifier(name, "drawable", pkg).takeIf { it != 0 } ?: r.getIdentifier(name, "mipmap", pkg)
            if (id == 0) null else runCatching {
                val d: Drawable = ResourcesCompat.getDrawable(r, id, null) ?: return@runCatching null
                val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                val c = android.graphics.Canvas(bmp)
                d.setBounds(0, 0, sizePx, sizePx); d.draw(c)
                bmp.asImageBitmap()
            }.getOrNull()
        }
    }

    companion object {
        private val actions = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme",
            "com.teslacoilsw.launcher.THEME", "com.fede.launcher.THEME_ICONPACK", "com.anddoes.launcher.THEME")

        /** Установленные наборы иконок */
        fun installed(ctx: Context): List<Info> {
            val pm = ctx.packageManager
            return actions.flatMap { a ->
                runCatching { pm.queryIntentActivities(Intent(a), PackageManager.GET_META_DATA) }.getOrDefault(emptyList())
            }.map { it.activityInfo.packageName }.distinct().map { p ->
                Info(p, runCatching { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() }.getOrDefault(p))
            }.sortedBy { it.label.lowercase() }
        }
    }
}
