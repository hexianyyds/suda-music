package com.sodamusic.app.ui.mine

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.sodamusic.app.ServiceLocator
import com.sodamusic.app.data.download.DownloadManager
import com.sodamusic.app.data.model.Constants
import com.sodamusic.app.data.model.Song
import com.sodamusic.app.data.model.sourceLabel
import com.sodamusic.app.data.repo.FavoritesManager
import com.sodamusic.app.data.store.AppSettings
import com.sodamusic.app.data.store.ListenStats
import com.sodamusic.app.data.store.UserProfile
import com.sodamusic.app.ui.components.SongListItem
import com.sodamusic.app.ui.library.LibraryViewModel
import com.sodamusic.app.ui.player.PlayerViewModel
import com.sodamusic.app.ui.theme.SodaRed
import kotlinx.coroutines.launch
import java.io.File

/** 我的页面内部子页 */
/** 我的页子页（key 用于 rememberSaveable 持久化） */
private enum class MinePage(val key: Int) {
    MAIN(0), FAVORITES(1), HISTORY(2), DOWNLOADS(3);

    companion object {
        fun of(key: Int): MinePage = entries.firstOrNull { it.key == key } ?: MAIN
    }
}

/**
 * 「我的」页面：个人主页（头像/背景/昵称）+ 听歌统计 + 收藏/历史/下载 + 主题与设置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineScreen(playerVm: PlayerViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: LibraryViewModel = viewModel {
        LibraryViewModel(ServiceLocator.libraryStore, ServiceLocator.settingsStore)
    }

    val profile by ServiceLocator.profileStore.profile.collectAsState(initial = UserProfile())
    val stats by ServiceLocator.listenStats.stats.collectAsState(initial = ListenStats())
    val favorites by FavoritesManager.favorites.collectAsState()
    val downloads by DownloadManager.items.collectAsState()
    val settings by vm.settings.collectAsState()
    val history by vm.history.collectAsState(initial = emptyList<Song>())
    val progressMap by DownloadManager.progress.collectAsState()

    // 用 rememberSaveable：切到播放页（组合被销毁）再返回时，仍停在这个子页，
    // 否则会直接退回"我的"主页
    var pageKey by rememberSaveable { mutableIntStateOf(MinePage.MAIN.key) }
    val page = MinePage.of(pageKey)
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var editingNickname by remember { mutableStateOf(false) }

    // 侧滑返回：先从子页退回「我的」主页，而不是直接退出整个页面
    androidx.activity.compose.BackHandler(enabled = page != MinePage.MAIN) {
        pageKey = MinePage.MAIN.key
    }

    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { ServiceLocator.profileStore.setAvatar(uri) }
    }
    val pickBanner = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { ServiceLocator.profileStore.setBanner(uri) }
    }

    when (page) {
        MinePage.MAIN -> MineMain(
            profile = profile,
            stats = stats,
            favoriteCount = favorites.size,
            downloadCount = downloads.size,
            historyCount = history.size,
            themeMode = settings.themeMode,
            onPickAvatar = {
                pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onPickBanner = {
                pickBanner.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onEditNickname = { editingNickname = true },
            onOpenFavorites = { pageKey = MinePage.FAVORITES.key },
            onOpenHistory = { pageKey = MinePage.HISTORY.key },
            onOpenDownloads = { pageKey = MinePage.DOWNLOADS.key },
            onThemeChange = { mode -> vm.setThemeMode(mode) },
            onOpenSettings = { showSettings = true },
            onOpenAbout = { showAbout = true },
        )

        MinePage.FAVORITES -> MineSongList(
            title = "收藏的歌",
            songs = favorites,
            playerVm = playerVm,
            onBack = { pageKey = MinePage.MAIN.key },
            isFavorite = { true },
            onToggleFavorite = { song -> scope.launch { FavoritesManager.toggle(song) } },
            downloads = downloads,
            progress = progressMap,
            onDownload = { song -> downloadSong(scope, song) },
            onDeleteDownload = { song -> DownloadManager.delete(song) },
        )

        MinePage.HISTORY -> MineSongList(
            title = "历史播放",
            songs = history,
            playerVm = playerVm,
            onBack = { pageKey = MinePage.MAIN.key },
            isFavorite = { song -> FavoritesManager.isFavorite(song, favorites) },
            onToggleFavorite = { song -> scope.launch { FavoritesManager.toggle(song) } },
            downloads = downloads,
            progress = progressMap,
            onDownload = { song -> downloadSong(scope, song) },
            onDeleteDownload = { song -> DownloadManager.delete(song) },
        )

        MinePage.DOWNLOADS -> MineSongList(
            title = "下载",
            songs = downloads.map { it.song },
            playerVm = playerVm,
            onBack = { pageKey = MinePage.MAIN.key },
            isFavorite = { song -> FavoritesManager.isFavorite(song, favorites) },
            onToggleFavorite = { song -> scope.launch { FavoritesManager.toggle(song) } },
            downloads = downloads,
            progress = progressMap,
            onDownload = { song -> downloadSong(scope, song) },
            onDeleteDownload = { song -> DownloadManager.delete(song) },
            emptyHint = "还没有下载的歌曲\n在播放页点「下载」即可离线听",
        )
    }

    if (editingNickname) {
        var text by remember { mutableStateOf(profile.nickname) }
        AlertDialog(
            onDismissRequest = { editingNickname = false },
            title = { Text("修改昵称") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { ServiceLocator.profileStore.setNickname(text) }
                    editingNickname = false
                }) { Text("保存", color = SodaRed) }
            },
            dismissButton = {
                TextButton(onClick = { editingNickname = false }) { Text("取消") }
            },
        )
    }

    if (showSettings) {
        MineSettingsDialog(
            settings = settings,
            onDismiss = { showSettings = false },
            vm = vm,
        )
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

/** 免责声明正文 */
private val DISCLAIMER = listOf(
    "本应用（Soda Music）仅供学习与技术交流使用，不提供任何商业服务。",
    "应用内的歌曲、歌词、封面、评论等数据均来自第三方公开接口，著作权归原作者及相关权利人所有，本应用不主张对这些内容拥有任何权利。",
    "播放地址由第三方接口动态生成，可能随时失效或不可用；部分曲目因版权限制仅提供试听片段或完全无法播放。",
    "本应用不存储、不上传、不转售、不分享任何用户数据，所有个人资料（昵称、头像、背景、听歌记录与下载内容）仅保存在本机。",
    "请勿将本应用或通过其获取的任何内容用于商业用途。因个人使用本应用而产生的任何纠纷与后果，由使用者自行承担。",
    "如权利人认为相关内容侵犯其合法权益，请通过应用商店或源码仓库反馈，我们核实后会立即下线相关内容。",
)

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0.0"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("关于苏打音乐") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Soda Music · v$version",
                    color = SodaRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                DISCLAIMER.forEach { para ->
                    Text(
                        para,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了", color = SodaRed) }
        },
    )
}

