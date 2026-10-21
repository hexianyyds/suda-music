package com.sodamusic.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 汽水风格暗色主题：近黑背景 + 高饱和“汽水红”强调色
val SodaRed = Color(0xFFFF3B5C)
val SodaRedDim = Color(0xFFB22A42)
val SodaBg = Color(0xFF0E0E10)
val SodaSurface = Color(0xFF17171A)
val SodaSurfaceHigh = Color(0xFF232327)
val SodaTextDim = Color(0xFF9E9EA6)

private val SodaColorScheme = darkColorScheme(
    primary = SodaRed,
    onPrimary = Color.White,
    secondary = SodaRedDim,
    background = SodaBg,
    onBackground = Color.White,
    surface = SodaSurface,
    onSurface = Color.White,
    surfaceVariant = SodaSurfaceHigh,
    onSurfaceVariant = SodaTextDim,
    surfaceContainer = SodaSurface,
    surfaceContainerHigh = SodaSurfaceHigh,
    outline = Color(0xFF3A3A40),
)

/** 浅色主题：同一套汽水红强调，背景转为浅灰白 */
private val SodaLightColorScheme = lightColorScheme(
    primary = SodaRed,
    onPrimary = Color.White,
    secondary = SodaRedDim,
    background = Color(0xFFF7F7FA),
    onBackground = Color(0xFF16161A),
    surface = Color.White,
    onSurface = Color(0xFF16161A),
    surfaceVariant = Color(0xFFEFEFF3),
    onSurfaceVariant = Color(0xFF6B6B75),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF2F2F6),
    outline = Color(0xFFD5D5DD),
)

/**
 * @param mode 主题模式：`system` 跟随系统 / `light` 浅色 / `dark` 深色
 */
@Composable
fun SodaMusicTheme(mode: String = "system", content: @Composable () -> Unit) {
    val dark = when (mode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) SodaColorScheme else SodaLightColorScheme, content = content)
}

