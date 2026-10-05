package com.lin0721.linmusic.core.source

import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "NativeSourceFetcher"
private const val MODULE_TIMEOUT_MS = 5000L

// 本地原生直连音源获取器，直接向各第三方公开接口发起请求获取音频直链
object NativeSourceFetcher {

    suspend fun fetchUrl(module: UnmModule, songId: Long): String? =
        withTimeoutOrNull(MODULE_TIMEOUT_MS) {
            try {
                when (module) {
                    UnmModule.BYFUNS -> fetchByfuns(songId)
                    UnmModule.DDYR -> fetchDdyr(songId)
                    UnmModule.GDMUSIC -> fetchGdmusic(songId)
                    UnmModule.OI -> fetchOi(songId)
                    UnmModule.QIJIEYA -> fetchQijieya(songId)
                    UnmModule.MSLS -> fetchMsls(songId)
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "${module.displayName} 本地解析异常 [songId=$songId]: ${e.message}")
                null
            }
        }

    private suspend fun fetchByfuns(songId: Long): String? {
        val url = "https://api.byfuns.top/1/?id=$songId&level=lossless"
        val res = SourceHttpClient.fetchUrlOrRedirect(url, mapOf("User-Agent" to "Melodia/1.0"))?.trim() ?: return null
        return if (isValidAudioUrl(res)) res else null
    }

    private suspend fun fetchDdyr(songId: Long): String? {
        val url = "https://yy.zddyr.top/lx/api/?source=netease&songmid=$songId&quality=hires"
        val res = SourceHttpClient.getOrNull(url, mapOf("User-Agent" to "Melodia/1.0")) ?: return null
        return runCatching {
            val root = SourceHttpClient.json.parseToJsonElement(res).jsonObject
            root["url"]?.jsonPrimitive?.contentOrNull?.trim()
        }.getOrNull()?.takeIf { isValidAudioUrl(it) }
    }

    private suspend fun fetchGdmusic(songId: Long): String? {
        val url = "https://music-api.gdstudio.xyz/api.php?types=url&source=netease&id=$songId&br=999"
        val res = SourceHttpClient.getOrNull(url, mapOf("User-Agent" to "Melodia/1.0")) ?: return null
        return runCatching {
            val root = SourceHttpClient.json.parseToJsonElement(res).jsonObject
            root["url"]?.jsonPrimitive?.contentOrNull?.trim()
        }.getOrNull()?.takeIf { isValidAudioUrl(it) }
    }

    private suspend fun fetchOi(songId: Long): String? {
        val url = "https://oiapi.net/api/Music_163?id=$songId"
        val res = SourceHttpClient.getOrNull(url, mapOf("User-Agent" to "Melodia/1.0")) ?: return null
        return runCatching {
            val root = SourceHttpClient.json.parseToJsonElement(res).jsonObject
            val dataArray = root["data"]?.jsonArray
            val firstObj = dataArray?.firstOrNull()?.jsonObject
            firstObj?.get("url")?.jsonPrimitive?.contentOrNull?.trim()
        }.getOrNull()?.takeIf { isValidAudioUrl(it) }
    }

    private suspend fun fetchQijieya(songId: Long): String? {
        val url = "https://api.qijieya.cn/meting/?type=url&id=$songId"
        val res = SourceHttpClient.fetchUrlOrRedirect(url, mapOf("User-Agent" to "Melodia/1.0"))?.trim() ?: return null
        if (isValidAudioUrl(res)) return res
        return runCatching {
            val root = SourceHttpClient.json.parseToJsonElement(res).jsonObject
            root["url"]?.jsonPrimitive?.contentOrNull?.trim()
        }.getOrNull()?.takeIf { isValidAudioUrl(it) }
    }

    private suspend fun fetchMsls(songId: Long): String? {
        val url = "https://api.msls1441.com/?type=url&id=$songId"
        val res = SourceHttpClient.fetchUrlOrRedirect(url, mapOf("User-Agent" to "Melodia/1.0"))?.trim() ?: return null
        if (isValidAudioUrl(res)) return res
        return runCatching {
            val root = SourceHttpClient.json.parseToJsonElement(res).jsonObject
            root["url"]?.jsonPrimitive?.contentOrNull?.trim()
        }.getOrNull()?.takeIf { isValidAudioUrl(it) }
    }

    private fun isValidAudioUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        return (trimmed.startsWith("http://") || trimmed.startsWith("https://")) &&
                !trimmed.contains("<html", ignoreCase = true)
    }
}
