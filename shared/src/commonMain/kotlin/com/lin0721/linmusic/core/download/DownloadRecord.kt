package com.lin0721.linmusic.core.download

import com.lin0721.linmusic.core.model.qualityRank
import kotlinx.serialization.Serializable

// 下载记录实体；mediaStoreUri 在 Android 为 content Uri，桌面端为文件绝对路径
@Serializable
data class DownloadRecord(
    val songId: Long,
    val mediaStoreUri: String,
    val quality: String,
    val downloadedAt: Long,
    val fileSize: Long,
    val songName: String = "",
    val artistName: String = "",
    // 发起下载时请求的音质；服务端按歌曲上限降级下发时，据此判断无需再次下载
    val requestedLevel: String = ""
)

// 已下载文件是否满足目标音质：实际下发或当初请求的档位不低于目标即视为满足
fun DownloadRecord.satisfies(level: String): Boolean {
    val target = qualityRank(level)
    if (target < 0) return quality == level || requestedLevel == level
    return maxOf(qualityRank(quality), qualityRank(requestedLevel)) >= target
}
