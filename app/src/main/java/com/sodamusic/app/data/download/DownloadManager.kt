package com.sodamusic.app.data.download

import android.content.Context
import com.sodamusic.app.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** 已下载的一首歌 */
@Serializable
data class DownloadedSong(
    val song: Song,
    val fileName: String,
    val sizeBytes: Long = 0,
    val createdAt: Long = 0,
)

/**
 * 歌曲下载：把播放直链抓成本地文件，并维护一份索引（JSON）。
 * 播放时优先用本地文件，省流量也支持离线。
 */
object DownloadManager {

    private const val TAG = "DownloadManager"
    private const val INDEX_NAME = "index.json"

    private var appContext: Context? = null
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _items = MutableStateFlow<List<DownloadedSong>>(emptyList())
    val items: StateFlow<List<DownloadedSong>> = _items

    /** songKey -> 进度百分比（0..100），完成后移除 */
    private val _progress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val progress: StateFlow<Map<String, Int>> = _progress

    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors: StateFlow<Map<String, String>> = _errors

    fun init(context: Context) {
        appContext = context.applicationContext
        _items.value = readIndex()
    }

    fun songKey(song: Song): String = "${song.source}:${song.id}"

    private fun dir(): File =
        (appContext?.filesDir ?: return File("/tmp")).resolve("downloads").apply { if (!exists()) mkdirs() }

    private fun indexFile(): File = File(dir(), INDEX_NAME)

    private fun fileName(song: Song): String =
        "${song.source}_${song.id}".replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".mp3"

    fun isDownloaded(song: Song): Boolean =
        _items.value.any { it.song.source == song.source && it.song.id == song.id }

    fun localFile(song: Song): File? =
        _items.value.firstOrNull { it.song.source == song.source && it.song.id == song.id }
            ?.let { File(dir(), it.fileName) }
            ?.takeIf { it.exists() && it.length() > 0 }

    /** 开始下载；[url] 由调用方解析好后传入 */
    fun download(scope: CoroutineScope, song: Song, url: String) {
        val key = songKey(song)
        if (isDownloaded(song) || _progress.value.containsKey(key)) return
        _progress.update { it + (key to 0) }
        _errors.update { it - key }

        scope.launch(Dispatchers.IO) {
            val target = File(dir(), fileName(song))
            try {
                val req = Request.Builder().url(url).header("Accept", "*/*").build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val body = resp.body ?: error("空响应")
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        target.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var read = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n <= 0) break
                                out.write(buf, 0, n)
                                read += n
                                if (total > 0) {
                                    val pct = (read * 100 / total).toInt().coerceIn(0, 99)
                                    _progress.update { it + (key to pct) }
                                }
                            }
                        }
                    }
                }
                if (!target.exists() || target.length() <= 0) error("文件为空")

                val entry = DownloadedSong(
                    song = song,
                    fileName = target.name,
                    sizeBytes = target.length(),
                    createdAt = System.currentTimeMillis(),
                )
                _items.update { list ->
                    (list.filterNot { it.song.source == song.source && it.song.id == song.id } + entry)
                }
                writeIndex(_items.value)
            } catch (e: Exception) {
                runCatching { if (target.exists()) target.delete() }
                _errors.update { it + (key to (e.message ?: "下载失败")) }
            } finally {
                _progress.update { it - key }
            }
        }
    }

    fun delete(song: Song) {
        val hit = _items.value.firstOrNull { it.song.source == song.source && it.song.id == song.id }
        if (hit != null) {
            runCatching { File(dir(), hit.fileName).takeIf { it.exists() }?.delete() }
        }
        _items.update { list ->
            list.filterNot { it.song.source == song.source && it.song.id == song.id }
        }
        writeIndex(_items.value)
    }

    fun clearAll() {
        _items.value.forEach { runCatching { File(dir(), it.fileName).takeIf { f -> f.exists() }?.delete() } }
        _items.value = emptyList()
        writeIndex(emptyList())
    }

    fun consumeError(song: Song): String? {
        val key = songKey(song)
        val e = _errors.value[key]
        if (e != null) _errors.update { it - key }
        return e
    }

    private fun readIndex(): List<DownloadedSong> = runCatching {
        val f = indexFile()
        if (!f.exists()) emptyList() else json.decodeFromString<List<DownloadedSong>>(f.readText())
    }.getOrDefault(emptyList())

    private fun writeIndex(list: List<DownloadedSong>) {
        runCatching { indexFile().writeText(json.encodeToString(list)) }
    }
}
