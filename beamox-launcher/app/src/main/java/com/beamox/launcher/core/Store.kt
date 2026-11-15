package com.beamox.launcher.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Обычные настройки лаунчера. */
class Prefs(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var hiddenApps: Set<String>
        get() = p.getStringSet("hidden", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("hidden", v).apply()

    /** Док: "module:CLAUDE", "sys:PHONE" или "pkg:com.android.chrome" */
    var dock: List<String>
        get() = p.getString("dock", null)?.split("|")?.filter { it.isNotBlank() }
            ?: listOf("sys:PHONE", "sys:MESSAGES", "module:CLAUDE", "sys:CAMERA")
        set(v) = p.edit().putString("dock", v.joinToString("|")).apply()

    var claudeModel: String?
        get() = p.getString("claudeModel", null)
        set(v) = p.edit().putString("claudeModel", v).apply()

    var studioUrl: String?
        get() = p.getString("studioUrl", null)
        set(v) = p.edit().putString("studioUrl", v).apply()

    var statsUrl: String?
        get() = p.getString("statsUrl", null)
        set(v) = p.edit().putString("statsUrl", v).apply()

    var handsFree: Boolean
        get() = p.getBoolean("handsFree", false)
        set(v) = p.edit().putBoolean("handsFree", v).apply()

    var speakAnswers: Boolean
        get() = p.getBoolean("speakAnswers", true)
        set(v) = p.edit().putBoolean("speakAnswers", v).apply()

    var wallpaperApplied: Int
        get() = p.getInt("wallpaperApplied", 0)
        set(v) = p.edit().putInt("wallpaperApplied", v).apply()

    var lastProvisionHash: Int
        get() = p.getInt("provHash", 0)
        set(v) = p.edit().putInt("provHash", v).apply()

    var dashboardUrl: String?
        get() = p.getString("dashUrl", null)
        set(v) = p.edit().putString("dashUrl", v).apply()

    var lastDashJson: String?
        get() = p.getString("lastDash", null)
        set(v) = p.edit().putString("lastDash", v).apply()

    var bloatScanned: Boolean
        get() = p.getBoolean("bloatScanned", false)
        set(v) = p.edit().putBoolean("bloatScanned", v).apply()

    var lastStatsJson: String?
        get() = p.getString("lastStats", null)
        set(v) = p.edit().putString("lastStats", v).apply()

    var chatJson: String
        get() = p.getString("chat", "[]") ?: "[]"
        set(v) = p.edit().putString("chat", v).apply()

    var tasksJson: String
        get() = p.getString("tasks", "[]") ?: "[]"
        set(v) = p.edit().putString("tasks", v).apply()
}

/** Секреты (ключ Claude, пароли почт, токен статистики) — зашифрованы ключом из Android Keystore. */
class Secrets(ctx: Context) {
    private val p: SharedPreferences = try {
        openPrefs(ctx)
    } catch (e: Throwable) {
        try {
            // Хранилище повреждено (например, после восстановления из бэкапа) — пересоздаём, но всё равно зашифрованным.
            ctx.deleteSharedPreferences("secrets")
            openPrefs(ctx)
        } catch (e2: Throwable) {
            // Нет Android Keystore (предпросмотр) — секреты только в памяти, на диск ничего не пишется
            MemoryPrefs()
        }
    }

    private fun openPrefs(ctx: Context): SharedPreferences {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            ctx, "secrets", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var claudeKey: String
        get() = p.getString("claudeKey", "") ?: ""
        set(v) = p.edit().putString("claudeKey", v.trim()).apply()

    var statsToken: String
        get() = p.getString("statsToken", "") ?: ""
        set(v) = p.edit().putString("statsToken", v.trim()).apply()

    fun mailPassword(address: String): String = p.getString("mail:$address", "") ?: ""
    fun setMailPassword(address: String, pass: String) =
        p.edit().putString("mail:$address", pass.replace(" ", "")).apply()
}

/**
 * Провижининг: install.sh кладёт provision.json в
 * /sdcard/Android/data/com.beamox.launcher/files/ — приложение читает его, переносит
 * секреты в зашифрованное хранилище и удаляет файл.
 */
object Provisioning {
    fun importIfPresent(ctx: Context, prefs: Prefs, secrets: Secrets): Boolean {
        val f = File(ctx.getExternalFilesDir(null), "provision.json")
        if (!f.exists()) return false
        val text = runCatching { f.readText() }.getOrNull() ?: return false
        val hash = text.hashCode()
        if (prefs.lastProvisionHash == hash) { f.delete(); return false } // уже импортирован
        return try {
            val o = JSONObject(text)
            o.optString("claudeApiKey").takeIf { it.isNotBlank() }?.let { secrets.claudeKey = it }
            o.optString("statsUrl").takeIf { it.isNotBlank() }?.let { prefs.statsUrl = it }
            o.optString("dashboardUrl").takeIf { it.isNotBlank() }?.let { prefs.dashboardUrl = it }
            o.optString("statsToken").takeIf { it.isNotBlank() }?.let { secrets.statsToken = it }
            o.optString("studioUrl").takeIf { it.isNotBlank() }?.let { prefs.studioUrl = it }
            o.optString("claudeModel").takeIf { it.isNotBlank() }?.let { prefs.claudeModel = it }
            if (o.has("handsFree")) prefs.handsFree = o.optBoolean("handsFree")
            o.optJSONObject("mailPasswords")?.let { mp ->
                mp.keys().forEach { addr -> secrets.setMailPassword(addr, mp.getString(addr)) }
            }
            o.optJSONObject("config")?.let { cfg -> BeamoxConfig.overrideFile(ctx).writeText(cfg.toString(2)) }
            prefs.lastProvisionHash = hash
            true
        } catch (e: Exception) {
            false
        } finally {
            f.delete()
        }
    }
}

/** Простые задачи офиса (локально на телефоне). */
data class OfficeTask(val id: Long, val text: String, val done: Boolean)

object Tasks {
    fun load(prefs: Prefs): List<OfficeTask> = try {
        val a = JSONArray(prefs.tasksJson)
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            OfficeTask(o.getLong("id"), o.getString("text"), o.optBoolean("done"))
        }
    } catch (e: Exception) { emptyList() }

    fun save(prefs: Prefs, list: List<OfficeTask>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("text", it.text).put("done", it.done)) }
        prefs.tasksJson = a.toString()
    }
}