/** 解析直链并开始下载 */
private fun downloadSong(scope: kotlinx.coroutines.CoroutineScope, song: Song) {
    scope.launch {
        val url = runCatching {
            ServiceLocator.repository.resolveUrl(song, ServiceLocator.settingsStore.current().quality)
        }.getOrNull()
        if (url.isNullOrBlank()) return@launch
        DownloadManager.download(scope, song, url)
    }
}

/* ======================= 主页面 ======================= */

@Composable
private fun MineMain(
    profile: UserProfile,
    stats: ListenStats,
    favoriteCount: Int,
    downloadCount: Int,
    historyCount: Int,
    themeMode: String,
    onPickAvatar: () -> Unit,
    onPickBanner: () -> Unit,
    onEditNickname: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onThemeChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        // 背景图 + 头像
        item {
            Box(Modifier.fillMaxWidth().height(230.dp)) {
                // 背景（可点击更换）
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clickable(onClick = onPickBanner),
                ) {
                    if (profile.bannerPath.isNotBlank()) {
                        AsyncImage(
                            model = File(profile.bannerPath),
                            contentDescription = "背景",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF3A1B2A), Color(0xFF1A1520), SodaRed.copy(alpha = 0.55f)),
                                    ),
                                ),
                        )
                    }
                    // 底部渐隐，保证标题可读
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                                ),
                            ),
                    )
                    Text(
                        if (profile.bannerPath.isBlank()) "点击更换背景" else "点击更换背景",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(12.dp),
                    )
                }

                // 头像（叠在背景下沿）
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 20.dp)
                        .size(88.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            .clickable(onClick = onPickAvatar),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (profile.avatarPath.isNotBlank()) {
                            AsyncImage(
                                model = File(profile.avatarPath),
                                contentDescription = "头像",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                            )
                        } else {
                            Text("🎧", fontSize = 34.sp)
                        }
                    }
                    // 相机角标
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(SodaRed)
                            .clickable(onClick = onPickAvatar),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.CameraAlt,
                            contentDescription = "更换头像",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        // 昵称
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    profile.nickname,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "修改昵称",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(onClick = onEditNickname),
                )
            }
            Text(
                "本地账号 · 数据仅保存在本机",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 20.dp, top = 2.dp),
            )
        }

        // 听歌统计
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .padding(vertical = 14.dp),
            ) {
                StatCell("听歌时长", formatListenTime(stats.totalSeconds), Modifier.weight(1f))
                StatCell("今日", formatListenTime(stats.todaySeconds), Modifier.weight(1f))
                StatCell("收藏", "$favoriteCount", Modifier.weight(1f))
            }
        }

        // 快捷入口
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EntryTile("收藏的歌", "$favoriteCount", Icons.Filled.Favorite, Modifier.weight(1f), onOpenFavorites)
                EntryTile("历史播放", "$historyCount", Icons.Filled.History, Modifier.weight(1f), onOpenHistory)
                EntryTile("下载", "$downloadCount", Icons.Filled.Download, Modifier.weight(1f), onOpenDownloads)
            }
        }

        // 主题
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                SectionLabel("外观")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChip("跟随系统", "system", themeMode, onThemeChange)
                    ThemeChip("浅色", "light", themeMode, onThemeChange)
                    ThemeChip("深色", "dark", themeMode, onThemeChange)
                }
            }
        }

        // 设置
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "设置",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "音源 / 音质 / 歌词",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
        }

        // 关于
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .clickable(onClick = onOpenAbout)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "关于",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "免责声明",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun EntryTile(
    label: String,
    count: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = SodaRed, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp)
        Text(count, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeChip(label: String, value: String, current: String, onChange: (String) -> Unit) {
    val selected = current == value
    FilterChip(
        selected = selected,
        onClick = { onChange(value) },
        label = { Text(label, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SodaRed.copy(alpha = 0.22f),
            selectedLabelColor = SodaRed,
        ),
    )
}

/* ======================= 列表子页 ======================= */

@Composable
private fun MineSongList(
    title: String,
    songs: List<Song>,
    playerVm: PlayerViewModel,
    onBack: () -> Unit,
    isFavorite: (Song) -> Boolean,
    onToggleFavorite: (Song) -> Unit,
    downloads: List<com.sodamusic.app.data.download.DownloadedSong>,
    progress: Map<String, Int>,
    onDownload: (Song) -> Unit,
    onDeleteDownload: (Song) -> Unit,
    emptyHint: String = "暂无内容",
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBackIosNew,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            if (songs.isNotEmpty()) {
                Text(
                    "${songs.size} 首",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        }

        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎵", fontSize = 34.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        emptyHint,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                items(songs) { song ->
                    val key = DownloadManager.songKey(song)
                    val downloaded = downloads.any { it.song.source == song.source && it.song.id == song.id }
                    Column {
                        SongListItem(
                            song = song,
                            isPlaying = false,
                            isFavorite = isFavorite(song),
                            onClickPlay = {
                                val index = songs.indexOf(song)
                                playerVm.setQueueAndPlay(songs, index)
                            },
                            onToggleFavorite = { onToggleFavorite(song) },
                            onAddToQueue = {
                                val index = songs.indexOf(song)
                                playerVm.setQueueAndPlay(songs, index)
                            },
                            downloaded = downloaded,
                            downloadProgress = progress[key],
                            onToggleDownload = { if (downloaded) onDeleteDownload(song) else onDownload(song) },
                        )
                    }
                }
            }
        }
    }
}

