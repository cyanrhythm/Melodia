package com.lin0721.linmusic.desktop.platform.native

import com.lin0721.linmusic.desktop.platform.HotkeyAction
import com.lin0721.linmusic.desktop.platform.HotkeyCombo
import kotlinx.coroutines.flow.StateFlow

// 全局快捷键服务：Windows 用 RegisterHotKey + WM_HOTKEY，
// 其他平台暂用 no-op（X11 XGrabKey / Wayland 另行实现）。
interface GlobalHotkeyService {

    var onAction: ((HotkeyAction) -> Unit)?

    // 注册失败（多为被其他程序占用）的自定义快捷键
    val failed: StateFlow<Set<HotkeyAction>>

    fun start()

    fun stop()

    fun apply(custom: Map<HotkeyAction, HotkeyCombo?>, mediaKeys: Boolean)

    // 录制新组合期间暂停，避免按键被已注册的热键拦截
    fun pause()

    fun resume()
}
