package com.sodamusic.app.data.repo

import com.sodamusic.app.data.model.Song
import com.sodamusic.app.data.store.LibraryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** 全局共享收藏状态：UI 观察此流，切换收藏时写回持久化 */
object FavoritesManager {
    private lateinit var store: LibraryStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val favorites = MutableStateFlow<List<Song>>(emptyList())

    fun init(libraryStore: LibraryStore) {
        store = libraryStore
        scope.launch { store.favorites.collect { favorites.value = it } }
    }

    fun isFavorite(song: Song, list: List<Song> = favorites.value): Boolean =
        list.any { it.source == song.source && it.id == song.id }

    /** 切换收藏状态，返回切换后是否已收藏 */
    suspend fun toggle(song: Song): Boolean {
        val current = favorites.value.toMutableList()
        val existing = current.indexOfFirst { it.source == song.source && it.id == song.id }
        val nowFavorite: Boolean
        if (existing >= 0) {
            current.removeAt(existing)
            nowFavorite = false
        } else {
            current.add(0, song)
            nowFavorite = true
        }
        favorites.value = current
        store.saveFavorites(current)
        return nowFavorite
    }

    suspend fun addRange(songs: List<Song>) {
        val current = favorites.value.toMutableList()
        songs.forEach { song ->
            if (current.none { it.source == song.source && it.id == song.id }) current.add(song)
        }
        favorites.value = current
        store.saveFavorites(current)
    }
}
