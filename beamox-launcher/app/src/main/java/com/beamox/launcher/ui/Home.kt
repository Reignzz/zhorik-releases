package com.beamox.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.Dp
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
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamox.launcher.core.AppEntry

/** Элемент сетки главного экрана */
sealed class HomeItem {
    data class Mod(val m: Module) : HomeItem()
    data class Sys(val a: SysApp) : HomeItem()
    data class App(val app: AppEntry) : HomeItem()
}

private const val COLS = 4
private const val ROWS = 6

@Composable
fun HomeScreen(s: LauncherState) {
    // Порядок как на iPhone: свои разделы, системные, рабочие приложения
    val items = buildList {
        listOf(Module.CLAUDE, Module.STATS, Module.STUDIO, Module.OFFICE, Module.MAIL).forEach { add(HomeItem.Mod(it)) }
        listOf(SysApp.CALENDAR, SysApp.PHOTOS, SysApp.CLOCK, SysApp.MAPS, SysApp.CONTACTS, SysApp.FILES, SysApp.CALCULATOR)
            .forEach { add(HomeItem.Sys(it)) }
        s.config.homeApps.mapNotNull { pkg -> s.apps.firstOrNull { it.pkg == pkg && pkg !in s.hidden } }.forEach { add(HomeItem.App(it)) }
        add(HomeItem.Mod(Module.VOICE)); add(HomeItem.Sys(SysApp.SYSTEM_SETTINGS)); add(HomeItem.Mod(Module.SETTINGS))
    }
    // Первая страница: 2 квадратных виджета (= 2 ряда) + 4 ряда иконок; дальше по 6 рядов
    val first = items.take(COLS * (ROWS - 2))
    val rest = items.drop(first.size).chunked(COLS * ROWS)
    val pages = 1 /*Сегодня*/ + 1 + rest.size + 1 /*Библиотека*/
    val pager = rememberPagerState(initialPage = s.previewPage.coerceIn(0, pages - 1)) { pages }
    LaunchedEffect(s.homeRequest) { if (s.homeRequest > 0) pager.animateScrollToPage(1) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        // Масштаб: точки iPhone (ширина 430) → dp этого экрана
        val k = maxWidth.value / 430f
        Box(
            Modifier.fillMaxSize().navigationBarsPadding()
                // Свайп вниз: из правого верхнего угла — пункт управления, в остальном месте — поиск (как в iOS)
                .pointerInput(pager.currentPage) {
                    var startX = 0f; var startY = 0f; var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { o -> startX = o.x; startY = o.y; total = 0f },
                        onVerticalDrag = { _, dy -> total += dy },
                        onDragEnd = {
                            if (total > 120f && pager.currentPage != 0) {
                                if (startX > w * 0.6f && startY < 260f) s.controlOpen = true else s.searchOpen = true
                            }
                        },
                    )
                }
        ) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                when {
                    page == 0 -> Box(Modifier.fillMaxSize().padding(top = IosStatusBarHeight, bottom = (150 * k).dp)) { TodayPage(s) }
                    page == pages - 1 -> Box(Modifier.fillMaxSize().padding(top = IosStatusBarHeight, bottom = (150 * k).dp)) { LibraryPage(s) }
                    page == 1 -> HomePage(s, first, withWidgets = true, k = k)
                    else -> HomePage(s, rest[page - 2], withWidgets = false, k = k)
                }
            }
            // «Поиск» (центр в 170 pt от низа) / точки страниц
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = ((170 - 15) * k).dp)) {
                LiquidGlass(CircleShape, Modifier.height((30 * k).dp).bounceClick { s.searchOpen = true },
                    tint = Color(0x33505055), contentAlignment = Alignment.Center) {
                    Row(Modifier.padding(horizontal = (13 * k).dp), verticalAlignment = Alignment.CenterVertically) {
                        if (pager.currentPage in 1 until pages - 1) {
                            Icon(Icons.Rounded.Search, null, tint = B.Text, modifier = Modifier.size((15 * k).dp))
                            Spacer(Modifier.width((5 * k).dp))
                            Text("Поиск", color = B.Text, fontSize = (15 * k).sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp)
                        } else {
                            repeat(pages) { i ->
                                Box(Modifier.padding(horizontal = (4 * k).dp).size((7 * k).dp).clip(CircleShape)
                                    .background(if (pager.currentPage == i) Color.White else Color.White.copy(alpha = 0.3f)))
                            }
                        }
                    }
                }
            }
            Dock(s, k, Modifier.align(Alignment.BottomCenter))
        }
        AnimatedVisibility(s.searchOpen, enter = fadeIn(), exit = fadeOut()) { SearchOverlay(s) }
    }
}

