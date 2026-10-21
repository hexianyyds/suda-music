package com.sodamusic.app.data.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Context.listenDataStore by preferencesDataStore(name = "soda_listen")

/** 听歌时长统计（总时长 + 今日） */
data class ListenStats(
    val totalSeconds: Long = 0,
    val todaySeconds: Long = 0,
    val songCount: Int = 0,
)

class ListenStatsStore(private val context: Context) {

    private fun dayKey(date: String) = longPreferencesKey("day_$date")
    private val totalKey = longPreferencesKey("total_seconds")
    private val countKey = longPreferencesKey("played_count")

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    val stats: Flow<ListenStats> = context.listenDataStore.data.map { p ->
        ListenStats(
            totalSeconds = p[totalKey] ?: 0L,
            todaySeconds = p[dayKey(today())] ?: 0L,
            songCount = (p[countKey] ?: 0L).toInt(),
        )
    }

    /** 累加收听秒数（由播放服务周期性调用） */
    suspend fun addSeconds(seconds: Long) {
        if (seconds <= 0) return
        val dk = dayKey(today())
        context.listenDataStore.edit { p ->
            p[totalKey] = (p[totalKey] ?: 0L) + seconds
            p[dk] = (p[dk] ?: 0L) + seconds
        }
    }

    /** 记录一首歌开始播放（用于统计听过多少首） */
    suspend fun markPlayed() {
        context.listenDataStore.edit { p ->
            p[countKey] = (p[countKey] ?: 0L) + 1
        }
    }
}
