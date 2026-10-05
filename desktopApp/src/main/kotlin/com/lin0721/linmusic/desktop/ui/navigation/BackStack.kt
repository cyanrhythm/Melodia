package com.lin0721.linmusic.desktop.ui.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.derivedStateOf

sealed interface DesktopRoute {
    data object Home : DesktopRoute
    data class Playlist(val id: Long, val title: String, val isAlbum: Boolean = false) : DesktopRoute
    data object Search : DesktopRoute
    data object Browse : DesktopRoute
    data class PlaylistCategory(val name: String) : DesktopRoute
    data class Artist(val id: Long, val name: String) : DesktopRoute
    data class Style(val id: Long, val name: String) : DesktopRoute
    data object Settings : DesktopRoute
}

// 浏览器式历史：新导航清空前进栈，前进/后退只在两栈间搬移
@Stable
class BackStack(start: DesktopRoute) {
    private val history = mutableStateListOf(start)
    private val forwardStack = mutableStateListOf<DesktopRoute>()

    val current: DesktopRoute by derivedStateOf { history.last() }
    val canGoBack: Boolean by derivedStateOf { history.size > 1 }
    val canGoForward: Boolean by derivedStateOf { forwardStack.isNotEmpty() }

    fun navigate(route: DesktopRoute) {
        if (route == history.last()) return
        history.add(route)
        forwardStack.clear()
    }

    fun back() {
        if (history.size <= 1) return
        forwardStack.add(history.removeAt(history.lastIndex))
    }

    fun forward() {
        if (forwardStack.isEmpty()) return
        history.add(forwardStack.removeAt(forwardStack.lastIndex))
    }
}
