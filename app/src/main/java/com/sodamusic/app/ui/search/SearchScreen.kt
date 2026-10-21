package com.sodamusic.app.ui.search

import android.widget.Toast
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.HazeInput
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sodamusic.app.ServiceLocator
import com.sodamusic.app.data.model.Constants
import com.sodamusic.app.ui.discover.QqDiscoverScreen
import com.sodamusic.app.data.repo.FavoritesManager
import com.sodamusic.app.playback.PlaybackBus
import com.sodamusic.app.playback.QueueEditor
import com.sodamusic.app.ui.components.LocalBottomBarHeight
import com.sodamusic.app.ui.components.SongListItem
import com.sodamusic.app.ui.player.PlayerViewModel
import com.sodamusic.app.ui.theme.SodaRed
import kotlinx.coroutines.launch

/** 搜索页：关键词 + 音源切换 + 结果列表（播放/加队列/收藏）+ 分页 + 历史记忆 */
@Composable
fun SearchScreen(playerVm: PlayerViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: SearchViewModel = viewModel {
        SearchViewModel(ServiceLocator.repository, ServiceLocator.libraryStore, ServiceLocator.settingsStore)
    }
    val query by vm.query.collectAsState()
    val source by vm.source.collectAsState()
    val results by vm.results.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val history by vm.history.collectAsState()
    val hasMore by vm.hasMore.collectAsState()
    val favorites by FavoritesManager.favorites.collectAsState()
    val playerState by playerVm.state.collectAsState()
    var showDiscover by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) vm.loadMore()
    }

    fun playFromResults(index: Int) {
        val list = results
        if (index !in list.indices) return
        playerVm.setQueueAndPlay(list, index)
    }

    val hazeState = rememberHazeState()
    val glassStyle = remember {
        GlassStyle.clear.then {
            shape(RoundedCornerShape(26.dp))
            tint(Color(0xFF9AA0A6).copy(alpha = 0.05f))
            edgeShadow(Color.Black.copy(alpha = 0.22f))
            edgeSoftness(1.dp)
        }
    }

    Box(Modifier.fillMaxSize()) {
    // 采样源：页面背景，给液态玻璃提供可折射的色彩层次
    Box(
        Modifier
            .fillMaxSize()
            .hazeSource(hazeState)
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color(0xFF2A1220), Color(0xFF12101A), Color(0xFF0B1118)),
                    ),
                )
            },
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // 搜索栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 悬浮液态玻璃搜索框
            Box(
                modifier = Modifier
                    .weight(1f)
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(26.dp),
                        ambientColor = Color.Black.copy(alpha = 0.30f),
                        spotColor = Color.Black.copy(alpha = 0.40f),
                    )
                    .hazeGlass(input = HazeInput.Sources(hazeState), style = glassStyle),
            ) {
                TextField(
                    value = query,
                    onValueChange = vm::setQuery,
                    placeholder = { Text("搜索歌曲 / 歌手", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    shape = RoundedCornerShape(26.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = SodaRed,
                    ),
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TextButton(onClick = { vm.search() }, enabled = query.isNotBlank()) { Text("搜索", color = SodaRed) }
        }

        when {
            results.isEmpty() && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SodaRed)
            }
            results.isEmpty() && error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { vm.search() }) { Text("重试") }
                }
            }
            results.isEmpty() -> SearchHistory(
                history = history,
                onPick = { vm.setQuery(it); vm.search() },
                onClear = vm::clearHistory,
            )
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = LocalBottomBarHeight.current + 12.dp),
            ) {
                itemsIndexed(results, key = { index, song -> "$index-${song.source}-${song.id}" }) { index, song ->
                    val current = playerState.song
                    SongListItem(
                        song = song,
                        isPlaying = current != null && current.source == song.source && current.id == song.id,
                        isFavorite = FavoritesManager.isFavorite(song, favorites),
                        onClickPlay = { playFromResults(index) },
                        onToggleFavorite = { scope.launch { FavoritesManager.toggle(song) } },
                        onAddToQueue = {
                            scope.launch {
                                QueueEditor.append(ServiceLocator.libraryStore, listOf(song))
                                Toast.makeText(context, "已加入播放队列", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
                if (loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = SodaRed)
                        }
                    }
                }
                if (!loading && !hasMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("— 没有更多了 —", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (results.isNotEmpty() && error != null) {
            Text(
                error ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }

    }
}

@Composable
private fun SearchHistory(history: List<String>, onPick: (String) -> Unit, onClear: () -> Unit) {
    if (history.isEmpty()) return
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("搜索历史", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, contentDescription = "清空历史", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(history) { kw ->
                Text(
                    kw,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                        .clickable { onPick(kw) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}








