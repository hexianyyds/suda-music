package com.sodamusic.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sodamusic.app.ServiceLocator
import com.sodamusic.app.data.model.Constants
import com.sodamusic.app.data.model.Song
import com.sodamusic.app.data.repo.FavoritesManager
import com.sodamusic.app.data.repo.MusicRepository
import com.sodamusic.app.data.repo.RadarFeed
import com.sodamusic.app.data.store.AppSettings
import com.sodamusic.app.data.store.LibraryStore
import com.sodamusic.app.data.store.SettingsStore
import com.sodamusic.app.playback.PlaybackBus
import com.sodamusic.app.playback.QueueEditor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val store: LibraryStore,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val queue = PlaybackBus.queue
    val currentIndex = PlaybackBus.index
    val favorites = FavoritesManager.favorites
    val history = store.history
    val settings = kotlinx.coroutines.flow.MutableStateFlow(AppSettings())
    val usingFallback = ServiceLocator.repository.usingFallback

    init {
        viewModelScope.launch { settingsStore.settings.collect { settings.value = it } }
    }

    fun playQueueAt(index: Int) = ServiceLocator.playerConnection.playAt(index)

    fun removeQueueAt(index: Int) = viewModelScope.launch { QueueEditor.removeAt(store, index) }

    fun clearQueue() = viewModelScope.launch {
        QueueEditor.stopPlaybackAndClear(ServiceLocator.playerConnection, store)
    }

    fun clearHistory() = viewModelScope.launch { store.clearHistory() }

    fun toggleFavorite(song: Song) = viewModelScope.launch { FavoritesManager.toggle(song) }

    fun addAllFavoritesToQueue() = viewModelScope.launch {
        QueueEditor.append(store, FavoritesManager.favorites.value)
    }

    fun toggleRadar(id: String) = viewModelScope.launch {
        val current = settings.value.radarIds.toMutableSet()
        if (!current.remove(id)) current.add(id)
        settingsStore.setRadarIds(current)
    }

    fun refreshRadar(onDone: (String?) -> Unit) = viewModelScope.launch {
        RadarFeed.refresh(ServiceLocator.repository)
        onDone(RadarFeed.error.value)
    }

    fun setQuality(quality: String) = viewModelScope.launch { settingsStore.setQuality(quality) }

    /** 默认音乐源（网易云 / 酷我 / JOOX / B站 / QQ音乐） */
    fun setSource(source: String) = viewModelScope.launch { settingsStore.setSource(source) }

    /** 主题模式：system / light / dark */
    fun setThemeMode(mode: String) = viewModelScope.launch { settingsStore.setThemeMode(mode) }

    /** 桌面歌词颜色 */
    fun setLyricColor(color: Long) = viewModelScope.launch { settingsStore.setLyricColor(color) }

    fun setApiBase(value: String) = viewModelScope.launch {
        val v = value.trim().ifBlank { Constants.DEFAULT_API_BASE }
        settingsStore.setApiBase(v)
        ServiceLocator.api.baseUrl = v
        MusicRepository.clearPrefetchedUrls()
    }

    /**
     * 切换内置音源：立即生效。
     * 若当前正在播放，则停止并用新音源重新解析、重新播放当前这一首（跨平台曲目会自动匹配）。
     */
    fun setDataSource(value: String) = viewModelScope.launch {
        settingsStore.setDataSource(value)
        MusicRepository.clearPrefetchedUrls()
        val index = PlaybackBus.index.value
        if (index >= 0 && PlaybackBus.queue.value.isNotEmpty() && PlaybackBus.queue.value.size > index) {
            ServiceLocator.playerConnection.playAt(index)
        }
    }

    fun setAutoFallback(value: Boolean) = viewModelScope.launch { settingsStore.setAutoFallback(value) }

    fun setNeteaseCookie(value: String) = viewModelScope.launch { settingsStore.setNeteaseCookie(value) }

    fun exportJson(onReady: (String) -> Unit) = viewModelScope.launch { onReady(store.exportLibrary()) }

    fun importJson(text: String, onDone: (String) -> Unit) = viewModelScope.launch {
        val result = store.importLibrary(text)
        if (result == null) {
            onDone("导入失败：文件格式不正确")
        } else {
            PlaybackBus.queue.value = store.queue.first()
            PlaybackBus.index.value = store.currentIndex.first().coerceAtMost(PlaybackBus.queue.value.size - 1)
            onDone("导入成功：队列 ${result.first} 首，收藏 ${result.second} 首")
        }
    }
}

