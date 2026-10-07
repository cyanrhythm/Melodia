package com.lin0721.linmusic.desktop.platform.download

import com.lin0721.linmusic.core.download.BatchEnqueueResult
import com.lin0721.linmusic.core.download.DownloadQueueGate
import com.lin0721.linmusic.core.download.DownloadRecord
import com.lin0721.linmusic.core.download.DownloadTrackInfo
import com.lin0721.linmusic.core.download.SongDownloader
import com.lin0721.linmusic.core.download.data.DownloadApi
import com.lin0721.linmusic.core.download.data.SongDownloadUrlRequest
import com.lin0721.linmusic.core.download.satisfies
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory

private const val TAG = "DesktopSongDownloader"
private const val MAX_CONCURRENT_DOWNLOADS = 3
private const val MAX_ATTEMPTS = 3
private const val RETRY_DELAY_MS = 2_000L
private const val PARTIAL_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
private const val BUFFER_SIZE = 64 * 1024

private val LOSSLESS_AND_ABOVE = setOf("lossless", "hires", "jyeffect", "sky", "jymaster")
private val COMPRESSED_ENCODE_TYPES = setOf("mp3", "aac", "m4a")

// 单个任务的终态
private sealed interface Outcome {
    data object Success : Outcome
    data object Skipped : Outcome
    data class Failed(val reason: String) : Outcome
}

// 失败原因明确、重试也不会改变结果
private class NonRetryableException(message: String) : Exception(message)

// 批量任务的收尾统计，全部结束时汇总提示一次
private class BatchProgress(val label: String, val total: Int) {
    val settled = AtomicInteger(0)
    val failed = AtomicInteger(0)
}

