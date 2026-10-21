package com.sodamusic.app.data.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sodamusic.app.data.model.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "soda_settings")

data class AppSettings(
    val apiBase: String = Constants.DEFAULT_API_BASE,
    val quality: String = "320",
    val source: String = "netease",
    val radarIds: Set<String> = Constants.DEFAULT_RADAR_IDS,
    /** 内置音源：洛雪源（[Constants.DATA_SOURCE_LX]）/ 网易源（[Constants.DATA_SOURCE_NETEASE]） */
    val dataSource: String = Constants.DATA_SOURCE_LX,
    /** 选洛雪源时：其故障则自动切换到网易源 */
    val autoFallback: Boolean = true,
    /** 可选：网易云登录 Cookie（MUSIC_U=...），配置后可解析 VIP / 高音质曲目 */
    val neteaseCookie: String = "",
    /** 桌面歌词文字颜色（ARGB） */
    val lyricColor: Long = 0xFFFFFFFFL,
    /** 主题模式：system / light / dark */
    val themeMode: String = "system",
)

/** 应用设置持久化（对应 Solara 的 localStorage 设置项） */
class SettingsStore(private val context: Context) {

    private object Keys {
        val API_BASE = stringPreferencesKey("apiBase")
        val QUALITY = stringPreferencesKey("playbackQuality")
        val SOURCE = stringPreferencesKey("searchSource")
        val RADAR_IDS = stringSetPreferencesKey("radarSettings")
        val DATA_SOURCE = stringPreferencesKey("dataSource")
        val AUTO_FALLBACK = booleanPreferencesKey("autoFallback")
        val NETEASE_COOKIE = stringPreferencesKey("neteaseCookie")
        val LYRIC_COLOR = longPreferencesKey("lyricColor")
        val THEME_MODE = stringPreferencesKey("themeMode")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            apiBase = p[Keys.API_BASE]?.takeIf { it.isNotBlank() } ?: Constants.DEFAULT_API_BASE,
            quality = p[Keys.QUALITY] ?: "320",
            source = p[Keys.SOURCE] ?: "netease",
            radarIds = p[Keys.RADAR_IDS]?.takeIf { it.isNotEmpty() } ?: Constants.DEFAULT_RADAR_IDS,
            dataSource = p[Keys.DATA_SOURCE]?.takeIf { it.isNotBlank() } ?: Constants.DATA_SOURCE_LX,
            autoFallback = p[Keys.AUTO_FALLBACK] ?: true,
            neteaseCookie = p[Keys.NETEASE_COOKIE].orEmpty(),
            lyricColor = p[Keys.LYRIC_COLOR] ?: 0xFFFFFFFFL,
            themeMode = p[Keys.THEME_MODE] ?: "system",
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setApiBase(value: String) {
        context.settingsDataStore.edit { it[Keys.API_BASE] = value.trim() }
    }

    suspend fun setQuality(value: String) {
        context.settingsDataStore.edit { it[Keys.QUALITY] = value }
    }

    suspend fun setSource(value: String) {
        context.settingsDataStore.edit { it[Keys.SOURCE] = value }
    }

    suspend fun setRadarIds(value: Set<String>) {
        context.settingsDataStore.edit { it[Keys.RADAR_IDS] = value }
    }

    suspend fun setDataSource(value: String) {
        context.settingsDataStore.edit { it[Keys.DATA_SOURCE] = value }
    }

    suspend fun setAutoFallback(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_FALLBACK] = value }
    }

    suspend fun setNeteaseCookie(value: String) {
        context.settingsDataStore.edit { it[Keys.NETEASE_COOKIE] = value.trim() }
    }

    suspend fun setLyricColor(value: Long) {
        context.settingsDataStore.edit { it[Keys.LYRIC_COLOR] = value }
    }

    suspend fun setThemeMode(value: String) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = value }
    }
}


