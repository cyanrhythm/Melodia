package com.lin0721.linmusic.desktop.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import com.lin0721.linmusic.core.model.Track

// 跨页面跳转与全局提示，避免逐层透传回调
@Immutable
class DesktopNavigator(
    val isLoggedIn: Boolean,
    val openArtist: (id: Long, name: String) -> Unit,
    val openPlaylist: (id: Long, name: String) -> Unit,
    val openAlbum: (id: Long, name: String) -> Unit,
    val openRadio: (id: Long) -> Unit,
    val openPodcastSubscribed: () -> Unit,
    val openPodcastToplist: () -> Unit,
    val openPodcastCategory: (id: Long, name: String) -> Unit,
    val openLogin: () -> Unit,
    // 下载音质跟随播放音质设置
    val downloadLevel: String,
    val downloadTrack: (Track) -> Unit,
    val showMessage: (String) -> Unit,
    val goBack: () -> Unit
)

val LocalDesktopNavigator = compositionLocalOf<DesktopNavigator> {
    error("DesktopNavigator 未提供")
}
