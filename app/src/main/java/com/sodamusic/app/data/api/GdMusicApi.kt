package com.sodamusic.app.data.api

import com.sodamusic.app.data.model.Constants
import com.sodamusic.app.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

/** API 被 Cloudflare 人机验证拦截时抛出，用于 UI 提示降级 */
class CloudflareChallengeException(message: String) : IOException(message)

/**
 * GD 音乐台聚合 API 客户端。
 * 接口契约与 Solara js/constants.js 中 API 对象完全一致：
 *   ?types=search&source=&name=&count=&pages=&s=
 *   ?types=url&id=&source=&br=&s=      -> [{url,size,br}]
 *   ?types=pic&id=&source=&size=&s=    -> [{url}]
 *   ?types=lyric&id=&source=&s=        -> [{lyric}]
 *   ?types=playlist&id=&limit=&offset= -> {playlist:{tracks:[...]}}
 */
class GdMusicApi {

    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    @Volatile
    var baseUrl: String = Constants.DEFAULT_API_BASE

    private fun signature(): String = buildString {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        repeat(13) { append(chars.random()) }
    }

    private suspend fun fetchJson(url: String): String = withContext(Dispatchers.IO) {
        val body = try {
            client.newCall(
                okhttp3.Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                resp.body?.string() ?: throw IOException("Empty response")
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e.message ?: "Network error", e)
        }
        val trimmed = body.trimStart()
        // Cloudflare 拦截时返回 HTML 挑战页而非 JSON
        if (trimmed.startsWith("<")) {
            throw CloudflareChallengeException("API 被 Cloudflare 人机验证拦截")
        }
        body
    }

    /**
     * 关键词搜索。接口的 artist 字段形态不固定（字符串或字符串数组，如 ["郑智化"]），
     * 故手动解析 JsonElement 做双形态兼容。
     */
    suspend fun search(keyword: String, source: String, count: Int = Constants.PAGE_SIZE, page: Int = 1): List<Song> {
        val url = "${baseUrl}?types=search&source=$source&name=${urlEncode(keyword)}&count=$count&pages=$page&s=${signature()}"
        val text = fetchJson(url)
        val arr = runCatching { json.parseToJsonElement(text).jsonArray }
            .getOrElse { throw IOException("搜索结果格式异常") }
        return arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            fun str(key: String): String? = (obj[key] as? JsonPrimitive)?.content
            val id = str("id") ?: return@mapNotNull null
            val name = str("name") ?: return@mapNotNull null
            val artist = when (val raw = obj["artist"]) {
                is JsonPrimitive -> raw.content.ifBlank { "未知艺术家" }
                is JsonArray -> raw.mapNotNull { (it as? JsonPrimitive)?.content }
                    .joinToString(" / ").ifBlank { "未知艺术家" }
                else -> "未知艺术家"
            }
            Song(
                id = id,
                name = name,
                artist = artist,
                album = str("album") ?: "",
                source = str("source") ?: source,
                picId = str("pic_id") ?: "",
                urlId = str("url_id") ?: id,
                lyricId = str("lyric_id") ?: id,
            )
        }
    }

    /** 解析播放直链，失败或空结果返回 null。接口返回单个对象，兼容数组包裹的镜像服务 */
    suspend fun getSongUrl(song: Song, br: String): String? {
        val url = "${baseUrl}?types=url&id=${urlEncode(song.urlId.ifBlank { song.id })}&source=${song.source}&br=$br&s=${signature()}"
        return try {
            val text = fetchJson(url)
            val dto = runCatching { json.decodeFromString<SongUrlDto>(text) }
                .getOrElse { runCatching { json.decodeFromString<List<SongUrlDto>>(text) }.getOrNull()?.firstOrNull() }
            dto?.url?.takeIf { it.isNotBlank() }
        } catch (e: IOException) {
            // 网络 / Cloudflare 拦截等“接口不可用”信号必须上抛，供上层熔断并切换到备用音源
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** 封面地址：优先用歌曲自带 pic，否则请求 pic 接口（返回单个对象，兼容数组包裹） */
    suspend fun getPicUrl(song: Song, size: Int = 300): String {
        song.pic.takeIf { it.isNotBlank() }?.let { return it }
        if (song.picId.isBlank()) return ""
        val url = "${baseUrl}?types=pic&id=${urlEncode(song.picId)}&source=${song.source}&size=$size&s=${signature()}"
        return try {
            val text = fetchJson(url)
            val dto = runCatching { json.decodeFromString<PicDto>(text) }
                .getOrElse { runCatching { json.decodeFromString<List<PicDto>>(text) }.getOrNull()?.firstOrNull() }
            dto?.url.orEmpty()
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            ""
        }
    }

    /** 歌词原文（LRC），失败返回 null。接口返回单个对象，兼容数组包裹 */
    suspend fun getLyric(song: Song): String? {
        val url = "${baseUrl}?types=lyric&id=${urlEncode(song.lyricId.ifBlank { song.id })}&source=${song.source}&s=${signature()}"
        return try {
            val text = fetchJson(url)
            val dto = runCatching { json.decodeFromString<LyricDto>(text) }
                .getOrElse { runCatching { json.decodeFromString<List<LyricDto>>(text) }.getOrNull()?.firstOrNull() }
            dto?.lyric?.takeIf { it.isNotBlank() }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** 拉取网易云官方榜单（音乐雷达数据源），与 Solara getRadarPlaylist 一致 */
    suspend fun getRadarPlaylist(playlistId: String, limit: Int = 20, offset: Int = 0): List<Song> {
        val url = "${baseUrl}?types=playlist&id=$playlistId&limit=$limit&offset=$offset&s=${signature()}"
        val dto = json.decodeFromString<PlaylistDto>(fetchJson(url))
        val tracks = dto.playlist?.tracks.orEmpty()
        if (tracks.isEmpty()) throw IOException("榜单数据为空")
        return tracks.mapNotNull { t ->
            val id = t.id ?: return@mapNotNull null
            val name = t.name ?: return@mapNotNull null
            val directPic = t.al?.picUrl?.takeIf { it.isNotBlank() }
                ?: t.al?.pic?.takeIf { it.startsWith("http") }
            Song(
                id = id.toString(),
                name = name,
                artist = t.ar?.mapNotNull { it.name }?.joinToString(" / ") ?: "未知艺术家",
                album = t.al?.name ?: "",
                source = "netease",
                pic = directPic ?: "",
                picId = t.al?.pic_str ?: t.al?.pic ?: "",
                urlId = id.toString(),
                lyricId = id.toString(),
            )
        }
    }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
