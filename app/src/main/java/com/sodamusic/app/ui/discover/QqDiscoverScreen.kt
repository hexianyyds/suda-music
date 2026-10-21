package com.sodamusic.app.ui.discover

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sodamusic.app.data.api.QqMusicApi
import com.sodamusic.app.data.api.QqSinger
import com.sodamusic.app.data.api.QqPlaylist
import com.sodamusic.app.data.api.QqTopList
import com.sodamusic.app.data.model.Song
import com.sodamusic.app.ui.player.PlayerViewModel
import com.sodamusic.app.ui.theme.SodaRed
import kotlinx.coroutines.launch

/**
 * QQ 音乐发现页：热词 / 歌单广场 / 榜单。
 * 全部直连 QQ 官方接口（QqMusicApi），不经过中间服务。
 */
@Composable
fun QqDiscoverScreen(
    playerVm: PlayerViewModel,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var hot by remember { mutableStateOf<List<String>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<QqPlaylist>>(emptyList()) }
    var tops by remember { mutableStateOf<List<QqTopList>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    // 歌手 / 专辑二级页
    var singers by remember { mutableStateOf<List<QqSinger>>(emptyList()) }
    var singerView by remember { mutableStateOf<QqSinger?>(null) }
    var albumView by remember { mutableStateOf<Pair<String, String>?>(null) }
    // 站内搜索结果（点热词直接在本页展示，不依赖外部导航）
    var searchKeyword by remember { mutableStateOf("") }
    var searchSongs by remember { mutableStateOf<List<Song>?>(null) }

    // 歌单详情
    var sheetTitle by remember { mutableStateOf("") }
    var sheetSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var sheetLoading by remember { mutableStateOf(false) }

    // ── 二级页的"位置"用 rememberSaveable 记住 ──
    // 切到播放页时整个发现页组合会被销毁，返回后若只靠 remember 就会退回首页；
    // 这里保存可序列化的 key，恢复时再重新拉数据。
    var singerKey by rememberSaveable { mutableStateOf<String?>(null) }
    var singerName by rememberSaveable { mutableStateOf<String?>(null) }
    var albumKey by rememberSaveable { mutableStateOf<String?>(null) }
    var albumName by rememberSaveable { mutableStateOf<String?>(null) }
    var searchKey by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetKey by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetName by rememberSaveable { mutableStateOf("") }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(Unit) {
        hot = runCatching { QqMusicApi.hotKeys() }.getOrDefault(emptyList())
        playlists = runCatching { QqMusicApi.songLists(limit = 10) }.getOrDefault(emptyList())
        tops = runCatching { QqMusicApi.topLists() }.getOrDefault(emptyList())
        // 用几个高频歌手名定位 mid（接口按关键词直出歌手）
        singers = HOT_SINGERS.mapNotNull { name ->
            runCatching { QqMusicApi.searchSinger(name) }.getOrNull()
        }
        loading = false
    }

    // 从播放页返回时恢复二级页（按 key 重新取数据）
    LaunchedEffect(singerKey) {
        val nm = singerName ?: return@LaunchedEffect
        if (singerView == null) {
            singerView = runCatching { QqMusicApi.searchSinger(nm) }.getOrNull()
        }
    }
    LaunchedEffect(searchKey) {
        val kw = searchKey ?: return@LaunchedEffect
        if (searchSongs == null) {
            searchSongs = runCatching { QqMusicApi.search(kw, 1, 30) }.getOrDefault(emptyList())
        }
    }
    LaunchedEffect(sheetKey) {
        val id = sheetKey ?: return@LaunchedEffect
        if (sheetSongs.isEmpty()) {
            sheetTitle = sheetName
            sheetLoading = true
            sheetSongs = runCatching { QqMusicApi.songListDetail(id) }.getOrDefault(emptyList())
            sheetLoading = false
        }
    }

    fun doSearch(kw: String) {
        searchKeyword = kw
        searchSongs = emptyList()
        scope.launch {
            searchSongs = runCatching { QqMusicApi.search(kw, 1, 30) }.getOrDefault(emptyList())
        }
    }

    fun openPlaylist(p: QqPlaylist) {
        sheetTitle = p.name
        sheetSongs = emptyList()
        sheetLoading = true
        scope.launch {
            // 详情块在歌单区下方，滚过去让用户直接看到
            runCatching { listState.animateScrollToItem(3) }
            sheetSongs = runCatching { QqMusicApi.songListDetail(p.id) }.getOrDefault(emptyList())
            sheetLoading = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0E0D12))) {
    // 站内搜索结果
    val sr = searchSongs
    if (sr != null) {
        QqSearchResultPage(
            keyword = searchKeyword,
            songs = sr,
            playerVm = playerVm,
            onBack = { searchSongs = null; searchKey = null },
        )
        return@Box
    }

    // 专辑页
    val av = albumView
    if (av != null) {
        QqAlbumPage(
            albumId = av.first,
            albumName = av.second,
            playerVm = playerVm,
            onBack = { albumView = null; albumKey = null; albumName = null },
        )
        return@Box
    }
    // 歌手页
    val sv = singerView
    if (sv != null) {
        QqSingerPage(
            singer = sv,
            playerVm = playerVm,
            onBack = { singerView = null; singerKey = null; singerName = null },
            onOpenAlbum = {
                albumView = it.id to it.name
                albumKey = it.id
                albumName = it.name
            },
        )
        return@Box
    }

    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 120.dp)) {
        // 顶栏
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(19.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.ArrowBackIosNew,
                        "返回",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("QQ 音乐", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("发现 · 歌单 · 榜单", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
                }
            }
        }

        if (loading) {
            item {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SodaRed, modifier = Modifier.size(24.dp))
                }
            }
            return@LazyColumn
        }

        // 热词
        if (hot.isNotEmpty()) {
            item {
                SectionTitle("热门搜索")
                Column(Modifier.padding(horizontal = 16.dp)) {
                    hot.take(12).chunked(3).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEach { w ->
                                Text(
                                    w,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White.copy(alpha = 0.10f))
                                        .clickable { doSearch(w) }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                )
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }

        // 热门歌手
        if (singers.isNotEmpty()) {
            item {
                SectionTitle("热门歌手")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(singers) { sg ->
                        Column(
                            Modifier.width(72.dp).clickable {
                singerView = sg
                singerKey = sg.id
                singerName = sg.name
            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AsyncImage(
                                model = sg.pic.ifBlank { null },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(Color.White.copy(alpha = 0.08f)),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                sg.name,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        // 歌单广场（两列网格，点击直接展开详情，避免横滑容器吞掉点击）
        if (playlists.isNotEmpty()) {
            item {
                SectionTitle("歌单广场")
                Column(Modifier.padding(horizontal = 16.dp)) {
                    playlists.take(6).chunked(2).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            row.forEach { p ->
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { openPlaylist(p) },
                                ) {
                                    AsyncImage(
                                        model = p.cover.ifBlank { null },
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White.copy(alpha = 0.08f)),
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        p.name,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        formatCount(p.listenCount),
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                            repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }

        // 歌单详情（内联展开）
        if (sheetTitle.isNotBlank()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(sheetTitle, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "收起",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        modifier = Modifier.clickable {
                    sheetTitle = ""; sheetSongs = emptyList(); sheetKey = null
                }
                            .padding(6.dp),
                    )
                }
            }
            if (sheetLoading) {
                item {
                    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SodaRed, modifier = Modifier.size(20.dp))
                    }
                }
            } else if (sheetSongs.isEmpty()) {
                item {
                    Text(
                        "该歌单暂无可用曲目（部分歌单需要登录 QQ 音乐）",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    )
                }
            } else {
                itemsIndexed(sheetSongs) { index, s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                playerVm.setQueueAndPlay(sheetSongs, index)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 12.sp,
                            modifier = Modifier.width(24.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.name,
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                s.artist,
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            Icons.Filled.PlayArrow,
                            "播放",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        // 榜单（用 topList 自带的 3 首预览，上游详情接口不可用）
        if (tops.isNotEmpty()) {
            item { SectionTitle("排行榜") }
            items(tops.take(8)) { top ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = top.pic.ifBlank { null },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(top.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "收听 ${formatCount(top.listenCount)}",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }


    }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SodaRed),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 用于定位歌手 mid 的高频歌手名 */
private val HOT_SINGERS = listOf("周杰伦", "林俊杰", "邓紫棋", "薛之谦", "毛不易", "李荣浩", "陈奕迅", "周深")

private fun formatCount(v: Long): String = when {
    v >= 100_000_000 -> "%.1f亿".format(v / 100_000_000.0)
    v >= 10_000 -> "%.1f万".format(v / 10_000.0)
    v > 0 -> v.toString()
    else -> "-"
}




