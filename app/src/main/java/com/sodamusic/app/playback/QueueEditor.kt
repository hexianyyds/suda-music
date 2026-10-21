package com.sodamusic.app.playback

import com.sodamusic.app.data.model.Song
import com.sodamusic.app.data.store.LibraryStore

/**
 * 队列编辑工具：追加、定点删除（指针重定位，防幽灵播放）、清空。
 * 直接操作 PlaybackBus 并持久化，对应 Solara playlist.js 的队列维护逻辑。
 */
object QueueEditor {

    suspend fun append(store: LibraryStore, songs: List<Song>) {
        if (songs.isEmpty()) return
        val queue = PlaybackBus.queue.value.toMutableList()
        val currentKey = PlaybackBus.currentSong?.let { "${it.source}:${it.id}" }
        songs.forEach { song ->
            val key = "${song.source}:${song.id}"
            if (queue.none { "${it.source}:${it.id}" == key }) queue.add(song)
        }
        val newIndex = queue.indexOfFirst { "${it.source}:${it.id}" == currentKey }
        PlaybackBus.queue.value = queue
        PlaybackBus.index.value = newIndex
        store.saveQueue(queue, newIndex)
    }

    suspend fun removeAt(store: LibraryStore, index: Int) {
        val queue = PlaybackBus.queue.value.toMutableList()
        if (index !in queue.indices) return
        val current = PlaybackBus.index.value
        queue.removeAt(index)
        // 删歌指针重定位：删除当前曲之前的歌时指针前移；删除当前曲时指针保持指向同一位
        val newIndex = when {
            queue.isEmpty() -> -1
            index < current -> current - 1
            index == current -> current.coerceAtMost(queue.size - 1)
            else -> current
        }
        PlaybackBus.queue.value = queue
        PlaybackBus.index.value = newIndex
        store.saveQueue(queue, newIndex)
    }

    suspend fun clear(store: LibraryStore) {
        PlaybackBus.queue.value = emptyList()
        PlaybackBus.index.value = -1
        store.saveQueue(emptyList(), -1)
    }

    suspend fun stopPlaybackAndClear(connection: com.sodamusic.app.playback.PlayerConnection, store: LibraryStore) {
        connection.stopPlayback()
        clear(store)
    }
}