/** Хранилище «только в памяти» — на случай отсутствия Keystore. Ничего не сохраняет на диск. */
private class MemoryPrefs : SharedPreferences {
    private val m = HashMap<String, Any?>()
    override fun getAll(): MutableMap<String, *> = m
    override fun getString(k: String?, d: String?) = m[k] as? String ?: d
    override fun getStringSet(k: String?, d: MutableSet<String>?) = @Suppress("UNCHECKED_CAST") (m[k] as? MutableSet<String> ?: d)
    override fun getInt(k: String?, d: Int) = m[k] as? Int ?: d
    override fun getLong(k: String?, d: Long) = m[k] as? Long ?: d
    override fun getFloat(k: String?, d: Float) = m[k] as? Float ?: d
    override fun getBoolean(k: String?, d: Boolean) = m[k] as? Boolean ?: d
    override fun contains(k: String?) = m.containsKey(k)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(k: String?, v: String?) = apply { m[k!!] = v }
        override fun putStringSet(k: String?, v: MutableSet<String>?) = apply { m[k!!] = v }
        override fun putInt(k: String?, v: Int) = apply { m[k!!] = v }
        override fun putLong(k: String?, v: Long) = apply { m[k!!] = v }
        override fun putFloat(k: String?, v: Float) = apply { m[k!!] = v }
        override fun putBoolean(k: String?, v: Boolean) = apply { m[k!!] = v }
        override fun remove(k: String?) = apply { m.remove(k) }
        override fun clear() = apply { m.clear() }
        override fun commit() = true
        override fun apply() {}
    }
}
