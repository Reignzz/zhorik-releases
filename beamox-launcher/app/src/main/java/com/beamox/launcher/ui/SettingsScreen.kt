package com.beamox.launcher.ui

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamox.launcher.BuildConfig
import com.beamox.launcher.core.AppRepository
import com.beamox.launcher.core.BeamoxWallpaper
import com.beamox.launcher.core.Bloat
import com.beamox.launcher.core.ClaudeModels
import com.beamox.launcher.core.Sounds
import com.beamox.launcher.core.Updater
import kotlinx.coroutines.launch

private class EditField(val title: String, val initial: String, val secret: Boolean, val onSave: (String) -> Unit)

// Цвета иконок как в Настройках iOS
private val cOrange = Color(0xFFFF9500); private val cBlue = Color(0xFF0A84FF); private val cGreen = Color(0xFF30D158)
private val cRed = Color(0xFFFF453A); private val cPurple = Color(0xFFBF5AF2); private val cIndigo = Color(0xFF5E5CE6)
private val cGray = Color(0xFF8E8E93); private val cPink = Color(0xFFFF375F); private val cTeal = Color(0xFF40C8E0)

@Composable
fun SettingsScreen(s: LauncherState) {
    if (s.cleanupOpen) { CleanupScreen(s); return }
    var edit by remember { mutableStateOf<EditField?>(null) }
    var modelPick by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }

    fun start(i: Intent) = AppRepository.safeStart(s.act, i)
    fun mask(v: String) = if (v.isBlank()) "не задан" else "••••" + v.takeLast(4)

    ModuleSheet("Настройки", onBack = { s.goHome() }) {
        tick.let { }
        LazyColumn(contentPadding = ListPadding) {
            if (status.isNotBlank()) item { Hint(status) }
            item {
                VSpace(8.dp)
                Group {
                    RowItem("Панель Beamox", (s.prefs.dashboardUrl ?: "").ifBlank { "не подключена — нажмите, чтобы вставить ссылку" },
                        icon = Icons.Rounded.Link, iconColor = B.Coral, onClick = {
                            edit = EditField("Ссылка на панель («Генератор охватов»)", s.prefs.dashboardUrl ?: "", false) { s.setPanelLink(it); tick++ }
                        })
                    s.dashError?.let { RowItem(it, icon = Icons.Rounded.Info, iconColor = cRed) }
                }
            }
            item {
                SectionTitle("Телефон")
                Group {
                    RowItem("Очистка от рекламы", "${s.hidden.size} скрыто · удалить в один тап", icon = Icons.Rounded.CleaningServices,
                        iconColor = cRed, onClick = { s.cleanupOpen = true })
                    RowItem("Главный экран — Beamox", icon = Icons.Rounded.Home, iconColor = cBlue, onClick = {
                        if (Build.VERSION.SDK_INT >= 29) {
                            val rm = s.act.getSystemService(RoleManager::class.java)
                            val a = s.activity
                            if (a != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME))
                                a.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 5)
                            else start(Intent(Settings.ACTION_HOME_SETTINGS))
                        } else start(Intent(Settings.ACTION_HOME_SETTINGS))
                    })
                    RowItem("Жесты вместо кнопок", "Как на iPhone: свайп снизу — домой", icon = Icons.Rounded.Swipe, iconColor = cIndigo, onClick = {
                        start(Intent("android.settings.GESTURES_SETTINGS")).let { ok -> if (!ok) start(Intent(Settings.ACTION_SETTINGS)) }
                    })
                    RowItem("Заставка Beamox", "Экран блокировки и обои", icon = Icons.Rounded.Wallpaper, iconColor = cTeal, onClick = {
                        s.scope.launch {
                            status = BeamoxWallpaper.apply(s.ctx, s.config.company, s.config.tagline)
                                .fold({ "✓ Заставка Beamox установлена" }, { "Ошибка: ${it.message}" })
                        }
                    })
                    RowItem("Звуки Beamox", "Рингтон, уведомления, будильник", icon = Icons.Rounded.MusicNote, iconColor = cPink, onClick = {
                        s.scope.launch {
                            status = Sounds.install(s.ctx).fold({ it }, { "Ошибка: ${it.message}" })
                            if (!Sounds.canWriteSettings(s.ctx))
                                start(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${s.ctx.packageName}")))
                        }
                    })
                    RowItem("Пункт управления", "Свайп вниз из правого угла", icon = Icons.Rounded.Tune, iconColor = cGray,
                        onClick = { s.controlOpen = true })
                }
            }
            item {
                SectionTitle("Голос")
                Group {
                    RowItem("«Привет, Бимокс»", "Слушать при включённом экране", icon = Icons.Rounded.Mic, iconColor = cPurple, end = {
                        Switch(s.handsFree, { s.setHandsFreeMode(it) }, colors = SwitchDefaults.colors(checkedTrackColor = cGreen))
                    })
                    RowItem("Озвучивать ответы", icon = Icons.AutoMirrored.Rounded.VolumeUp, iconColor = cOrange, end = {
                        var on by remember { mutableStateOf(s.prefs.speakAnswers) }
                        Switch(on, { on = it; s.prefs.speakAnswers = it }, colors = SwitchDefaults.colors(checkedTrackColor = cGreen))
                    })
                    RowItem("Ассистент по умолчанию", "Вызов долгим нажатием/жестом", icon = Icons.Rounded.SmartToy, iconColor = cIndigo,
                        onClick = { start(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) })
                    RowItem("Проверить голос", icon = Icons.Rounded.RecordVoiceOver, iconColor = cBlue, onClick = { s.startVoice() })
                }
            }
            item {
                SectionTitle("Claude")
                Group {
                    RowItem("Ключ API", trailing = mask(s.secrets.claudeKey), icon = Icons.Rounded.Key, iconColor = B.Coral, onClick = {
                        edit = EditField("Ключ Claude API (sk-ant-…)", "", true) { s.secrets.claudeKey = it; tick++ }
                    })
                    RowItem("Модель", ClaudeModels.all.firstOrNull { it.first == s.claudeModel() }?.second ?: s.claudeModel(),
                        icon = Icons.Rounded.Psychology, iconColor = cPurple, onClick = { modelPick = true })
                    RowItem("Очистить переписку", icon = Icons.Rounded.Delete, iconColor = cGray, onClick = { s.clearChat(); status = "Переписка очищена" })
                }
            }
            item {
                SectionTitle("Студия и почта")
                Group {
                    RowItem("Адрес студии", (s.prefs.studioUrl ?: "").ifBlank { s.config.studioUrl }.ifBlank { "не задан" },
                        icon = Icons.Rounded.Movie, iconColor = cRed, onClick = {
                            edit = EditField("Адрес студии (https://…)", s.prefs.studioUrl ?: s.config.studioUrl, false) { s.prefs.studioUrl = it.trim() }
                        })
                    s.config.mailboxes.forEach { mb ->
                        RowItem(mb.name, mb.address + if (s.secrets.mailPassword(mb.address).isBlank()) " · нет пароля" else " · ✓",
                            icon = Icons.Rounded.Email, iconColor = cBlue, onClick = {
                                edit = EditField("Пароль приложения для ${mb.address}", "", true) {
                                    s.secrets.setMailPassword(mb.address, it); s.refreshMail(); tick++
                                }
                            })
                    }
                }
            }
            item {
                SectionTitle("Система")
                Group {
                    RowItem("Настройки Android", icon = Icons.Rounded.Settings, iconColor = cGray, onClick = { s.openSys(SysApp.SYSTEM_SETTINGS) })
                    RowItem("Уведомления", icon = Icons.Rounded.Notifications, iconColor = cRed, onClick = {
                        start(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, s.ctx.packageName))
                    })
                    RowItem("Экран и яркость", icon = Icons.Rounded.LightMode, iconColor = cBlue, onClick = { start(Intent(Settings.ACTION_DISPLAY_SETTINGS)) })
                    RowItem("Аккумулятор", trailing = "${s.device.batteryPct}%", icon = Icons.Rounded.BatteryFull, iconColor = cGreen,
                        onClick = { start(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) })
                    RowItem("Хранилище", trailing = "${"%.0f".format(s.device.storageFreeGb)} ГБ своб.", icon = Icons.Rounded.Storage, iconColor = cGray,
                        onClick = { start(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) })
                    RowItem("Приложения", icon = Icons.Rounded.Apps, iconColor = cIndigo, onClick = { start(Intent(Settings.ACTION_APPLICATION_SETTINGS)) })
                }
            }
            item {
                SectionTitle("Об этом лаунчере")
                Group {
                    RowItem("Версия", trailing = BuildConfig.VERSION_NAME, icon = Icons.Rounded.Info, iconColor = cGray)
                    RowItem("Проверить обновления", icon = Icons.Rounded.Download, iconColor = cBlue, onClick = {
                        status = "Проверяю…"
                        s.scope.launch {
                            Updater.check().fold({ info ->
                                if (info == null) status = "✓ Установлена последняя версия"
                                else {
                                    status = "Скачиваю версию ${info.versionName}…"
                                    Updater.downloadAndInstall(s.ctx, info).onFailure { status = "Ошибка: ${it.message}" }
                                }
                            }, { status = "Не удалось проверить: ${it.message}" })
                        }
                    })
                    RowItem("Обновить конфигурацию", icon = Icons.Rounded.Refresh, iconColor = cGreen,
                        onClick = { s.reloadConfig(); s.refreshStats(); status = "Обновлено" })
                }
            }
        }
    }

    edit?.let { f ->
        var v by remember(f) { mutableStateOf(f.initial) }
        AlertDialog(
            onDismissRequest = { edit = null },
            containerColor = Color(0xFF2A2330),
            title = { Text(f.title, fontWeight = FontWeight.ExtraBold) },
            text = {
                OutlinedTextField(v, { v = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (f.secret) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (f.secret) KeyboardType.Password else KeyboardType.Uri))
            },
            confirmButton = { TextButton({ f.onSave(v); edit = null; status = "Сохранено" }) { Text("Сохранить", color = B.Coral, fontWeight = FontWeight.ExtraBold) } },
            dismissButton = { TextButton({ edit = null }) { Text("Отмена", color = B.Dim) } },
        )
    }
    if (modelPick) {
        AlertDialog(
            onDismissRequest = { modelPick = false },
            containerColor = Color(0xFF2A2330),
            title = { Text("Модель Claude", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column {
                    ClaudeModels.all.forEach { (id, label) ->
                        Text((if (id == s.claudeModel()) "✓ " else "   ") + label, fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth().clickableNoRipple { s.prefs.claudeModel = id; modelPick = false }
                                .padding(vertical = 12.dp))
                    }
                }
            },
            confirmButton = { TextButton({ modelPick = false }) { Text("Готово", color = B.Coral) } },
        )
    }
}

