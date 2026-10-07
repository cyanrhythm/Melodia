package com.lin0721.linmusic.desktop.platform.download

import com.lin0721.linmusic.core.download.DownloadTrackInfo
import kotlinx.serialization.Serializable

@Serializable
enum class DownloadTaskStatus { QUEUED, DOWNLOADING, PAUSED, FAILED, SUCCEEDED }

// 下载面板展示与重启恢复用的任务快照；进度只在内存里实时更新
@Serializable
data class DownloadTask(
    val id: String,
    val songId: Long,
    val songName: String,
    val artistName: String,
    val albumName: String = "",
    val coverUrl: String? = null,
    val albumYear: Int = 0,
    val level: String,
    // 批量下载所属的歌单或专辑名，单曲下载为空
    val batchLabel: String? = null,
    val status: DownloadTaskStatus,
    val progress: Int = 0,
    val failureReason: String? = null,
    // 已下载过同等或更高音质而跳过
    val skipped: Boolean = false,
    val createdAt: Long,
    val finishedAt: Long = 0
) {
    // 排队或下载中
    val isActive: Boolean get() = status == DownloadTaskStatus.QUEUED || status == DownloadTaskStatus.DOWNLOADING

    // 还会继续占用队列的任务，同一首歌不重复入队
    val isLive: Boolean get() = isActive || status == DownloadTaskStatus.PAUSED

    fun toTrackInfo() = DownloadTrackInfo(songId, songName, artistName, albumName, coverUrl, albumYear)
}
