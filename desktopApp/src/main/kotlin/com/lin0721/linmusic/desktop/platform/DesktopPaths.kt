package com.lin0721.linmusic.desktop.platform

import java.io.File

// 用户数据目录：Windows 取 %APPDATA%\Melodia，其他平台回退到用户主目录
object DesktopPaths {
    val dataDir: File by lazy {
        val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: System.getProperty("user.home")
        File(base, "Melodia").apply { mkdirs() }
    }

    fun preferencesFile(name: String): File = File(File(dataDir, "datastore").apply { mkdirs() }, "$name.preferences_pb")
}
