package com.beamox.launcher.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamox.launcher.core.AppRepository

@Composable
fun OfficeScreen(s: LauncherState) {
    var newTask by remember { mutableStateOf("") }
    ModuleSheet("Офис ${s.config.company}", onBack = { s.goHome() }) {
        LazyColumn(contentPadding = ListPadding) {
            item {
                Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Gap) {
                    OfficeTile(Modifier.weight(1f), Module.MAIL, s.unreadTotal?.let { "$it новых" } ?: "Почты") { s.open(Module.MAIL) }
                    OfficeTile(Modifier.weight(1f), Module.STATS, s.stats.kpis.firstOrNull()?.value ?: "") { s.open(Module.STATS) }
                    OfficeTile(Modifier.weight(1f), Module.STUDIO, "Ролики") { s.open(Module.STUDIO) }
                    OfficeTile(Modifier.weight(1f), Module.CLAUDE, "Ассистент") { s.open(Module.CLAUDE) }
                }
            }
            item {
                SectionTitle("Задачи")
                Group {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        OutlinedTextField(newTask, { newTask = it }, placeholder = { Text("Новая задача…") },
                            singleLine = true, modifier = Modifier.weight(1f))
                        Text("Добавить", color = B.Blue, modifier = Modifier.padding(start = 10.dp).clickable {
                            if (newTask.isNotBlank()) { s.addTask(newTask.trim()); newTask = "" }
                        })
                    }
                    if (s.tasks.isEmpty()) RowItem("Задач нет", "Скажите: «Бимокс, добавь задачу позвонить клиенту»", emoji = "🎉")
                    s.tasks.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                            Checkbox(t.done, { s.toggleTask(t) }, colors = CheckboxDefaults.colors(checkedColor = B.Green))
                            Text(t.text, color = if (t.done) B.Gray else Color.White, fontSize = 16.sp,
                                textDecoration = if (t.done) TextDecoration.LineThrough else null, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.Close, "Удалить", tint = B.Gray,
                                modifier = Modifier.padding(12.dp).clickable { s.deleteTask(t) })
                        }
                    }
                }
            }
            item {
                SectionTitle("Почтовые ящики")
                Group {
                    s.config.mailboxes.forEach { mb ->
                        val st = s.mailStatus.firstOrNull { it.mailbox.address == mb.address }
                        RowItem(mb.name, mb.address, emoji = "✉️", trailing = st?.unread?.toString() ?: "",
                            onClick = { s.open(Module.MAIL) })
                    }
                }
            }
            item {
                SectionTitle("Команда")
                Group {
                    s.config.team.forEach { tm ->
                        RowItem(tm.name, listOf(tm.role, tm.phone, tm.email).filter { it.isNotBlank() }.joinToString(" · "),
                            emoji = "👤", end = {
                                if (tm.phone.isNotBlank()) Text("📞", fontSize = 20.sp, modifier = Modifier.padding(6.dp).clickable {
                                    AppRepository.safeStart(s.activity, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${tm.phone}")))
                                })
                                if (tm.email.isNotBlank()) Text("✉️", fontSize = 20.sp, modifier = Modifier.padding(6.dp).clickable {
                                    s.composeMail("", "", tm.email)
                                })
                            })
                    }
                }
            }
            item {
                SectionTitle("Соцсети компании")
                Group { s.config.socials.forEach { so -> RowItem(so.title, so.url, emoji = so.emoji, onClick = { s.openSocial(so) }) } }
            }
            item {
                SectionTitle("Быстрые ссылки")
                Group { s.config.links.forEach { l -> RowItem(l.title, l.url, emoji = l.emoji, onClick = { s.openUrl(l.url) }) } }
            }
        }
    }
}

@Composable
private fun OfficeTile(modifier: Modifier, m: Module, sub: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Column(modifier.glass(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
        .clickable { onClick() }.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ModuleIcon(m, 44.dp)
        Text(m.title, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Text(sub, color = B.Gray, fontSize = 11.sp, maxLines = 1)
    }
}
