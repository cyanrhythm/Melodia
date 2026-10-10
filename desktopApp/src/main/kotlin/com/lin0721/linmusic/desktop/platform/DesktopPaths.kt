package com.lin0721.linmusic.desktop.platform

import java.io.File

// 用户数据目录：Windows 取 %APPDATA%\Melodia，其他平台取 XDG（$XDG_DATA_HOME / ~/.local/share）。
object DesktopPaths {
    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    private val home = System.getProperty("user.home")

    val dataDir: File by lazy {
        val base = if (isWindows) {
            System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: home
        } else {
            System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share"
        }
        File(base, "Melodia").apply { mkdirs() }
    }

    val logDir: File by lazy { File(dataDir, "logs") }

    // 缓存：Windows 取 %LOCALAPPDATA%\Melodia\cache；其他平台取 $XDG_CACHE_HOME / ~/.cache
    val localCacheDir: File by lazy {
        if (isWindows) {
            val base = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
            if (base != null) File(File(base, "Melodia"), "cache") else File(dataDir, "cache")
        } else {
            val base = System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.cache"
            File(base, "Melodia")
        }
    }

    val imageCacheDir: File by lazy { File(localCacheDir, "image_cache") }
    val audioCacheDir: File by lazy { File(localCacheDir, "audio_cache") }
    val metadataCacheDir: File by lazy { File(localCacheDir, "meta_cache") }

    val downloadTempDir: File by lazy { File(localCacheDir, "download_tmp") }

    val defaultDownloadDir: File by lazy { File(File(System.getProperty("user.home"), "Music"), "Melodia") }

    fun preferencesFile(name: String): File = File(File(dataDir, "datastore").apply { mkdirs() }, "$name.preferences_pb")
}
