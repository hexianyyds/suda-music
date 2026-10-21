package com.sodamusic.app.playback

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.sodamusic.app.data.model.PlayMode
import com.sodamusic.app.data.repo.MusicException
import com.sodamusic.app.data.model.Song
import com.sodamusic.app.data.repo.MusicRepository
import com.sodamusic.app.data.store.LibraryStore
import com.sodamusic.app.data.store.LocalCacheStore
import com.sodamusic.app.data.store.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Serializable
internal data class QueuePayload(val songs: List<Song> = emptyList(), val startIndex: Int = 0)

/**
 * 前台播放服务：持有 ExoPlayer 与 MediaSession，负责队列推进、直链解析、通知栏/锁屏控制。
 * 队列推进逻辑对应 Solara core/audio.js（防幽灵播放、删歌指针重定位、播放模式）。
 */
class PlaybackService : MediaSessionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 切歌单飞令牌：resolve 期间来了新请求则旧的作废（快速连滑只认最后一次） */
    /** 实际在播放的队列下标（解析成功后才更新，失败时用于回滚） */
    private var playingIndex = -1

    /** 当前正在尝试切到的下标（prepare 失败时用于决定跳到哪一首） */
    private var targetIndex = -1

    /** 连续失败次数（用于自动跳到下一首可播曲目） */
    private var consecutiveFailures = 0

    /** 回滚/跳转信号序号 */
    private var rollbackSeq = 0L

    /** 连续播放错误次数（超过上限不再自动恢复，避免死循环） */
    private var errorRecoverCount = 0

    /** 用户的播放意图（区分主动暂停与切歌临时暂停） */
    private var lastPlayIntent = false

    private var switchToken = 0
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private lateinit var repository: MusicRepository
    private lateinit var settingsStore: SettingsStore
    private lateinit var libraryStore: LibraryStore

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        libraryStore = LibraryStore(this)
        repository = MusicRepository(com.sodamusic.app.data.api.GdMusicApi(), settingsStore, libraryStore)

        val httpFactory = OkHttpDataSource.Factory(OkHttpClient())
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    // 真正开始播放才算"听过"：记录历史 + 写入本地缓存
                    PlaybackBus.currentSong?.let { song ->
                        scope.launch {
                            libraryStore.recordHistory(song)
                            LocalCacheStore.record(applicationContext, song, settingsStore.current().quality)
                        }
                    }
                }
                if (state == Player.STATE_ENDED) onTrackEnded()
            }

            override fun onPlayerError(error: PlaybackException) {
                PlaybackBus.error.value = "播放出错：${error.errorCodeName}"
                PlaybackBus.loading.value = false
                // prepare 失败：播放器已停在坏 item 上。
                // 与取链失败一样，自动跳到下一首，避免"界面是新歌、实际卡在坏歌上"
                errorRecoverCount++
                consecutiveFailures++
                val q = PlaybackBus.queue.value
                val next = targetIndex + 1
                if (next in q.indices && consecutiveFailures <= 5) {
                    PlaybackBus.rollback.value = RollbackSignal(++rollbackSeq, next)
                    playAtInternal(next, ++switchToken)
                } else {
                    // 连续失败：停止播放，UI 不再显示任何"当前歌曲"
                    PlaybackBus.index.value = -1
                    player.stop()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    lastPlayIntent = true
                } else if (!PlaybackBus.switching.value) {
                    // 用户主动暂停（区别于切歌时的临时暂停）
                    lastPlayIntent = false
                }
                PlaybackBus.switching.value = false
            }
        })

        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            // 点击通知 / 锁屏卡片回到 App
            .setSessionActivity(
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    android.content.Intent(this, com.sodamusic.app.MainActivity::class.java)
                        .setFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()

        // 听歌时长统计：每秒累计，满 10 秒落盘一次（减少 DataStore 写入）
        scope.launch {
            var acc = 0L
            while (true) {
                kotlinx.coroutines.delay(1000)
                if (player.isPlaying) acc++
                if (acc >= 10) {
                    runCatching { com.sodamusic.app.ServiceLocator.listenStats.addSeconds(acc) }
                    acc = 0
                }
            }
        }

        // 进程重启后恢复持久化的队列指针
        scope.launch {
            val queue = libraryStore.queue.first()
            val index = libraryStore.currentIndex.first()
            if (PlaybackBus.queue.value.isEmpty() && queue.isNotEmpty()) {
                PlaybackBus.queue.value = queue
                PlaybackBus.index.value = index.coerceIn(-1, queue.size - 1)
                playingIndex = PlaybackBus.index.value
            }
            PlaybackBus.mode.value = libraryStore.playMode.first()
        }
    }

    inner class SessionCallback : MediaSession.Callback {

        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(PlaybackBus.CMD_SET_QUEUE, Bundle.EMPTY))
                .add(SessionCommand(PlaybackBus.CMD_PLAY_AT, Bundle.EMPTY))
                .add(SessionCommand(PlaybackBus.CMD_NEXT, Bundle.EMPTY))
                .add(SessionCommand(PlaybackBus.CMD_PREVIOUS, Bundle.EMPTY))
                .add(SessionCommand(PlaybackBus.CMD_SET_MODE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                PlaybackBus.CMD_SET_QUEUE -> {
                    runCatching {
                        val payload = json.decodeFromString<QueuePayload>(args.getString("payload") ?: "{}")
                        PlaybackBus.queue.value = payload.songs
                        scope.launch { libraryStore.saveQueue(payload.songs, payload.startIndex) }
                        playAtInternal(payload.startIndex)
                    }.onFailure { PlaybackBus.error.value = "队列数据异常" }
                }
                PlaybackBus.CMD_PLAY_AT -> {
                    val index = args.getInt("index", -1)
                    if (index >= 0) playAtInternal(index)
                }
                PlaybackBus.CMD_NEXT -> playNextInternal()
                PlaybackBus.CMD_PREVIOUS -> playPrevInternal()
                PlaybackBus.CMD_SET_MODE -> {
                    val mode = runCatching { PlayMode.valueOf(args.getString("mode") ?: "") }.getOrNull()
                    if (mode == null) {
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                    }
                    PlaybackBus.mode.value = mode
                    scope.launch { libraryStore.savePlayMode(mode) }
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        /**
         * 通知栏/锁屏的上一首/下一首转发到自定义队列推进。
         *
         * Media3 1.11 将该回调标记为废弃（推荐用 onPlayerInteractionFinished 观察交互），
         * 但后者只能"事后通知"，无法拦截并改变行为；官方当前的替代方案是用
         * ForwardingPlayer 移除 COMMAND_SEEK_TO_NEXT/PREVIOUS 并自行处理队列 —— 属于一次
         * 播放器层重构，此处先保持原行为，仅局部屏蔽废弃告警。
         */
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onPlayerCommandRequest(session: MediaSession, controller: MediaSession.ControllerInfo, command: Int): Int {
            // 通知栏/锁屏的上一首/下一首转发到自定义队列推进逻辑
            when (command) {
                Player.COMMAND_SEEK_TO_NEXT -> { playNextInternal(); return SessionResult.RESULT_SUCCESS }
                Player.COMMAND_SEEK_TO_PREVIOUS -> { playPrevInternal(); return SessionResult.RESULT_SUCCESS }
            }
            return super.onPlayerCommandRequest(session, controller, command)
        }
    }

    private fun onTrackEnded() {
        when (PlaybackBus.mode.value) {
            PlayMode.SINGLE -> {
                player.seekTo(0)
                player.play()
            }
            PlayMode.SHUFFLE -> playAtInternal(randomIndex())
            PlayMode.LIST_LOOP -> playAtInternal(if (PlaybackBus.index.value >= PlaybackBus.queue.value.size - 1) 0 else PlaybackBus.index.value + 1)
            PlayMode.SEQUENTIAL -> {
                if (PlaybackBus.index.value < PlaybackBus.queue.value.size - 1) {
                    playAtInternal(PlaybackBus.index.value + 1)
                } else {
                    player.seekTo(0)
                    player.pause()
                }
            }
        }
    }

    private fun randomIndex(): Int {
        val size = PlaybackBus.queue.value.size
        if (size <= 1) return PlaybackBus.index.value.coerceAtLeast(0)
        var next = PlaybackBus.index.value
        while (next == PlaybackBus.index.value) next = (0 until size).random()
        return next
    }

    private fun playNextInternal() {
        val size = PlaybackBus.queue.value.size
        if (size == 0) return
        val current = PlaybackBus.index.value
        when (PlaybackBus.mode.value) {
            PlayMode.SHUFFLE -> playAtInternal(randomIndex())
            else -> {
                if (current < size - 1) playAtInternal(current + 1)
                else if (PlaybackBus.mode.value == PlayMode.LIST_LOOP) playAtInternal(0)
            }
        }
    }

    private fun playPrevInternal() {
        val size = PlaybackBus.queue.value.size
        if (size == 0) return
        val current = PlaybackBus.index.value
        when {
            // 播放超过 3 秒时"上一首"回到开头，与主流播放器一致
            player.currentPosition > 3000 -> player.seekTo(0)
            current > 0 -> playAtInternal(current - 1)
            PlaybackBus.mode.value == PlayMode.LIST_LOOP -> playAtInternal(size - 1)
            else -> player.seekTo(0)
        }
    }

    /** 核心切歌：解析直链 → 构建 MediaItem → prepare/play；更新总线与持久化指针。
     *  预期单飞：resolve 期间新请求到来（如快速连滑）则旧的作废，只播最新的 */
    private fun playAtInternal(index: Int, token: Int = ++switchToken) {
        targetIndex = index
        val queue = PlaybackBus.queue.value
        val song = queue.getOrNull(index) ?: return
        // 记录切歌前正在播的那首：取链失败时用它在新队列里找回
        val prevKey = PlaybackBus.currentSong?.let { "${it.source}:${it.id}" }
        PlaybackBus.loading.value = true
        PlaybackBus.error.value = null

        scope.launch {
            try {
                val quality = settingsStore.current().quality
                val url = repository.resolveUrl(song, quality)
                // 探测直链是否真的可访问：避免 prepare() 失败把播放卡在坏 item 上
                if (!repository.isPlayable(url)) {
                    throw MusicException("该播放链接已失效或被拒绝")
                }
                val pic = repository.resolvePic(song)
                if (token != switchToken) return@launch // 已有更新的切歌请求，作废本次
                val metadata = MediaMetadata.Builder()
                    .setTitle(song.name)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(pic.takeIf { it.isNotBlank() }?.let { android.net.Uri.parse(it) })
                    .build()
                val item = MediaItem.Builder()
                    .setUri(url)
                    .setMediaId("${song.source}:${song.id}:${quality}")
                    .setMediaMetadata(metadata)
                    .build()
                player.setMediaItem(item)
                player.prepare()
                player.playWhenReady = true
                player.play()
                // 播放成功后才更新队列指针：保证 UI 显示的"当前歌曲"与实际播放一致
                if (token == switchToken) {
                    playingIndex = index
                    PlaybackBus.index.value = index
                    scope.launch { libraryStore.saveQueue(queue, index) }
                }
            } catch (e: Exception) {
                if (token == switchToken) {
                    consecutiveFailures++
                    PlaybackBus.error.value = e.message ?: "播放失败"
                    val q = PlaybackBus.queue.value
                    val next = index + 1
                    // 连续遇到不可播的曲目时，自动跳到下一首（最多连跳 3 首），
                    // 让竖滑卡片与实际播放保持一致，不会卡在某一首反复失败
                    if (consecutiveFailures <= 3 && next < q.size) {
                        PlaybackBus.rollback.value =
                            RollbackSignal(++rollbackSeq, next)
                        playAtInternal(next, ++switchToken)
                    } else {
                        // 全部失败：回滚到实际在播的曲目
                        val restored = prevKey?.let { key ->
                            q.indexOfFirst { "${it.source}:${it.id}" == key }
                        } ?: -1
                        val target = if (restored >= 0) restored else playingIndex.coerceIn(-1, q.lastIndex)
                        PlaybackBus.index.value = target
                        PlaybackBus.rollback.value = RollbackSignal(++rollbackSeq, target)
                        if (lastPlayIntent) player.play()
                    }
                }
            } finally {
                if (token == switchToken) PlaybackBus.loading.value = false
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.isPlaying) stopSelf()
    }

    override fun onDestroy() {
        session.release()
        player.release()
        scope.cancel()
        super.onDestroy()
    }
}

