package com.biamox.launcher.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biamox.launcher.core.AppEntry
import com.biamox.launcher.core.AppRepository
import kotlinx.coroutines.launch

private const val PAGE_TODAY = 0
private const val PAGE_HOME = 1
private const val PAGE_LIBRARY = 2

@Composable
fun HomeScreen(s: LauncherState) {
    val pager = rememberPagerState(initialPage = PAGE_HOME) { 3 }
    LaunchedEffect(s.homeRequest) { if (s.homeRequest > 0) pager.animateScrollToPage(PAGE_HOME) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            HorizontalPager(state = pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 1) { page ->
                when (page) {
                    PAGE_TODAY -> TodayPage(s)
                    PAGE_HOME -> HomeGridPage(s)
                    else -> LibraryPage(s)
                }
            }
            // Точки страниц + кнопка поиска (как iOS 16+)
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.glass(CircleShape, 0.18f).clickable { s.searchOpen = true }
                    .padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Поиск", color = Color.White, fontSize = 13.sp)
                    Spacer(Modifier.width(10.dp))
                    repeat(3) { i ->
                        Box(Modifier.padding(horizontal = 3.dp).size(6.dp).clip(CircleShape)
                            .background(if (pager.currentPage == i) Color.White else Color.White.copy(alpha = 0.35f)))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(32.dp).glass(CircleShape, 0.18f).clickable { s.haptic(); s.controlOpen = true },
                    contentAlignment = Alignment.Center) { Text("🎛", fontSize = 15.sp) }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(32.dp).glass(CircleShape, 0.18f).clickable { s.startVoice() },
                    contentAlignment = Alignment.Center) { Text("🎙️", fontSize = 15.sp) }
            }
            Dock(s)
        }
        AnimatedVisibility(s.searchOpen, enter = fadeIn(), exit = fadeOut()) { SearchOverlay(s) }
    }
}

// ------------------------------------------------------------------ Главный экран
@Composable
private fun HomeGridPage(s: LauncherState) {
    val homeApps = s.config.homeApps.mapNotNull { pkg -> s.apps.firstOrNull { it.pkg == pkg && pkg !in s.hidden } }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 22.dp, bottom = 16.dp),
    ) {
        item(span = { GridItemSpan(4) }) { CompactStatsWidget(s) }
        items(Module.entries.filter { it != Module.SETTINGS && it != Module.VOICE }) { m -> ModuleCell(s, m) }
        items(SysApp.homeRow) { a -> SysCell(s, a) }
        items(homeApps, key = { it.pkg + it.activity }) { app -> AppCell(s, app) }
        item { ModuleCell(s, Module.VOICE) }
        item { ModuleCell(s, Module.SETTINGS) }
    }
}

@Composable
fun ModuleCell(s: LauncherState, m: Module) {
    Column(Modifier.clickable { s.open(m) }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            ModuleIcon(m)
            if (m == Module.MAIL) s.unreadTotal?.takeIf { it > 0 }?.let { Badge(it) }
        }
        IconLabel(m.title)
    }
}

@Composable
private fun Badge(n: Int) {
    Box(Modifier.padding(start = 44.dp).size(22.dp).clip(CircleShape).background(B.Red), contentAlignment = Alignment.Center) {
        Text(if (n > 99) "99+" else "$n", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCell(s: LauncherState, app: AppEntry, size: Int = 60, showLabel: Boolean = true) {
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier.combinedClickable(onClick = { s.launch(app) }, onLongClick = { menu = true }),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(app.icon, app.label, Modifier.size(size.dp).clip(Squircle))
        if (showLabel) IconLabel(app.label)
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("О приложении") }, onClick = { menu = false; s.appsRepo.appInfo(app.pkg) })
            DropdownMenuItem(text = { Text(if (app.pkg in s.hidden) "Показать" else "Скрыть с экрана") },
                onClick = { menu = false; s.setHidden(app.pkg, app.pkg !in s.hidden) })
            (0..3).forEach { i ->
                DropdownMenuItem(text = { Text("Поставить в док (место ${i + 1})") },
                    onClick = { menu = false; s.setDockSlot(i, "pkg:${app.pkg}") })
            }
            DropdownMenuItem(text = { Text("Удалить", color = B.Red) }, onClick = { menu = false; s.appsRepo.uninstall(app.pkg) })
        }
    }
}

