package com.sodamusic.app.playback

import com.sodamusic.app.data.model.PlayMode
import com.sodamusic.app.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 进程内共享的播放队列状态（单一事实来源）。
 * UI 与 PlaybackService 同进程，直接读写此对象，避免跨进程同步队列数据。
 */
/** 跳转信号：seq 自增保证不丢，index 为目标曲目下标 */
data class RollbackSignal(val seq: Long, val index: Int)

object PlaybackBus {

    val queue = MutableStateFlow<List<Song>>(emptyList())
    val index = MutableStateFlow(-1)
    val mode = MutableStateFlow(PlayMode.LIST_LOOP)

    /** 服务侧透出的播放错误（获取直链失败等），UI 观察后弹提示并清空 */
    val error = MutableStateFlow<String?>(null)

    /** 服务加载中标记（解析直链期间置位，UI 显示加载态） */
    val loading = MutableStateFlow(false)

    /**
     * 是否正在"切歌暂停"（竖滑切歌时会先暂停当前曲）。
     * 用于区分"切歌临时暂停"与"用户主动暂停"，前者失败后要恢复播放。
     */
    val switching = MutableStateFlow(false)

    /**
     * 回滚/跳转信号：切歌取链失败时（多为 VIP / 无版权），服务端把要跳到的曲目下标写进来。
     * 带 seq 是为了保证每次都能发射——StateFlow 会去重，值没变就收不到。
     */
    val rollback = MutableStateFlow(RollbackSignal(0, -1))

    /** 当前歌曲歌词（由 UI 侧解析后写入，供桌面歌词悬浮窗读取） */
    val lyricLines = MutableStateFlow<List<com.sodamusic.app.util.LyricLine>>(emptyList())

    val currentSong: Song?
        get() = queue.value.getOrNull(index.value)

    // 与 PlaybackService 约定的自定义 SessionCommand
    const val CMD_SET_QUEUE = "soda.SET_QUEUE"
    const val CMD_PLAY_AT = "soda.PLAY_AT"
    const val CMD_NEXT = "soda.NEXT"
    const val CMD_PREVIOUS = "soda.PREVIOUS"
    const val CMD_SET_MODE = "soda.SET_MODE"

    fun consumeError(): String? {
        val e = error.value
        if (e != null) error.value = null
        return e
    }
}
