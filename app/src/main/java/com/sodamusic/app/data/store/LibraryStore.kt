package com.sodamusic.app.data.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sodamusic.app.data.model.PlayMode
import com.sodamusic.app.data.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.libraryDataStore by preferencesDataStore(name = "soda_library")

/** 导入/导出用的 JSON 结构（对应 Solara 列表导入导出） */
@Serializable
data class LibraryExport(
    val version: Int = 1,
    val queue: List<Song> = emptyList(),
    val favorites: List<Song> = emptyList(),
)

@Serializable
data class LastSearchState(
    val keyword: String = "",
    val source: String = "netease",
)

/** 播放队列 / 收藏 / 搜索历史的 JSON 持久化（对应 Solara localStorage） */
class LibraryStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val songListSerializer = ListSerializer(Song.serializer())

    private object Keys {
        val QUEUE = stringPreferencesKey("playlistSongs")
        val INDEX = stringPreferencesKey("currentTrackIndex")
        val MODE = stringPreferencesKey("playMode")
        val FAVORITES = stringPreferencesKey("favoriteSongs")
        val HISTORY = stringPreferencesKey("playHistory.v1")
        val SEARCH_HISTORY = stringPreferencesKey("searchHistory.v1")
        val LAST_SEARCH = stringPreferencesKey("lastSearchState.v1")
    }

    val queue: Flow<List<Song>> = context.libraryDataStore.data.map { p ->
        p[Keys.QUEUE]?.let { runCatching { json.decodeFromString(songListSerializer, it) }.getOrNull() } ?: emptyList()
    }

    val favorites: Flow<List<Song>> = context.libraryDataStore.data.map { p ->
        p[Keys.FAVORITES]?.let { runCatching { json.decodeFromString(songListSerializer, it) }.getOrNull() } ?: emptyList()
    }

    val searchHistory: Flow<List<String>> = context.libraryDataStore.data.map { p ->
        p[Keys.SEARCH_HISTORY]?.let { runCatching { json.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull() } ?: emptyList()
    }

    val lastSearch: Flow<LastSearchState> = context.libraryDataStore.data.map { p ->
        p[Keys.LAST_SEARCH]?.let { runCatching { json.decodeFromString<LastSearchState>(it) }.getOrNull() } ?: LastSearchState()
    }

    val currentIndex: Flow<Int> = context.libraryDataStore.data.map { p ->
        p[Keys.INDEX]?.toIntOrNull() ?: -1
    }

    val playMode: Flow<PlayMode> = context.libraryDataStore.data.map { p ->
        runCatching { PlayMode.valueOf(p[Keys.MODE] ?: PlayMode.LIST_LOOP.name) }.getOrDefault(PlayMode.LIST_LOOP)
    }

    /** 听歌历史：最近播放在前，去重上限 200 */
    val history: Flow<List<Song>> = context.libraryDataStore.data.map { p ->
        p[Keys.HISTORY]?.let { runCatching { json.decodeFromString(songListSerializer, it) }.getOrNull() } ?: emptyList()
    }

    suspend fun recordHistory(song: Song) {
        val old = history.first().toMutableList()
        old.removeAll { it.source == song.source && it.id == song.id }
        old.add(0, song)
        val trimmed = old.take(200)
        context.libraryDataStore.edit { it[Keys.HISTORY] = json.encodeToString(songListSerializer, trimmed) }
    }

    suspend fun clearHistory() {
        context.libraryDataStore.edit { it.remove(Keys.HISTORY) }
    }

    suspend fun saveQueue(songs: List<Song>, index: Int) {
        context.libraryDataStore.edit {
            it[Keys.QUEUE] = json.encodeToString(songListSerializer, songs)
            it[Keys.INDEX] = index.toString()
        }
    }

    suspend fun saveFavorites(songs: List<Song>) {
        context.libraryDataStore.edit { it[Keys.FAVORITES] = json.encodeToString(songListSerializer, songs) }
    }

    suspend fun savePlayMode(mode: PlayMode) {
        context.libraryDataStore.edit { it[Keys.MODE] = mode.name }
    }

    suspend fun saveSearchState(keyword: String, source: String) {
        context.libraryDataStore.edit { it[Keys.LAST_SEARCH] = json.encodeToString(LastSearchState(keyword, source)) }
    }

    suspend fun addSearchHistory(keyword: String) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        val old = searchHistory.first().toMutableList()
        old.removeAll { it.equals(kw, ignoreCase = true) }
        old.add(0, kw)
        val trimmed = old.take(10)
        context.libraryDataStore.edit { it[Keys.SEARCH_HISTORY] = json.encodeToString(ListSerializer(String.serializer()), trimmed) }
    }

    suspend fun clearSearchHistory() {
        context.libraryDataStore.edit { it.remove(Keys.SEARCH_HISTORY) }
    }

    suspend fun exportLibrary(): String {
        val p = context.libraryDataStore.data.first()
        val export = LibraryExport(
            queue = p[Keys.QUEUE]?.let { runCatching { json.decodeFromString(songListSerializer, it) }.getOrNull() } ?: emptyList(),
            favorites = p[Keys.FAVORITES]?.let { runCatching { json.decodeFromString(songListSerializer, it) }.getOrNull() } ?: emptyList(),
        )
        return json.encodeToString(LibraryExport.serializer(), export)
    }

    /** 导入 JSON；返回导入的队列与收藏数量，失败返回 null */
    suspend fun importLibrary(text: String): Pair<Int, Int>? {
        val export = runCatching { json.decodeFromString<LibraryExport>(text) }.getOrNull() ?: return null
        val restored = export.copy(
            queue = export.queue.filter { it.id.isNotBlank() && it.name.isNotBlank() },
            favorites = export.favorites.filter { it.id.isNotBlank() && it.name.isNotBlank() },
        )
        context.libraryDataStore.edit {
            it[Keys.QUEUE] = json.encodeToString(songListSerializer, restored.queue)
            it[Keys.INDEX] = "0"
            it[Keys.FAVORITES] = json.encodeToString(songListSerializer, restored.favorites)
        }
        return restored.queue.size to restored.favorites.size
    }
}
