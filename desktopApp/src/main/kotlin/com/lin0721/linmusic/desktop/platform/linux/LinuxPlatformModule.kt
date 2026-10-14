package com.lin0721.linmusic.desktop.platform.linux

import com.lin0721.linmusic.desktop.platform.AutoStartService
import com.lin0721.linmusic.desktop.platform.BrowserLocator
import com.lin0721.linmusic.desktop.platform.GlobalHotkeyService
import com.lin0721.linmusic.desktop.platform.MediaSessionService
import com.lin0721.linmusic.desktop.platform.WindowEffects
import com.lin0721.linmusic.desktop.platform.linux.LinuxTray
import com.lin0721.linmusic.desktop.platform.tray.TrayService
import org.koin.dsl.module
import java.io.File

val linuxPlatformModule = module {
    // 系统媒体控制：MPRIS over D-Bus
    single<MediaSessionService> { MprisSession() }
    // X11 XGrabKey / Wayland 待实现，暂为 no-op
    single<GlobalHotkeyService> { LinuxGlobalHotkeys() }
    // XDG autostart 待实现
    single<AutoStartService> { LinuxAutoStart() }
    // 无边框外观交给窗口管理器；桌面歌词穿透走 X11 Shape 扩展
    single<WindowEffects> { LinuxWindowEffects() }
    // 系统托托盘：优先 SNI，无宿主时回退 AWT
    single<TrayService> { LinuxTray() }
    single<BrowserLocator> { BrowserLocator(::findChromium) }
}

// 常见的 Chromium 内核浏览器可执行文件名
private val CHROMIUM_NAMES = listOf("chromium", "chromium-browser", "google-chrome", "google-chrome-stable", "brave-browser")

// 依次在 PATH 与常见安装目录中查找可执行的 Chromium 内核浏览器
private fun findChromium(): File? {
    val dirs = (System.getenv("PATH")?.split(File.pathSeparatorChar).orEmpty())
        .filter { it.isNotBlank() }
        .map { File(it) } + listOf(
        File("/usr/bin"), File("/usr/local/bin"), File("/snap/bin"), File("/opt/google/chrome")
    )
    return CHROMIUM_NAMES.asSequence()
        .flatMap { name -> dirs.asSequence().map { dir -> File(dir, name) } }
        .firstOrNull { it.isFile && it.canExecute() }
}
