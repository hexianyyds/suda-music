package com.sodamusic.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sodamusic.app.playback.PlayerConnection
import com.sodamusic.app.ui.nav.AppRoot
import com.sodamusic.app.ui.player.PlayerViewModel
import com.sodamusic.app.ui.theme.SodaMusicTheme

val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection> {
    error("PlayerConnection not provided")
}

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // MediaController 绑定即会拉起 PlaybackService，无需显式 startForegroundService

        val connection = ServiceLocator.playerConnection
        setContent {
            // 主题跟随设置（深色 / 浅色 / 跟随系统）
            val settings by ServiceLocator.settingsStore.settings.collectAsState(
                initial = com.sodamusic.app.data.store.AppSettings(),
            )
            SodaMusicTheme(mode = settings.themeMode) {
                CompositionLocalProvider(LocalPlayerConnection provides connection) {
                    val playerVm: PlayerViewModel = viewModel {
                        PlayerViewModel(ServiceLocator.repository, ServiceLocator.settingsStore)
                    }
                    // 进入 App 即是苏打音乐
                    AppRoot(playerVm)
                }
            }
        }
    }
}