// ------------------------------------------------------------------ Страницы иконок (точные размеры iPhone)
@Composable
private fun HomePage(s: LauncherState, items: List<HomeItem>, withWidgets: Boolean, k: Float) {
    Box(Modifier.fillMaxSize()) {
        var top = 88f
        if (withWidgets) {
            Box(Modifier.offset(x = (28 * k).dp, y = (88 * k).dp).width((177 * k).dp)) { StudioSquare(s, Modifier.fillMaxWidth()) }
            Box(Modifier.offset(x = ((28 + 177 + 23) * k).dp, y = (88 * k).dp).width((177 * k).dp)) { ClaudeSquare(s, Modifier.fillMaxWidth()) }
            top = 304f
        }
        items.chunked(COLS).forEachIndexed { r, row ->
            row.forEachIndexed { c, item ->
                // ячейка шириной 99 pt, иконка 69 pt по центру ячейки
                Box(Modifier.offset(x = ((31 - 15) * k + c * 99 * k).dp, y = ((top + r * 108) * k).dp).width((99 * k).dp),
                    contentAlignment = Alignment.TopCenter) {
                    HomeCell(s, item, size = (69 * k).dp)
                }
            }
        }
    }
}

@Composable
fun HomeCell(s: LauncherState, item: HomeItem, label: Boolean = true, size: Dp = 64.dp) {
    when (item) {
        is HomeItem.Mod -> ModuleCell(s, item.m, label, size)
        is HomeItem.Sys -> SysCell(s, item.a, label, size)
        is HomeItem.App -> AppCell(s, item.app, size = size.value.toInt(), showLabel = label)
    }
}

@Composable
fun ModuleCell(s: LauncherState, m: Module, label: Boolean = true, size: Dp = 64.dp) {
    Column(Modifier.bounceClick { s.haptic(); s.open(m) }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            ModuleIcon(m, size)
            if (m == Module.MAIL) s.unreadTotal?.takeIf { it > 0 }?.let { Badge(it) }
        }
        if (label) IconLabel(m.title)
    }
}

@Composable
private fun Badge(n: Int) {
    Box(Modifier.padding(start = 44.dp).size(22.dp).clip(CircleShape).background(Color(0xFFFF3B30)), contentAlignment = Alignment.Center) {
        Text(if (n > 99) "99+" else "$n", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun AppCell(s: LauncherState, app: AppEntry, size: Int = 64, showLabel: Boolean = true) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.bounceClick(onLongClick = { s.haptic(); menu = true }) { s.launch(app) },
        horizontalAlignment = Alignment.CenterHorizontally) {
        Image(app.icon, app.label, Modifier.size(size.dp).clip(Squircle))
        if (showLabel) IconLabel(app.label)
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("О приложении") }, onClick = { menu = false; s.appsRepo.appInfo(app.pkg) })
            DropdownMenuItem(text = { Text(if (app.pkg in s.hidden) "Показать" else "Скрыть") },
                onClick = { menu = false; s.setHidden(app.pkg, app.pkg !in s.hidden) })
            (0..3).forEach { i ->
                DropdownMenuItem(text = { Text("В док, место ${i + 1}") }, onClick = { menu = false; s.setDockSlot(i, "pkg:${app.pkg}") })
            }
            DropdownMenuItem(text = { Text("Удалить приложение", color = B.Red) }, onClick = { menu = false; s.appsRepo.uninstall(app.pkg) })
        }
    }
}

// ------------------------------------------------------------------ Библиотека приложений
@Composable
private fun LibraryPage(s: LauncherState) {
    var q by remember { mutableStateOf("") }
    val visible = s.apps.filter { it.pkg !in s.hidden && (q.isBlank() || it.label.contains(q, true)) }
    val hiddenApps = s.apps.filter { it.pkg in s.hidden }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(14.dp))
        SearchField(q, { q = it }, "Библиотека приложений")
        LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.fillMaxSize().padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            items(visible, key = { it.pkg + it.activity }) { Box(contentAlignment = Alignment.Center) { AppCell(s, it) } }
            if (hiddenApps.isNotEmpty() && q.isBlank()) {
                item(span = { GridItemSpan(4) }) {
                    Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("СКРЫТЫЕ И РЕКЛАМНЫЕ · ${hiddenApps.size}", color = B.Faint, fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f))
                        Pill("Очистить", B.Red) { s.screen = Module.SETTINGS; s.cleanupOpen = true }
                    }
                }
                items(hiddenApps, key = { "h" + it.pkg + it.activity }) { Box(contentAlignment = Alignment.Center) { AppCell(s, it) } }
            }
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier,
                focus: FocusRequester? = null, onMic: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().glass(CircleShape, 0.10f).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Search, null, tint = B.Dim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = B.Faint, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            BasicTextField(value, onChange, singleLine = true,
                textStyle = TextStyle(color = B.Text, fontSize = 17.sp, fontFamily = Rounded, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(B.Coral),
                modifier = Modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier))
        }
        if (onMic != null) Icon(Icons.Rounded.Mic, "Голос", tint = B.Dim, modifier = Modifier.size(22.dp).clickable { onMic() })
    }
}