/** Очистка телефона от рекламного мусора без root */
@Composable
private fun CleanupScreen(s: LauncherState) {
    val junk = remember(s.apps) { Bloat.detect(s.ctx, s.apps) }
    ModuleSheet("Очистка", onBack = { s.cleanupOpen = false }) {
        LazyColumn(contentPadding = ListPadding) {
            item {
                Hint("Найдено ${junk.size}. Рекламные игры и сервисы Motorola уже скрыты с экранов. «Удалить» убирает приложение " +
                    "с телефона (подтвердите в окне Android). Системные пакеты можно только отключить — откроется их карточка.")
                if (junk.isNotEmpty()) Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Pill("Удалить все по очереди", B.Red) { junk.filter { !it.system }.forEach { s.appsRepo.uninstall(it.pkg) } }
                }
            }
            item { VSpace(6.dp) }
            item {
                Group {
                    if (junk.isEmpty()) RowItem("Мусора не найдено", icon = Icons.Rounded.CleaningServices, iconColor = cGreen)
                    junk.forEach { j ->
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Image(j.app.icon, null, Modifier.size(40.dp).clip(Squircle))
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(j.app.label, color = B.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(j.reason + if (j.system) " · системное" else "", color = B.Faint, fontSize = 12.5.sp)
                            }
                            Pill(if (j.system) "Отключить" else "Удалить", if (j.system) B.Amber else B.Red) {
                                if (j.system) s.appsRepo.appInfo(j.pkg) else s.appsRepo.uninstall(j.pkg)
                            }
                        }
                    }
                }
            }
            item {
                Hint("Полностью и сразу всё (включая системный мусор) удаляет скрипт с Mac по кабелю: beamox-launcher/scripts/mac/install.sh")
            }
        }
    }
}
