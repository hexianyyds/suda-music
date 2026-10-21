package com.sodamusic.app.ui.lyric

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.ListenableFuture
import com.sodamusic.app.playback.PlaybackBus
import com.sodamusic.app.playback.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 桌面歌词悬浮窗。
 *
 * - 用 WindowManager 叠加一层 TextView（TYPE_APPLICATION_OVERLAY）
 * - 通过 MediaController 连到 [PlaybackService] 读播放进度
 * - 歌词取自 [PlaybackBus.lyricLines]（UI 侧解析后写入）
 */
class DesktopLyricService : Service() {

    companion object {
        private const val TAG = "DesktopLyric"

        /** 颜色即时覆盖（App 内改色后立刻生效，无需重启服务） */
        @Volatile
        var colorOverride: Int? = null
        const val ACTION_START = "soda.lyric.START"
        const val ACTION_STOP = "soda.lyric.STOP"

        /** 悬浮窗是否在运行（App 内可用于开关状态） */
        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var lyricView: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var ticker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // 恢复上次保存的颜色
        if (colorOverride == null) {
            scope.launch {
                val saved = runCatching {
                    com.sodamusic.app.data.store.SettingsStore(applicationContext).current().lyricColor.toInt()
                }.getOrNull()
                if (saved != null && colorOverride == null) colorOverride = saved
            }
        }
        connectPlayer()
        ticker = scope.launch {
            while (true) {
                updateLyric()
                delay(300)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> if (lyricView == null) showWindow()
        }
        isRunning = true
        return START_STICKY
    }

    private fun connectPlayer() {
        val token = SessionToken(this, android.content.ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                controller = runCatching { future.get() }.getOrNull()
            },
            MoreExecutors.directExecutor(),
        )
    }

    private fun showWindow() {
        val wm = windowManager ?: return
        val view = TextView(this).apply {
            setTextColor(colorOverride ?: Color.WHITE)
            textSize = 18f
            setShadowLayer(6f, 0f, 2f, Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(48, 20, 48, 20)
            text = "桌面歌词"
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 260
        }

        // 拖动移动位置
        var downX = 0f
        var downY = 0f
        var startY = 0
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startY = lp.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = (downY - event.rawY).toInt()
                    lp.y = (startY + dy).coerceIn(0, 2000)
                    runCatching { wm.updateViewLayout(view, lp) }
                    true
                }
                else -> false
            }
        }

        runCatching { wm.addView(view, lp) }
            .onFailure { }
        lyricView = view
        params = lp
    }

    private fun updateLyric() {
        val view = lyricView ?: return
        view.setTextColor(colorOverride ?: Color.WHITE)
        val lines = PlaybackBus.lyricLines.value
        if (lines.isEmpty()) {
            view.text = PlaybackBus.currentSong?.let { "${it.name} - ${it.artist}" } ?: "桌面歌词"
            return
        }
        val pos = controller?.currentPosition ?: 0L
        // 二分找当前行（歌词按时间升序）
        var lo = 0
        var hi = lines.lastIndex
        var hit = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].timeMs <= pos) {
                hit = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        view.text = if (hit >= 0) lines[hit].text.ifBlank { "♪" } else "♪"
    }

    override fun onDestroy() {
        ticker?.cancel()
        scope.cancel()
        lyricView?.let { runCatching { windowManager?.removeView(it) } }
        lyricView = null
        runCatching { MediaController.releaseFuture(controllerFuture ?: return@runCatching) }
        controllerFuture = null
        controller = null
        isRunning = false
        super.onDestroy()
    }
}

