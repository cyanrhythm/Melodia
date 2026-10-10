package com.lin0721.linmusic.desktop.platform.native

import java.awt.Window

// 桌面歌词窗口的点击穿透：Windows 用 WS_EX_TRANSPARENT，
// 其他平台暂为 no-op（X11 shape / Wayland 另行实现）。
interface DesktopLyricBehavior {

    fun setClickThrough(window: Window, enabled: Boolean)
}
