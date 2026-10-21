package com.sodamusic.app.data.api

import com.sodamusic.app.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 无版权/无直链 */
class NoCopyrightException(message: String) : Exception(message)

/** QQ 音乐接口（纯 Kotlin 直连上游，不依赖中间服务） */
object QqMusicApi {

    private const val TAG = "QqMusicApi"
    private const val Y_BASE = "https://c.y.qq.com"
    private const val U_BASE = "https://u.y.qq.com/cgi-bin/musicu.fcg"
    private const val SIGN = "zzannc1o6o9b4i971602f3554385022046ab796512b7012"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val guid: String by lazy {
        ((Math.random() * Int.MAX_VALUE).toLong() * 977 % 10000000000L).toString()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /* ---------- 网络 ---------- */

    internal fun yGet(path: String, params: Map<String, String>, withCommon: Boolean = true): JSONObject? {
        val builder = (Y_BASE + path).toHttpUrlOrNull()?.newBuilder() ?: return null
        if (withCommon) {
            mapOf(
                "g_tk" to "1124214810", "loginUin" to "0", "hostUin" to "0",
                "inCharset" to "utf8", "outCharset" to "utf-8", "notice" to "0",
                "platform" to "yqq.json", "needNewCode" to "0",
            ).forEach { (k, v) -> builder.addQueryParameter(k, v) }
        }
        params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return execute(builder.build(), "https://c.y.qq.com/")
    }

    internal fun uGet(payload: JSONObject): JSONObject? {
        val url = "$U_BASE?format=json&sign=$SIGN&data=${enc(payload.toString())}"
        return execute(url.toHttpUrlOrNull() ?: return null, "https://y.qq.com/portal/player.html")
    }

    private fun execute(url: HttpUrl, referer: String): JSONObject? = try {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Referer", referer)
            .header("Accept", "*/*")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                null
            } else {
                runCatching { JSONObject(text) }.getOrElse {
                    null
                }
            }
        }
    } catch (e: Exception) {
        null
    }


    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

    /* ---------- 搜索 ---------- */

    suspend fun search(keyword: String, page: Int = 1, limit: Int = 30): List<Song> =
        withContext(Dispatchers.IO) {
            val json = yGet(
                "/soso/fcgi-bin/client_search_cp",
                mapOf(
                    "format" to "json", "outCharset" to "utf-8", "ct" to "24",
                    "qqmusic_ver" to "1298", "remoteplace" to "txt.yqq.song",
                    "t" to "0", "aggr" to "1", "cr" to "1", "lossless" to "0",
                    "flag_qc" to "0", "platform" to "yqq.json",
                    "w" to keyword, "n" to limit.toString(), "p" to page.toString(),
                    "catZhida" to "1",
                ),
            ) ?: return@withContext emptyList()
            val list = json.optJSONObject("data")?.optJSONObject("song")?.optJSONArray("list")
                ?: return@withContext emptyList()
            (0 until list.length()).mapNotNull { list.optJSONObject(it)?.toSong() }
        }

    /* ---------- 播放 ---------- */

    private val QUALITY_FILE = mapOf(
        "m4a" to ("C400" to ".m4a"),
        "128" to ("M500" to ".mp3"),
        "320" to ("M800" to ".mp3"),
        "ape" to ("A000" to ".ape"),
        "flac" to ("F000" to ".flac"),
    )

    suspend fun playUrl(songmid: String, mediaId: String = "", quality: String = "128"): String =
        withContext(Dispatchers.IO) {
            val (prefix, ext) = QUALITY_FILE[quality] ?: QUALITY_FILE.getValue("128")
            val media = mediaId.ifBlank { songmid }
            val payload = JSONObject().apply {
                put(
                    "req_0",
                    JSONObject().apply {
                        put("module", "vkey.GetVkeyServer")
                        put("method", "CgiGetVkey")
                        put(
                            "param",
                            JSONObject().apply {
                                put("filename", JSONArray().put("$prefix$songmid$media$ext"))
                                put("guid", guid)
                                put("songmid", JSONArray().put(songmid))
                                put("songtype", JSONArray().put(0))
                                put("uin", "0")
                                put("loginflag", 1)
                                put("platform", "20")
                            },
                        )
                    },
                )
                put("loginUin", "0")
                put(
                    "comm",
                    JSONObject().apply {
                        put("uin", "0"); put("format", "json"); put("ct", 24); put("cv", 0)
                    },
                )
            }

            val json = uGet(payload) ?: throw com.sodamusic.app.data.repo.MusicException("QQ 音乐无响应")
            val req0 = json.optJSONObject("req_0")
            if (req0 == null || req0.optInt("code", -1) != 0) {
                throw com.sodamusic.app.data.repo.MusicException("QQ 音乐取流失败（${req0?.optInt("code") ?: -1}）")
            }
            val data = req0.optJSONObject("data")
            val info = data?.optJSONArray("midurlinfo")?.optJSONObject(0)
            val purl = info?.optString("purl").orEmpty()
            if (purl.isBlank()) {
                val result = info?.optInt("result", 0) ?: 0
                throw NoCopyrightException(
                    if (result == 104003) "该曲目受版权限制，试试其他版本" else "暂无播放链接",
                )
            }
            val sip = data?.optJSONArray("sip")
            var domain = ""
            if (sip != null) {
                for (i in 0 until sip.length()) {
                    val s = sip.optString(i)
                    if (s.isNotBlank() && !s.startsWith("http://ws")) {
                        domain = s
                        break
                    }
                }
            }
            if (domain.isBlank()) domain = "https://dl.stream.qqmusic.qq.com/"
            domain.trimEnd('/') + "/" + purl.trimStart('/')
        }

    /* ---------- 歌词 ---------- */

    suspend fun lyric(songmid: String): String = withContext(Dispatchers.IO) {
        val json = yGet(
            "/lyric/fcgi-bin/fcg_query_lyric_new.fcg",
            mapOf(
                "format" to "json", "outCharset" to "utf-8",
                "pcachetime" to System.currentTimeMillis().toString(),
                "songmid" to songmid,
            ),
        ) ?: return@withContext ""
        val raw = when (val v = json.opt("lyric")) {
            is String -> v
            is JSONObject -> v.optString("lyric")
            null -> ""
            else -> ""
        }
        if (raw.isBlank()) return@withContext ""
        // 上游返回 Base64 编码的 LRC（已是明文时以 [ 开头）
        if (raw.startsWith("[")) {
            raw
        } else {
            runCatching {
                String(java.util.Base64.getDecoder().decode(raw), Charsets.UTF_8)
            }.getOrDefault(raw)
        }
    }

    /* ---------- 歌手 ---------- */

    /**
     * 按关键词定位歌手（用搜索的 zhida 直出，命中率最高）。
     */
    suspend fun searchSinger(keyword: String): QqSinger? = withContext(Dispatchers.IO) {
        val json = yGet(
            "/soso/fcgi-bin/client_search_cp",
            mapOf(
                "format" to "json", "outCharset" to "utf-8", "ct" to "24",
                "qqmusic_ver" to "1298", "remoteplace" to "txt.yqq.singer",
                "t" to "0", "aggr" to "1", "cr" to "1", "lossless" to "0",
                "flag_qc" to "0", "platform" to "yqq.json",
                "w" to keyword, "n" to "5", "p" to "1", "catZhida" to "1",
            ),
        ) ?: return@withContext null
        val z = json.optJSONObject("data")
            ?.optJSONObject("zhida")
            ?.optJSONObject("zhida_singer")
            ?: return@withContext null
        val mid = z.optString("singerMID").ifBlank { z.optString("singerID") }
        if (mid.isBlank()) return@withContext null
        // 热门专辑（zhida 直出 albumMID）
        val albums = mutableListOf<QqAlbum>()
        z.optJSONArray("hotalbum")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val amid = o.optString("albumMID").ifBlank { o.optString("albumMid") }
                if (amid.isNotBlank()) albums += QqAlbum(amid, o.optString("albumName"))
            }
        }
        QqSinger(
            id = mid,
            name = z.optString("singerName"),
            pic = z.optString("singerPic"),
            songCount = z.optInt("songNum"),
            albumCount = z.optInt("albumNum"),
            albums = albums,
        )
    }

    /** 热词 */

    suspend fun hotKeys(): List<String> = withContext(Dispatchers.IO) {
        val json = yGet(
            "/splcloud/fcgi-bin/gethotkey.fcg",
            mapOf("format" to "json", "outCharset" to "utf-8", "hostUin" to "0", "needNewCode" to "0"),
        ) ?: return@withContext emptyList()
        val arr = json.optJSONObject("data")?.optJSONArray("hotkey") ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull {
            arr.optJSONObject(it)?.optString("k")?.trim()?.takeIf { s -> s.isNotBlank() }
        }
    }

    /* ---------- 榜单 ---------- */

    suspend fun topLists(): List<QqTopList> = withContext(Dispatchers.IO) {
        val json = yGet(
            "/v8/fcg-bin/fcg_myqq_toplist.fcg",
            mapOf("format" to "json", "outCharset" to "utf-8", "platform" to "h5", "needNewCode" to "1"),
            withCommon = false,
        ) ?: return@withContext emptyList()
        val arr = json.optJSONObject("data")?.optJSONArray("topList") ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            QqTopList(
                id = o.optInt("id"),
                title = o.optString("topTitle"),
                pic = o.optString("picUrl"),
                listenCount = o.optLong("listenCount"),
            )
        }
    }

    suspend fun rankSongs(topId: Int, limit: Int = 30, page: Int = 1): List<Song> =
        withContext(Dispatchers.IO) {
            // 上游要求 ISO 周（moment().isoWeek()），不能用 Calendar.WEEK_OF_YEAR
            val today = java.time.LocalDate.now()
            val isoYear = today.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR)
            val isoWeek = today.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            val period = "${isoYear}_$isoWeek"
            val payload = JSONObject().apply {
                put(
                    "comm",
                    JSONObject().apply {
                        put("cv", 4747474)
                        put("ct", 24)
                        put("format", "json")
                        put("inCharset", "utf-8")
                        put("needNewCode", 1)
                        put("uin", 0)
                    },
                )
                put(
                    "req_1",
                    JSONObject().apply {
                        put("module", "musicToplist.ToplistInfoServer")
                        put("method", "GetDetail")
                        put(
                            "param",
                            JSONObject().apply {
                                put("topId", topId)
                                put("offset", (page - 1) * limit)
                                put("num", limit)
                                put("period", period)
                            },
                        )
                    },
                )
            }
            val json = uGet(payload) ?: return@withContext emptyList()
            val node = json.optJSONObject("req_1")?.optJSONObject("data") ?: return@withContext emptyList()
            // 结构：req_1.data.songInfoList（部分场景多包一层 data）
            val arr = node.optJSONArray("songInfoList")
                ?: node.optJSONObject("data")?.optJSONArray("songInfoList")
                ?: return@withContext emptyList()
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.toSong() }
        }

    /* ---------- 歌单 ---------- */

    suspend fun songLists(
        page: Int = 0,
        limit: Int = 20,
        categoryId: Int = 10000000,
        sortId: Int = 5,
    ): List<QqPlaylist> = withContext(Dispatchers.IO) {
        val json = yGet(
            "/splcloud/fcgi-bin/fcg_get_diss_by_tag.fcg",
            mapOf(
                "format" to "json", "outCharset" to "utf-8",
                "categoryId" to categoryId.toString(),
                "sortId" to sortId.toString(),
                "sin" to (page * limit).toString(),
                "ein" to (limit * (page + 1) - 1).toString(),
                "rnd" to System.currentTimeMillis().toString(),
            ),
        ) ?: return@withContext emptyList()
        val arr = json.optJSONObject("data")?.optJSONArray("list") ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("dissid").ifBlank { o.optString("tid") }
            if (id.isBlank()) return@mapNotNull null
            QqPlaylist(
                id = id,
                name = o.optString("dissname"),
                cover = o.optString("imgurl"),
                listenCount = o.optLong("listennum"),
                songCount = o.optInt("song_count"),
            )
        }
    }

    suspend fun songListDetail(disstid: String): List<Song> = withContext(Dispatchers.IO) {
        val json = yGet(
            "/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg",
            mapOf(
                "format" to "json", "outCharset" to "utf-8",
                "disstid" to disstid, "type" to "1", "json" to "1",
                "utf8" to "1", "onlysong" to "0", "new_format" to "1",
            ),
        ) ?: return@withContext emptyList()
        val cd = json.optJSONArray("cdlist")?.optJSONObject(0)
        if (cd == null) {
            return@withContext emptyList()
        }
        val arr = cd.optJSONArray("songlist")
        if (arr == null) {
            return@withContext emptyList()
        }
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.toSong() }
    }

    /* ---------- 歌手 / 专辑 ---------- */

    suspend fun singerHotsong(singermid: String, limit: Int = 30, page: Int = 1): List<Song> =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().apply {
                put("comm", JSONObject().apply { put("ct", 24); put("cv", 0) })
                put(
                    "req_1",
                    JSONObject().apply {
                        put("module", "music.web_singer_info_svr")
                        put("method", "get_singer_detail_info")
                        put(
                            "param",
                            JSONObject().apply {
                                put("sort", 5)
                                put("singermid", singermid)
                                put("sin", (page - 1) * limit)
                                put("num", limit)
                            },
                        )
                    },
                )
            }
            val json = uGet(payload) ?: return@withContext emptyList()
            val arr = json.optJSONObject("req_1")?.optJSONObject("data")
                ?.optJSONArray("songlist") ?: return@withContext emptyList()
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.toSong() }
        }

    suspend fun albumSongs(albumMid: String): List<Song> = withContext(Dispatchers.IO) {
        val json = yGet(
            "/v8/fcg-bin/fcg_v8_album_info_cp.fcg",
            mapOf("format" to "json", "outCharset" to "utf-8", "albummid" to albumMid),
        ) ?: return@withContext emptyList()
        val arr = json.optJSONObject("data")?.optJSONArray("list") ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.toSong() }
    }

    /* ---------- 封面 ---------- */

    fun cover(albumMid: String, size: String = "300x300"): String =
        if (albumMid.isBlank()) "" else "https://y.gtimg.cn/music/photo_new/T002R${size}M000$albumMid.jpg"

    /* ---------- 映射 ---------- */

    private fun JSONObject.toSong(): Song? {
        val mid = optString("songmid").ifBlank { optString("mid") }.ifBlank { optString("songMid") }
        if (mid.isBlank()) return null
        val name = optString("songname").ifBlank { optString("name") }.ifBlank { optString("title") }
        if (name.isBlank()) return null

        val artist = run {
            val arr = optJSONArray("singer")
            if (arr != null) {
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name") }
                    .filter { it.isNotBlank() }.joinToString("/")
            } else {
                optString("singername")
            }
        }

        val albumObj = optJSONObject("album")
        val albumName = albumObj?.optString("name")?.takeIf { it.isNotBlank() }
            ?: optString("albumname")
        val albumMid = albumObj?.optString("mid")?.takeIf { it.isNotBlank() }
            ?: optString("albummid")
        val mediaMid = optString("strMediaMid").ifBlank { optString("media_mid") }

        return Song(
            id = mid,
            name = name,
            artist = artist,
            album = albumName,
            source = "qq",
            // urlId 存 media_mid，取流时用（缺省回退 songmid）
            urlId = mediaMid,
            lyricId = mid,
            pic = cover(albumMid),
            albumId = albumMid,
        )
    }
}

/** QQ 榜单 */
data class QqTopList(
    val id: Int,
    val title: String,
    val pic: String,
    val listenCount: Long = 0,
)

/** QQ 歌手 */
data class QqSinger(
    val id: String,
    val name: String,
    val pic: String = "",
    val songCount: Int = 0,
    val albumCount: Int = 0,
    /** 热门专辑（来自搜索 zhida 的 hotalbum） */
    val albums: List<QqAlbum> = emptyList(),
)

/** QQ 专辑 */
data class QqAlbum(
    val id: String,
    val name: String,
)

/** QQ 歌单 */
data class QqPlaylist(
    val id: String,
    val name: String,
    val cover: String,
    val listenCount: Long = 0,
    val songCount: Int = 0,
)



