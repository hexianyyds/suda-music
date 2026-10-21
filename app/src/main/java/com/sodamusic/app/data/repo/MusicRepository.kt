package com.sodamusic.app.data.repo

import com.sodamusic.app.data.api.CloudflareChallengeException
import com.sodamusic.app.data.api.GdMusicApi
import com.sodamusic.app.data.api.NeteaseDirectApi
import com.sodamusic.app.data.api.NoCopyrightException
import com.sodamusic.app.data.api.QqMusicApi
import com.sodamusic.app.data.model.Constants
import com.sodamusic.app.data.model.Song
import com.sodamusic.app.data.store.AppSettings
import com.sodamusic.app.data.store.LibraryStore
import com.sodamusic.app.data.store.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** 带用户可读文案的业务异常 */
class MusicException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 音乐业务仓库：搜索、直链/封面/歌词解析、榜单雷达。
 *
 * 音源策略（双内置源，设置页可切换）：
 * - [Constants.DATA_SOURCE_LX] **洛雪源**（默认）：原内置的聚合接口 [GdMusicApi]；
 *   开启「故障自动切换」时，一旦出现网络错误 / HTTP 异常 / Cloudflare 拦截，熔断 [DOWN_COOLDOWN_MS]，
 *   期间自动改用网易源，冷却结束后自动回切。
 * - [Constants.DATA_SOURCE_NETEASE] **网易源**：官方接口直连 [NeteaseDirectApi]，不依赖任何三方服务。
 *
 * 雷达逻辑对应 Solara 音乐雷达：随机抽取一个已选榜单 Top20，过滤重复后乱序注入。
 */