/* ======================= 设置面板 ======================= */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MineSettingsDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    vm: LibraryViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiBase by remember { mutableStateOf(settings.apiBase) }
    var cookie by remember { mutableStateOf(settings.neteaseCookie) }
    var lyricOn by remember { mutableStateOf(com.sodamusic.app.ui.lyric.DesktopLyricService.isRunning) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("设置") },
        text = {
            LazyColumn(Modifier.height(460.dp)) {
                item {
                    SectionLabel("数据源")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Constants.DATA_SOURCE_OPTIONS.forEach { option ->
                            val selected = option.value == settings.dataSource
                            FilterChip(
                                selected = selected,
                                onClick = { vm.setDataSource(option.value) },
                                label = { Text(option.label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SodaRed.copy(alpha = 0.22f),
                                    selectedLabelColor = SodaRed,
                                ),
                            )
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(14.dp))
                    SectionLabel("音乐源")
                    Column {
                        Constants.SOURCE_OPTIONS.chunked(3).forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 6.dp),
                            ) {
                                row.forEach { option ->
                                    FilterChip(
                                        selected = option.value == settings.source,
                                        onClick = { vm.setSource(option.value) },
                                        label = { Text(option.label, fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SodaRed.copy(alpha = 0.22f),
                                            selectedLabelColor = SodaRed,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(14.dp))
                    SectionLabel("首选音质")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Constants.QUALITY_OPTIONS.forEach { option ->
                            FilterChip(
                                selected = option.value == settings.quality,
                                onClick = { vm.setQuality(option.value) },
                                label = {
                                    Text(
                                        if (option.value == "999") "FLAC" else "${option.value}K",
                                        fontSize = 12.sp,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SodaRed.copy(alpha = 0.22f),
                                    selectedLabelColor = SodaRed,
                                ),
                            )
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(14.dp))
                    SectionLabel("桌面歌词")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "在桌面悬浮显示歌词",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        androidx.compose.material3.Switch(
                            checked = lyricOn,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    if (!android.provider.Settings.canDrawOverlays(context)) {
                                        runCatching {
                                            context.startActivity(
                                                android.content.Intent(
                                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    Uri.parse("package:${context.packageName}"),
                                                ),
                                            )
                                        }
                                    } else {
                                        runCatching {
                                            context.startService(
                                                android.content.Intent(
                                                    context,
                                                    com.sodamusic.app.ui.lyric.DesktopLyricService::class.java,
                                                ).setAction(com.sodamusic.app.ui.lyric.DesktopLyricService.ACTION_START),
                                            )
                                        }
                                        lyricOn = true
                                    }
                                } else {
                                    runCatching {
                                        context.stopService(
                                            android.content.Intent(
                                                context,
                                                com.sodamusic.app.ui.lyric.DesktopLyricService::class.java,
                                            ),
                                        )
                                    }
                                    lyricOn = false
                                }
                            },
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(14.dp))
                    SectionLabel("网易云 Cookie（可选，提升高音质/评论覆盖）")
                    OutlinedTextField(
                        value = cookie,
                        onValueChange = { cookie = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = {
                        scope.launch {
                            vm.setNeteaseCookie(cookie)
                        }
                    }) { Text("保存 Cookie", color = SodaRed) }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionLabel("洛雪源 API 地址")
                    OutlinedTextField(
                        value = apiBase,
                        onValueChange = { apiBase = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { vm.setApiBase(apiBase) }) {
                        Text("保存 API 地址", color = SodaRed)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成", color = SodaRed) }
        },
    )
}

/* ======================= 工具 ======================= */

private fun formatListenTime(seconds: Long): String {
    if (seconds <= 0) return "0 分钟"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return when {
        h > 0 -> "${h} 小时"
        m > 0 -> "${m} 分钟"
        else -> "${seconds} 秒"
    }
}

