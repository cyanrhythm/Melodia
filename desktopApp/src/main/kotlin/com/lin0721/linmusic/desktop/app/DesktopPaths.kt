package com.lin0721.linmusic.desktop.app

import java.io.File

// 用户数据目录：
//   Windows → %APPDATA%\Melodia，缓存 %LOCALAPPDATA%\Melodia\cache
//   Linux   → XDG：数据 $XDG_DATA_HOME/Melodia（默认 ~/.local/share/Melodia），
//             缓存 $XDG_CACHE_HOME/Melodia（默认 ~/.cache/Melodia）
//   其他    → 回退到用户主目录
object DesktopPaths {
    private val isWindows: Boolean
        get() = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    val dataDir: File by lazy {
        if (isWindows) {
            val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: System.getProperty("user.home")
            File(base, "Melodia").apply { mkdirs() }
        } else {
            val home = System.getProperty("user.home")
            val base = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share"
            File(base, "Melodia").apply { mkdirs() }
        }
    }

    val logDir: File by lazy { File(dataDir, "logs") }

    // 缓存放 %LOCALAPPDATA%（Windows）或 $XDG_CACHE_HOME（Linux），取不到时回退数据目录
    val localCacheDir: File by lazy {
        if (isWindows) {
            val base = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
            if (base != null) File(File(base, "Melodia"), "cache") else File(dataDir, "cache")
        } else {
            val home = System.getProperty("user.home")
            val base = System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.cache"
            File(base, "Melodia").apply { mkdirs() }
        }
    }

    val imageCacheDir: File by lazy { File(localCacheDir, "image_cache") }
    val audioCacheDir: File by lazy { File(localCacheDir, "audio_cache") }
    val metadataCacheDir: File by lazy { File(localCacheDir, "meta_cache") }

    val downloadTempDir: File by lazy { File(localCacheDir, "download_tmp") }

    val defaultDownloadDir: File by lazy { File(File(System.getProperty("user.home"), "Music"), "Melodia") }

    fun preferencesFile(name: String): File = File(File(dataDir, "datastore").apply { mkdirs() }, "$name.preferences_pb")
}
