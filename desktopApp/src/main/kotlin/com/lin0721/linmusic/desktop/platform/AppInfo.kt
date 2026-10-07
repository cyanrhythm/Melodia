package com.lin0721.linmusic.desktop.platform

import java.util.Properties

private const val RESOURCE_NAME = "app-info.properties"
private const val UNKNOWN_VERSION = "unknown"

// 构建时由 Gradle 生成的运行时信息，版本号与打包版本一致
object AppInfo {
    val version: String by lazy {
        runCatching {
            Thread.currentThread().contextClassLoader?.getResourceAsStream(RESOURCE_NAME)?.use { stream ->
                Properties().apply { load(stream) }.getProperty("version")
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: UNKNOWN_VERSION
    }
}
