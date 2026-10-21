package com.sodamusic.app.data.store

import android.content.Context
import com.sodamusic.app.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class CachedSong(
    val song: Song,
    /** 缓存时的音质码率 */
    val quality: String,
    val cachedAt: Long,
)

/**
 * 本地歌曲缓存：歌曲元数据 JSON 存 app 私有缓存目录，播放成功即写入（对应“听过的歌自动缓存”）。
 * 音频文件本身不落地（直链有时效性，缓存直链会在过期后失效，故播放时实时重新解析）。
 */
object LocalCacheStore {

    @Serializable
    private data class CacheFile(val version: Int = 1, val items: List<CachedSong> = emptyList())

    private const val FILE_NAME = "song_cache.json"
    private const val MAX_ITEMS = 500

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val mutex = Mutex()

    /** UI 观察的缓存列表（最近缓存在前） */
    val cached = MutableStateFlow<List<CachedSong>>(emptyList())

    private fun cacheFile(context: Context): File = File(context.filesDir, FILE_NAME)

    /** 启动时调用：从磁盘加载缓存列表到内存 */
    suspend fun init(context: Context) {
        withContext(Dispatchers.IO) {
            val file = cacheFile(context)
            val loaded = runCatching {
                if (file.exists()) json.decodeFromString<CacheFile>(file.readText())
                else CacheFile()
            }.getOrDefault(CacheFile())
            cached.value = loaded.items
        }
    }

    /** 播放成功后调用：写入/刷新缓存（已存在则提到最前并更新音质与时间） */
    suspend fun record(context: Context, song: Song, quality: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val list = cached.value.toMutableList()
            list.removeAll { it.song.source == song.source && it.song.id == song.id }
            list.add(0, CachedSong(song, quality, System.currentTimeMillis()))
            val trimmed = list.take(MAX_ITEMS)
            cached.value = trimmed
            runCatching {
                cacheFile(context).writeText(json.encodeToString(CacheFile(version = 1, items = trimmed)))
            }
        }
    }

    /** 手动移除单条缓存 */
    suspend fun remove(context: Context, song: Song) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val list = cached.value.filterNot { it.song.source == song.source && it.song.id == song.id }
            cached.value = list
            runCatching { cacheFile(context).writeText(json.encodeToString(CacheFile(version = 1, items = list))) }
        }
    }

    /** 清空全部缓存 */
    suspend fun clear(context: Context) = mutex.withLock {
        withContext(Dispatchers.IO) {
            cached.value = emptyList()
            runCatching { cacheFile(context).delete() }
        }
    }

    suspend fun isCached(song: Song): Boolean =
        cached.value.any { it.song.source == song.source && it.song.id == song.id }

    suspend fun firstCached(): CachedSong? = cached.value.firstOrNull()
}
