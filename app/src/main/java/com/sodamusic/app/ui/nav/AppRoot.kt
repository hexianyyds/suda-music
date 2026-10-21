package com.sodamusic.app.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.MeshGradientPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.sodamusic.app.LocalPlayerConnection
import com.sodamusic.app.data.store.LocalCacheStore
import com.sodamusic.app.ui.components.LocalBottomBarHeight
import com.sodamusic.app.playback.PlaybackBus
import com.sodamusic.app.ui.cache.CacheScreen
import com.sodamusic.app.ui.home.HomeScreen
import com.sodamusic.app.ui.library.LibraryScreen
import com.sodamusic.app.ui.player.PlayerScreen
import com.sodamusic.app.ui.mine.MineScreen
import com.sodamusic.app.ui.player.PlayerViewModel
import com.sodamusic.app.ui.search.SearchScreen
import com.sodamusic.app.ui.theme.SodaRed

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val PLAYER = "player"
    const val CACHE = "cache"
}

/** 启动自动播放只触发一次（每个进程） */
private var cacheAutoPlayed = false

@Composable
fun AppRoot(playerVm: PlayerViewModel) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val playerState by playerVm.state.collectAsState()

    // 启动默认定位：有缓存则默认打开缓存页并自动接着播，否则落乐库
    var startRoute by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val hasCache = LocalCacheStore.cached.value.isNotEmpty()
        startRoute = if (hasCache) Routes.CACHE else Routes.HOME
        if (hasCache && !cacheAutoPlayed) {
            cacheAutoPlayed = true
            val songs = LocalCacheStore.cached.value.map { it.song }
            if (!playerVm.state.value.isPlaying && PlaybackBus.currentSong == null) {
                playerVm.setQueueAndPlay(songs, 0)
            }
        }
    }

    val ready = startRoute != null
    if (!ready) {
        Box(Modifier.fillMaxSize())
        return
    }

    // 毛玻璃：内容层作为采样源，底部悬浮栏作为效果层
    val hazeState = rememberHazeState()

    // 玻璃底：真实背景模糊 + 深色着色，比纯毛玻璃更实一点保证图标可读
    val barBlurStyle = remember {
        HazeBlurStyle {
            blurRadius(28.dp)
            backgroundColor(Color(0xFF0D0D12).copy(alpha = 0.58f))
            noiseFactor(0.02f)
        }
    }

    // 液态玻璃（Liquid Glass）：折射 + Fresnel + 镜面高光 + 边缘光照
    // 四个 tab 统一为乐库那套：clear 预设 + 极淡中性灰 tint，背景色彩直接透出
    val liquidGlassStyle = remember {
        GlassStyle.clear.then {
            shape(RoundedCornerShape(28.dp))
            tint(Color(0xFF9AA0A6).copy(alpha = 0.05f))
            edgeShadow(Color.Black.copy(alpha = 0.22f))
            edgeSoftness(1.2.dp)
        }
    }


    // 网格渐变背景：深色底 + 低饱和红/紫/蓝，为毛玻璃提供可采样的色彩层次
    val meshBackground = remember {
        MeshGradientPainter(rows = 2, columns = 2) {
            setVertex(0, 0, Offset(0f, 0f), Color(0xFF24101A))
            setVertex(0, 1, Offset(0.55f, 0f), Color(0xFF1A1220))
            setVertex(0, 2, Offset(1f, 0f), Color(0xFF101823))
            setVertex(1, 0, Offset(0f, 0.45f), Color(0xFF2E1220))
            setVertex(1, 1, Offset(0.5f, 0.5f), Color(0xFF140E14))
            setVertex(1, 2, Offset(1f, 0.5f), Color(0xFF0F1620))
            setVertex(2, 0, Offset(0f, 1f), Color(0xFF1C0D14))
            setVertex(2, 1, Offset(0.5f, 1f), Color(0xFF120C12))
            setVertex(2, 2, Offset(1f, 1f), Color(0xFF0B1118))
        }
    }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val bottomBarHeight = with(LocalDensity.current) { bottomBarHeightPx.toDp() }

    val bottomBarVisible = remember { androidx.compose.runtime.mutableStateOf(true) }
    CompositionLocalProvider(
        LocalBottomBarHeight provides bottomBarHeight,
        com.sodamusic.app.ui.components.LocalBottomBarVisible provides bottomBarVisible,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .paint(meshBackground),
        ) {
            NavHost(
                navController = navController,
                startDestination = startRoute!!,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
                // 播放页：从底部推入 / 下滑收起，配合 Android 13+ 预测性返回手势
                enterTransition = {
                    if (targetState.destination.route == Routes.PLAYER) {
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, animationSpec = tween(320))
                    } else {
                        fadeIn(tween(200))
                    }
                },
                exitTransition = {
                    if (initialState.destination.route == Routes.PLAYER) {
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, animationSpec = tween(320))
                    } else {
                        fadeOut(tween(160))
                    }
                },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = {
                    if (initialState.destination.route == Routes.PLAYER) {
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, animationSpec = tween(320))
                    } else {
                        fadeOut(tween(160))
                    }
                },
                // 预测性返回：手指拖动过程中由系统驱动，这里给出与 pop 一致的预览动画
                predictivePopEnterTransition = { fadeIn(tween(200)) },
                predictivePopExitTransition = {
                    if (initialState.destination.route == Routes.PLAYER) {
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, animationSpec = tween(320))
                    } else {
                        fadeOut(tween(160))
                    }
                },
            ) {
                composable(Routes.CACHE) { CacheScreen(playerVm) }
                composable(Routes.HOME) { HomeScreen(playerVm) { navController.navigate(Routes.PLAYER) } }
                composable(Routes.SEARCH) { SearchScreen(playerVm) }
                composable(Routes.LIBRARY) { MineScreen(playerVm) }
                composable(Routes.PLAYER) { PlayerScreen(playerVm) { navController.popBackStack() } }
            }

            // 悬浮扁椭圆毛玻璃胶囊（左右 16 / 底部 8 / 圆角 28 / 高 64）
            // 发现页等全屏页面会通过 LocalBottomBarVisible 隐藏它
            if (bottomBarVisible.value) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { bottomBarHeightPx = it.height }
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp)
                    .shadow(
                        elevation = 14.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = Color.Black.copy(alpha = 0.35f),
                        spotColor = Color.Black.copy(alpha = 0.50f),
                    )
                    // Adaptive 会在图片多/滚动频繁的页面（乐库、搜索）自动降级成普通模糊，
                    // 固定 Quality 才能保住液态玻璃的折射与高光
                    .hazeGlass(
                        input = HazeInput.Sources(hazeState),
                        style = liquidGlassStyle,
                        performanceMode = HazePerformanceMode.Quality,
                    ),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    // 控制栏：一旦播放过就常驻（暂停也显示），
                    // 只有冷启动、从未播放（无进度）时才隐藏
                    AnimatedVisibility(
                        visible = playerState.song != null &&
                            (playerState.isPlaying || playerState.position > 0) &&
                            currentRoute != Routes.PLAYER,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) {
                        MiniPlayerBar(playerVm) { navController.navigate(Routes.PLAYER) }
                    }
                    AnimatedVisibility(visible = currentRoute != Routes.PLAYER) {
                        BottomNavBar(navController, currentRoute)
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun BottomNavBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar(
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0),
        // 扁一点：默认 80dp 改成 64dp
        modifier = Modifier.height(64.dp),
    ) {
        val items = listOf(
            Triple("乐库", Icons.Filled.Home, Routes.HOME),
            Triple("搜索", Icons.Filled.Search, Routes.SEARCH),
            Triple("缓存", Icons.Filled.DownloadDone, Routes.CACHE),
            Triple("我的", Icons.Filled.LibraryMusic, Routes.LIBRARY),
        )
        items.forEach { (label, icon, route) ->
            NavigationBarItem(
                selected = currentRoute == route,
                onClick = {
                    navController.navigate(route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = SodaRed,
                    selectedTextColor = SodaRed,
                    indicatorColor = SodaRed.copy(alpha = 0.22f),
                    // 跟随主题：深色下灰白、浅色下深灰，保证两种模式都可读
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/** 底部迷你播放条：封面 + 歌名/歌手 + 播放暂停 + 下一首，点击进全屏 */
@Composable
fun MiniPlayerBar(playerVm: PlayerViewModel, onOpenPlayer: () -> Unit) {
    val state by playerVm.state.collectAsState()
    val song = state.song ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenPlayer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = state.coverUrl.ifBlank { song.pic },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(42.dp)
                .clip(MaterialTheme.shapes.small),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        ) {
            Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = {
            if (state.duration > 0) {
                playerVm.togglePlayPause()
            } else {
                // 尚未加载媒体（如刚恢复的展示队列）：直接从当前队列指针对应歌曲开播
                val q = PlaybackBus.queue.value
                val i = PlaybackBus.index.value.coerceAtLeast(0)
                if (q.isNotEmpty()) playerVm.setQueueAndPlay(q, i) else playerVm.togglePlayPause()
            }
        }) {
            Icon(
                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (state.isPlaying) "暂停" else "播放",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = { playerVm.next() }) {
            Icon(Icons.Filled.SkipNext, contentDescription = "下一首", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}




