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

private const val TAG = "KuwoSourceProvider"

// 酷我音乐音源提供者，基于公开搜索端点与解析接口
class KuwoSourceProvider : AudioSourceProvider {

    override val platform: MusicPlatform = MusicPlatform.KUWO

    override suspend fun resolveUrl(
        songName: String,
        artists: String,
        albumName: String?,
        durationMs: Long,
        quality: String
    ): SourceResult? {
        val searchResults = search("$songName $artists", 0, 5)
        val matched = searchResults.firstOrNull() ?: return null
        val songId = matched.id

        // 优先通过 zddyr 解析直链
        val ddyrUrl = "https://yy.zddyr.top/lx/api/?source=kuwo&songmid=$songId&quality=128k"
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

        // 备用：酷我 antiserver 解析
        val antiserverUrl = "http://antiserver.kuwo.cn/anti.s?type=convert_url&format=mp3&response=url&rid=MUSIC_$songId"
        val antiResp = SourceHttpClient.getOrNull(antiserverUrl, mapOf("User-Agent" to "okhttp/3.10.0"))
        if (!antiResp.isNullOrBlank() && antiResp.startsWith("http")) {
            return SourceResult(url = antiResp.trim(), platform = platform, quality = "128k")
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
            val url = "http://search.kuwo.cn/r.s?&correct=1&vipver=1&stype=comprehensive&encoding=utf8&rformat=json&mobi=1&show_copyright_off=1&searchapi=6&all=$encoded&pn=$pn&rn=$limit"
            val resp = SourceHttpClient.getOrNull(url) ?: return emptyList()

            val root = SourceHttpClient.json.parseToJsonElement(resp).jsonObject
            val content = root["content"]?.jsonArray ?: return emptyList()

            var abslist = content.firstNotNullOfOrNull { elem ->
                elem.jsonObject["musicpage"]?.jsonObject?.get("abslist")?.jsonArray
            }
            if (abslist == null && content.size > 1) {
                abslist = content[1].jsonObject["musicpage"]?.jsonObject?.get("abslist")?.jsonArray
            }
            if (abslist == null) return emptyList()

            abslist.mapNotNull { itemElem ->
                val obj = itemElem.jsonObject
                val ridRaw = obj["MUSICRID"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val id = ridRaw.removePrefix("MUSIC_")
                val songName = obj["SONGNAME"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
                val artist = obj["ARTIST"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
                val album = obj["ALBUM"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
                val durationSec = obj["DURATION"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                val mvPic = obj["hts_MVPIC"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val albumPicShort = obj["web_albumpic_short"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val cover = when {
                    mvPic.isNotBlank() -> mvPic
                    albumPicShort.isNotBlank() -> "https://img4.kuwo.cn/star/albumcover/$albumPicShort"
                    else -> ""
                }

                ExternalTrack(
                    id = id,
                    name = songName,
                    artists = artist,
                    artistList = if (artist.isNotBlank()) artist.split("&", "、", "/") else emptyList(),
                    albumName = album,
                    durationMs = durationSec * 1000L,
                    coverUrl = cover,
                    platform = platform
                )
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "酷我搜索异常 [keyword=$keyword]: ${e.message}")
            emptyList()
        }
    }
}
