package com.lin0721.linmusic.desktop.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf

// 跨页面跳转与全局提示，避免逐层透传回调
@Immutable
class DesktopNavigator(
    val isLoggedIn: Boolean,
    val openArtist: (id: Long, name: String) -> Unit,
    val openPlaylist: (id: Long, name: String) -> Unit,
    val openAlbum: (id: Long, name: String) -> Unit,
    val showMessage: (String) -> Unit
)

val LocalDesktopNavigator = compositionLocalOf<DesktopNavigator> {
    error("DesktopNavigator 未提供")
}
