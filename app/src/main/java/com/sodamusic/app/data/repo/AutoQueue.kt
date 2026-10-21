package com.sodamusic.app.data.repo

import com.sodamusic.app.ServiceLocator
import com.sodamusic.app.data.api.QqMusicApi
import com.sodamusic.app.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 自动续播队列。
 *
 * App 进入时从「网易源」与「QQ 音乐源」各随机抽取一半，凑够 30 首放进队列；
 * 播放到第 25 首附近时再补 30 首，保证可以一直播下去。
 */
object AutoQueue {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 当前自动队列（乐库展示 + 播放队列） */
    val songs = MutableStateFlow<List<Song>>(emptyList())

    val loading = MutableStateFlow(false)

    /** 触达该索引时补充下一批 */
    private var threshold = 25

    /** 重置（App 冷启动时调用） */
    fun reset() {
        threshold = 25
        songs.value = emptyList()
    }

    /** 自有的网易直连实例（不依赖 ServiceLocator，便于独立可用） */
    private val neteaseApi = com.sodamusic.app.data.api.NeteaseDirectApi()

    /** 从网易源随机抽取曲目：榜单直连优先，搜索兜底 */
    private suspend fun fetchNetease(count: Int): List<Song> {
        val ids = com.sodamusic.app.data.model.Constants.DEFAULT_RADAR_IDS
        for (pid in ids.shuffled().take(3)) {
            val list = runCatching { neteaseApi.toplist(pid, limit = 30) }.getOrNull()
            if (!list.isNullOrEmpty()) return list.shuffled().take(count)
        }
        val keyword = listOf("热歌榜", "新歌", "民谣", "华语热歌", "轻音乐").random()
        return runCatching { neteaseApi.search(keyword, 1, 30) }
            .getOrNull()
            ?.shuffled()
            ?.take(count)
            .orEmpty()
    }

    /** 抽取一批混合歌曲（网易 + QQ），排除已存在的曲目；网络不稳时重试一次 */
    private suspend fun fetch(count: Int, exclude: List<Song>): List<Song> {
        var out = fetchOnce(count, exclude)
        if (out.size < count / 2) {
            // 首次失败（冷启动网络未就绪）时再试一次
            out = fetchOnce(count, exclude)
        }
        return out
    }

    private suspend fun fetchOnce(count: Int, exclude: List<Song>): List<Song> {
        val out = mutableListOf<Song>()
        val half = count / 2

        // 网易源：随机抽取（榜单直连 + 搜索兜底）
        runCatching { fetchNetease(half + 10) }
            .getOrNull()
            ?.shuffled()
            ?.take(half)
            ?.let { out += it }

        // QQ 音乐源：依次尝试多个随机歌单（部分歌单可能取不到曲目）
        runCatching {
            val playlists = QqMusicApi.songLists(limit = 10)
            val picked = mutableListOf<Song>()
            for (pl in playlists.shuffled().take(4)) {
                if (picked.size >= count - half) break
                runCatching { QqMusicApi.songListDetail(pl.id) }.getOrNull()?.let { picked += it }
            }
            picked
        }.getOrNull()
            ?.shuffled()
            ?.take(count - out.size)
            ?.let { out += it }

        // 不足则再用网易补齐
        if (out.size < count) {
            runCatching { fetchNetease(count - out.size + 10) }
                .getOrNull()
                ?.shuffled()
                ?.take(count - out.size)
                ?.let { out += it }
        }

        return out
            .distinctBy { "${it.source}:${it.id}" }
            .filter { s -> exclude.none { it.source == s.source && it.id == s.id } }
            .take(count)
    }

    /** 首次填充（App 进入时调用） */
    suspend fun fill(count: Int = 30): List<Song> {
        if (loading.value) return songs.value
        loading.value = true
        return try {
            val list = fetch(count, emptyList())
            songs.value = list
            // 留 5 首余量：播到 (总数 - 5) 时就补下一批
            threshold = (list.size - 5).coerceAtLeast(20)
            list
        } finally {
            loading.value = false
        }
    }

    /**
     * 播放到指定位置时检查是否需要补充。
     * @return 需要追加到播放队列的新曲目（空列表表示无需补充）
     */
    suspend fun ensureCapacity(currentIndex: Int): List<Song> {
        if (currentIndex < threshold) return emptyList()
        if (loading.value) return emptyList()
        // 只在"当前播放队列确实来自自动队列"时补充，避免污染用户手动搜索的队列
        val current = com.sodamusic.app.playback.PlaybackBus.queue.value
        val fromAuto = current.isNotEmpty() && songs.value.any { s ->
            current.any { it.source == s.source && it.id == s.id }
        }
        if (!fromAuto) return emptyList()
        loading.value = true
        return try {
            val fresh = fetch(30, songs.value)
            if (fresh.isEmpty()) {
                threshold = Int.MAX_VALUE
                emptyList()
            } else {
                songs.value = songs.value + fresh
                threshold = songs.value.size - 5
                fresh
            }
        } finally {
            loading.value = false
        }
    }

    /** 继续追加一批（滑到末尾时调用） */
    suspend fun appendMore(count: Int = 30): List<Song> {
        if (loading.value) return emptyList()
        loading.value = true
        return try {
            val fresh = fetch(count, songs.value)
            if (fresh.isNotEmpty()) {
                songs.value = songs.value + fresh
                threshold = songs.value.size - 5
            }
            fresh
        } finally {
            loading.value = false
        }
    }

    /** 异步补充（供播放推进时调用） */
    fun topUpAsync(currentIndex: Int, onReady: (List<Song>) -> Unit) {
        scope.launch {
            val fresh = ensureCapacity(currentIndex)
            if (fresh.isNotEmpty()) onReady(fresh)
        }
    }
}
