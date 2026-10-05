package com.lin0721.linmusic.core.source.provider

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.source.AudioSourceProvider
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.source.SourceHttpClient
import com.lin0721.linmusic.core.source.SourceResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.security.MessageDigest

private const val TAG = "KugouSourceProvider"

// 酷狗音乐音源提供者，基于公开搜索端点与解析接口
class KugouSourceProvider : AudioSourceProvider {

    override val platform: MusicPlatform = MusicPlatform.KUGOU

    override suspend fun resolveUrl(
        songName: String,
        artists: String,
        albumName: String?,
        durationMs: Long,
        quality: String
    ): SourceResult? {
        val tracks = search("$songName $artists", 0, 5)
        val matched = tracks.firstOrNull() ?: return null
        val hash = matched.hash ?: matched.id

        // 1. 尝试通过 zddyr 解析
        val ddyrUrl = "https://yy.zddyr.top/lx/api/?source=kugou&songmid=$hash&quality=128k"
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

        // 2. 尝试酷狗 trackercdn 接口
        try {
            val md5 = MessageDigest.getInstance("MD5")
                .digest("${hash}kgcloudv2".toByteArray())
                .joinToString("") { "%02x".format(it) }
            val trackerUrl = "http://trackercdn.kugou.com/i/v2/?key=$md5&hash=$hash&appid=1005&pid=2&cmd=25&behavior=play"
            val trackerResp = SourceHttpClient.getOrNull(trackerUrl)
            if (!trackerResp.isNullOrBlank()) {
                val playUrl = runCatching {
                    val root = SourceHttpClient.json.parseToJsonElement(trackerResp).jsonObject
                    root["url"]?.jsonPrimitive?.contentOrNull?.trim()
                }.getOrNull()
                if (!playUrl.isNullOrBlank() && playUrl.startsWith("http")) {
                    return SourceResult(url = playUrl, platform = platform, quality = "128k")
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "酷狗 trackercdn 解析异常: ${e.message}")
        }

        // 3. 跨源兜底：尝试借由酷我公共接口解析同名曲目
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
        return try {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val pn = (offset / limit) + 1
            val url = "http://mobilecdn.kugou.com/api/v3/search/song?keyword=$encoded&page=$pn&pagesize=$limit"
            val resp = SourceHttpClient.getOrNull(url) ?: return emptyList()

            val root = SourceHttpClient.json.parseToJsonElement(resp).jsonObject
            val dataObj = root["data"]?.jsonObject ?: return emptyList()
            val infoList = dataObj["info"]?.jsonArray ?: return emptyList()

            infoList.mapNotNull { itemElem ->
                val obj = itemElem.jsonObject
                val hash = obj["hash"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
                val songName = obj["songname"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
                val artist = obj["singername"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
                val album = obj["album_name"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
                val durationSec = obj["duration"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                val transParamElem = obj["trans_param"]
                val unionCover = when {
                    transParamElem is JsonObject -> {
                        transParamElem["union_cover"]?.jsonPrimitive?.contentOrNull
                    }
                    transParamElem != null -> {
                        val str = transParamElem.jsonPrimitive.contentOrNull.orEmpty()
                        Regex("""union_cover=([^;,\s}]+)""").find(str)?.groupValues?.get(1)
                    }
                    else -> null
                }
                val coverUrl = unionCover?.replace("{size}", "400")?.replace("http://", "https://").orEmpty()

                ExternalTrack(
                    id = hash,
                    name = songName,
                    artists = artist,
                    artistList = if (artist.isNotBlank()) artist.split("、", "/") else emptyList(),
                    albumName = album,
                    durationMs = durationSec * 1000L,
                    coverUrl = coverUrl,
                    platform = platform,
                    hash = hash
                )
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "酷狗搜索异常 [keyword=$keyword]: ${e.message}")
            emptyList()
        }
    }
}
