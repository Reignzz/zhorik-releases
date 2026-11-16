package com.beamox.launcher.core

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.beamox.launcher.island.IslandService

/** Что ещё не настроено на телефоне, чтобы он стал «как iPhone». */
data class SetupState(
    val defaultHome: Boolean = true,
    val gestures: Boolean = true,
    val overlay: Boolean = true,
    val notifications: Boolean = true,
    val panel: Boolean = true,
    /** «Спец. возможности → Beamox: островок и шторки» */
    val access: Boolean = true,
    /** Владелец уже открыл настройки клавиатуры (вибрация) */
    val keyboard: Boolean = true,
) {
    /** Капсуле нужно окно: спец. возможности (лучше) или «поверх приложений» */
    val islandReady get() = access || overlay
    val missing get() = listOf(defaultHome, gestures, access, notifications, keyboard, panel).count { !it }
}

object PhoneSetup {
    fun read(ctx: Context, panelUrl: String?): SetupState = SetupState(
        defaultHome = isDefaultHome(ctx),
        gestures = isGestureNav(ctx),
        overlay = Settings.canDrawOverlays(ctx),
        notifications = hasNotificationAccess(ctx),
        panel = !panelUrl.isNullOrBlank(),
        access = com.beamox.launcher.island.BeamoxAccess.isEnabled(ctx),
        keyboard = ctx.getSharedPreferences("island", Context.MODE_PRIVATE).getBoolean("kbd", false),
    )

    /** «Спец. возможности» — сразу на нашу службу, если система умеет. */
    fun openAccessibility(ctx: Context) {
        toast(ctx, "Включите «Beamox: островок и шторки». Если серое — «О приложении» → ⋮ → «Разрешить ограниченные настройки»")
        if (Build.VERSION.SDK_INT >= 31) {
            val i = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName(ctx, com.beamox.launcher.island.BeamoxAccess::class.java).flattenToString())
            if (start(ctx, i)) return
        }
        start(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /** Настройки текущей клавиатуры (Gboard и др.): там «Вибрация при нажатии клавиш» и тема. */
    fun openKeyboardSettings(ctx: Context) {
        ctx.getSharedPreferences("island", Context.MODE_PRIVATE).edit().putBoolean("kbd", true).apply()
        toast(ctx, "Выключите «Вибрация при нажатии клавиш». Вид как у iPhone: Тема → светлая с контуром клавиш")
        val id = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()
        val imm = ctx.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        val info = runCatching { imm.inputMethodList.firstOrNull { it.id == id } }.getOrNull()
        val act = info?.settingsActivity
        if (info != null && !act.isNullOrBlank() && start(ctx, Intent(Intent.ACTION_MAIN).setComponent(ComponentName(info.packageName, act)))) return
        start(ctx, Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }

    /** Вибрация при касании по всей системе (нужно «Изменение системных настроек»). */
    fun touchHaptics(ctx: Context): Boolean = runCatching {
        Settings.System.getInt(ctx.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 1
    }.getOrDefault(true)

    fun setTouchHaptics(ctx: Context, on: Boolean): Boolean {
        if (!Settings.System.canWrite(ctx)) {
            toast(ctx, "Разрешите Beamox «Изменение системных настроек» и нажмите ещё раз")
            start(ctx, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName)))
            return false
        }
        return runCatching { Settings.System.putInt(ctx.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, if (on) 1 else 0) }.getOrDefault(false)
    }

    private fun toast(ctx: Context, t: String) = android.widget.Toast.makeText(ctx, t, android.widget.Toast.LENGTH_LONG).show()

    fun isDefaultHome(ctx: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = ctx.getSystemService(android.app.role.RoleManager::class.java)
            rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)
        } else {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            ctx.packageManager.resolveActivity(home, 0)?.activityInfo?.packageName == ctx.packageName
        }
    }.getOrDefault(true)

    /** Навигация жестами (одна полоска снизу). Читаем системный ресурс: 2 = жесты, 0 = три кнопки. */
    fun isGestureNav(ctx: Context): Boolean = runCatching {
        val res = ctx.resources
        val id = res.getIdentifier("config_navBarInteractionMode", "integer", "android")
        id == 0 || res.getInteger(id) == 2
    }.getOrDefault(true)

    fun hasNotificationAccess(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    private fun start(ctx: Context, i: Intent): Boolean = runCatching {
        if (ctx !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i); true
    }.getOrDefault(false)

    /** Системный вопрос «Сделать Beamox главным экраном?», если нельзя — список лаунчеров в настройках. */
    fun requestDefaultHome(activity: Activity?, ctx: Context) {
        if (Build.VERSION.SDK_INT >= 29 && activity != null) {
            val rm = activity.getSystemService(android.app.role.RoleManager::class.java)
            if (rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME) && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                runCatching { activity.startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), 5); return }
            }
        }
        if (!start(activity ?: ctx, Intent(Settings.ACTION_HOME_SETTINGS))) start(activity ?: ctx, Intent(Settings.ACTION_SETTINGS))
    }

    /** Экран выбора навигации (жесты / кнопки). У Motorola и стока путь разный — пробуем по очереди. */
    fun openGestureSettings(ctx: Context) {
        val tries = listOf(
            Intent("android.settings.NAVIGATION_MODE_SETTINGS"),
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$SystemNavigationSettingsActivity")),
            Intent("android.settings.GESTURES_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS),
        )
        toast(ctx, "Система → Жесты → Системная навигация → «Навигация жестами»")
        for (i in tries) if (start(ctx, i)) return
    }

    fun openOverlaySettings(ctx: Context) {
        if (!start(ctx, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))))
            start(ctx, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
    }

    fun openNotificationAccess(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 30) {
            val i = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(ctx, IslandService::class.java).flattenToString())
            if (start(ctx, i)) return
        }
        start(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    /** «О приложении» — там в меню ⋮ есть «Разрешить ограниченные настройки» (Android 13+ для установленных не из Play). */
    fun openAppInfo(ctx: Context) {
        start(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
    }
}

/** Claude, ChatGPT и Gemini — официальные приложения, вход под аккаунтами владельца, без ключей. */
enum class AiApp(val label: String, val pkg: String) {
    CLAUDE("Claude", "com.anthropic.claude"),
    CHATGPT("ChatGPT", "com.openai.chatgpt"),
    GEMINI("Gemini", "com.google.android.apps.bard"),
}

object AiApps {
    fun installed(ctx: Context, a: AiApp) = ctx.packageManager.getLaunchIntentForPackage(a.pkg) != null

    /** Открыть приложение и передать вопрос. Возвращает подсказку для всплывающего сообщения. */
    fun ask(ctx: Context, a: AiApp, text: String): String {
        val pm = ctx.packageManager
        val launch = pm.getLaunchIntentForPackage(a.pkg)
        fun go(i: Intent) = runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
        if (launch == null) {
            if (!go(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${a.pkg}"))))
                go(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${a.pkg}")))
            return "${a.label} не установлен — откройте Google Play, установите и войдите в свой аккаунт"
        }
        val q = text.trim()
        if (q.isNotEmpty()) {
            // На случай, если приложение не примет текст, — он уже в буфере
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Вопрос", q))
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, q).setPackage(a.pkg)
            if (send.resolveActivity(pm) != null && go(send)) return "Вопрос передан в ${a.label}"
            go(launch)
            return "Вопрос скопирован — вставьте его в ${a.label}"
        }
        go(launch)
        return ""
    }
}
