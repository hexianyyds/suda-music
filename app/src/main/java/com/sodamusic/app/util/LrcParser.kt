package com.sodamusic.app.util

/** LRC 歌词解析：支持一行多时间标签、无时间标签纯文本降级 */
data class LyricLine(val timeMs: Long, val text: String)

object LrcParser {

    private val timeRegex = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parse(raw: String): List<LyricLine> {
        if (raw.isBlank()) return emptyList()
        val lines = mutableListOf<LyricLine>()
        var rawTextCount = 0
        raw.lineSequence().forEach { line ->
            val matches = timeRegex.findAll(line).toList()
            if (matches.isEmpty()) {
                val text = line.trim()
                if (text.isNotBlank() && !text.startsWith("[")) {
                    rawTextCount++
                    lines.add(LyricLine(-1, text))
                }
                return@forEach
            }
            val text = line.substring(matches.last().range.last + 1).trim()
            if (text.isEmpty()) return@forEach
            matches.forEach { m ->
                val minutes = m.groupValues[1].toLong()
                val seconds = m.groupValues[2].toLong()
                val fracRaw = m.groupValues[3]
                val fracMs = when (fracRaw.length) {
                    0 -> 0L
                    1 -> fracRaw.toLong() * 100
                    2 -> fracRaw.toLong() * 10
                    else -> fracRaw.take(3).toLong()
                }
                lines.add(LyricLine(minutes * 60_000 + seconds * 1_000 + fracMs, text))
            }
        }
        // 纯文本无时间戳：整体降级为不可滚动歌词
        if (lines.all { it.timeMs < 0 }) {
            return lines.takeIf { rawTextCount > 0 } ?: emptyList()
        }
        return lines.filter { it.timeMs >= 0 }.sortedBy { it.timeMs }
    }

    /** 二分查找当前播放时间对应的歌词行下标 */
    fun currentIndex(lines: List<LyricLine>, positionMs: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var result = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].timeMs <= positionMs) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }
}
