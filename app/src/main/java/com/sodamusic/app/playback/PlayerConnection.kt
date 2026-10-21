package com.sodamusic.app.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.sodamusic.app.data.model.PlayMode
import com.sodamusic.app.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.Executor

/**
 * UI 侧播放控制统一入口：封装 MediaController，向 ViewModel 暴露状态流与控制方法。
 * 队列/指针的真实状态在 PlaybackBus（与服务同进程共享）。
 */
class PlayerConnection(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var controllerRef: MediaController? = null
    val ready = MutableStateFlow(false)

    val isPlaying = MutableStateFlow(false)
    val currentSong = MutableStateFlow<Song?>(null)
    val duration = MutableStateFlow(0L)

    /** 每 500ms 刷新一次的播放进度 */
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controllerRef = c
                attach(c)
                ready.value = true
            } catch (e: Exception) {
                PlaybackBus.error.value = "播放服务连接失败：${e.message}"
            }
        }, Executor { it.run() })

        // 服务异步恢复持久化队列后，同步当前歌曲到 UI
        scope.launch {
            combine(PlaybackBus.queue, PlaybackBus.index) { q, i -> q.getOrNull(i) }
                .collect { song ->
                    // 控制器就绪前也允许展示（如恢复场景）；就绪后以总线为准
                    currentSong.value = song
                }
        }

        scope.launch {
            while (true) {
                controllerRef?.let { c ->
                    if (c.isPlaying) _position.value = c.currentPosition.coerceAtLeast(0)
                }
                delay(500)
            }
        }
    }

    private fun attach(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying.value = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    duration.value = controller.duration.coerceAtLeast(0)
                }
            }
        })
    }

    fun setQueueAndPlay(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        val payload = json.encodeToString(QueuePayload(songs, startIndex))
        send(SessionCommand(PlaybackBus.CMD_SET_QUEUE, Bundle.EMPTY)) {
            it.sendCustomCommand(SessionCommand(PlaybackBus.CMD_SET_QUEUE, Bundle.EMPTY), bundleOf("payload" to payload))
        }
    }

    fun playAt(index: Int) {
        send(SessionCommand(PlaybackBus.CMD_PLAY_AT, Bundle.EMPTY)) {
            it.sendCustomCommand(SessionCommand(PlaybackBus.CMD_PLAY_AT, Bundle.EMPTY), bundleOf("index" to index))
        }
    }

    fun togglePlayPause() {
        controllerRef?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    /** 滑动切歌时立即暂停当前歌（不等新歌解析完成） */
    fun pauseForSwitch() {
        // 标记为切歌暂停：新歌解析失败时需要恢复播放上一首
        PlaybackBus.switching.value = true
        controllerRef?.let { if (it.isPlaying) it.pause() }
    }

    fun next() {
        send(SessionCommand(PlaybackBus.CMD_NEXT, Bundle.EMPTY)) {
            it.sendCustomCommand(SessionCommand(PlaybackBus.CMD_NEXT, Bundle.EMPTY), Bundle.EMPTY)
        }
    }

    fun previous() {
        send(SessionCommand(PlaybackBus.CMD_PREVIOUS, Bundle.EMPTY)) {
            it.sendCustomCommand(SessionCommand(PlaybackBus.CMD_PREVIOUS, Bundle.EMPTY), Bundle.EMPTY)
        }
    }

    fun seekTo(positionMs: Long) {
        controllerRef?.seekTo(positionMs)
        _position.value = positionMs
    }

    fun setMode(mode: PlayMode) {
        PlaybackBus.mode.value = mode
        send(SessionCommand(PlaybackBus.CMD_SET_MODE, Bundle.EMPTY)) {
            it.sendCustomCommand(SessionCommand(PlaybackBus.CMD_SET_MODE, Bundle.EMPTY), bundleOf("mode" to mode.name))
        }
    }

    /** 清空列表并停止播放（用于“清空队列”等操作） */
    fun stopPlayback() {
        controllerRef?.let {
            it.stop()
            it.clearMediaItems()
        }
    }

    private fun send(command: SessionCommand, action: (MediaController) -> Unit) {
        val c = controllerRef ?: return
        c.availableSessionCommands.commands.firstOrNull { it.customAction == command.customAction } ?: return
        action(c)
    }

    private fun bundleOf(vararg pairs: Pair<String, Any?>): Bundle {
        val b = Bundle()
        pairs.forEach { (k, v) ->
            when (v) {
                is String -> b.putString(k, v)
                is Int -> b.putInt(k, v)
            }
        }
        return b
    }
}
