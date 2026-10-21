package com.sodamusic.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sodamusic.app.data.model.PlayMode
import com.sodamusic.app.data.model.Song
import com.sodamusic.app.data.model.qualityOption
import com.sodamusic.app.data.repo.MusicRepository
import com.sodamusic.app.data.store.SettingsStore
import com.sodamusic.app.playback.PlaybackBus
import com.sodamusic.app.playback.PlayerConnection
import com.sodamusic.app.util.LrcParser
import com.sodamusic.app.util.LyricLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PlayerUiState(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val mode: PlayMode = PlayMode.LIST_LOOP,
    val quality: String = "320",
    val showLyrics: Boolean = false,
    val lyricLines: List<LyricLine> = emptyList(),
    val lyricRawCount: Int = 0,
    val lyricLoading: Boolean = false,
    val coverUrl: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

/** 全局共享的播放器 ViewModel（Activity 级），迷你条与全屏播放页共用 */
class PlayerViewModel(
    private val repository: MusicRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val connection: PlayerConnection = com.sodamusic.app.ServiceLocator.playerConnection

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state

    private var lyricSongKey: String? = null
    private var coverSongKey: String? = null

    init {
        // 自动续播：播放位置推进到阈值附近时，再抽 30 首（网易 + QQ 混合）补进队列
        viewModelScope.launch {
            PlaybackBus.index.collect { index ->
                com.sodamusic.app.data.repo.AutoQueue.topUpAsync(index) { fresh ->
                    // 队列由 App 侧维护（每次切歌只 setMediaItem），直接追加即可
                    PlaybackBus.queue.value = PlaybackBus.queue.value + fresh
                }
            }
        }

        viewModelScope.launch {
            var lastSongKey: String? = null
            kotlinx.coroutines.flow.combine(
                connection.currentSong,
                kotlinx.coroutines.flow.combine(
                    connection.isPlaying,
                    connection.position,
                    connection.duration,
                    PlaybackBus.mode,
                    PlaybackBus.loading,
                ) { playing, pos, dur, mode, loading ->
                    SongMeta(playing, pos, dur, mode, loading)
                },
            ) { song, meta -> song to meta }.collect { (song, meta) ->
                val key = song?.let { "${it.source}:${it.id}" }
                _state.value = _state.value.copy(
                    song = song,
                    isPlaying = meta.playing,
                    position = meta.pos,
                    duration = meta.dur,
                    mode = meta.mode,
                    loading = meta.loading,
                )
                if (key != lastSongKey) {
                    lastSongKey = key
                    _state.value = _state.value.copy(showLyrics = _state.value.showLyrics, lyricLines = emptyList(), lyricRawCount = 0)
                    if (song != null) {
                        loadLyric(song)
                        loadCover(song)
                    }
                }
            }
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(quality = qualityOption(settingsStore.current().quality).value)
        }
        viewModelScope.launch {
            PlaybackBus.error.collect { err ->
                if (err != null) {
                    _state.value = _state.value.copy(error = err)
                    PlaybackBus.consumeError()
                }
            }
        }
    }

    private data class SongMeta(val playing: Boolean, val pos: Long, val dur: Long, val mode: PlayMode, val loading: Boolean)

    fun togglePlayPause() = connection.togglePlayPause()
    fun pauseForSwitch() = connection.pauseForSwitch()
    fun next() = connection.next()
    fun previous() = connection.previous()
    fun seekTo(ms: Long) = connection.seekTo(ms)
    fun playAt(index: Int) = connection.playAt(index)
    fun setQueueAndPlay(songs: List<Song>, index: Int) = connection.setQueueAndPlay(songs, index)
    fun setMode(mode: PlayMode) = connection.setMode(mode)

    fun toggleLyrics() {
        _state.value = _state.value.copy(showLyrics = !_state.value.showLyrics)
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    /** 切换音质：保存设置并重载当前歌（对应 Solara 音质切换重载逻辑） */
    fun setQuality(quality: String) {
        _state.value = _state.value.copy(quality = quality)
        viewModelScope.launch {
            settingsStore.setQuality(quality)
            val index = PlaybackBus.index.value
            if (PlaybackBus.currentSong != null && index >= 0) {
                connection.playAt(index)
            }
        }
    }

    private fun loadLyric(song: Song) {
        val key = "${song.source}:${song.id}"
        lyricSongKey = key
        _state.value = _state.value.copy(lyricLoading = true)
        viewModelScope.launch {
            val raw = repository.resolveLyric(song)
            val lines = raw?.let { LrcParser.parse(it) }.orEmpty()
            if (lyricSongKey == key) {
                _state.value = _state.value.copy(
                    lyricLines = lines,
                    lyricRawCount = lines.size,
                    lyricLoading = false,
                )
                // 同步给桌面歌词悬浮窗（即使 UI 退到后台也能读）
                com.sodamusic.app.playback.PlaybackBus.lyricLines.value = lines
            }
        }
    }

    private fun loadCover(song: Song) {
        val key = "${song.source}:${song.id}"
        coverSongKey = key
        if (song.pic.isNotBlank()) {
            _state.value = _state.value.copy(coverUrl = song.pic)
            return
        }
        viewModelScope.launch {
            val url = repository.resolvePic(song)
            if (coverSongKey == key) {
                _state.value = _state.value.copy(coverUrl = url)
            }
        }
    }

    fun currentQualityLabel(): String = qualityOption(_state.value.quality).label
}
