package com.lin0721.linmusic.desktop.platform.linux

import com.lin0721.linmusic.desktop.platform.AutoStartService

// Linux XDG autostart 尚未实现；先返回不支持，后续写 ~/.config/autostart 下的 .desktop。
internal class LinuxAutoStart : AutoStartService {

    override val unavailableReason: String? = "当前系统暂不支持"

    override fun isEnabled(): Boolean = false

    override fun setEnabled(enabled: Boolean): Boolean = false
}
