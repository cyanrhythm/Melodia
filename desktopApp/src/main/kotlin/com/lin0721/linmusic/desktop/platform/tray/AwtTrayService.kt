package com.lin0721.linmusic.desktop.platform.tray

import java.awt.SystemTray

// 通用 AWT 托盘实现：Windows 的主路径，也是 Linux 无 SNI Watcher 时的兜底。
// 桌面环境会派发左键激活与右键菜单事件（Linux/XEmbed 下只保证图标可见）。
class AwtTrayService : TrayService {

    private var impl: AwtTrayIcon? = null

    override val supported: Boolean
        get() = runCatching { SystemTray.isSupported() }.getOrDefault(false)

    override fun install(
        tooltip: String,
        iconPng: ByteArray,
        onActivate: (x: Int, y: Int) -> Unit,
        onContextMenu: (x: Int, y: Int) -> Unit
    ) {
        impl = AwtTrayIcon(iconPng, tooltip, { onActivate(0, 0) }, onContextMenu).also { it.install() }
    }

    override fun updateTooltip(tooltip: String) {
        impl?.updateTooltip(tooltip)
    }

    // AWT 托盘不承载菜单（菜单仍由 Compose 绘制，点击也不经过平台层）
    override fun updateMenu(menu: TrayMenuModel) = Unit

    override fun uninstall() {
        impl?.uninstall()
        impl = null
    }
}
