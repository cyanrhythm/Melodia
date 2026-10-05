package com.lin0721.linmusic.core.source

// 外部平台歌曲通用模型，用于跨平台匹配与搜索结果
data class ExternalTrack(
    val id: String,
    val name: String,
    val artists: String,
    val artistList: List<String> = emptyList(),
    val albumName: String = "",
    val albumId: String = "",
    val durationMs: Long = 0,
    val coverUrl: String = "",
    val platform: MusicPlatform,
    // 酷狗专用 32 位 hex hash
    val hash: String? = null
)
