package com.sodamusic.app.data.api

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/** 真实网络验证：App 直连 QQ 音乐上游，不经过任何中间服务 */
class QqMusicApiLiveTest {

    @Test
    fun allCapabilities() = runBlocking {
        // 1. 搜索
        val songs = QqMusicApi.search("告白气球", limit = 10)
        songs.take(3).forEach {
        }
        assertTrue("搜索应有结果", songs.isNotEmpty())

        // 2. 播放直链（尝试前几首，统计成功率）
        var ok = 0
        songs.take(6).forEach { s ->
            val url = runCatching { QqMusicApi.playUrl(s.id, s.urlId, "128") }
                .getOrNull()
            if (!url.isNullOrBlank()) {
                ok++
            }
        }

        // 3. 歌词
        val lyric = QqMusicApi.lyric(songs.first().id)

        // 4. 热词
        val hot = QqMusicApi.hotKeys()

        // 5. 榜单索引
        val tops = QqMusicApi.topLists()
        tops.firstOrNull()?.let { top ->
            val rankSongs = QqMusicApi.rankSongs(top.id, limit = 10)
        }

        // 6. 歌单
        val lists = QqMusicApi.songLists(limit = 5)
        lists.firstOrNull()?.let {
            val detail = QqMusicApi.songListDetail(it.id)
        }

        // 7. 歌手热歌
        val singerSongs = QqMusicApi.singerHotsong("0025NhlN2yWrP4", limit = 10)

        // 8. 专辑
        val albumSongs = QqMusicApi.albumSongs("000MkMni19ClKG")

        // 9. 封面
    }
}
