package com.beamox.launcher.ui

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
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
    var iconPick by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }

    fun start(i: Intent) = AppRepository.safeStart(s.act, i)
    fun mask(v: String) = if (v.isBlank()) "Не задан" else "••••" + v.takeLast(4)
    val airplane = runCatching { Settings.Global.getInt(s.ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1 }.getOrDefault(false)
    val wifiOn = runCatching { (s.ctx.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager).isWifiEnabled }.getOrDefault(true)
    val btOn = runCatching { (s.ctx.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter?.isEnabled == true }.getOrDefault(true)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        androidx.compose.runtime.CompositionLocalProvider(LocalRowFilter provides query) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = IosStatusBarHeight, bottom = 140.dp)) {
            item {
                tick.let { }
                // Большой заголовок как в iOS (без кнопки «Назад»: назад — жестом/кнопкой телефона)
                Text("Настройки", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp,
                    modifier = Modifier.padding(start = 18.dp, top = 52.dp, bottom = 12.dp).clickableNoRipple { s.goHome() })
                if (status.isNotBlank()) Hint(status)
            }
            // --- Профиль (как Apple Account)
            item {
                Group {
                    if (query.isBlank()) Row(Modifier.fillMaxWidth().clickableNoRipple {
                        edit = EditField("Ссылка на панель («Генератор охватов»)", s.prefs.dashboardUrl ?: "", false) { s.setPanelLink(it); tick++ }
                    }.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(62.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF2B2B2E), Color(0xFF111113)))),
                            contentAlignment = Alignment.Center) { ClaudeBurst(36.dp, B.Coral) }
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(s.config.company, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp)
                            Text(if (s.dash != null) "Панель, Claude, почты и ещё" else "Подключите панель Beamox", color = Color(0x99EBEBF5),
                                fontSize = 15.sp, letterSpacing = (-0.2).sp)
                        }
                        IosChevron()
                    }
                    RowItem("Команда", icon = Icons.Rounded.Groups,
                        iconColor = Color(0xFF5E5CE6), onClick = { s.open(Module.OFFICE) })
                }
            }
            item { VSpace(36.dp) }
            // --- Связь (как Airplane / Wi-Fi / Bluetooth / Mobile / Hotspot / Battery)
            item {
                Group {
                    RowItem("Авиарежим", icon = Icons.Rounded.AirplanemodeActive, iconColor = cOrange, end = {
                        IosSwitch(airplane) { start(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) }
                    })
                    RowItem("Wi-Fi", trailing = if (wifiOn) "Вкл." else "Выкл.", icon = Icons.Rounded.Wifi, iconColor = cBlue, onClick = {
                        start(if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS))
                    })
                    RowItem("Bluetooth", trailing = if (btOn) "Вкл." else "Выкл.", icon = Icons.Rounded.Bluetooth, iconColor = cBlue,
                        onClick = { start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) })
                    RowItem("Сотовая связь", icon = Icons.Rounded.CellTower, iconColor = cGreen,
                        onClick = { start(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)) })
                    RowItem("Режим модема", icon = Icons.Rounded.Link, iconColor = cGreen,
                        onClick = { start(Intent("android.settings.TETHER_SETTINGS")) })
                    RowItem("Аккумулятор", trailing = "${s.device.batteryPct}%", icon = Icons.Rounded.BatteryFull, iconColor = cGreen,
                        onClick = { start(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) })
                }
            }
            item { VSpace(36.dp) }
            // --- Основные
            item {
                Group {
                    RowItem("Основные", icon = Icons.Rounded.Settings, iconColor = cGray, onClick = { s.openSys(SysApp.SYSTEM_SETTINGS) })
                    RowItem("Пункт управления", icon = Icons.Rounded.ToggleOn, iconColor = cGray, onClick = { s.controlOpen = true })
                    RowItem("Экран и яркость", icon = Icons.Rounded.LightMode, iconColor = cBlue, onClick = { start(Intent(Settings.ACTION_DISPLAY_SETTINGS)) })
                    RowItem("Главный экран — Beamox", icon = Icons.Rounded.Apps, iconColor = cIndigo, onClick = {
                        if (Build.VERSION.SDK_INT >= 29) {
                            val rm = s.act.getSystemService(RoleManager::class.java)
                            val a = s.activity
                            if (a != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME))
                                a.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 5)
                            else start(Intent(Settings.ACTION_HOME_SETTINGS))
                        } else start(Intent(Settings.ACTION_HOME_SETTINGS))
                    })
                    RowItem("Жесты как на iPhone", icon = Icons.Rounded.Swipe, iconColor = cBlue, onClick = {
                        if (!start(Intent("android.settings.GESTURES_SETTINGS"))) start(Intent(Settings.ACTION_SETTINGS))
                    })
                    RowItem("Значки", trailing = s.iconPackPkg?.let { p -> com.beamox.launcher.core.IconPack.installed(s.ctx).firstOrNull { it.pkg == p }?.label } ?: "Beamox",
                        icon = Icons.Rounded.Apps, iconColor = cBlue, onClick = { iconPick = true })
                    RowItem("Обои", icon = Icons.Rounded.Wallpaper, iconColor = cTeal, onClick = {
                        s.scope.launch {
                            status = BeamoxWallpaper.apply(s.ctx, s.config.company, s.config.tagline)
                                .fold({ "Обои и экран блокировки Beamox установлены" }, { "Ошибка: ${it.message}" })
                        }
                    })
                    RowItem("Уведомления", icon = Icons.Rounded.Notifications, iconColor = cRed, onClick = {
                        start(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, s.ctx.packageName))
                    })
                    RowItem("Звуки, тактильные сигналы", icon = Icons.AutoMirrored.Rounded.VolumeUp, iconColor = cPink, onClick = {
                        s.scope.launch {
                            status = Sounds.install(s.ctx).fold({ it }, { "Ошибка: ${it.message}" })
                            if (!Sounds.canWriteSettings(s.ctx))
                                start(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${s.ctx.packageName}")))
                        }
                    })
                    RowItem("Хранилище", trailing = "${"%.0f".format(s.device.storageFreeGb)} ГБ", icon = Icons.Rounded.Storage, iconColor = cGray,
                        onClick = { start(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) })
                    RowItem("Очистка от рекламы", trailing = "${s.hidden.size}", icon = Icons.Rounded.CleaningServices, iconColor = cRed,
                        onClick = { s.cleanupOpen = true })
                }
            }
            item { VSpace(36.dp) }
            // --- Siri-подобное: голос и Claude
            item {
                Group {
                    RowItem("«Привет, Бимокс»", icon = Icons.Rounded.Mic, iconColor = Color(0xFF1C1C1E), end = {
                        IosSwitch(s.handsFree) { s.setHandsFreeMode(it) }
                    })
                    RowItem("Озвучивать ответы", icon = Icons.Rounded.RecordVoiceOver, iconColor = cOrange, end = {
                        var on by remember { mutableStateOf(s.prefs.speakAnswers) }
                        IosSwitch(on) { on = it; s.prefs.speakAnswers = it }
                    })
                    RowItem("Ассистент по умолчанию", icon = Icons.Rounded.SmartToy, iconColor = cIndigo,
                        onClick = { start(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) })
                    RowItem("Ключ Claude", trailing = mask(s.secrets.claudeKey), icon = Icons.Rounded.Key, iconColor = B.Coral, onClick = {
                        edit = EditField("Ключ Claude API (sk-ant-…)", "", true) { s.secrets.claudeKey = it; tick++ }
                    })
                    RowItem("Модель", trailing = ClaudeModels.all.firstOrNull { it.first == s.claudeModel() }?.second?.substringBefore(" —") ?: s.claudeModel(),
                        icon = Icons.Rounded.Psychology, iconColor = cPurple, onClick = { modelPick = true })
                    RowItem("Очистить переписку", icon = Icons.Rounded.Delete, iconColor = cGray, onClick = { s.clearChat(); status = "Переписка очищена" })
                }
            }
            item { VSpace(36.dp) }
            // --- Beamox: панель, студия, почты
            item {
                Group {
                    RowItem("Панель Beamox", trailing = if (s.prefs.dashboardUrl.isNullOrBlank()) "Нет" else "Подключена",
                        icon = Icons.Rounded.BarChart, iconColor = cGreen, onClick = {
                            edit = EditField("Ссылка на панель («Генератор охватов»)", s.prefs.dashboardUrl ?: "", false) { s.setPanelLink(it); tick++ }
                        })
                    RowItem("Студия роликов", trailing = if ((s.prefs.studioUrl ?: s.config.studioUrl).isBlank()) "Нет" else "Есть",
                        icon = Icons.Rounded.Movie, iconColor = cRed, onClick = {
                            edit = EditField("Адрес студии (https://…)", s.prefs.studioUrl ?: s.config.studioUrl, false) { s.prefs.studioUrl = it.trim() }
                        })
                    s.config.mailboxes.forEach { mb ->
                        RowItem(mb.name, trailing = if (s.secrets.mailPassword(mb.address).isBlank()) "Нет пароля" else "",
                            icon = Icons.Rounded.Email, iconColor = cBlue, onClick = {
                                edit = EditField("Пароль приложения для ${mb.address}", "", true) {
                                    s.secrets.setMailPassword(mb.address, it); s.refreshMail(); tick++
                                }
                            })
                    }
                }
            }
            item { VSpace(36.dp) }
            item {
                Group {
                    RowItem("Обновление ПО", trailing = BuildConfig.VERSION_NAME, icon = Icons.Rounded.Download, iconColor = cGray, onClick = {
                        status = "Проверяю…"
                        s.scope.launch {
                            Updater.check().fold({ info ->
                                if (info == null) status = "Установлена последняя версия"
                                else {
                                    status = "Скачиваю версию ${info.versionName}…"
                                    Updater.downloadAndInstall(s.ctx, info).onFailure { status = "Ошибка: ${it.message}" }
                                }
                            }, { status = "Не удалось проверить: ${it.message}" })
                        }
                    })
                    RowItem("Обновить данные", icon = Icons.Rounded.Refresh, iconColor = cGreen,
                        onClick = { s.reloadConfig(); s.refreshStats(); status = "Обновлено" })
                }
            }
        }
        }
        // Плавающий поиск iOS 26 (Liquid Glass) с микрофоном
        LiquidGlass(CircleShape, Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
            .padding(start = 28.dp, end = 28.dp, bottom = 24.dp).fillMaxWidth().height(48.dp),
            tint = Color(0x40303034), contentAlignment = Alignment.CenterStart) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Box(Modifier.weight(1f).padding(start = 10.dp)) {
                    if (query.isEmpty()) Text("Поиск", color = Color(0x99EBEBF5), fontSize = 17.sp)
                    androidx.compose.foundation.text.BasicTextField(query, { query = it }, singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 17.sp, fontFamily = Rounded),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(B.Accent), modifier = Modifier.fillMaxWidth())
                }
                Icon(Icons.Rounded.Mic, "Голос", tint = Color.White, modifier = Modifier.size(22.dp).clickableNoRipple { s.startVoice() })
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
    if (iconPick) {
        val packs = remember { com.beamox.launcher.core.IconPack.installed(s.ctx) }
        AlertDialog(
            onDismissRequest = { iconPick = false },
            containerColor = Color(0xFF2C2C2E),
            title = { Text("Значки", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text((if (s.iconPackPkg == null) "✓ " else "   ") + "Стандартные Beamox", fontSize = 17.sp,
                        modifier = Modifier.fillMaxWidth().clickableNoRipple { s.setIconPack(null); iconPick = false }.padding(vertical = 12.dp))
                    packs.forEach { p ->
                        Text((if (p.pkg == s.iconPackPkg) "✓ " else "   ") + p.label, fontSize = 17.sp,
                            modifier = Modifier.fillMaxWidth().clickableNoRipple { s.setIconPack(p.pkg); iconPick = false }.padding(vertical = 12.dp))
                    }
                    if (packs.isEmpty()) Text("Наборов не найдено. Установите любой набор иконок в стиле iOS из Google Play — он появится здесь.",
                        color = Color(0x99EBEBF5), fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
                    Text("Найти наборы iOS в Google Play", color = B.Accent, fontSize = 17.sp,
                        modifier = Modifier.fillMaxWidth().clickableNoRipple {
                            iconPick = false
                            if (!AppRepository.safeStart(s.act, Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=iOS%2026%20icon%20pack&c=apps"))))
                                s.openUrl("https://play.google.com/store/search?q=iOS%2026%20icon%20pack&c=apps")
                        }.padding(vertical = 12.dp))
                }
            },
            confirmButton = { TextButton({ iconPick = false }) { Text("Готово", color = B.Accent) } },
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
