package com.sodamusic.app.data.api

import kotlinx.serialization.Serializable

/** GD 音乐台 search 接口返回的单曲结构 */
@Serializable
data class SearchItemDto(
    val id: String? = null,
    val name: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val source: String? = null,
    val pic_id: String? = null,
    val url_id: String? = null,
    val lyric_id: String? = null,
)

/** types=url 返回结构 */
@Serializable
data class SongUrlDto(
    val url: String? = null,
    val size: Long? = null,
    val br: String? = null,
)

/** types=pic 返回结构 */
@Serializable
data class PicDto(val url: String? = null)

/** types=lyric 返回结构 */
@Serializable
data class LyricDto(val lyric: String? = null)

/** types=playlist（网易云歌单）返回结构，仅解析雷达所需字段 */
@Serializable
data class PlaylistDto(val playlist: PlaylistBodyDto? = null)

@Serializable
data class PlaylistBodyDto(val tracks: List<TrackDto> = emptyList())

@Serializable
data class TrackDto(
    val id: Long? = null,
    val name: String? = null,
    val ar: List<TrackArtistDto>? = null,
    val al: TrackAlbumDto? = null,
)

@Serializable
data class TrackArtistDto(val name: String? = null)

@Serializable
data class TrackAlbumDto(
    val name: String? = null,
    val picUrl: String? = null,
    val pic_str: String? = null,
    val pic: String? = null,
)
