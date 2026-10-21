package com.sodamusic.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sodamusic.app.data.model.Constants
import com.sodamusic.app.data.model.Song
import com.sodamusic.app.data.repo.MusicException
import com.sodamusic.app.data.repo.MusicRepository
import com.sodamusic.app.data.store.LibraryStore
import com.sodamusic.app.data.store.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 搜索引擎：防抖由 UI 触发节奏控制，分页加载；搜索状态与历史持久化。
 * 切换音源不自动触发搜索（与 Solara 行为一致）。
 */
class SearchViewModel(
    private val repository: MusicRepository,
    private val libraryStore: LibraryStore,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val query = MutableStateFlow("")
    val source = MutableStateFlow("netease")
    val results = MutableStateFlow<List<Song>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val history = MutableStateFlow<List<String>>(emptyList())
    val hasMore = MutableStateFlow(false)
    val restored = MutableStateFlow(false)

    private var page = 1
    private var lastQuery = ""

    init {
        viewModelScope.launch {
            // 不再恢复上次的搜索词与结果（进入就是干净状态）
            history.value = libraryStore.searchHistory.first()
            restored.value = true
        }
        // 音乐源统一取设置里的「音乐源」，设置变更实时生效
        viewModelScope.launch {
            settingsStore.settings.collect { s -> source.value = s.source }
        }
    }

    /** 清空当前搜索状态（进入搜索页时调用） */
    fun clearSession() {
        query.value = ""
        results.value = emptyList()
        error.value = null
        hasMore.value = false
        page = 1
        lastQuery = ""
    }

    fun setQuery(value: String) {
        query.value = value
    }

    /** 切换音源：只记住选择，不自动清空/刷新结果 */
    fun setSource(value: String) {
        source.value = value
        viewModelScope.launch { settingsStore.setSource(value) }
    }

    fun search() {
        viewModelScope.launch { doSearch(reset = true) }
    }

    fun loadMore() {
        if (loading.value || !hasMore.value || query.value.isBlank()) return
        viewModelScope.launch { doSearch(reset = false) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            libraryStore.clearSearchHistory()
            history.value = emptyList()
        }
    }

    private suspend fun doSearch(reset: Boolean) {
        val keyword = query.value.trim()
        if (keyword.isEmpty()) return
        loading.value = true
        error.value = null
        try {
            val targetPage = if (reset) 1 else page
            if (reset) results.value = emptyList()
            val list = repository.search(keyword, source.value, page = targetPage)
            results.value = if (targetPage == 1) list else results.value + list
            hasMore.value = list.size >= Constants.PAGE_SIZE
            page = targetPage + 1
            if (reset) {
                lastQuery = keyword
                libraryStore.addSearchHistory(keyword)
                libraryStore.saveSearchState(keyword, source.value)
                history.value = libraryStore.searchHistory.first()
            }
        } catch (e: MusicException) {
            error.value = e.message
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error.value = "搜索失败：${e.message}"
        } finally {
            loading.value = false
        }
    }
}
