package com.lin0721.linmusic.desktop.platform

import java.io.File

// 用户数据目录：Windows 取 %APPDATA%\Melodia，其他平台回退到用户主目录
object DesktopPaths {
    val dataDir: File by lazy {
        val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: System.getProperty("user.home")
        File(base, "Melodia").apply { mkdirs() }
    }

    // 运行日志目录，AppLogger 在其中滚动写入 app_log_0/1.txt
    val logDir: File by lazy { File(dataDir, "logs") }

    // 下载目录默认值：用户音乐目录下的 Melodia
    val defaultDownloadDir: File by lazy { File(File(System.getProperty("user.home"), "Music"), "Melodia") }

    fun preferencesFile(name: String): File = File(File(dataDir, "datastore").apply { mkdirs() }, "$name.preferences_pb")
}
