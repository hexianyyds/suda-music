package com.sodamusic.app.data.api

import com.sodamusic.app.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 网易云官方接口「直连」客户端（不依赖任何三方聚合服务）。
 *
 * 走的是 music.163.com 上**不需要加密**的 /api 系列端点，已实测：
 *   - /api/search/get            搜索（分页）
 *   - /api/song/enhance/player/url 取播放直链（免费曲 320k 可用；VIP 曲返回 404/-110）
 *   - /api/song/lyric            歌词
 *   - /api/song/detail           歌曲详情（补充封面地址）
 *   - /api/playlist/detail       官方榜单（音乐雷达）
 *
 * 配置了 [cookie]（MUSIC_U=...）后，VIP / 高音质曲目也能解析。
 * 仅在第三方聚合接口故障时作为兜底使用，默认不参与日常请求。
 */
class NeteaseDirectApi {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** 可选的登录 Cookie（形如 MUSIC_U=xxxx），为空则只能解析免费曲目 */
    @Volatile
    var cookie: String = ""

    private fun base(path: String): Request.Builder {
        val builder = Request.Builder()
            .url("https://music.163.com$path")
            .header("User-Agent", MOBILE_UA)
            .header("Referer", "https://music.163.com/")
            .header("Accept", "application/json")
        if (cookie.isNotBlank()) builder.header("Cookie", cookie.trim())
        return builder
    }