// ------------------------------------------------------------------ Док
@Composable
private fun Dock(s: LauncherState, k: Float, modifier: Modifier) {
    // Док iPhone: высота 116 pt, отступ 14 pt по бокам и 16 pt снизу, скругление 42 pt, иконки 70 pt
    LiquidGlass(RoundedCornerShape((42 * k).dp),
        modifier.padding(start = (14 * k).dp, end = (14 * k).dp, bottom = (16 * k).dp).fillMaxWidth().height((116 * k).dp),
        tint = Color(0x26606065), contentAlignment = Alignment.CenterStart) {
        s.dock.take(4).forEachIndexed { i, slot ->
            Box(Modifier.offset(x = ((38 - 14 - 12) * k + i * 94.7f * k).dp).width((94 * k).dp), contentAlignment = Alignment.Center) {
                when {
                    slot.startsWith("module:") -> runCatching { Module.valueOf(slot.removePrefix("module:")) }.getOrNull()
                        ?.let { ModuleCell(s, it, label = false, size = (70 * k).dp) }
                    slot.startsWith("pkg:") -> s.apps.firstOrNull { it.pkg == slot.removePrefix("pkg:") }
                        ?.let { AppCell(s, it, size = (70 * k).toInt(), showLabel = false) }
                    slot.startsWith("sys:") -> runCatching { SysApp.valueOf(slot.removePrefix("sys:")) }.getOrNull()
                        ?.let { SysCell(s, it, label = false, size = (70 * k).dp) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Поиск (как Spotlight)
@Composable
private fun SearchOverlay(s: LauncherState) {
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val apps = if (q.isBlank()) s.apps.filter { it.pkg in s.config.homeApps }.take(8) else s.apps.filter { it.label.contains(q, true) }.take(12)
    val mods = if (q.isBlank()) emptyList() else Module.entries.filter { it.title.contains(q, true) }
    val sys = if (q.isBlank()) emptyList() else SysApp.entries.filter { it.title.contains(q, true) }
    Column(Modifier.fillMaxSize().background(Color(0xE6000000)).padding(top = IosStatusBarHeight).imePadding().padding(horizontal = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
            SearchField(q, { q = it }, "Поиск", Modifier.weight(1f), focus, onMic = { s.searchOpen = false; s.startVoice() })
            Text("Отменить", color = B.Accent, fontSize = 17.sp,
                modifier = Modifier.padding(start = 14.dp).clickable { s.searchOpen = false })
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 14.dp)) {
            if (q.isNotBlank()) item {
                Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).clickable {
                    s.searchOpen = false; s.claudeDraft = q; s.open(Module.CLAUDE)
                }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    ModuleIcon(Module.CLAUDE, 40.dp); Spacer(Modifier.width(12.dp))
                    Column { Text("Спросить Claude", color = B.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("«$q»", color = B.Faint, fontSize = 14.sp) }
                }
                Spacer(Modifier.height(12.dp))
            } else item {
                Text("ПРЕДЛОЖЕНИЯ", color = B.Faint, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
            items(mods) { m -> SearchRow({ ModuleIcon(m, 40.dp) }, m.title) { s.open(m) } }
            items(sys) { a -> SearchRow({ SysIcon(a, 40.dp) }, a.title) { s.searchOpen = false; s.openSys(a) } }
            items(apps, key = { it.pkg + it.activity }) { a ->
                SearchRow({ Image(a.icon, null, Modifier.size(40.dp).clip(Squircle)) }, a.label) { s.launch(a) }
            }
        }
    }
}

@Composable
private fun SearchRow(icon: @Composable () -> Unit, title: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        icon(); Spacer(Modifier.width(14.dp)); Text(title, color = B.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}
