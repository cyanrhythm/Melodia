package com.lin0721.linmusic.core.source

// 音源解析结果
data class SourceResult(
    val url: String,
    val platform: MusicPlatform,
    val quality: String = "",
    val pluginId: String? = null,
    val isTrial: Boolean = false
)