    private suspend fun exec(request: Request): JsonObject = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val text = resp.body?.string() ?: throw IOException("Empty response")
                if (text.trimStart().startsWith("<")) throw IOException("接口返回网页而非数据")
                json.parseToJsonElement(text) as? JsonObject ?: throw IOException("响应格式异常")
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e.message ?: "网络错误", e)
        }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

    private fun JsonObject.num(key: String): Int? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull

    /** 搜索单曲（type=1），返回字段已映射为项目内的 [Song] */
    suspend fun search(keyword: String, page: Int, count: Int): List<Song> {
        val offset = (page - 1).coerceAtLeast(0) * count
        val body = FormBody.Builder()
            .add("s", keyword)
            .add("type", "1")
            .add("limit", count.toString())
            .add("offset", offset.toString())
            .build()
        val root = exec(base("/api/search/get").post(body).build())
        val songs = runCatching {
            root["result"]?.jsonObject?.get("songs")?.jsonArray
        }.getOrNull() ?: return emptyList()

        val parsed = songs.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.num("id")?.toString() ?: obj.str("id") ?: return@mapNotNull null
            val name = obj.str("name") ?: return@mapNotNull null
            val artist = runCatching {
                obj["artists"]?.jsonArray?.mapNotNull { it.jsonObject.str("name") }
                    ?.joinToString(" / ")
            }.getOrNull().orEmpty().ifBlank { "未知艺术家" }
            val album = runCatching { obj["album"]?.jsonObject?.str("name") }.getOrNull().orEmpty()
            Song(
                id = id,
                name = name,
                artist = artist,
                album = album,
                source = "netease",
                urlId = id,
                lyricId = id,
            )
        }
        if (parsed.isEmpty()) return emptyList()

        // 搜索接口不返回封面地址，补一次批量详情（1 个请求换 30 张图）
        val pics = runCatching { fetchPics(parsed.map { it.id }) }.getOrElse { emptyMap() }
        return parsed.map { song ->
            val pic = pics[song.id].orEmpty()
            if (pic.isBlank()) song else song.copy(pic = pic)
        }
    }

    /** 批量取封面：/api/song/detail?ids=[id1,id2,...] */
    private suspend fun fetchPics(ids: List<String>): Map<String, String> {
        if (ids.isEmpty()) return emptyMap()
        val list = ids.take(50).joinToString(",")
        val root = exec(base("/api/song/detail?ids=[$list]").build())
        val songs = root["songs"]?.jsonArray ?: return emptyMap()
        return songs.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.num("id")?.toString() ?: return@mapNotNull null
            val pic = obj["album"]?.jsonObject?.str("picUrl")?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            id to pic
        }.toMap()
    }

    /** 播放直链 + 是否为试听片段 */
    data class NeteaseUrl(val url: String, val trial: Boolean)

    /** 取链（含试听标记）；无版权 / 需要 VIP 且无试听时返回 null */
    suspend fun songUrlWithTrial(id: String, quality: String): NeteaseUrl? {
        val br = bitrateOf(quality)
        val root = exec(base("/api/song/enhance/player/url?ids=[$id]&br=$br").build())
        val item = runCatching { root["data"]?.jsonArray?.firstOrNull()?.jsonObject }.getOrNull()
            ?: return null
        val url = item.str("url")?.takeIf { it.isNotBlank() } ?: return null
        // 未登录时 VIP 曲只返回 0~30 秒试听片段（freeTrialInfo 非空）
        val trial = item["freeTrialInfo"]
        val isTrial = trial != null && trial !is kotlinx.serialization.json.JsonNull
        return NeteaseUrl(url, isTrial)
    }

    /** 完整播放直链；仅试听 / 无版权时返回 null */
    suspend fun songUrl(id: String, quality: String): String? =
        songUrlWithTrial(id, quality)?.takeIf { !it.trial }?.url

    /** 歌词（LRC 原文）；无歌词返回 null */
    suspend fun lyric(id: String): String? {
        val root = exec(base("/api/song/lyric?id=$id&lv=1&kv=1&tv=-1").build())
        return runCatching { root["lrc"]?.jsonObject?.str("lyric") }.getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    /** 一条评论 */
    data class NeComment(
        val user: String,
        val content: String,
        val liked: Int,
        val time: String,
    )

    /** 评论（只读展示）：返回 (总条数, 热评/最新评论) */
    suspend fun comments(id: String, limit: Int = 20): Pair<Int, List<NeComment>> {
        val root = exec(base("/api/v1/resource/comments/R_SO_4_$id?limit=$limit&offset=0").build())
        val total = root.num("total")?.toInt() ?: 0
        val arr = root["hotComments"]?.jsonArray ?: root["comments"]?.jsonArray
        val list = arr?.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            NeComment(
                user = o["user"]?.jsonObject?.str("nickname").orEmpty(),
                content = o.str("content").orEmpty(),
                liked = o.num("likedCount")?.toInt() ?: 0,
                time = o.str("timeStr").orEmpty(),
            )
        }.orEmpty()
        return total to list
    }

    /** 单曲封面 */
    suspend fun pic(id: String): String {
        val root = exec(base("/api/song/detail?ids=[$id]").build())
        return runCatching {
            root["songs"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("album")?.jsonObject?.str("picUrl")
        }.getOrNull().orEmpty()
    }

    /** 官方榜单曲目（音乐雷达），limit 为 0 时取全部 */
    suspend fun toplist(playlistId: String, limit: Int): List<Song> {
        val root = exec(base("/api/playlist/detail?id=$playlistId").build())
        val tracks = runCatching { root["result"]?.jsonObject?.get("tracks")?.jsonArray }.getOrNull()
            ?: throw IOException("榜单数据为空")
        if (tracks.isEmpty()) throw IOException("榜单数据为空")
        val picked = if (limit > 0) tracks.take(limit) else tracks.toList()
        return picked.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.num("id")?.toString() ?: return@mapNotNull null
            val name = obj.str("name") ?: return@mapNotNull null
            val artist = runCatching {
                obj["artists"]?.jsonArray?.mapNotNull { it.jsonObject.str("name") }
                    ?.joinToString(" / ")
            }.getOrNull().orEmpty().ifBlank { "未知艺术家" }
            val albumObj = runCatching { obj["album"]?.jsonObject }.getOrNull()
            Song(
                id = id,
                name = name,
                artist = artist,
                album = albumObj?.str("name").orEmpty(),
                source = "netease",
                pic = albumObj?.str("picUrl").orEmpty().ifBlank { "" },
                urlId = id,
                lyricId = id,
            )
        }
    }

    companion object {
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

        /** 项目音质档位（128/192/320/999）→ 网易云 br 参数 */
        fun bitrateOf(quality: String): String = when (quality) {
            "128" -> "128000"
            "192" -> "192000"
            "999" -> "999000"
            else -> "320000"
        }
    }
}

