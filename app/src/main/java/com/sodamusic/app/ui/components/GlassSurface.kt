package com.sodamusic.app.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 底部悬浮栏（迷你播放条 + 导航栏）的实时高度。
 *
 * 毛玻璃需要「内容绘制在栏下方」才能采样到真实像素，所以 NavHost 不再做底部留白，
 * 改由各列表自己按这个高度留出滚动余量。
 */
val LocalBottomBarHeight = compositionLocalOf<Dp> { 0.dp }

/**
 * 底部导航胶囊的显隐开关。
 * 全屏页面（如 QQ 发现页）把 value 置 false，即可临时隐藏悬浮胶囊。
 */
val LocalBottomBarVisible = androidx.compose.runtime.compositionLocalOf<androidx.compose.runtime.MutableState<Boolean>> {
    androidx.compose.runtime.mutableStateOf(true)
}
