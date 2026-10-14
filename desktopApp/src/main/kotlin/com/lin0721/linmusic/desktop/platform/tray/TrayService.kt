package com.lin0721.linmusic.desktop.platform.tray

// 托盘菜单的平台无关模型。
//
// Linux 会把它发布成 com.canonical.dbusmenu，由托盘宿主用自己的 QMenu 渲染
// （外观/缩放/键盘导航都是原生样式）；Windows 仍由 Compose 绘制菜单，
// 模型不参与渲染，仅保持接口一致。
data class TrayMenuModel(
    val header: String?,
    val nodes: List<TrayMenuNode>,
)

sealed interface TrayMenuNode {
    data class Action(val label: String, val enabled: Boolean = true, val onSelect: () -> Unit) : TrayMenuNode

    data class Toggle(val label: String, val checked: Boolean, val onSelect: () -> Unit) : TrayMenuNode

    data object Separator : TrayMenuNode
}

// 系统托盘集成。
//
// Windows：AWT TrayIcon，系统托盘会派发鼠标事件（左键激活 / 右键菜单）。
// Linux：优先 StatusNotifierItem（SNI over D-Bus）。KDE Wayland 下系统托盘展示的是
// xembedsniproxy 桥接出的 SNI 项，点击被转发为合成 X11 事件，AWT/XEmbed 图标收不到
// （实测无任何鼠标事件），且 XEmbed 渲染不吃 alpha（图标圆角外会被填白）；
// SNI 由托盘宿主直接回调 Activate/ContextMenu，点击可靠、IconPixmap 支持透明。
// 找不到 SNI Watcher 的宿主回退 AWT（仅保证图标可见）。
interface TrayService {

    // 是否可用：决定关闭窗口时"隐藏到托盘"还是直接退出
    val supported: Boolean

    // iconPng 为带 alpha 的 PNG；onActivate/onContextMenu 回传屏幕像素坐标
    fun install(
        tooltip: String,
        iconPng: ByteArray,
        onActivate: (x: Int, y: Int) -> Unit,
        onContextMenu: (x: Int, y: Int) -> Unit
    )

    fun updateTooltip(tooltip: String)

    // 菜单内容更新。Linux 会发布成 DBusMenu 交给托盘宿主用原生样式渲染；
    // Windows 忽略（菜单仍由 Compose 绘制，点击也不经过平台层）
    fun updateMenu(menu: TrayMenuModel)

    fun uninstall()
}