// 桌面端歌曲下载：协程队列 + OkHttp 断点续传，写入标签后落到用户目录
class DesktopSongDownloader(
    private val downloadApi: DownloadApi,
    private val playbackRepository: PlaybackRepository,
    private val settingsPreferences: SettingsPreferences,
    private val downloadPreferences: DesktopDownloadPreferences,
    private val tempDir: File,
    private val defaultDir: File
) : SongDownloader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val sequence = AtomicLong(0)
    private val queueOrder = ConcurrentHashMap<String, Long>()
    private val gate = DownloadQueueGate(MAX_CONCURRENT_DOWNLOADS) { workIds ->
        workIds.associateWith { queueOrder[it] ?: Long.MAX_VALUE }
    }

    // 排队与进行中的歌曲，避免同一首歌重复入队
    private val inFlight = ConcurrentHashMap<Long, UUID>()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        java.util.logging.Logger.getLogger("org.jaudiotagger").level = java.util.logging.Level.OFF
        scope.launch { deleteStalePartials() }
    }

    override fun enqueueSingle(track: DownloadTrackInfo, level: String): UUID {
        val id = UUID.randomUUID()
        val existing = inFlight.putIfAbsent(track.songId, id)
        if (existing != null) return existing
        launchTask(id, track, level, batch = null, batchLabel = null)
        return id
    }

    override suspend fun enqueueBatch(
        tracks: List<DownloadTrackInfo>,
        level: String,
        batchTag: String,
        batchLabel: String
    ): BatchEnqueueResult {
        val distinctTracks = tracks.distinctBy { it.songId }
        val downloadedIds = downloadPreferences.findVerifiedRecords(distinctTracks.map { it.songId })
            .filter { it.satisfies(level) }
            .mapTo(HashSet()) { it.songId }
        val queuedIds = distinctTracks.mapNotNullTo(HashSet()) { it.songId.takeIf(inFlight::containsKey) }
        val pending = distinctTracks.filterNot { it.songId in downloadedIds || it.songId in queuedIds }

        val batch = BatchProgress(batchLabel, pending.size)
        pending.forEach { track ->
            val id = UUID.randomUUID()
            // 并发入队时以先到者为准，落败的不计入本批
            if (inFlight.putIfAbsent(track.songId, id) == null) launchTask(id, track, level, batch, batchLabel)
            else batch.settled.incrementAndGet()
        }
        return BatchEnqueueResult(
            enqueuedCount = pending.size,
            skippedCount = downloadedIds.size,
            queuedCount = distinctTracks.count { it.songId in queuedIds && it.songId !in downloadedIds }
        )
    }

    private fun launchTask(id: UUID, track: DownloadTrackInfo, level: String, batch: BatchProgress?, batchLabel: String?) {
        queueOrder[id.toString()] = sequence.incrementAndGet()
        scope.launch {
            val outcome = try {
                gate.withPermit(id.toString()) { runWithRetry(track, level, batchLabel) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "下载任务异常 songId=${track.songId}", e)
                Outcome.Failed("下载异常")
            } finally {
                inFlight.remove(track.songId)
                queueOrder.remove(id.toString())
            }
            report(outcome, track, batch)
        }
    }

    private fun report(outcome: Outcome, track: DownloadTrackInfo, batch: BatchProgress?) {
        if (outcome is Outcome.Failed) batch?.failed?.incrementAndGet()
        if (batch == null) {
            when (outcome) {
                Outcome.Success -> _messages.tryEmit("《${track.songName}》下载完成")
                Outcome.Skipped -> _messages.tryEmit("《${track.songName}》已下载过")
                is Outcome.Failed -> _messages.tryEmit("《${track.songName}》下载失败：${outcome.reason}")
            }
            return
        }
        if (batch.settled.incrementAndGet() >= batch.total) {
            val failed = batch.failed.get()
            val suffix = if (failed > 0) "，$failed 首失败" else ""
            _messages.tryEmit("「${batch.label}」下载完成$suffix")
        }
    }

    private suspend fun runWithRetry(track: DownloadTrackInfo, level: String, batchLabel: String?): Outcome {
        var attempt = 0
        while (true) {
            attempt++
            try {
                return download(track, level, batchLabel)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NonRetryableException) {
                return Outcome.Failed(e.message ?: "下载失败")
            } catch (e: Exception) {
                AppLogger.w(TAG, "下载失败 songId=${track.songId} 第 $attempt 次", e)
                if (attempt >= MAX_ATTEMPTS) {
                    return Outcome.Failed(if (e is IOException) "网络异常，请检查网络后重试" else "下载异常")
                }
                delay(RETRY_DELAY_MS * attempt)
            }
        }
    }

    private suspend fun download(track: DownloadTrackInfo, level: String, batchLabel: String?): Outcome {
        val songId = track.songId
        val existing = downloadPreferences.findVerifiedRecord(songId)
        if (existing != null && existing.satisfies(level)) return Outcome.Skipped

        val response = downloadApi.getSongDownloadUrl(SongDownloadUrlRequest(id = songId, level = level))
        val item = response.data
        val url = item?.url
        if (!response.isSuccess || url.isNullOrBlank()) throw NonRetryableException("获取下载链接失败")
        if (item.freeTrialInfo != null) throw NonRetryableException("该音质仅支持试听，需要 VIP 或购买后才能完整下载")
        val encodeType = (item.type ?: item.encodeType)?.lowercase()
        // 无权限时服务端会静默降级为压缩格式
        if (level in LOSSLESS_AND_ABOVE && encodeType in COMPRESSED_ENCODE_TYPES) {
            throw NonRetryableException("需要更高会员等级才能下载该音质")
        }
        val extension = encodeType ?: "mp3"

        val directory = resolveDirectory(batchLabel)
        tempDir.mkdirs()
        val partial = File(tempDir, "dl_${songId}_${level}_${item.size}.part")
        val staged = File(tempDir, "dl_${songId}_${System.currentTimeMillis()}.$extension")
        try {
            downloadToFile(partial, url, item.size)
            moveFile(partial, staged)
            runCatching { writeTags(staged, track) }
                .onFailure { AppLogger.w(TAG, "写入标签失败，跳过 songId=$songId", it) }

            val fileSize = staged.length()
            val target = resolveTarget(directory, "${sanitizeFileName(track.artistName, track.songName, songId)}.$extension", songId)
            withContext(Dispatchers.IO) { moveFile(staged, target) }

            downloadPreferences.addRecord(
                DownloadRecord(
                    songId = songId,
                    mediaStoreUri = target.absolutePath,
                    quality = item.level ?: level,
                    downloadedAt = System.currentTimeMillis(),
                    fileSize = fileSize,
                    songName = track.songName,
                    artistName = track.artistName,
                    requestedLevel = level
                )
            )
            // 升级音质后旧文件已被新记录取代
            existing?.mediaStoreUri?.takeIf { it != target.absolutePath }?.let { File(it).delete() }
            return Outcome.Success
        } finally {
            staged.delete()
        }
    }

    private suspend fun resolveDirectory(batchLabel: String?): File {
        val root = settingsPreferences.downloadFolderUri.first()?.takeIf { it.isNotBlank() }?.let(::File) ?: defaultDir
        val directory = if (batchLabel != null) File(root, sanitizeName(batchLabel).ifBlank { "batch" }) else root
        // 并发任务可能同时创建同一目录，mkdirs 返回 false 不代表失败
        val usable = withContext(Dispatchers.IO) {
            directory.mkdirs()
            directory.isDirectory && directory.canWrite()
        }
        if (!usable) throw NonRetryableException("下载目录不可用，请到设置里重新选择")
        return directory
    }

    // 同名文件属于同一首歌时覆盖，被另一首歌占用时加时间戳
    private suspend fun resolveTarget(directory: File, displayName: String, songId: Long): File {
        val candidate = File(directory, displayName)
        if (!candidate.exists()) return candidate
        if (!downloadPreferences.isPathClaimedByOtherSong(candidate.absolutePath, songId)) return candidate
        val dot = displayName.lastIndexOf('.')
        val stamp = System.currentTimeMillis()
        val unique = if (dot > 0) "${displayName.substring(0, dot)}_$stamp${displayName.substring(dot)}" else "${displayName}_$stamp"
        return File(directory, unique)
    }

    // 已有部分数据且服务端支持 Range 时从断点续传，写满预期大小才算完成
    private suspend fun downloadToFile(file: File, url: String, expectedSize: Long) = withContext(Dispatchers.IO) {
        val existing = if (file.exists()) file.length() else 0L
        if (expectedSize > 0 && existing == expectedSize) return@withContext
        val resumeFrom = if (expectedSize > 0 && existing in 1 until expectedSize) existing else 0L
        val request = Request.Builder()
            .url(url)
            .apply { if (resumeFrom > 0) header("Range", "bytes=$resumeFrom-") }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("响应体为空")
            val append = resumeFrom > 0 && response.code == 206 &&
                contentRangeStart(response.header("Content-Range")) == resumeFrom
            val start = if (append) resumeFrom else 0L
            val bodyLength = body.contentLength()
            val total = if (bodyLength > 0) start + bodyLength else expectedSize
            var written = start
            FileOutputStream(file, append).use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        written += read
                    }
                }
            }
            if (total > 0 && written != total) throw IOException("文件不完整 $written/$total")
        }
    }

    // 解析 "bytes 100-199/200" 的起始偏移
    private fun contentRangeStart(header: String?): Long? =
        header?.removePrefix("bytes")?.trim()?.substringBefore('-')?.toLongOrNull()

    private suspend fun writeTags(file: File, track: DownloadTrackInfo) {
        val rawLyrics = if (settingsPreferences.downloadLyricsEnabled.first()) {
            runCatching { playbackRepository.getRawLyrics(track.songId).first().getOrNull() }
                .onFailure { AppLogger.w(TAG, "获取歌词失败，跳过 songId=${track.songId}", it) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val cover = track.coverUrl?.let { fetchCoverBytes(it) }

        withContext(Dispatchers.IO) {
            val audio = AudioFileIO.read(file)
            val tag = audio.tagOrCreateAndSetDefault
            tag.setField(FieldKey.TITLE, track.songName)
            tag.setField(FieldKey.ARTIST, track.artistName)
            if (track.albumName.isNotBlank()) tag.setField(FieldKey.ALBUM, track.albumName)
            if (track.albumYear > 0) tag.setField(FieldKey.YEAR, track.albumYear.toString())
            if (rawLyrics != null) tag.setField(FieldKey.LYRICS, rawLyrics)
            if (cover != null) {
                val artwork = ArtworkFactory.getNew().apply {
                    binaryData = cover
                    mimeType = "image/jpeg"
                    pictureType = 3
                    description = "Front Cover"
                }
                tag.deleteArtworkField()
                tag.setField(artwork)
            }
            audio.commit()
        }
    }

    private suspend fun fetchCoverBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val sized = if (url.contains('?')) url else "$url?param=500y500"
            client.newCall(Request.Builder().url(sized).build()).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        }.getOrNull()
    }

    private fun moveFile(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            source.delete()
        }
    }

    private fun deleteStalePartials() {
        val threshold = System.currentTimeMillis() - PARTIAL_MAX_AGE_MS
        tempDir.listFiles { file -> file.name.startsWith("dl_") && file.name.endsWith(".part") }
            ?.filter { it.lastModified() < threshold }
            ?.forEach { it.delete() }
    }

    private fun sanitizeName(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().trimEnd('.')

    private fun sanitizeFileName(artist: String, song: String, songId: Long): String =
        sanitizeName("$artist - $song").ifBlank { "song_$songId" }
}
