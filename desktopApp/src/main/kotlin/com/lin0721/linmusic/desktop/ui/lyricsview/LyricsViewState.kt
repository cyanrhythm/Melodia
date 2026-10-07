package com.lin0721.linmusic.desktop.ui.lyricsview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lin0721.linmusic.desktop.ui.FullscreenState

// 全屏歌词界面的开关，与窗口全屏联动：只有“全屏”入口把窗口带进全屏时，收起歌词界面才一并退出窗口全屏
@Stable
class LyricsViewState internal constructor(private val fullscreen: FullscreenState) {
    var isOpen by mutableStateOf(false)
        private set

    private var enteredFullscreenWithView = false

    fun open() {
        enteredFullscreenWithView = false
        isOpen = true
    }

    // 窗口本来就是全屏时不算“带进去”，收起时保持全屏
    fun openWithFullscreen() {
        enteredFullscreenWithView = !fullscreen.isFullscreen
        if (enteredFullscreenWithView) fullscreen.toggle()
        isOpen = true
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        if (enteredFullscreenWithView) fullscreen.exit()
        enteredFullscreenWithView = false
    }

    // 手动切换窗口全屏后，窗口状态不再归属歌词界面
    fun toggleFullscreen() {
        enteredFullscreenWithView = false
        fullscreen.toggle()
    }
}

@Composable
fun rememberLyricsViewState(fullscreen: FullscreenState): LyricsViewState =
    remember(fullscreen) { LyricsViewState(fullscreen) }
