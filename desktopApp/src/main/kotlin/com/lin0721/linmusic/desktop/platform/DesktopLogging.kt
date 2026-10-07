package com.lin0721.linmusic.desktop.platform

import com.lin0721.linmusic.core.AppEnvironment
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private const val TAG = "DesktopLogging"

// 桌面端日志与崩溃记录的启动入口，须先于 Koin 与其余任何 AppLogger 调用
object DesktopLogging {

    // gradle run 时由构建脚本传入，安装包不带此参数即为发布环境
    const val DEBUG_PROPERTY = "melodia.debug"

    fun install(
        logDir: File = DesktopPaths.logDir,
        savedLevel: () -> String = {
            val settings = SettingsPreferences(PreferencesStores.get(DesktopPaths.preferencesFile(PreferencesStores.SETTINGS)))
            runBlocking { settings.logLevel.first() }
        }
    ) {
        AppEnvironment.isDebug = System.getProperty(DEBUG_PROPERTY) == "true"
        AppLogger.init(logDir, savedLevel)
        DesktopCrashHandler.install()
        AppLogger.i(TAG, "启动 version=${AppInfo.version} debug=${AppEnvironment.isDebug}")
    }
}
