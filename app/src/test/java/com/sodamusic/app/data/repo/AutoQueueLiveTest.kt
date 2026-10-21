package com.sodamusic.app.data.repo

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/** 自动续播队列：从网易源与 QQ 音乐源混合随机抽取 */
class AutoQueueLiveTest {

    @Test
    fun fillMixesNeteaseAndQq() = runBlocking {
        val list = AutoQueue.fill(30)
        val netease = list.count { it.source == "netease" }
        val qq = list.count { it.source == "qq" }
        val other = list.size - netease - qq

        assertTrue("应抽到 30 首左右，实际 ${list.size}", list.size >= 20)
        assertTrue("应包含网易源曲目，实际 $netease", netease > 0)
        assertTrue("应包含 QQ 音乐源曲目，实际 $qq", qq > 0)
    }

    @Test
    fun appendMoreAddsFreshSongs() = runBlocking {
        AutoQueue.fill(30)
        val before = AutoQueue.songs.value
        val more = AutoQueue.appendMore(30)
        val after = AutoQueue.songs.value
        assertTrue("应追加新曲目", more.isNotEmpty())
        assertTrue("队列应变长", after.size > before.size)
        // 不应有重复
        val dup = after.groupBy { "${it.source}:${it.id}" }.filter { it.value.size > 1 }
        assertTrue("不应出现重复曲目", dup.isEmpty())
    }
}
