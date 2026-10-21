package com.sodamusic.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sodamusic.app.ServiceLocator
import com.sodamusic.app.data.model.Song
import com.sodamusic.app.ui.theme.SodaRed

/** 搜索/队列/收藏共用的歌曲行：封面 + 标题/歌手/专辑 + 播放/收藏/加队列 */
@Composable
fun SongListItem(
    song: Song,
    isPlaying: Boolean,
    isFavorite: Boolean,
    showAlbum: Boolean = true,
    onClickPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToQueue: () -> Unit,
    /** 是否已下载到本地 */
    downloaded: Boolean = false,
    /** 下载进度（0..100），null 表示未在下载 */
    downloadProgress: Int? = null,
    /** 提供则在右侧显示下载按钮 */
    onToggleDownload: (() -> Unit)? = null,
) {
    var picUrl by remember(song.id) { mutableStateOf(song.pic) }
    LaunchedEffect(song) {
        if (picUrl.isBlank()) picUrl = ServiceLocator.repository.resolvePic(song)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClickPlay)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = picUrl.ifBlank { null },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                song.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isPlaying) SodaRed else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                buildString {
                    append(song.artist)
                    if (showAlbum && song.album.isNotBlank()) append(" · ${song.album}")
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onAddToQueue) {
            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "加入播放队列", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onToggleDownload != null) {
            IconButton(onClick = onToggleDownload) {
                if (downloadProgress != null) {
                    // 下载中：显示环形进度
                    androidx.compose.material3.CircularProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.size(18.dp),
                        color = SodaRed,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        if (downloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                        contentDescription = if (downloaded) "删除下载" else "下载",
                        tint = if (downloaded) SodaRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (isFavorite) "取消收藏" else "收藏",
                tint = if (isFavorite) SodaRed else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

