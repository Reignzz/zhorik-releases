package com.beamox.launcher.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import com.beamox.launcher.core.AppRepository

/**
 * Студия роликов. Открывает веб-версию студии (адрес — в Настройках или в config.json),
 * поддерживает загрузку видео с телефона, камеру и микрофон внутри страницы.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun StudioScreen(s: LauncherState) {
    val url = (s.prefs.studioUrl ?: "").ifBlank { s.config.studioUrl }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    val fileCallback = remember { arrayOfNulls<ValueCallback<Array<Uri>>>(1) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val cb = fileCallback[0]; fileCallback[0] = null
        val data = r.data
        val uris: Array<Uri>? = if (r.resultCode != Activity.RESULT_OK || data == null) null else {
            data.clipData?.let { c -> Array(c.itemCount) { c.getItemAt(it).uri } } ?: data.data?.let { arrayOf(it) }
        }
        cb?.onReceiveValue(uris)
    }
    BackHandler(enabled = canGoBack) { webView?.goBack() }

    ModuleSheet("Студия роликов", onBack = { s.goHome() }, actions = {
        if (url.isNotBlank()) {
            IconButton(onClick = { webView?.reload() }) { Icon(Icons.Default.Refresh, "Обновить", tint = B.Blue) }
            Text("↗", color = B.Blue, fontSize = 22.sp,
                modifier = Modifier.padding(end = 16.dp).clickable { s.openUrl(url) })
        }
    }) {
        if (url.isBlank()) StudioSetup(s) else AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun doUpdateVisitedHistory(view: WebView, u: String?, isReload: Boolean) {
                            canGoBack = view.canGoBack()
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(
                            v: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams,
                        ): Boolean {
                            fileCallback[0]?.onReceiveValue(null)
                            fileCallback[0] = cb
                            val pick = params.createIntent().putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                                params.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
                            val chooser = Intent.createChooser(pick, "Выберите файл")
                                .putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(Intent(MediaStore.ACTION_VIDEO_CAPTURE)))
                            return try { picker.launch(chooser); true } catch (e: Exception) { fileCallback[0] = null; false }
                        }

                        override fun onPermissionRequest(request: PermissionRequest) {
                            // Камера/микрофон — только для сайта студии, остальным отказ
                            val studioHost = Uri.parse(url).host
                            if (studioHost != null && request.origin?.host == studioHost) {
                                ActivityCompat.requestPermissions(s.activity,
                                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 78)
                                request.grant(request.resources.filter {
                                    it == PermissionRequest.RESOURCE_VIDEO_CAPTURE || it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                                }.toTypedArray())
                            } else request.deny()
                        }
                    }
                    loadUrl(url)
                    webView = this
                }
            },
        )
    }
}

@Composable
private fun StudioSetup(s: LauncherState) {
    var u by remember { mutableStateOf("") }
    LazyColumn(contentPadding = ListPadding) {
        item {
            Hint("Подключите вашу студию роликов: вставьте её адрес (ссылку), и она будет открываться прямо здесь, " +
                "с загрузкой видео с телефона, камерой и микрофоном.")
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                OutlinedTextField(u, { u = it }, label = { Text("Адрес студии, https://…") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Pill("Сохранить и открыть", B.Pink) {
                    val v = u.trim()
                    if (v.startsWith("http")) { s.prefs.studioUrl = v; s.screen = null; s.screen = Module.STUDIO }
                    else s.toast("Адрес должен начинаться с https://")
                }
            }
        }
        item {
            SectionTitle("Быстрые действия")
            Group {
                RowItem("Снять ролик", "Камера в режиме видео", emoji = "🎥", onClick = {
                    AppRepository.safeStart(s.activity, Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA))
                })
                RowItem("Мои видео", "Галерея", emoji = "🖼", onClick = {
                    AppRepository.safeStart(s.activity, Intent(Intent.ACTION_VIEW, MediaStore.Video.Media.EXTERNAL_CONTENT_URI))
                })
                RowItem("Идеи для роликов от Claude", emoji = "✳️", onClick = {
                    s.claudeDraft = "Предложи 5 идей коротких роликов для соцсетей Beamox: хук, сценарий на 30 сек, призыв к действию."
                    s.open(Module.CLAUDE)
                })
            }
        }
        item {
            SectionTitle("Соцсети для публикации")
            Group { s.config.socials.forEach { so -> RowItem(so.title, so.url, emoji = so.emoji, onClick = { s.openSocial(so) }) } }
        }
    }
}
