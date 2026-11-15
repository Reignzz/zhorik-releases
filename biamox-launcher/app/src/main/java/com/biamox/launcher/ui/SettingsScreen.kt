package com.biamox.launcher.ui

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.biamox.launcher.BuildConfig
import com.biamox.launcher.core.AppRepository
import com.biamox.launcher.core.BiamoxWallpaper
import com.biamox.launcher.core.ClaudeModels
import com.biamox.launcher.core.Sounds
import com.biamox.launcher.core.Updater
import kotlinx.coroutines.launch

private class EditField(val title: String, val initial: String, val secret: Boolean, val onSave: (String) -> Unit)

@Composable
fun SettingsScreen(s: LauncherState) {
    var edit by remember { mutableStateOf<EditField?>(null) }
    var modelPick by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) } // перерисовка после сохранения секретов
    var status by remember { mutableStateOf("") }

    fun start(i: Intent) = AppRepository.safeStart(s.activity, i)
    fun mask(v: String) = if (v.isBlank()) "не задан" else "••••" + v.takeLast(4)

    ModuleSheet("Настройки", onBack = { s.goHome() }) {
        tick.let { }
        LazyColumn(contentPadding = ListPadding) {
            if (status.isNotBlank()) item { Hint(status) }
            item {
                SectionTitle("Biamox")
                Group {
                    RowItem("Сделать лаунчером по умолчанию", "Кнопка «Домой» будет открывать Biamox", emoji = "🏠", onClick = {
                        if (Build.VERSION.SDK_INT >= 29) {
                            val rm = s.activity.getSystemService(RoleManager::class.java)
                            if (rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME))
                                s.activity.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 5)
                            else start(Intent(Settings.ACTION_HOME_SETTINGS))
                        } else start(Intent(Settings.ACTION_HOME_SETTINGS))
                    })
                    RowItem("Заставка Biamox", "Экран блокировки и рабочий стол", emoji = "🖼", onClick = {
                        s.scope.launch {
                            status = BiamoxWallpaper.apply(s.ctx, s.config.company, s.config.tagline)
                                .fold({ "✅ Заставка Biamox установлена" }, { "Ошибка: ${it.message}" })
                        }
                    })
                    RowItem("Звуки Biamox", "Рингтон, уведомления, будильник в стиле iOS", emoji = "🎵", onClick = {
                        s.scope.launch {
                            status = Sounds.install(s.ctx).fold({ it }, { "Ошибка: ${it.message}" })
                            if (!Sounds.canWriteSettings(s.ctx))
                                start(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${s.ctx.packageName}")))
                        }
                    })
                    RowItem("Пункт управления", "Фонарик, яркость, звук, Wi-Fi", emoji = "🎛", onClick = { s.controlOpen = true })
                }
            }
            item {
                SectionTitle("Голосовой ассистент")
                Group {
                    RowItem("«Привет, Биамокс»", "Слушать фразу-активатор при включённом экране", emoji = "🎙️", end = {
                        Switch(s.handsFree, { s.setHandsFreeMode(it) }, colors = SwitchDefaults.colors(checkedTrackColor = B.Green))
                    })
                    RowItem("Озвучивать ответы", emoji = "🔊", end = {
                        var on by remember { mutableStateOf(s.prefs.speakAnswers) }
                        Switch(on, { on = it; s.prefs.speakAnswers = it }, colors = SwitchDefaults.colors(checkedTrackColor = B.Green))
                    })
                    RowItem("Biamox как ассистент по умолчанию", "Вызов долгим нажатием кнопки/жестом", emoji = "🤖", onClick = {
                        start(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                    })
                    RowItem("Проверить голос", "Скажите команду, например «открой офис»", emoji = "🗣", onClick = { s.startVoice() })
                }
                Hint("Команды: «открой офис / статистику / студию / почту / сообщения / наш инстаграм», «какая выручка», " +
                    "«добавь задачу …», «позвони …», «напиши письмо клиенту …», «пункт управления». Остальное — вопрос к Claude.")
            }
            item {
                SectionTitle("Claude")
                Group {
                    RowItem("Ключ API", mask(s.secrets.claudeKey), emoji = "🔑", onClick = {
                        edit = EditField("Ключ Claude API (sk-ant-…)", "", true) { s.secrets.claudeKey = it; tick++ }
                    })
                    RowItem("Модель", ClaudeModels.all.firstOrNull { it.first == s.claudeModel() }?.second ?: s.claudeModel(),
                        emoji = "🧠", onClick = { modelPick = true })
                    RowItem("Очистить переписку", emoji = "🗑", onClick = { s.clearChat(); status = "Переписка очищена" })
                }
            }
            item {
                SectionTitle("Статистика")
                Group {
                    RowItem("Адрес сервера статистики", (s.prefs.statsUrl ?: "").ifBlank { s.config.statsUrl }.ifBlank { "не задан — показываются демо" },
                        emoji = "🌐", onClick = {
                            edit = EditField("URL JSON-статистики", (s.prefs.statsUrl ?: s.config.statsUrl), false) { s.prefs.statsUrl = it.trim(); s.refreshStats() }
                        })
                    RowItem("Токен доступа", mask(s.secrets.statsToken), emoji = "🔐", onClick = {
                        edit = EditField("Токен (Bearer)", "", true) { s.secrets.statsToken = it; s.refreshStats(); tick++ }
                    })
                }
            }
            item {
                SectionTitle("Студия роликов")
                Group {
                    RowItem("Адрес студии", (s.prefs.studioUrl ?: "").ifBlank { s.config.studioUrl }.ifBlank { "не задан" }, emoji = "🎬", onClick = {
                        edit = EditField("Адрес студии (https://…)", s.prefs.studioUrl ?: s.config.studioUrl, false) { s.prefs.studioUrl = it.trim() }
                    })
                }
            }
            item {
                SectionTitle("Почты (пароли приложений)")
                Group {
                    s.config.mailboxes.forEach { mb ->
                        RowItem(mb.name, mb.address + " · " + if (s.secrets.mailPassword(mb.address).isBlank()) "нет пароля" else "пароль сохранён",
                            emoji = "✉️", onClick = {
                                edit = EditField("Пароль приложения для ${mb.address}", "", true) {
                                    s.secrets.setMailPassword(mb.address, it); s.refreshMail(); tick++
                                }
                            })
                    }
                }
            }
            item {
                SectionTitle("Телефон")
                Group {
                    RowItem("Системные настройки", emoji = "🛠", onClick = { s.openSys(SysApp.SYSTEM_SETTINGS) })
                    RowItem("Звук и вибрация", emoji = "🔔", onClick = { start(Intent(Settings.ACTION_SOUND_SETTINGS)) })
                    RowItem("Экран", emoji = "☀️", onClick = { start(Intent(Settings.ACTION_DISPLAY_SETTINGS)) })
                    RowItem("Уведомления", emoji = "🔕", onClick = {
                        start(if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, s.ctx.packageName) else Intent(Settings.ACTION_SETTINGS))
                    })
                    RowItem("Батарея", "${s.device.batteryPct}%", emoji = "🔋", onClick = { start(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) })
                    RowItem("Память", "свободно ${"%.1f".format(s.device.storageFreeGb)} ГБ", emoji = "💾", onClick = {
                        start(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
                    })
                    RowItem("Приложения", emoji = "📦", onClick = { start(Intent(Settings.ACTION_APPLICATION_SETTINGS)) })
                    RowItem("Показать скрытые приложения", "${s.hidden.size} скрыто", emoji = "👁", onClick = {
                        s.hidden = emptySet(); s.prefs.hiddenApps = emptySet()
                    })
                }
            }
            item {
                SectionTitle("Об устройстве")
                Group {
                    RowItem("Версия Biamox", BuildConfig.VERSION_NAME, emoji = "ℹ️")
                    RowItem("Проверить обновления", emoji = "⬇️", onClick = {
                        status = "Проверяю…"
                        s.scope.launch {
                            Updater.check().fold({ info ->
                                if (info == null) status = "✅ Установлена последняя версия"
                                else {
                                    status = "Скачиваю версию ${info.versionName}…"
                                    Updater.downloadAndInstall(s.ctx, info).onFailure { status = "Ошибка: ${it.message}" }
                                }
                            }, { status = "Не удалось проверить: ${it.message}" })
                        }
                    })
                    RowItem("Обновить конфигурацию", "Почты, соцсети, команда", emoji = "🔄", onClick = { s.reloadConfig(); status = "Конфигурация перечитана" })
                }
            }
        }
    }

    edit?.let { f ->
        var v by remember(f) { mutableStateOf(f.initial) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(f.title) },
            text = {
                OutlinedTextField(v, { v = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (f.secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (f.secret) KeyboardType.Password else KeyboardType.Uri))
            },
            confirmButton = { TextButton({ f.onSave(v); edit = null; status = "Сохранено" }) { Text("Сохранить") } },
            dismissButton = { TextButton({ edit = null }) { Text("Отмена") } },
        )
    }
    if (modelPick) {
        AlertDialog(
            onDismissRequest = { modelPick = false },
            title = { Text("Модель Claude") },
            text = {
                Column {
                    ClaudeModels.all.forEach { (id, label) ->
                        Text((if (id == s.claudeModel()) "✓ " else "   ") + label,
                            modifier = Modifier.fillMaxWidth().clickableNoRipple { s.prefs.claudeModel = id; modelPick = false }
                                .padding(vertical = 12.dp))
                    }
                }
            },
            confirmButton = { TextButton({ modelPick = false }) { Text("Закрыть") } },
        )
    }
}
