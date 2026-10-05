package com.lin0721.linmusic.core.source.provider

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.source.AudioSourceProvider
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.source.SourceHttpClient
import com.lin0721.linmusic.core.source.SourceResult
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

private const val TAG = "QQSourceProvider"

// QQ 音乐音源提供者，基于公开搜索端点与解析接口
class QQSourceProvider : AudioSourceProvider {

    override val platform: MusicPlatform = MusicPlatform.QQ

    override suspend fun resolveUrl(
        songName: String,
        artists: String,
        albumName: String?,
        durationMs: Long,
        quality: String
    ): SourceResult? {
        val tracks = search("$songName $artists", 0, 5)
        val matched = tracks.firstOrNull() ?: return null
        val songMid = matched.id

        // 1. 尝试通过 zddyr 解析
        val ddyrUrl = "https://yy.zddyr.top/lx/api/?source=qq&songmid=$songMid&quality=128k"
        val ddyrResp = SourceHttpClient.getOrNull(ddyrUrl, mapOf("User-Agent" to "Melodia/1.0"))
        if (!ddyrResp.isNullOrBlank()) {
            val playUrl = runCatching {
                val root = SourceHttpClient.json.parseToJsonElement(ddyrResp).jsonObject
                root["url"]?.jsonPrimitive?.contentOrNull?.trim()
            }.getOrNull()
            if (!playUrl.isNullOrBlank() && playUrl.startsWith("http")) {
                return SourceResult(url = playUrl, platform = platform, quality = "128k")
            }
        }

        // 2. 跨源兜底：尝试借由酷我公共接口解析同名曲目
        val kuwoFallback = KuwoSourceProvider().resolveUrl(songName, artists, albumName, durationMs, quality)
        if (kuwoFallback != null) {
            return kuwoFallback.copy(platform = platform)
        }

        return null
    }

    override suspend fun search(
        keyword: String,
        offset: Int,
        limit: Int
    ): List<ExternalTrack> {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val pn = (offset / limit) + 1
        val headers = mapOf("Referer" to "https://y.qq.com/")

        // 1. 优先使用标准搜索接口，具备完整专辑 mid 与时长信息
        try {
            val searchUrl = "https://c.y.qq.com/soso/fcgi-bin/search_for_qq_cp?w=$encoded&format=json&p=$pn&n=$limit"
            val resp = SourceHttpClient.getOrNull(searchUrl, headers)
            if (!resp.isNullOrBlank()) {
                val root = SourceHttpClient.json.parseToJsonElement(resp).jsonObject
                val songList = root["data"]?.jsonObject?.get("song")?.jsonObject?.get("list")?.jsonArray
                if (!songList.isNullOrEmpty()) {
                    return songList.mapNotNull { itemElem ->
                        val obj = itemElem.jsonObject
                        val mid = obj["songmid"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
                        val songName = obj["songname"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
                        val album = obj["albumname"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                        val albumMid = obj["albummid"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                        val intervalSec = obj["interval"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                        val singers = obj["singer"]?.jsonArray?.mapNotNull {
                            it.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.trim()
                        }.orEmpty()
                        val artist = singers.joinToString(" / ")
                        val firstSingerMid = obj["singer"]?.jsonArray?.firstOrNull()?.jsonObject?.get("mid")?.jsonPrimitive?.contentOrNull.orEmpty()
                        val coverUrl = when {
                            albumMid.isNotBlank() && albumMid != "0" -> "https://y.gtimg.cn/music/photo_new/T002R300x300M000$albumMid.jpg"
                            firstSingerMid.isNotBlank() -> "https://y.gtimg.cn/music/photo_new/T001R300x300M000$firstSingerMid.jpg"
                            else -> ""
                        }

                        ExternalTrack(
                            id = mid,
                            name = songName,
                            artists = artist,
                            artistList = singers,
                            albumName = album,
                            durationMs = intervalSec * 1000L,
                            coverUrl = coverUrl,
                            platform = platform
                        )
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "QQ 音乐标准搜索异常 [keyword=$keyword]: ${e.message}")
        }

        // 2. 备选：Smartbox 快速检索兜底
        return try {
            val url = "https://c.y.qq.com/splcloud/fcgi-bin/smartbox_new.fcg?key=$encoded&format=json"
            val resp = SourceHttpClient.getOrNull(url, headers) ?: return emptyList()

            val root = SourceHttpClient.json.parseToJsonElement(resp).jsonObject
            val dataObj = root["data"]?.jsonObject ?: return emptyList()
            val songObj = dataObj["song"]?.jsonObject ?: return emptyList()
            val itemList = songObj["itemlist"]?.jsonArray ?: return emptyList()

            itemList.mapNotNull { itemElem ->
                val obj = itemElem.jsonObject
                val mid = obj["mid"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
                val songName = obj["name"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
                val artist = obj["singer"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""

                ExternalTrack(
                    id = mid,
                    name = songName,
                    artists = artist,
                    artistList = if (artist.isNotBlank()) artist.split("、", "/") else emptyList(),
                    albumName = "",
                    durationMs = 0L,
                    coverUrl = "",
                    platform = platform
                )
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "QQ 音乐 smartbox 搜索异常 [keyword=$keyword]: ${e.message}")
            emptyList()
        }
    }
}
