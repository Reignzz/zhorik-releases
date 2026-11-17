package com.beamox.launcher.core

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.beamox.launcher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Самообновление из GitHub-релиза «beamox-launcher».
 * Ставит через PackageInstaller (сессия), а не «открыть файл»: так видны ошибки, а после первой такой установки
 * Beamox сам становится установщиком и следующие обновления идут без лишних вопросов.
 * Если установка не началась — открываем ссылку на APK в браузере (как вручную с GitHub).
 */
object Updater {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES).followRedirects(true).build()
    const val ACTION_STATUS = "com.beamox.launcher.UPDATE_STATUS"

    data class Info(val versionCode: Int, val versionName: String, val apkUrl: String)

    suspend fun check(): Result<Info?> = withContext(Dispatchers.IO) {
        runCatching {
            // ?t= — мимо кэшей CDN, чтобы не получить вчерашний version.json
            val req = Request.Builder().url("${BuildConfig.UPDATE_BASE}/version.json?t=${System.currentTimeMillis()}")
                .header("Cache-Control", "no-cache").build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                val o = JSONObject(r.body!!.string())
                val info = Info(o.getInt("versionCode"), o.optString("versionName"),
                    o.optString("apkUrl", "${BuildConfig.UPDATE_BASE}/BeamoxLauncher.apk"))
                if (info.versionCode > BuildConfig.VERSION_CODE) info else null
            }
        }
    }

    /** Скачать (с процентами) и поставить. onProgress вызывается в главном потоке. */
    suspend fun downloadAndInstall(ctx: Context, info: Info, onProgress: (Int) -> Unit = {}): Result<Unit> {
        val app = ctx.applicationContext
        val res = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(app.cacheDir, "updates").apply { mkdirs() }
                val f = File(dir, "BeamoxLauncher.apk")
                http.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { r ->
                    if (!r.isSuccessful) error("скачивание: HTTP ${r.code}")
                    val body = r.body ?: error("пустой ответ")
                    val total = body.contentLength()
                    var done = 0L
                    var last = -1
                    f.outputStream().use { out ->
                        body.byteStream().use { inp ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = inp.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                if (total > 0) {
                                    val p = (done * 100 / total).toInt()
                                    if (p != last) { last = p; withContext(Dispatchers.Main) { onProgress(p) } }
                                }
                            }
                        }
                    }
                    if (total > 0 && done < total) error("файл скачался не полностью")
                }
                val pkg = app.packageManager.getPackageArchiveInfo(f.path, 0) ?: error("файл обновления повреждён")
                if (pkg.packageName != app.packageName) error("это не Beamox")
                install(app, f)
            }
        }
        if (res.isFailure) openInBrowser(app, info)
        return res
    }

    private fun install(ctx: Context, f: File) {
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            setSize(f.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = pi.createSession(params)
        try {
            pi.openSession(id).use { s ->
                s.openWrite("base.apk", 0, f.length()).use { out ->
                    f.inputStream().use { it.copyTo(out) }
                    s.fsync(out)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val sender = PendingIntent.getBroadcast(ctx, id,
                    Intent(ctx, UpdateReceiver::class.java).setAction(ACTION_STATUS), flags)
                s.commit(sender.intentSender)
            }
        } catch (e: Exception) {
            runCatching { pi.abandonSession(id) }
            throw e
        }
    }

    /** Запасной путь: браузер скачает APK, установка — как с GitHub вручную. */
    fun openInBrowser(ctx: Context, info: Info? = null) {
        val url = info?.apkUrl ?: "${BuildConfig.UPDATE_BASE}/BeamoxLauncher.apk"
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** Ответ установщика: попросить подтверждение у владельца или сказать, что пошло не так. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (val st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    else i.getParcelableExtra(Intent.EXTRA_INTENT)
                val ok = confirm != null && runCatching { c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
                if (!ok) Updater.openInBrowser(c)
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // система перезапустит лаунчер
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit // владелец нажал «Отмена»
            else -> {
                val msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                Toast.makeText(c, "Обновление не установилось ($st) $msg. Открываю загрузку в браузере", Toast.LENGTH_LONG).show()
                Updater.openInBrowser(c)
            }
        }
    }
}
