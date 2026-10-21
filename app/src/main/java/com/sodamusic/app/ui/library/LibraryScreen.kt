package com.sodamusic.app.ui.library

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sodamusic.app.ServiceLocator
import com.sodamusic.app.data.model.Constants
import com.sodamusic.app.data.model.Song
import com.sodamusic.app.data.model.qualityOption
import com.sodamusic.app.ui.components.LocalBottomBarHeight
import com.sodamusic.app.data.repo.FavoritesManager
import com.sodamusic.app.data.repo.RadarFeed
import com.sodamusic.app.playback.QueueEditor
import com.sodamusic.app.ui.components.SongListItem
import com.sodamusic.app.ui.discover.QqDiscoverScreen
import com.sodamusic.app.ui.lyric.DesktopLyricService
import com.sodamusic.app.ui.player.PlayerViewModel
import com.sodamusic.app.ui.theme.SodaRed
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/** "我的"页：播放队列 / 收藏列表 / 榜单雷达 / 设置（API 地址、首选音质、导入导出） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(playerVm: PlayerViewModel) {
    val vm: LibraryViewModel = viewModel {
        LibraryViewModel(ServiceLocator.libraryStore, ServiceLocator.settingsStore)
    }
    var tab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    // rememberSaveable：切到播放页（组合销毁）再返回时，仍停在发现页
    var showDiscover by rememberSaveable { mutableStateOf(false) }

    // 发现页是全屏页：进入时隐藏底部导航胶囊
    val bottomBarVisible = com.sodamusic.app.ui.components.LocalBottomBarVisible.current
    LaunchedEffect(showDiscover) {
        bottomBarVisible.value = !showDiscover
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { bottomBarVisible.value = true }
    }

    // QQ 音乐发现页（热词 / 歌单 / 榜单 / 歌手 / 专辑）
    if (showDiscover) {
        QqDiscoverScreen(
            playerVm = playerVm,
            onBack = { showDiscover = false },
            onSearch = { showDiscover = false },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("我的", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { showDiscover = true }) {
                Icon(
                    Icons.Filled.Explore,
                    contentDescription = "发现",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "设置", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        PrimaryTabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            listOf("播放队列", "收藏列表", "历史记录", "榜单雷达").forEachIndexed { i, title ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(title, fontSize = 13.sp, color = if (tab == i) SodaRed else MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
        }
        when (tab) {
            0 -> QueueTab(vm, playerVm)
            1 -> FavoritesTab(vm, playerVm)
            2 -> HistoryTab(vm, playerVm)
            else -> RadarTab(vm)
        }
    }

    if (showSettings) {
        SettingsDialog(vm, onDismiss = { showSettings = false })
    }
}

@Composable
private fun QueueTab(vm: LibraryViewModel, playerVm: PlayerViewModel) {
    val queue by vm.queue.collectAsState()
    val currentIndex by vm.currentIndex.collectAsState()
    val favorites by FavoritesManager.favorites.collectAsState()
    val playerState by playerVm.state.collectAsState()

    if (queue.isEmpty()) {
        EmptyHint("播放队列还是空的\n去搜索或首页添加歌曲吧")
        return
    }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("共 ${queue.size} 首", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.clearQueue() }) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text("清空", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomBarHeight.current + 12.dp)) {
            itemsIndexed(queue, key = { i, s -> "q$i-${s.source}-${s.id}" }) { index, song ->
                SongListItem(
                    song = song,
                    isPlaying = index == currentIndex,
                    isFavorite = FavoritesManager.isFavorite(song, favorites),
                    onClickPlay = { vm.playQueueAt(index) },
                    onToggleFavorite = { vm.toggleFavorite(song) },
                    onAddToQueue = { vm.removeQueueAt(index) },
                )
            }
        }
    }
}

@Composable
private fun FavoritesTab(vm: LibraryViewModel, playerVm: PlayerViewModel) {
    val favorites by vm.favorites.collectAsState()
    val playerState by playerVm.state.collectAsState()

    if (favorites.isEmpty()) {
        EmptyHint("还没有收藏的歌曲\n点击歌曲右侧的爱心即可收藏")
        return
    }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("共 ${favorites.size} 首", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.addAllFavoritesToQueue() }) {
                Icon(Icons.Filled.Checklist, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text("全部加入队列", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomBarHeight.current + 12.dp)) {
            itemsIndexed(favorites, key = { i, s -> "f$i-${s.source}-${s.id}" }) { index, song ->
                val current = playerState.song
                SongListItem(
                    song = song,
                    isPlaying = current != null && current.source == song.source && current.id == song.id,
                    isFavorite = true,
                    onClickPlay = { playerVm.setQueueAndPlay(favorites, index) },
                    onToggleFavorite = { vm.toggleFavorite(song) },
                    onAddToQueue = { vm.toggleFavorite(song) },
                )
            }
        }
    }
}

@Composable
private fun HistoryTab(vm: LibraryViewModel, playerVm: PlayerViewModel) {
    val history by vm.history.collectAsState(initial = emptyList())
    val favorites by FavoritesManager.favorites.collectAsState()
    val playerState by playerVm.state.collectAsState()

    if (history.isEmpty()) {
        EmptyHint("还没有听歌记录\n播放过的歌会自动记录在这里")
        return
    }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("共 ${history.size} 首", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.clearHistory() }) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text("清空历史", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomBarHeight.current + 12.dp)) {
            itemsIndexed(history, key = { i, s -> "h$i-${s.source}-${s.id}" }) { index, song ->
                val current = playerState.song
                SongListItem(
                    song = song,
                    isPlaying = current != null && current.source == song.source && current.id == song.id,
                    isFavorite = FavoritesManager.isFavorite(song, favorites),
                    onClickPlay = { playerVm.setQueueAndPlay(history, index) },
                    onToggleFavorite = { vm.toggleFavorite(song) },
                    onAddToQueue = {
                        kotlinx.coroutines.MainScope().launch { QueueEditor.append(ServiceLocator.libraryStore, listOf(song)) }
                    },
                )
            }
        }
    }
}

@Composable
private fun RadarTab(vm: LibraryViewModel) {
    val settings by vm.settings.collectAsState()
    val refreshing by RadarFeed.refreshing.collectAsState()
    val error by RadarFeed.error.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("音乐雷达", fontWeight = FontWeight.Bold)
                Text("勾选参与注入的官方榜单，刷新后随机抽取 Top20 去重注入首页", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (refreshing) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = SodaRed)
            } else {
                IconButton(onClick = {
                    scope.launch {
                        vm.refreshRadar { err ->
                            Toast.makeText(
                                context,
                                err ?: "雷达已刷新，可回首页查看",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新雷达", tint = SodaRed)
                }
            }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
        }
        Spacer(Modifier.height(8.dp))
        Constants.RADAR_PLAYLISTS.forEach { playlist ->
            val checked = playlist.id in settings.radarIds
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { vm.toggleRadar(playlist.id) }
                    .padding(vertical = 2.dp),
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { vm.toggleRadar(playlist.id) },
                    colors = CheckboxDefaults.colors(checkedColor = SodaRed),
                )
                Column(Modifier.padding(start = 4.dp)) {
                    Text(playlist.name, fontSize = 15.sp)
                    Text(playlist.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** 桌面歌词可选颜色 */
