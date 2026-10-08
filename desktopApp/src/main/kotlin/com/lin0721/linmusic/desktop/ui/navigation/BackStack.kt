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
    data class Radio(val id: Long) : DesktopRoute
    data object PodcastSubscribed : DesktopRoute
    data object PodcastToplist : DesktopRoute
    data class PodcastCategory(val id: Long, val name: String) : DesktopRoute
    data object Settings : DesktopRoute
}

// 历史栈帧：id 唯一，同一路由在历史里重复出现时仍可区分各自的页面现场
data class DesktopEntry(val id: Long, val route: DesktopRoute)

// 浏览器式历史：新导航清空前进栈，前进/后退只在两栈间搬移
@Stable
class BackStack(start: DesktopRoute) {
    private var nextEntryId = 0L
    private val history = mutableStateListOf(DesktopEntry(nextEntryId++, start))
    private val forwardStack = mutableStateListOf<DesktopEntry>()

    val currentEntry: DesktopEntry by derivedStateOf { history.last() }
    val current: DesktopRoute by derivedStateOf { currentEntry.route }
    val canGoBack: Boolean by derivedStateOf { history.size > 1 }
    val canGoForward: Boolean by derivedStateOf { forwardStack.isNotEmpty() }

    // 历史与前进栈里仍然存在的全部栈帧
    val liveEntryIds: Set<Long> get() = (history + forwardStack).mapTo(HashSet()) { it.id }

    fun navigate(route: DesktopRoute) {
        if (route == history.last().route) return
        history.add(DesktopEntry(nextEntryId++, route))
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
