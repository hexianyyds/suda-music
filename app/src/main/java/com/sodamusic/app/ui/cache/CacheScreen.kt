package com.sodamusic.app.ui.cache

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sodamusic.app.ServiceLocator
import com.sodamusic.app.data.model.Song
import com.sodamusic.app.data.repo.FavoritesManager
import com.sodamusic.app.data.store.CachedSong
import com.sodamusic.app.data.store.LocalCacheStore
import com.sodamusic.app.playback.QueueEditor
import com.sodamusic.app.ui.components.LocalBottomBarHeight
import com.sodamusic.app.ui.components.SongListItem
import com.sodamusic.app.ui.player.PlayerViewModel
import kotlinx.coroutines.launch

/** 缓存页：听过的歌自动收纳于此，启动默认打开本页并自动接着播缓存歌曲 */
@Composable
fun CacheScreen(playerVm: PlayerViewModel) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val cached by LocalCacheStore.cached.collectAsState()
    val favorites by FavoritesManager.favorites.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("缓存音乐", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "听过的歌自动缓存，共 ${cached.size} 首",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (cached.isNotEmpty()) {
                TextButton(onClick = {
                    scope.launch {
                        LocalCacheStore.clear(context)
                        Toast.makeText(context, "缓存已清空", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.width(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("清空", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (cached.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "还没有缓存的歌曲\n播放过的歌会自动出现在这里",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomBarHeight.current + 12.dp)) {
                itemsIndexed(cached, key = { i, c -> "c$i-${c.song.source}-${c.song.id}" }) { index, item ->
                    SongListItem(
                        song = item.song,
                        isPlaying = false,
                        isFavorite = FavoritesManager.isFavorite(item.song, favorites),
                        onClickPlay = { playerVm.setQueueAndPlay(cached.map { it.song }, index) },
                        onToggleFavorite = { scope.launch { FavoritesManager.toggle(item.song) } },
                        onAddToQueue = { scope.launch { QueueEditor.append(ServiceLocator.libraryStore, listOf(item.song)) } },
                    )
                }
            }
        }
    }
}
