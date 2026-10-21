package com.sodamusic.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import coil.compose.AsyncImage
import com.sodamusic.app.data.model.Constants
import com.sodamusic.app.data.model.PlayMode
import com.sodamusic.app.data.model.qualityOption
import com.sodamusic.app.ui.theme.SodaRed
import com.sodamusic.app.util.LrcParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun formatTime(ms: Long): String {
    if (ms < 0) return "0:00"
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

private fun modeIcon(mode: PlayMode) = when (mode) {
    PlayMode.SHUFFLE -> Icons.Filled.Shuffle
    PlayMode.SINGLE -> Icons.Filled.RepeatOne
    else -> Icons.Filled.Repeat
}

/**
 * 全屏播放页：大封面 / LRC 逐行歌词（当前行高亮 + 自动滚动 + 手动滚动暂停跟随 3 秒回位）、
 * 进度条拖拽、上一首/播放/下一首、播放模式切换、音质切换菜单（切换即重载当前歌）。
 */
@Composable
@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun PlayerScreen(playerVm: PlayerViewModel, onBack: () -> Unit) {
    val state by playerVm.state.collectAsState()
    // 试听片段标记（VIP 曲在无登录态下平台只给 30 秒）
    val trialIds by com.sodamusic.app.ServiceLocator.repository.trialSongIds.collectAsState()
    val song = state.song

    // 下载状态
    val downloads by com.sodamusic.app.data.download.DownloadManager.items.collectAsState()
    val dlProgress by com.sodamusic.app.data.download.DownloadManager.progress.collectAsState()
    val isDownloaded = song != null &&
        downloads.any { it.song.source == song.source && it.song.id == song.id }
    val dlKey = song?.let { com.sodamusic.app.data.download.DownloadManager.songKey(it) }
    val dlPct = dlKey?.let { dlProgress[it] }

    // 收藏 / 评论（评论区只读）
    val favorites by com.sodamusic.app.data.repo.FavoritesManager.favorites.collectAsState()
    val isFavorite = song != null && com.sodamusic.app.data.repo.FavoritesManager.isFavorite(song, favorites)
    val scope = rememberCoroutineScope()
    var commentTotal by remember { mutableStateOf(0) }
    var commentList by remember {
        mutableStateOf<List<com.sodamusic.app.data.api.NeteaseDirectApi.NeComment>>(emptyList())
    }
    var showComments by remember { mutableStateOf(false) }

    LaunchedEffect(song?.id) {
        val s = song ?: return@LaunchedEffect
        val (total, list) = com.sodamusic.app.ServiceLocator.repository.comments(s, 20)
        commentTotal = total
        commentList = list
    }

    val hazeState = rememberHazeState()
    var panelHeightPx by remember { mutableIntStateOf(0) }
    val panelHeight = with(LocalDensity.current) { panelHeightPx.toDp() }
    // 液态玻璃（Liquid Glass）：折射 + Fresnel + 镜面高光 + 边缘光照
    // clear 预设保留更多背景可见性，tint 压到很淡，突出"透明感"
    val glassStyle = remember {
        GlassStyle.clear.then {
            shape(RoundedCornerShape(28.dp))
            tint(Color(0xFF9AA0A6).copy(alpha = 0.05f))
            edgeShadow(Color.Black.copy(alpha = 0.22f))
            edgeSoftness(1.2.dp)
        }
    }
    // 顶栏更薄
    val topGlassStyle = remember {
        GlassStyle.clear.then {
            shape(RoundedCornerShape(24.dp))
            tint(Color(0xFF9AA0A6).copy(alpha = 0.04f))
            edgeShadow(Color.Black.copy(alpha = 0.20f))
            edgeSoftness(0.8.dp)
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .hazeSource(hazeState),
    ) {
        // 为悬浮顶栏留出高度
        Spacer(Modifier.height(60.dp))

        // 中部：封面 或 歌词
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(
                visible = !state.showLyrics,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                CoverSection(state = state)
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = state.showLyrics,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                LyricsSection(playerVm = playerVm, bottomInset = panelHeight)
            }
        }
    }

    // 悬浮顶栏：扁椭圆毛玻璃胶囊（返回 + 标题 + 歌词）
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color.Black.copy(alpha = 0.30f),
                spotColor = Color.Black.copy(alpha = 0.40f),
            )
            .hazeGlass(input = HazeInput.Sources(hazeState), style = topGlassStyle)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 圆形毛玻璃按钮
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.ArrowBackIosNew,
                contentDescription = "返回",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                song?.name ?: "汽水音乐",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                song?.artist ?: "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f),
            )
            // 试听判定：显式命中试听链接，或实际时长只有 35 秒以内（平台对 VIP 曲的限制）
            val trial = song != null && (song.id in trialIds || state.duration in 1_000..35_000)
            if (trial) {
                Text(
                    "试听片段",
                    color = SodaRed,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .background(SodaRed.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (state.showLyrics) SodaRed.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f),
                )
                .clickable { playerVm.toggleLyrics() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Lyrics,
                contentDescription = if (state.showLyrics) "显示封面" else "显示歌词",
                tint = if (state.showLyrics) SodaRed else Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }

    // 底部控制板：悬浮扁椭圆毛玻璃胶囊（左右 16 / 底部 8 / 圆角 28）
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .onSizeChanged { panelHeightPx = it.height }
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 8.dp)
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = 0.50f),
            )
            .hazeGlass(input = HazeInput.Sources(hazeState), style = glassStyle)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        // 播放错误提示
        if (state.error != null) {
            Text(
                state.error ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { playerVm.dismissError() }
                    .padding(vertical = 4.dp),
            )
        }

        // 进度条
        Column {
            Slider(
                value = if (state.duration > 0) (state.position.toFloat() / state.duration).coerceIn(0f, 1f) else 0f,
                onValueChange = { fraction -> playerVm.seekTo((fraction * state.duration).toLong()) },
                colors = SliderDefaults.colors(
                    thumbColor = SodaRed,
                    activeTrackColor = SodaRed,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                enabled = state.duration > 0,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(formatTime(state.position), fontSize = 11.sp, color = Color.White.copy(alpha = 0.72f))
                Spacer(Modifier.weight(1f))
                Text(formatTime(state.duration), fontSize = 11.sp, color = Color.White.copy(alpha = 0.72f))
            }
        }

        // 收藏 / 评论（评论区只读展示）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 收藏（本地）：已收藏时高亮成红色胶囊，右侧显示本机收藏总数
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isFavorite) SodaRed.copy(alpha = 0.20f) else Color.Transparent,
                        RoundedCornerShape(16.dp),
                    )
                    .clickable(enabled = song != null) {
                        val s = song ?: return@clickable
                        scope.launch {
                            com.sodamusic.app.data.repo.FavoritesManager.toggle(s)
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "收藏",
                    tint = if (isFavorite) SodaRed else Color.White.copy(alpha = 0.80f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (isFavorite) "已收藏" else "收藏",
                    color = if (isFavorite) SodaRed else Color.White.copy(alpha = 0.80f),
                    fontSize = 11.sp,
                )
                if (favorites.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        formatCount(favorites.size),
                        color = if (isFavorite) SodaRed.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.55f),
                        fontSize = 10.sp,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            // 下载 / 已下载
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = song != null) {
                        val s = song ?: return@clickable
                        if (isDownloaded) {
                            com.sodamusic.app.data.download.DownloadManager.delete(s)
                        } else {
                            scope.launch {
                                val url = runCatching {
                                    com.sodamusic.app.ServiceLocator.repository.resolveUrl(s, state.quality)
                                }.getOrNull()
                                if (!url.isNullOrBlank()) {
                                    com.sodamusic.app.data.download.DownloadManager.download(scope, s, url)
                                }
                            }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                if (dlPct != null) {
                    CircularProgressIndicator(
                        progress = { dlPct / 100f },
                        modifier = Modifier.size(16.dp),
                        color = SodaRed,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("$dlPct%", color = Color.White.copy(alpha = 0.80f), fontSize = 10.sp)
                } else {
                    Icon(
                        if (isDownloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                        contentDescription = if (isDownloaded) "删除下载" else "下载",
                        tint = if (isDownloaded) SodaRed else Color.White.copy(alpha = 0.80f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (isDownloaded) "已下载" else "下载",
                        color = if (isDownloaded) SodaRed else Color.White.copy(alpha = 0.80f),
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            // 评论（数量为平台真实值）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = song != null) { showComments = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Comment,
                    contentDescription = "评论",
                    tint = Color.White.copy(alpha = 0.80f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (commentTotal > 0) "评论 ${formatCount(commentTotal)}" else "评论",
                    color = Color.White.copy(alpha = 0.80f),
                    fontSize = 11.sp,
                )
            }
        }

        // 主控制区：模式 / 上一首 / 播放 / 下一首 / 音质
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // 播放模式
            IconButton(onClick = { playerVm.setMode(state.mode.next()) }) {
                Icon(
                    modeIcon(state.mode),
                    contentDescription = state.mode.label,
                    tint = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.size(24.dp),
                )
            }
            // 上一首
            IconButton(onClick = { playerVm.previous() }) {
                Icon(
                    Icons.Filled.ArrowBackIosNew,
                    contentDescription = "上一首",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
            // 播放/暂停（圆形双层：外层浅光晕 + 内层品牌色）
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable { playerVm.togglePlayPause() },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(SodaRed),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = Color.White,
                            strokeWidth = 3.dp,
                        )
                    } else {
                        Icon(
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }
            // 下一首
            IconButton(onClick = { playerVm.next() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "下一首",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
            // 音质菜单
            QualityMenu(current = state.quality, onSelect = { playerVm.setQuality(it) })
        }
    }

    // 评论面板（只读展示）
    if (showComments) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showComments = false },
            containerColor = Color(0xFF17161C),
            contentColor = Color.White,
        ) {
            CommentSheet(commentTotal, commentList)
        }
    }
    }
}

/** 评论区（只读）：总数 + 热评列表 */
@Composable
private fun CommentSheet(
    total: Int,
    comments: List<com.sodamusic.app.data.api.NeteaseDirectApi.NeComment>,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            if (total > 0) "评论 ${formatCount(total)}" else "评论",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "仅展示热门评论，暂不支持发布",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(10.dp))
        if (comments.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                Text(
                    "暂无评论（或该歌曲不支持评论）",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                )
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 460.dp)) {
                items(comments) { c ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                c.user.ifBlank { "匿名用户" },
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f),
                            )
                            if (c.time.isNotBlank()) {
                                Text(
                                    c.time,
                                    color = Color.White.copy(alpha = 0.35f),
                                    fontSize = 10.sp,
                                )
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(c.content, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp)
                        if (c.liked > 0) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "♥ ${formatCount(c.liked)}",
                                color = SodaRed.copy(alpha = 0.85f),
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun formatCount(v: Int): String = when {
    v >= 100_000_000 -> "%.1f亿".format(v / 100_000_000.0)
    v >= 10_000 -> "%.1f万".format(v / 10_000.0)
    else -> v.toString()
}

@Composable
private fun CoverSection(state: PlayerUiState) {
    val song = state.song ?: return
    // 满幅封面：底部控制板才能采样到真实封面像素，形成真正的玻璃层
    AsyncImage(
        model = state.coverUrl.ifBlank { song.pic }.ifBlank { null },
        contentDescription = "专辑封面",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** 歌词区：逐行高亮 + 自动滚动；用户手动拖动后暂停跟随，3 秒后自动回位跟随 */
@Composable
private fun LyricsSection(playerVm: PlayerViewModel, bottomInset: Dp = 0.dp) {
    val state by playerVm.state.collectAsState()
    val lines = state.lyricLines
    val listState = rememberLazyListState()

    var userScrolling by remember { mutableStateOf(false) }
    var lastUserScrollAt by remember { mutableStateOf(0L) }

    val currentLine by remember(lines) {
        derivedStateOf { if (lines.all { it.timeMs < 0 }) -1 else LrcParser.currentIndex(lines, state.position) }
    }

    // 当前行变化 → 自动滚动到屏幕中间
    LaunchedEffect(currentLine, lines) {
        if (currentLine >= 0 && !userScrolling) {
            listState.animateScrollToItem(currentLine)
        }
    }

    // 监听用户拖动；拖动结束也算一次手动操作，重新计时回位
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    userScrolling = true
                    lastUserScrollAt = System.currentTimeMillis()
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    lastUserScrollAt = System.currentTimeMillis()
                }
            }
        }
    }

    // 手动滚动 3 秒无操作后恢复自动跟随（对应 Solara 歌词视图回位逻辑）
    LaunchedEffect(Unit) {
        while (true) {
            if (userScrolling && System.currentTimeMillis() - lastUserScrollAt > 3000) {
                userScrolling = false
            }
            delay(500)
        }
    }

    when {
        state.lyricLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = SodaRed)
        }
        lines.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无歌词", color = Color.White.copy(alpha = 0.72f))
        }
        else -> LazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomInset + 24.dp),
        ) {
            item { Spacer(Modifier.height(60.dp)) }
            itemsIndexed(lines, key = { i, _ -> i }) { i, line ->
                val active = i == currentLine
                Text(
                    line.text,
                    fontSize = if (active) 18.sp else 15.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) SodaRed else Color.White.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // QQ 音乐式点行跳播：仅可点击有有效时间戳的行
                            if (line.timeMs >= 0) {
                                userScrolling = false
                                playerVm.seekTo(line.timeMs)
                            }
                        }
                        .padding(vertical = 10.dp, horizontal = 8.dp)
                        .animateItem(),
                )
            }
            item { Spacer(Modifier.height(200.dp)) }
        }
    }
}

/** 音质切换菜单：128/192/320/FLAC，切换即重载当前歌 */
@Composable
private fun QualityMenu(current: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { expanded = true }
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                qualityOption(current).let { if (it.value == "999") "FLAC" else "${it.value}K" },
                fontSize = 12.sp,
                color = SodaRed,
            )
            Icon(Icons.Filled.ExpandMore, contentDescription = "切换音质", tint = SodaRed, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Constants.QUALITY_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.label, color = if (option.value == current) SodaRed else Color.Unspecified)
                            Text(option.description, fontSize = 11.sp, color = Color.White.copy(alpha = 0.72f))
                        }
                    },
                    onClick = {
                        expanded = false
                        if (option.value != current) onSelect(option.value)
                    },
                )
            }
        }
    }
}









