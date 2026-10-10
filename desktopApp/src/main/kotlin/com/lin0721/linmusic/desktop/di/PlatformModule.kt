package com.lin0721.linmusic.desktop.di

import com.lin0721.linmusic.desktop.platform.native.AutoStartManager
import com.lin0721.linmusic.desktop.platform.native.DesktopLyricBehavior
import com.lin0721.linmusic.desktop.platform.native.GlobalHotkeyService
import com.lin0721.linmusic.desktop.platform.native.SystemAccentProvider
import com.lin0721.linmusic.desktop.platform.native.SystemMediaSession
import com.lin0721.linmusic.desktop.platform.native.WindowDecoration
import com.lin0721.linmusic.desktop.platform.native.linux.LinuxAutoStartManager
import com.lin0721.linmusic.desktop.platform.native.linux.LinuxSystemMediaSession
import com.lin0721.linmusic.desktop.platform.native.linux.NoopDesktopLyricBehavior
import com.lin0721.linmusic.desktop.platform.native.linux.NoopGlobalHotkeyService
import com.lin0721.linmusic.desktop.platform.native.linux.NoopWindowDecoration
import com.lin0721.linmusic.desktop.platform.native.linux.NullSystemAccentProvider
import com.lin0721.linmusic.desktop.platform.native.windows.WindowsAutoStartManager
import com.lin0721.linmusic.desktop.platform.native.windows.WindowsDesktopLyricBehavior
import com.lin0721.linmusic.desktop.platform.native.windows.WindowsGlobalHotkeyService
import com.lin0721.linmusic.desktop.platform.native.windows.WindowsSystemAccentProvider
import com.lin0721.linmusic.desktop.platform.native.windows.WindowsSystemMediaSession
import com.lin0721.linmusic.desktop.platform.native.windows.WindowsWindowDecoration
import org.koin.dsl.module

// 全应用唯一的平台判断位置：新增平台只需在此登记实现，Main/Settings 等上层无需改动。
private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

val platformModule = module {
    if (isWindows) {
        single<SystemMediaSession> { WindowsSystemMediaSession() }
        single<GlobalHotkeyService> { WindowsGlobalHotkeyService() }
        single<AutoStartManager> { WindowsAutoStartManager() }
        single<WindowDecoration> { WindowsWindowDecoration() }
        single<SystemAccentProvider> { WindowsSystemAccentProvider() }
        single<DesktopLyricBehavior> { WindowsDesktopLyricBehavior() }
    } else {
        single<SystemMediaSession> { LinuxSystemMediaSession() }
        single<GlobalHotkeyService> { NoopGlobalHotkeyService() }
        single<AutoStartManager> { LinuxAutoStartManager() }
        single<WindowDecoration> { NoopWindowDecoration() }
        single<SystemAccentProvider> { NullSystemAccentProvider() }
        single<DesktopLyricBehavior> { NoopDesktopLyricBehavior() }
    }
}
