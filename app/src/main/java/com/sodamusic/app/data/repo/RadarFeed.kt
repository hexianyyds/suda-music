package com.sodamusic.app.data.repo

import com.sodamusic.app.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

/** 全局共享的雷达信息流（首页竖滑数据源，“我的”页刷新雷达后首页同步更新） */
object RadarFeed {
    val feed = MutableStateFlow<List<Song>>(emptyList())
    val refreshing = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    /** 刷新成功后置位：首页把展示队列对齐为新榜单第一首（仅一次） */
    val adoptPending = MutableStateFlow(false)

    suspend fun refresh(repository: MusicRepository) {
        refreshing.value = true
        error.value = null
        try {
            feed.value = repository.radarInject()
            adoptPending.value = true
        } catch (e: CancellationException) {
            // 页面离开导致的正常取消，不算错误
            throw e
        } catch (e: Exception) {
            error.value = e.message ?: "雷达刷新失败"
        } finally {
            refreshing.value = false
        }
    }

    /** 滑到末尾时追加一批新歌（去重，失败静默保留现有内容） */
    suspend fun append(repository: MusicRepository) {
        if (refreshing.value) return
        refreshing.value = true
        try {
            val more = repository.radarInject(extraExclude = feed.value)
            val fresh = more.filter { m -> feed.value.none { it.source == m.source && it.id == m.id } }
            if (fresh.isNotEmpty()) feed.value = feed.value + fresh
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        } finally {
            refreshing.value = false
        }
    }
}
