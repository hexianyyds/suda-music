package com.sodamusic.app

import android.app.Application
import com.sodamusic.app.data.api.GdMusicApi
import com.sodamusic.app.data.repo.FavoritesManager
import com.sodamusic.app.data.repo.MusicRepository
import com.sodamusic.app.data.store.LibraryStore
import com.sodamusic.app.data.store.LocalCacheStore
import com.sodamusic.app.data.download.DownloadManager
import com.sodamusic.app.data.store.ListenStatsStore
import com.sodamusic.app.data.store.ProfileStore
import com.sodamusic.app.data.store.SettingsStore
import com.sodamusic.app.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 简单依赖容器：单例 API / 存储 / 仓库 / 播放连接 */
object ServiceLocator {
    lateinit var api: GdMusicApi
        private set
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var libraryStore: LibraryStore
        private set
    lateinit var repository: MusicRepository
        private set
    lateinit var playerConnection: PlayerConnection
        private set

    /** 个人资料（昵称 / 头像 / 背景） */
    lateinit var profileStore: ProfileStore
        private set

    /** 听歌时长统计 */
    lateinit var listenStats: ListenStatsStore
        private set

    fun init(app: Application) {
        api = GdMusicApi()
        settingsStore = SettingsStore(app)
        libraryStore = LibraryStore(app)
        profileStore = ProfileStore(app)
        listenStats = ListenStatsStore(app)
        repository = MusicRepository(api, settingsStore, libraryStore)
        playerConnection = PlayerConnection(app)
        FavoritesManager.init(libraryStore)
        DownloadManager.init(app)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            LocalCacheStore.init(app)
        }
    }
}

class SodaMusicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}