private val LYRIC_COLORS = listOf(
    0xFFFFFFFFL to "白",
    0xFFFF7A9AL to "粉",
    0xFF64B5F6L to "蓝",
    0xFFFFD54FL to "黄",
    0xFF81C784L to "绿",
    0xFFBA68C8L to "紫",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDialog(vm: LibraryViewModel, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiBase by remember { mutableStateOf(settings.apiBase) }
    var cookie by remember { mutableStateOf(settings.neteaseCookie) }
    var lyricOn by remember { mutableStateOf(com.sodamusic.app.ui.lyric.DesktopLyricService.isRunning) }
    var showImport by remember { mutableStateOf(false) }
    val usingFallback by vm.usingFallback.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("设置") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("数据源", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Constants.DATA_SOURCE_OPTIONS.forEach { option ->
                        val selected = option.value == settings.dataSource
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    vm.setDataSource(option.value)
                                    Toast.makeText(
                                        context,
                                        "已切换到${option.label}，切换成功",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            label = { Text(option.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SodaRed.copy(alpha = 0.25f),
                                selectedLabelColor = SodaRed,
                            ),
                        )
                    }
                }
                Text(
                    text = Constants.DATA_SOURCE_OPTIONS
                        .firstOrNull { it.value == settings.dataSource }?.description.orEmpty(),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 音乐源：搜索与播放统一使用这里的设置
                Spacer(Modifier.height(14.dp))
                Text("音乐源", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Column {
                    Constants.SOURCE_OPTIONS.chunked(3).forEach { rowOptions ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 6.dp),
                        ) {
                            rowOptions.forEach { option ->
                                val selected = option.value == settings.source
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        if (!selected) {
                                            vm.setSource(option.value)
                                            Toast.makeText(
                                                context,
                                                "音乐源已切换为${option.label}",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                    label = { Text(option.label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SodaRed.copy(alpha = 0.25f),
                                        selectedLabelColor = SodaRed,
                                    ),
                                )
                            }
                        }
                    }
                }
                Text(
                    "QQ 音乐直连官方接口：免费曲可完整播放；独家/VIP 原唱受版权限制，仅能试听或不可播。",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 桌面歌词（悬浮窗，需要「显示在其他应用上层」权限）
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("桌面歌词", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "在桌面悬浮显示当前歌词（需要悬浮窗权限）",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                    Switch(
                        checked = lyricOn,
                        onCheckedChange = { enable ->
                            if (enable) {
                                if (!android.provider.Settings.canDrawOverlays(context)) {
                                    Toast.makeText(context, "请先允许「显示在其他应用上层」", Toast.LENGTH_LONG).show()
                                    runCatching {
                                        context.startActivity(
                                            android.content.Intent(
                                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                android.net.Uri.parse("package:${context.packageName}"),
                                            ),
                                        )
                                    }
                                } else {
                                    runCatching {
                                        context.startService(
                                            android.content.Intent(context, DesktopLyricService::class.java)
                                                .setAction(DesktopLyricService.ACTION_START),
                                        )
                                    }
                                    lyricOn = true
                                }
                            } else {
                                runCatching {
                                    context.stopService(
                                        android.content.Intent(context, DesktopLyricService::class.java),
                                    )
                                }
                                lyricOn = false
                            }
                        },
                    )
                }

                // 歌词颜色（开关打开后可选）
                if (lyricOn) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for ((value, _) in LYRIC_COLORS) {
                            val selected = settings.lyricColor == value
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(value))
                                    .border(
                                        width = if (selected) 2.dp else 0.dp,
                                        color = SodaRed,
                                        shape = CircleShape,
                                    )
                                    .clickable {
                                        vm.setLyricColor(value)
                                        DesktopLyricService.colorOverride = value.toInt()
                                    },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("首选音质", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Constants.QUALITY_OPTIONS.forEach { option ->
                        val selected = option.value == settings.quality
                        FilterChip(
                            selected = selected,
                            onClick = { vm.setQuality(option.value) },
                            label = { Text(if (option.value == "999") "FLAC" else "${option.value}K", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SodaRed.copy(alpha = 0.25f),
                                selectedLabelColor = SodaRed,
                            ),
                        )
                    }
                }
                if (settings.dataSource == Constants.DATA_SOURCE_LX) {
                    Spacer(Modifier.height(14.dp))
                    Text("洛雪源 API 地址（可填自建服务器）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = apiBase,
                        onValueChange = { apiBase = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = {
                        vm.setApiBase(apiBase)
                        Toast.makeText(context, "API 地址已保存", Toast.LENGTH_SHORT).show()
                    }) { Text("保存 API 地址", color = SodaRed) }

                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("洛雪源故障时自动切换网易源", fontSize = 13.sp)
                            Text(
                                "检测到洛雪源不可用时自动切到网易源，60 秒后自动回切",
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = settings.autoFallback, onCheckedChange = { vm.setAutoFallback(it) })
                    }
                    Text(
                        text = if (usingFallback) "当前数据来源：网易源（容灾中）" else "当前数据来源：洛雪源",
                        fontSize = 11.sp,
                        color = if (usingFallback) SodaRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                if (settings.dataSource == Constants.DATA_SOURCE_NETEASE || settings.autoFallback) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "网易源 Cookie（可选；填入后才能播放 VIP / 无损曲目）",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = cookie,
                        onValueChange = { cookie = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        placeholder = { Text("MUSIC_U=...", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = {
                        vm.setNeteaseCookie(cookie)
                        Toast.makeText(context, "网易源 Cookie 已保存", Toast.LENGTH_SHORT).show()
                    }) { Text("保存 Cookie", color = SodaRed) }
                }

                Spacer(Modifier.height(6.dp))
                Text("列表导入 / 导出", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        vm.exportJson { json ->
                            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clip.setPrimaryClip(ClipData.newPlainText("sodamusic-export", json))
                            Toast.makeText(context, "播放列表与收藏 JSON 已复制到剪贴板", Toast.LENGTH_LONG).show()
                        }
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("导出到剪贴板", fontSize = 12.sp)
                    }
                    TextButton(onClick = { showImport = true }) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("从剪贴板导入", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成", color = SodaRed) }
        },
    )

    if (showImport) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImport = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("从剪贴板导入") },
            text = {
                Column {
                    Text("将之前导出的 JSON 粘贴到下方（会覆盖当前队列与收藏）", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        minLines = 4,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipText = text.ifBlank { clip.primaryClip?.getItemAt(0)?.text?.toString().orEmpty() }
                        vm.importJson(clipText) { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                    showImport = false
                }) { Text("导入", color = SodaRed) }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}



