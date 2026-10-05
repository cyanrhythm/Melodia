package com.lin0721.linmusic.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 配色取值与 Android 端 core/ui/theme/Color.kt 一致
object DesktopColors {
    val WindowBackground = Color(0xFF000000)
    val Pane = Color(0xFF121212)
    val PaneHover = Color(0xFF1F1F1F)
    val Surface = Color(0xFF282828)
    val SurfaceLight = Color(0xFF3E3E3E)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextGray = Color(0xFFB3B3B3)
    val Accent = Color(0xFFC20C0C)
    val CoverPlaceholder = Color(0xFF2C2C2C)
}

object DesktopDimens {
    val TitleBarHeight = 64.dp
    val PlayerBarHeight = 80.dp
    val PaneGap = 8.dp
    val PaneRadius = 8.dp
    val SidebarWidth = 300.dp
    val LibraryMinWidth = 280.dp
    val LibraryMaxWidth = 420.dp
    val LibraryRailWidth = 72.dp
    val NowPlayingWidth = 340.dp
    val NowPlayingMinWidth = 280.dp
    val NowPlayingMaxWidth = 420.dp
    val NowPlayingHandleWidth = 28.dp
    val NowPlayingPeekWidth = 72.dp
    val CenterMinWidth = 400.dp
    val PaneSnapWidth = 220.dp
}

@Composable
fun MelodiaDesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = DesktopColors.Accent,
            background = DesktopColors.WindowBackground,
            surface = DesktopColors.Pane,
            surfaceVariant = DesktopColors.Surface,
            onPrimary = DesktopColors.TextPrimary,
            onBackground = DesktopColors.TextPrimary,
            onSurface = DesktopColors.TextPrimary,
            onSurfaceVariant = DesktopColors.TextGray
        ),
        content = content
    )
}
