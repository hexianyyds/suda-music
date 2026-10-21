package com.sodamusic.app.data.store

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/** 个人资料：昵称 + 头像 + 背景图（图片以文件形式保存在应用私有目录） */
data class UserProfile(
    val nickname: String = "音乐爱好者",
    val avatarPath: String = "",
    val bannerPath: String = "",
)

val Context.profileDataStore by preferencesDataStore(name = "soda_profile")

class ProfileStore(private val context: Context) {

    private object Keys {
        val NICKNAME = stringPreferencesKey("nickname")
        val AVATAR = stringPreferencesKey("avatarPath")
        val BANNER = stringPreferencesKey("bannerPath")
    }

    private val dir: File
        get() = File(context.filesDir, "profile").apply { if (!exists()) mkdirs() }

    val profile: Flow<UserProfile> = context.profileDataStore.data.map { p ->
        UserProfile(
            nickname = p[Keys.NICKNAME]?.takeIf { it.isNotBlank() } ?: "音乐爱好者",
            avatarPath = p[Keys.AVATAR].orEmpty(),
            bannerPath = p[Keys.BANNER].orEmpty(),
        )
    }

    suspend fun setNickname(value: String) {
        context.profileDataStore.edit { it[Keys.NICKNAME] = value.trim().ifBlank { "音乐爱好者" } }
    }

    /**
     * 保存头像：把相册选中的图片复制进私有目录，返回本地路径。
     * 直接存 content:// Uri 在重启后可能失效，所以复制一份。
     */
    suspend fun setAvatar(uri: Uri): String? = saveImage(uri, "avatar.jpg", Keys.AVATAR)

    /** 保存背景图 */
    suspend fun setBanner(uri: Uri): String? = saveImage(uri, "banner.jpg", Keys.BANNER)

    private suspend fun saveImage(uri: Uri, name: String, key: androidx.datastore.preferences.core.Preferences.Key<String>): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = File(dir, name)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { out -> input.copyTo(out) }
                } ?: return@runCatching null
                context.profileDataStore.edit { it[key] = target.absolutePath }
                target.absolutePath
            }.getOrNull()
        }

    /** 清掉某个图片（避免换图后旧文件堆积） */
    fun clearImage(path: String) {
        if (path.isBlank()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }
}