class MusicRepository(
    private val api: GdMusicApi,
    private val settingsStore: SettingsStore,
    private val libraryStore: LibraryStore,
    private val netease: NeteaseDirectApi = NeteaseDirectApi(),
) {

    private val picCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** 非网易云曲目 → 网易源歌曲 ID 的匹配缓存（切源后重放同一首歌用） */
    private val neteaseIdCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** 洛雪源熔断截止时间戳；0 表示健康 */
    @Volatile
    private var primaryDownUntil: Long = 0L

    private val _usingFallback = MutableStateFlow(false)

    /** 命中试听片段的曲目 id（播放页用于标注「试听」） */
    private val _trialSongIds = MutableStateFlow<Set<String>>(emptySet())
    val trialSongIds: StateFlow<Set<String>> = _trialSongIds

    /** 当前是否由网易源兜底提供数据（供设置页展示状态） */
    val usingFallback: StateFlow<Boolean> = _usingFallback.asStateFlow()

    fun isPrimaryHealthy(): Boolean = System.currentTimeMillis() >= primaryDownUntil

    /** 每次调用同步一次运行期配置，保证设置页修改即时生效 */
    private suspend fun prepareApi(settings: AppSettings? = null): AppSettings {
        val current = settings ?: settingsStore.current()
        api.baseUrl = current.apiBase
        netease.cookie = current.neteaseCookie
        return current
    }

    private fun markPrimaryDown() {
        primaryDownUntil = System.currentTimeMillis() + DOWN_COOLDOWN_MS
    }

    private fun markPrimaryUp() {
        primaryDownUntil = 0L
        _usingFallback.value = false
    }

    private fun neteaseOnly(settings: AppSettings): Boolean =
        settings.dataSource == Constants.DATA_SOURCE_NETEASE

    /** 洛雪源故障时是否允许切到网易源 */
    private fun fallbackAllowed(settings: AppSettings): Boolean =
        !neteaseOnly(settings) && settings.autoFallback

    /** 备用通道调用：吞掉异常返回 null，不打断主流程 */
    private suspend fun <T> tryFallback(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private fun toMusicException(e: Exception): MusicException = when (e) {
        is MusicException -> e
        is CloudflareChallengeException -> MusicException(e.message ?: "API 被拦截", e)
        else -> MusicException("洛雪源不可用：${e.message ?: "网络异常"}", e)
    }

    suspend fun search(keyword: String, source: String, page: Int = 1): List<Song> = withContext(Dispatchers.IO) {
        // QQ 音乐：直连官方接口，独立于洛雪/网易两套数据源
        if (source == "qq") {
            return@withContext try {
                QqMusicApi.search(keyword, page, Constants.PAGE_SIZE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw MusicException("QQ 音乐搜索失败：${e.message ?: "网络异常"}", e)
            }
        }

        val settings = prepareApi()

        // 选了「网易源」：直接走官方接口，忽略平台的音源标签
        if (neteaseOnly(settings)) {
            _usingFallback.value = true
            val list = try {
                netease.search(keyword, page, Constants.PAGE_SIZE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw MusicException("网易源搜索失败：${e.message ?: "网络异常"}", e)
            }
            return@withContext list
        }

        // 洛雪源 + 关闭容灾，或当前搜索平台不是网易云（网易源无法覆盖其他平台）
        val canFallback = fallbackAllowed(settings) && source == "netease"
        if (!canFallback) {
            return@withContext primarySearch(keyword, source, page)
        }

        // 熔断期：直接走网易源，省掉一次必然失败的等待
        if (!isPrimaryHealthy()) {
            val alt = tryFallback { netease.search(keyword, page, Constants.PAGE_SIZE) }
            if (alt != null) {
                _usingFallback.value = true
                return@withContext alt
            }
            // 网易源也不通，说明可能只是洛雪源已恢复，立刻重试一次
            markPrimaryUp()
            return@withContext primarySearch(keyword, source, page)
        }

        try {
            val list = primarySearch(keyword, source, page)
            markPrimaryUp()
            list
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            markPrimaryDown()
            val alt = tryFallback { netease.search(keyword, page, Constants.PAGE_SIZE) }
            if (alt != null) {
                _usingFallback.value = true
                alt
            } else {
                throw toMusicException(e)
            }
        }
    }

    private suspend fun primarySearch(keyword: String, source: String, page: Int): List<Song> {
        return try {
            api.search(keyword, source, page = page)
        } catch (e: CancellationException) {
            throw e
        } catch (e: CloudflareChallengeException) {
            throw MusicException(e.message ?: "API 被拦截", e)
        } catch (e: Exception) {
            throw MusicException("搜索失败：${e.message}", e)
        }
    }

    /**
     * 播放直链：优先命中预取缓存（竖滑时下一首已解析好），未命中再走真实解析。
     */
    suspend fun resolveUrl(song: Song, quality: String): String = resolveUrlInner(song, quality)

    private suspend fun resolveUrlInner(song: Song, quality: String): String {
        val key = cacheKey(song, quality)
        urlCache[key]?.let { (url, at) ->
            if (System.currentTimeMillis() - at < URL_TTL_MS) return url
            urlCache.remove(key)
        }
        val url = resolveUrlInternal(song, quality)
        urlCache[key] = url to System.currentTimeMillis()
        return url
    }

    /**
     * 预取若干首的播放直链，供竖滑信息流提前准备（滑到即播，不再等接口）。
     * 只填充缓存，失败静默忽略，不影响正常播放路径。
     */
    suspend fun prefetch(songs: List<Song>) = withContext(Dispatchers.IO) {
        if (songs.isEmpty()) return@withContext
        val quality = settingsStore.current().quality
        songs.forEach { song ->
            val key = cacheKey(song, quality)
            val hit = urlCache[key]
            if (hit != null && System.currentTimeMillis() - hit.second < URL_TTL_MS) return@forEach
            runCatching { resolveUrlInternal(song, quality) }
                .onSuccess { urlCache[key] = it to System.currentTimeMillis() }
        }
    }

    private suspend fun resolveUrlInternal(song: Song, quality: String): String = withContext(Dispatchers.IO) {
        // 已下载的曲目直接用本地文件（离线可播、省流量）
        com.sodamusic.app.data.download.DownloadManager.localFile(song)?.let { file ->
            return@withContext android.net.Uri.fromFile(file).toString()
        }

        // QQ 音乐曲目：直接走官方 vkey 接口（urlId 存的是 media_mid）
        if (song.source == "qq") {
            // 免费曲往往只有 128k，高档位拿不到就逐级降级
            val candidates = when (quality) {
                "999", "flac" -> listOf("flac", "320", "128")
                "320" -> listOf("320", "128")
                "m4a" -> listOf("m4a", "128")
                else -> listOf("128")
            }
            var last: Exception? = null
            for (q in candidates) {
                try {
                    return@withContext QqMusicApi.playUrl(song.id, song.urlId, q)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    last = e
                }
            }
            throw MusicException(
                (last as? NoCopyrightException)?.message ?: "QQ 音乐取链失败：${last?.message ?: "网络异常"}",
                last,
            )
        }

        val settings = prepareApi()

        // 「网易源」模式：网易云曲目直接取链，其它平台曲目按歌名+歌手跨源匹配后取链
        if (neteaseOnly(settings)) {
            val neId = neteaseIdFor(song)
                ?: throw MusicException("网易源未找到该曲目（${song.name} - ${song.artist}），可切回洛雪源播放")
            val ne = try {
                netease.songUrlWithTrial(neId, quality)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw MusicException("网易源取链失败：${e.message ?: "网络异常"}", e)
            }
            // 完整版
            if (ne != null && !ne.trial) {
                _usingFallback.value = true
                logResolvedByNetease(song)
                _trialSongIds.value = _trialSongIds.value - song.id
                return@withContext ne.url
            }
            // 拿到的是试听 → 先换洛雪源要完整版
            val lx = tryFallback { api.getSongUrl(song, quality) }
            if (!lx.isNullOrBlank()) {
                _trialSongIds.value = _trialSongIds.value - song.id
                return@withContext lx
            }
            // 都没有完整版 → 回退到试听片段，并在播放页标注「试听」
            if (ne != null) {
                _usingFallback.value = true
                _trialSongIds.value = _trialSongIds.value + song.id
                return@withContext ne.url
            }
            throw MusicException(
                "该曲目需要网易云 VIP：无登录态只能试听 30 秒，且洛雪源未取到链接。" +
                    "可在设置中填入网易云 Cookie 后重试"
            )
        }

        val canFallback = fallbackAllowed(settings) && song.source == "netease"
        var skippedPrimary = false
        var primaryError: Exception? = null

        if (!canFallback || isPrimaryHealthy()) {
            try {
                val url = api.getSongUrl(song, quality)
                if (!url.isNullOrBlank()) {
                    markPrimaryUp()
                    return@withContext url
                }
                // 洛雪源返回空直链：可能是 VIP / 无版权 / 该码率缺失，继续尝试网易源
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                primaryError = e
                if (!canFallback) throw toMusicException(e)
                markPrimaryDown()
            }
        } else {
            skippedPrimary = true
        }

        if (canFallback) {
            val alt = tryFallback { netease.songUrl(song.urlId.ifBlank { song.id }, quality) }
            if (!alt.isNullOrBlank()) {
                _usingFallback.value = true
                logResolvedByNetease(song)
                return@withContext alt
            }
        }

        // 熔断期内跳过了洛雪源：兜底再试一次，避免"网易源也没戏"时白白失败
        if (skippedPrimary) {
            markPrimaryUp()
            try {
                val url = api.getSongUrl(song, quality)
                if (!url.isNullOrBlank()) return@withContext url
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                markPrimaryDown()
                primaryError = e
            }
        }

        throw MusicException(
            if (primaryError != null) {
                "洛雪源不可用，网易源也未取到地址（VIP/无版权曲目需在设置中填入网易云 Cookie）"
            } else {
                "无法获取播放地址（音源无此码率，或该曲目需要 VIP）"
            },
            primaryError
        )
    }

    private fun logResolvedByNetease(song: Song) {
        // 调试日志已移除
    }

    /**
     * 拿到该曲目在网易源的歌曲 ID。
     * 网易云曲目直接复用原 ID；其它平台（酷我/JOOX/B站）的曲目按「歌名 + 首位歌手」在网易源检索匹配，
     * 保证用户切换音源后能继续播放当前这首歌。
     */
    private suspend fun neteaseIdFor(song: Song): String? {
        if (song.source == "netease") return song.urlId.ifBlank { song.id }

        val cacheKey = "${song.name}|${song.artist}"
        neteaseIdCache[cacheKey]?.let { return it }

        val singer = song.artist.split("/").firstOrNull()?.trim().orEmpty()
        val keyword = buildString {
            append(song.name.trim())
            if (singer.isNotBlank() && singer != "未知艺术家") append(' ').append(singer)
        }
        val hit = tryFallback { netease.search(keyword, 1, 5).firstOrNull() } ?: return null
        neteaseIdCache[cacheKey] = hit.id
        return hit.id
    }

    /**
     * 随机抽取网易源曲目（用于自动续播队列）。
     * 优先走网易官方榜单直连，失败时用洛雪源搜索兜底。
     */
    suspend fun randomNeteaseSongs(count: Int): List<Song> = withContext(Dispatchers.IO) {
        // 设置与 ServiceLocator 在某些环境下不可用，这里全部降级处理，保证抽歌不失败
        val settings = runCatching { settingsStore.current() }.getOrNull()
        netease.cookie = settings?.neteaseCookie.orEmpty()
        val ids = settings?.radarIds?.takeIf { it.isNotEmpty() } ?: Constants.DEFAULT_RADAR_IDS
        // 1) 网易官方榜单直连
        for (pid in ids.shuffled().take(2)) {
            val list = runCatching { netease.toplist(pid, limit = 30) }.getOrNull()
            if (!list.isNullOrEmpty()) return@withContext list.shuffled().take(count)
        }
        // 2) 网易官方搜索直连
        val keyword = listOf("热歌榜", "新歌", "民谣", "华语热歌", "轻音乐").random()
        runCatching { netease.search(keyword, 1, 30) }
            .getOrNull()
            ?.shuffled()
            ?.take(count)
            ?: emptyList()
    }

    /**
     * 校验播放直链是否真的可访问（Range 探测 1 字节）。
     *
     * 有些链接取链成功但实际返回 403 / 404 / 已过期，若直接交给播放器，
     * prepare() 会失败并把播放卡在坏 item 上（表现为"点了播放没反应"）。
     * 这里提前探测，失败时按取链失败处理，可以正常回滚。
     */
    suspend fun isPlayable(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = okhttp3.Request.Builder()
                .url(url)
                .header("Range", "bytes=0-0")
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()
            probeClient.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private val probeClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    /** 评论（只读）：返回 (总条数, 评论列表)；目前仅网易源支持 */
    suspend fun comments(
        song: Song,
        limit: Int = 20,
    ): Pair<Int, List<NeteaseDirectApi.NeComment>> = withContext(Dispatchers.IO) {
        if (song.source == "netease") {
            runCatching { netease.comments(song.id, limit) }.getOrDefault(0 to emptyList())
        } else {
            0 to emptyList()
        }
    }

    suspend fun resolvePic(song: Song): String = withContext(Dispatchers.IO) {
        val key = "${song.source}:${song.id}"
        picCache[key]?.let { return@withContext it }
        song.pic.takeIf { it.isNotBlank() }?.let {
            picCache[key] = it
            return@withContext it
        }
        val settings = prepareApi()
        val canFallback = fallbackAllowed(settings) && song.source == "netease"

        val url = try {
            api.getPicUrl(song)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (canFallback) {
                markPrimaryDown()
                tryFallback { netease.pic(song.urlId.ifBlank { song.id }) }.orEmpty()
            } else {
                ""
            }
        }

        val resolved = url.ifBlank {
            if (canFallback || neteaseOnly(settings)) {
                tryFallback { netease.pic(song.urlId.ifBlank { song.id }) }.orEmpty()
            } else {
                ""
            }
        }
        if (resolved.isNotBlank()) picCache[key] = resolved
        resolved
    }

    suspend fun resolveLyric(song: Song): String? = withContext(Dispatchers.IO) {
        // QQ 音乐：官方歌词接口（返回 LRC）
        if (song.source == "qq") {
            return@withContext try {
                QqMusicApi.lyric(song.lyricId.ifBlank { song.id }).ifBlank { null }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }

        val settings = prepareApi()
        val canFallback = fallbackAllowed(settings) && song.source == "netease"

        if (neteaseOnly(settings) && song.source == "netease") {
            return@withContext tryFallback { netease.lyric(song.lyricId.ifBlank { song.id }) }
        }

        val lyric = try {
            api.getLyric(song)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (canFallback) {
                markPrimaryDown()
                tryFallback { netease.lyric(song.lyricId.ifBlank { song.id }) }
            } else {
                null
            }
        }

        lyric ?: if (canFallback) {
            tryFallback { netease.lyric(song.lyricId.ifBlank { song.id }) }
        } else {
            null
        }
    }

    /**
     * 音乐雷达：随机抽取一个已启用榜单，取 Top20 并过滤掉当前队列/收藏中已有的歌。
     * 洛雪源榜单接口失败时，自动改用网易源的官方榜单。
     */
    suspend fun radarInject(extraExclude: List<Song> = emptyList()): List<Song> = withContext(Dispatchers.IO) {
        val settings = prepareApi()
        val ids = settings.radarIds.ifEmpty { Constants.DEFAULT_RADAR_IDS }

        var tracks: List<Song>? = null
        var lastError: Exception? = null

        if (neteaseOnly(settings)) {
            for (playlistId in ids.shuffled().take(2)) {
                val alt = tryFallback { netease.toplist(playlistId, limit = 20) }
                if (!alt.isNullOrEmpty()) {
                    tracks = alt
                    _usingFallback.value = true
                    break
                }
            }
        } else {
            // 依次尝试最多 2 个随机榜单：单个榜单接口超时/波动时自动换榜重试
            for (playlistId in ids.shuffled().take(2)) {
                try {
                    tracks = api.getRadarPlaylist(playlistId, limit = 20)
                    markPrimaryUp()
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                }
            }
            if (tracks == null && settings.autoFallback) {
                markPrimaryDown()
                for (playlistId in ids.shuffled().take(2)) {
                    val alt = tryFallback { netease.toplist(playlistId, limit = 20) }
                    if (!alt.isNullOrEmpty()) {
                        tracks = alt
                        _usingFallback.value = true
                        break
                    }
                }
            }
        }

        if (tracks == null) {
            throw MusicException("榜单拉取失败（接口超时或波动）：${lastError?.message ?: "未知错误"}", lastError)
        }
        val excludeKeys = buildSet {
            libraryStore.queue.first().forEach { add(key(it)) }
            libraryStore.favorites.first().forEach { add(key(it)) }
            extraExclude.forEach { add(key(it)) }
        }
        val list = tracks
        val deduped = list.filter { key(it) !in excludeKeys }.distinctBy { key(it) }.shuffled()
        // 兜底：若队列/收藏已包含整个榜单（如连续刷新雷达），放宽去重避免首页空转
        if (deduped.isEmpty() && list.isNotEmpty()) list.distinctBy { key(it) }.shuffled() else deduped
    }

    private fun key(song: Song): String = "${song.source}:${song.id}"

    companion object {
        private const val TAG = "SodaSource"

        /** 洛雪源判定为"挂掉"后的熔断时长：期间请求直接走网易源，避免每次等待超时 */
        private const val DOWN_COOLDOWN_MS = 60_000L

        /** 预取直链有效期；网易云直链本身有时效，8 分钟足够覆盖一次滑动会话 */
        private const val URL_TTL_MS = 8 * 60_000L

        /** 跨实例共享的直链缓存（Service 与 UI 的 Repository 是两个实例，缓存必须静态） */
        private val urlCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()

        private fun cacheKey(song: Song, quality: String): String =
            "${song.source}:${song.urlId.ifBlank { song.id }}:$quality"

        /** 切换音源 / 修改 API 地址后清空预取，避免用到旧通道解析出的直链 */
        fun clearPrefetchedUrls() {
            urlCache.clear()
        }
    }
}






