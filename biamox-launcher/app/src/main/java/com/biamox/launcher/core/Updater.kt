package com.biamox.launcher.core

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.biamox.launcher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Самообновление из GitHub-релиза «biamox-launcher» (как у Жорика). */
object Updater {
    private val http = OkHttpClient.Builder().callTimeout(5, TimeUnit.MINUTES).followRedirects(true).build()

    data class Info(val versionCode: Int, val versionName: String, val apkUrl: String)

    suspend fun check(): Result<Info?> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("${BuildConfig.UPDATE_BASE}/version.json").build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                val o = JSONObject(r.body!!.string())
                val info = Info(o.getInt("versionCode"), o.optString("versionName"),
                    o.optString("apkUrl", "${BuildConfig.UPDATE_BASE}/BiamoxLauncher.apk"))
                if (info.versionCode > BuildConfig.VERSION_CODE) info else null
            }
        }
    }

    suspend fun downloadAndInstall(ctx: Context, info: Info): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            val f = File(dir, "BiamoxLauncher.apk")
            http.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                f.outputStream().use { out -> r.body!!.byteStream().copyTo(out) }
            }
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
            val i = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            withContext(Dispatchers.Main) { ctx.startActivity(i) }
        }
    }
}
