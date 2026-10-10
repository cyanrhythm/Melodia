package com.lin0721.linmusic.desktop.platform.native.linux

import com.lin0721.linmusic.desktop.platform.native.DesktopLyricBehavior
import java.awt.Window

// Linux 桌面歌词点击穿透（X11 shape / Wayland）尚未实现；先 no-op。
class NoopDesktopLyricBehavior : DesktopLyricBehavior {

    override fun setClickThrough(window: Window, enabled: Boolean) = Unit
}
