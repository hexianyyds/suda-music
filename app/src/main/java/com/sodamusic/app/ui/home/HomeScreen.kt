package com.sodamusic.app.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sodamusic.app.ServiceLocator
import com.sodamusic.app.data.repo.FavoritesManager
import com.sodamusic.app.data.repo.RadarFeed
import com.sodamusic.app.playback.PlaybackBus
import com.sodamusic.app.playback.QueueEditor
import com.sodamusic.app.ui.player.PlayerViewModel
import com.sodamusic.app.ui.theme.SodaRed
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * 首页：汽水式全屏竖滑信息流，上下滑即切歌并自动播放，数据来自榜单雷达。
 */
@Composable
fun HomeScreen(playerVm: PlayerViewModel, onOpenPlayer: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val radar by RadarFeed.feed.collectAsState()
    val autoSongs by com.sodamusic.app.data.repo.AutoQueue.songs.collectAsState()
    // 优先展示自动续播队列（网易 + QQ 混合随机 30 首），没有则回落到雷达榜单
    val feed = remember(autoSongs, radar) { if (autoSongs.isNotEmpty()) autoSongs else radar }
    val refreshing by RadarFeed.refreshing.collectAsState()
    val feedError by RadarFeed.error.collectAsState()
    val favorites by FavoritesManager.favorites.collectAsState()
    val playerState by playerVm.state.collectAsState()

    val pagerState = rememberPagerState(pageCount = { feed.size })

    // 首次进入：抽取自动续播队列（网易 + QQ 各一半），失败时回落到雷达
    LaunchedEffect(Unit) {
        if (com.sodamusic.app.data.repo.AutoQueue.songs.value.isEmpty()) {
            scope.launch {
                com.sodamusic.app.data.repo.AutoQueue.fill(30)
                if (com.sodamusic.app.data.repo.AutoQueue.songs.value.isEmpty() &&
                    RadarFeed.feed.value.isEmpty() && !RadarFeed.refreshing.value
                ) {
                    RadarFeed.refresh(ServiceLocator.repository)
                }
            }
        } else if (RadarFeed.feed.value.isEmpty() && !RadarFeed.refreshing.value) {
            RadarFeed.refresh(ServiceLocator.repository)
        }
    }

    // 上下滑切歌：滑到新卡片立即停当前歌并自动播放目标歌；滑到倒数第 3 张时自动追加新歌
    // key 用 feed 列表本身：自动队列补充会让 feed 变化并触发重启，
    // 但用 lastPage 去重后，重启不会重复处理当前页，也不会漏掉新页
    LaunchedEffect(feed) {
        if (feed.isEmpty()) return@LaunchedEffect
        // 用 lastPage 去重，而不是 drop(1)：
        // 本协程必须与 feed.size 解耦，否则自动队列补充导致协程重启时，
        // snapshotFlow 的首次发射会被 drop(1) 丢掉，那一页的切歌请求就永远发不出去
        var lastPage = pagerState.currentPage
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page == lastPage) return@collect
            lastPage = page
            val song = feed.getOrNull(page) ?: return@collect
            val current = PlaybackBus.currentSong
            val isCurrentLoaded = current != null && current.source == song.source && current.id == song.id &&
                playerVm.state.value.duration > 0
            if (isCurrentLoaded) {
                // 滑回正在播/已加载的歌：若被暂停则恢复
                if (!playerVm.state.value.isPlaying) playerVm.togglePlayPause()
            } else {
                playerVm.pauseForSwitch()
                val q = PlaybackBus.queue.value
                // 只比对重叠部分：自动队列补充时两边长度会短暂不一致
                val overlap = minOf(q.size, feed.size)
                val queueIsFeed = overlap > 0 &&
                    q.take(overlap).zip(feed.take(overlap)).all { (a, b) -> a.source == b.source && a.id == b.id }
                if (queueIsFeed) playerVm.playAt(page) else playerVm.setQueueAndPlay(feed, page)
            }
            if (page >= feed.size - 3) {
                // 滑到末尾：再抽一批（网易 + QQ 混合）保证不断供
                scope.launch { com.sodamusic.app.data.repo.AutoQueue.appendMore(30) }
            }
        }
    }

    // 等待用户手势彻底结束后再校正页码。
    // 手势进行中调用滚动会与拖动动画互相打架，Pager 会停在两页（两首歌的封面）之间。
    // 这里用带超时的等待 + 瞬时归位（scrollToPage）：
    //   - scrollToPage 没有动画，不会再与手势/惯性打架
    //   - 超时兜底：即使 Pager 卡住导致 isScrollInProgress 一直为 true，也能强制归位
    suspend fun waitScrollIdle() {
        withTimeoutOrNull(1500) {
            snapshotFlow { pagerState.isScrollInProgress }.first { !it }
        }
    }

    suspend fun settleToIndex(target: Int) {
        if (target !in feed.indices) return
        waitScrollIdle()
        if (target != pagerState.currentPage) {
            pagerState.scrollToPage(target)
        }
    }

    // 切歌失败（VIP / 无版权）时，服务端会回滚到实际在播的那首，
    // 这里把竖滑卡片同步跳回去，避免"滑到的歌"与"控制栏的歌"不一致
    LaunchedEffect(feed) {
        PlaybackBus.rollback.collect { sig ->
            if (sig.seq > 0) settleToIndex(sig.index)
        }
    }

    // 竖滑卡片始终跟随"实际在播的那首"：
    // key 同时包含 currentPage 与 song —— 只监听 song 变化的话，
    // 滑动结束那一刻 song 没变，就再也不会校正，卡片会停在错误的歌上
    LaunchedEffect(pagerState.currentPage, playerState.song, feed.size) {
        if (feed.isEmpty()) return@LaunchedEffect
        waitScrollIdle()
        // 等待期间播放目标可能已变（自动跳过到下一首），这里重新取一次
        val song = playerState.song ?: return@LaunchedEffect
        val idx = feed.indexOfFirst { it.source == song.source && it.id == song.id }
        if (idx >= 0 && idx != pagerState.currentPage) {
            pagerState.scrollToPage(idx)
        }
    }

    // 预取相邻卡片直链：提前把下一首/上一首的播放地址解析好，滑到即播
    LaunchedEffect(pagerState.currentPage, feed.size) {
        if (feed.isEmpty()) return@LaunchedEffect
        val page = pagerState.currentPage
        val targets = buildList {
            feed.getOrNull(page + 1)?.let { add(it) }
            feed.getOrNull(page + 2)?.let { add(it) }
            feed.getOrNull(page - 1)?.let { add(it) }
        }
        if (targets.isNotEmpty()) ServiceLocator.repository.prefetch(targets)
    }

    // QQ 音乐式手动切歌：滑动只浏览卡片，点播放键才播，不随滑动自动播放

    // 雷达刷新成功后（仅一次）：若当前未在播放，把展示队列对齐为新榜单第一首
    val adoptPending by RadarFeed.adoptPending.collectAsState()
    LaunchedEffect(adoptPending) {
        if (!adoptPending) return@LaunchedEffect
        RadarFeed.adoptPending.value = false
        if (!playerVm.state.value.isPlaying) {
            val fresh = RadarFeed.feed.value
            if (fresh.isNotEmpty()) {
                PlaybackBus.queue.value = fresh
                PlaybackBus.index.value = 0
                ServiceLocator.libraryStore.saveQueue(fresh, 0)
            }
        }
    }

    fun playFeedAt(page: Int) {
        val song = feed.getOrNull(page) ?: return
        val current = playerState.song
        if (current != null && current.source == song.source && current.id == song.id &&
            PlaybackBus.index.value == page && playerState.duration > 0
        ) {
            // 已加载好当前卡片这首歌，切换播放/暂停
            playerVm.togglePlayPause()
        } else {
            playerVm.setQueueAndPlay(feed, page)
        }
    }

    fun toggleFav(page: Int) {
        val song = feed.getOrNull(page) ?: return
        scope.launch {
            val nowFav = FavoritesManager.toggle(song)
            Toast.makeText(context, if (nowFav) "已收藏" else "已取消收藏", Toast.LENGTH_SHORT).show()
        }
    }

    fun appendQueue(page: Int) {
        val song = feed.getOrNull(page) ?: return
        scope.launch {
            QueueEditor.append(ServiceLocator.libraryStore, listOf(song))
            Toast.makeText(context, "已加入播放队列", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("汽水雷达", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            // 滑到列表末尾会自动追加新歌，无需手动刷新
            if (refreshing) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = SodaRed)
            }
        }

        when {
            feed.isEmpty() && refreshing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SodaRed)
            }
            feed.isEmpty() && feedError != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(feedError ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { scope.launch { RadarFeed.refresh(ServiceLocator.repository) } }) { Text("重试") }
                }
            }
            feed.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("雷达加载中，请稍候…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { page ->
                FeedPage(
                    coverUrl = playerState.coverUrl.takeIf { isCurrentFeedSong(playerState.song, feed.getOrNull(page)) }
                        ?: feed.getOrNull(page)?.pic.orEmpty(),
                    song = feed[page],
                    isPlaying = playerState.isPlaying && isCurrentFeedSong(playerState.song, feed[page]),
                    isFavorite = FavoritesManager.isFavorite(feed[page], favorites),
                    onOpenPlayer = onOpenPlayer,
                    onTogglePlay = { playFeedAt(page) },
                    onToggleFav = { toggleFav(page) },
                    onAddQueue = { appendQueue(page) },
                )
            }
        }
    }
}

private fun isCurrentFeedSong(current: com.sodamusic.app.data.model.Song?, song: com.sodamusic.app.data.model.Song?): Boolean =
    current != null && song != null && current.source == song.source && current.id == song.id

@Composable
private fun FeedPage(
    coverUrl: String,
    song: com.sodamusic.app.data.model.Song,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onOpenPlayer: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleFav: () -> Unit,
    onAddQueue: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onOpenPlayer),
    ) {
        AsyncImage(
            model = coverUrl.ifBlank { null },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // 底部暗色渐变压住文字，保证可读性
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Black.copy(alpha = 0.15f),
                        1f to Color.Black.copy(alpha = 0.75f),
                    )
                ),
        )
        // 右侧操作列
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 18.dp, bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            IconButton(onClick = onToggleFav) {
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏",
                    tint = if (isFavorite) SodaRed else Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
            IconButton(onClick = onTogglePlay) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            IconButton(onClick = onAddQueue) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "加入播放队列", tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
        // 左下角歌曲信息
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.78f)
                .padding(start = 20.dp, bottom = 110.dp),
        ) {
            Text(
                song.name,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                song.artist,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (song.album.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    song.album,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // 底部留白给迷你条与底栏
        Spacer(Modifier.width(0.dp))
    }
}