// ------------------------------------------------------------------ Библиотека приложений
@Composable
private fun LibraryPage(s: LauncherState) {
    var q by remember { mutableStateOf("") }
    val visible = s.apps.filter { it.pkg !in s.hidden && (q.isBlank() || it.label.contains(q, true)) }
    val hiddenApps = s.apps.filter { it.pkg in s.hidden }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Text("Библиотека приложений", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 18.dp, start = 6.dp, bottom = 10.dp))
        SearchField(q, { q = it }, "Найти приложение")
        LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.fillMaxSize().padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(visible, key = { it.pkg + it.activity }) { AppCell(s, it) }
            if (hiddenApps.isNotEmpty() && q.isBlank()) {
                item(span = { GridItemSpan(4) }) {
                    Text("Скрытые (долгое нажатие → «Показать»)", color = B.Gray, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 14.dp))
                }
                items(hiddenApps, key = { "h" + it.pkg + it.activity }) { AppCell(s, it) }
            }
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, focus: FocusRequester? = null) {
    Row(modifier.fillMaxWidth().glass(RoundedCornerShape(12.dp), 0.16f).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Search, null, tint = B.Text2, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = B.Text2, fontSize = 16.sp)
            BasicTextField(value, onChange, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(B.Blue),
                modifier = Modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier))
        }
    }
}

// ------------------------------------------------------------------ Док
@Composable
private fun Dock(s: LauncherState) {
    Row(
        Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp).fillMaxWidth()
            .glass(RoundedCornerShape(32.dp), 0.2f).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        s.dock.take(4).forEach { slot ->
            when {
                slot.startsWith("module:") -> {
                    val m = runCatching { Module.valueOf(slot.removePrefix("module:")) }.getOrNull()
                    if (m != null) Box(Modifier.clickable { s.open(m) }) { ModuleIcon(m) }
                }
                slot.startsWith("pkg:") -> {
                    val app = s.apps.firstOrNull { it.pkg == slot.removePrefix("pkg:") }
                    if (app != null) AppCell(s, app, showLabel = false)
                }
                slot.startsWith("sys:") -> {
                    val a = runCatching { SysApp.valueOf(slot.removePrefix("sys:")) }.getOrNull()
                    if (a != null) SysCell(s, a, label = false)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Поиск (Spotlight)
@Composable
private fun SearchOverlay(s: LauncherState) {
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val apps = if (q.isBlank()) emptyList() else s.apps.filter { it.label.contains(q, true) }.take(12)
    val mods = if (q.isBlank()) emptyList() else Module.entries.filter { it.title.contains(q, true) }
    Column(
        Modifier.fillMaxSize().background(Color(0xE6070B1C)).statusBarsPadding().imePadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            SearchField(q, { q = it }, "Приложения, разделы, вопрос Claude", Modifier.weight(1f), focus)
            Text("Отмена", color = B.Blue, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp).clickable { s.searchOpen = false })
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 12.dp)) {
            if (q.isNotBlank()) item {
                Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp)).clickable {
                    s.searchOpen = false; s.claudeDraft = q; s.open(Module.CLAUDE)
                }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    ModuleIcon(Module.CLAUDE, 36.dp); Spacer(Modifier.width(12.dp))
                    Text("Спросить Claude: «$q»", color = Color.White, fontSize = 16.sp)
                }
                Spacer(Modifier.height(10.dp))
            }
            items(mods) { m ->
                Row(Modifier.fillMaxWidth().clickable { s.open(m) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ModuleIcon(m, 40.dp); Spacer(Modifier.width(12.dp)); Text(m.title, color = Color.White, fontSize = 16.sp)
                }
            }
            items(apps, key = { it.pkg + it.activity }) { a ->
                Row(Modifier.fillMaxWidth().clickable { s.launch(a) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(a.icon, null, Modifier.size(40.dp).clip(Squircle)); Spacer(Modifier.width(12.dp))
                    Text(a.label, color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}
