package com.sodamusic.app.data.model

import kotlinx.serialization.Serializable

/** 一首歌曲；字段语义与 Solara constants.js 中 API.search 返回结构一致 */
@Serializable
data class Song(
    val id: String,
    val name: String,
    val artist: String,
    val album: String = "",
    val source: String = "netease",
    val picId: String = "",
    val urlId: String = "",
    val lyricId: String = "",
    /** 部分榜单接口直接给出封面地址 */
    val pic: String = "",
    /** 专辑 mid（QQ 源用于进入专辑页） */
    val albumId: String = "",
)

/** 播放模式，对应 Solara audio.js 的播放模式逻辑 */
enum class PlayMode {
    SEQUENTIAL, LIST_LOOP, SINGLE, SHUFFLE;

    fun next(): PlayMode = entries[(ordinal + 1) % entries.size]

    val label: String
        get() = when (this) {
            SEQUENTIAL -> "顺序播放"
            LIST_LOOP -> "列表循环"
            SINGLE -> "单曲循环"
            SHUFFLE -> "随机播放"
        }
}

data class SourceOption(val value: String, val label: String)

data class QualityOption(val value: String, val label: String, val description: String)

object Constants {
    /** 支持的音源，与 Solara SOURCE_OPTIONS 保持一致 */
    val SOURCE_OPTIONS = listOf(
        SourceOption("netease", "网易云音乐"),
        SourceOption("kuwo", "酷我音乐"),
        SourceOption("joox", "JOOX音乐"),
        SourceOption("bilibili", "哔哩哔哩"),
        // 直连 QQ 音乐官方接口（免登录；独家/VIP 原唱受版权限制，翻唱与免费曲可播）
        SourceOption("qq", "QQ音乐"),
    )

    /** 音质选项，与 Solara QUALITY_OPTIONS 保持一致（999 = FLAC 无损） */
    val QUALITY_OPTIONS = listOf(
        QualityOption("128", "标准音质", "128 kbps"),
        QualityOption("192", "高品音质", "192 kbps"),
        QualityOption("320", "极高音质", "320 kbps"),
        QualityOption("999", "无损音质", "FLAC"),
    )

    /** 网易云官方榜单，与 Solara RADAR_PLAYLISTS 保持一致 */
    val RADAR_PLAYLISTS = listOf(
        RadarPlaylist("3778678", "热歌榜", "网易云音乐官方热歌榜"),
        RadarPlaylist("19723756", "飙升榜", "网易云音乐官方飙升榜"),
        RadarPlaylist("3779629", "新歌榜", "网易云音乐官方新歌榜"),
        RadarPlaylist("13372522766", "潮流风向榜", "网易云音乐官方潮流风向榜"),
        RadarPlaylist("2884035", "原创榜", "网易云音乐官方原创榜"),
        RadarPlaylist("14028249541", "网易云全球说唱榜", "网易云音乐全球说唱榜"),
        RadarPlaylist("60198", "美国Billboard榜", "网易云音乐美国Billboard榜"),
    )

    val DEFAULT_RADAR_IDS = setOf("3778678", "19723756", "3779629")

    const val DEFAULT_API_BASE = "https://music-api.gdstudio.xyz/api.php"
    const val PAGE_SIZE = 30

    /** 内置音源：洛雪源（原内置的聚合接口，即上方 API 地址） */
    const val DATA_SOURCE_LX = "lx"

    /** 内置音源：网易源（官方接口直连，不依赖任何三方服务） */
    const val DATA_SOURCE_NETEASE = "netease_direct"

    val DATA_SOURCE_OPTIONS = listOf(
        DataSourceOption(DATA_SOURCE_LX, "洛雪源", "原内置接口（聚合网易云/酷我/JOOX/B站）"),
        DataSourceOption(DATA_SOURCE_NETEASE, "网易源", "官方接口直连，不依赖三方服务"),
    )
}

data class DataSourceOption(val value: String, val label: String, val description: String)

data class RadarPlaylist(val id: String, val name: String, val description: String)

fun dataSourceLabel(value: String): String =
    Constants.DATA_SOURCE_OPTIONS.firstOrNull { it.value == value }?.label ?: value

fun sourceLabel(source: String): String =
    Constants.SOURCE_OPTIONS.firstOrNull { it.value == source }?.label ?: source

fun qualityOption(value: String): QualityOption =
    Constants.QUALITY_OPTIONS.firstOrNull { it.value == value } ?: Constants.QUALITY_OPTIONS[2]
