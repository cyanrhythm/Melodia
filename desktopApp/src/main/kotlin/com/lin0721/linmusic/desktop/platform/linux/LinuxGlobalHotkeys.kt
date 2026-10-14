package com.lin0721.linmusic.desktop.platform.linux

import com.lin0721.linmusic.desktop.platform.GlobalHotkeyService
import com.lin0721.linmusic.desktop.platform.HotkeyAction
import com.lin0721.linmusic.desktop.platform.HotkeyCombo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Linux 全局快捷键尚未实现（X11 XGrabKey / Wayland 需另行处理）；先全部 no-op。
internal class LinuxGlobalHotkeys : GlobalHotkeyService {

    override val isSupported: Boolean = false

    override var onAction: ((HotkeyAction) -> Unit)? = null

    override val failed: StateFlow<Set<HotkeyAction>> = MutableStateFlow(emptySet<HotkeyAction>()).asStateFlow()

    override fun start() = Unit

    override fun stop() = Unit

    override fun apply(custom: Map<HotkeyAction, HotkeyCombo?>, mediaKeys: Boolean) = Unit

    override fun pause() = Unit

    override fun resume() = Unit
}
