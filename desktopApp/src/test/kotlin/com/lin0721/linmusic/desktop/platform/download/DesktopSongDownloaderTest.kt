package com.lin0721.linmusic.desktop.platform.download

import com.lin0721.linmusic.core.download.DownloadTrackInfo
import com.lin0721.linmusic.core.download.data.DownloadApi
import com.lin0721.linmusic.core.download.data.SongDownloadUrlItem
import com.lin0721.linmusic.core.download.data.SongDownloadUrlRequest
import com.lin0721.linmusic.core.download.data.SongDownloadUrlResponse
import com.lin0721.linmusic.core.player.data.FreeTrialInfo
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.lang.reflect.Proxy
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DesktopSongDownloaderTest {

    private lateinit var root: File
    private lateinit var server: HttpServer
    private val requests = CopyOnWriteArrayList<String>()
    private var trial = false

    // 100 个 MPEG1 Layer3 128kbps 44.1kHz 静音帧，足够 jaudiotagger 识别并写标签
    private val audio: ByteArray = run {
        val frame = ByteArray(417).also {
            it[0] = 0xFF.toByte()
            it[1] = 0xFB.toByte()
            it[2] = 0x90.toByte()
            it[3] = 0x64
        }
        ByteArray(417 * 100) { frame[it % 417] }
    }

    @Before
    fun setUp() {
        root = Files.createTempDirectory("melodia-download-test").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            val range = ex.requestHeaders.getFirst("Range")
            requests += "${ex.requestURI.path} range=$range"
            ex.responseHeaders.add("Accept-Ranges", "bytes")
            if (range != null && range.startsWith("bytes=")) {
                val start = range.removePrefix("bytes=").substringBefore('-').toInt()
                ex.responseHeaders.add("Content-Range", "bytes $start-${audio.size - 1}/${audio.size}")
                ex.sendResponseHeaders(206, (audio.size - start).toLong())
                ex.responseBody.use { it.write(audio, start, audio.size - start) }
            } else {
                ex.sendResponseHeaders(200, audio.size.toLong())
                ex.responseBody.use { it.write(audio) }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.stop(0)
        root.deleteRecursively()
    }

    private fun downloader(): Triple<DesktopSongDownloader, DesktopDownloadPreferences, File> {
        val downloadApi = object : DownloadApi {
            override suspend fun getSongDownloadUrl(body: SongDownloadUrlRequest) = SongDownloadUrlResponse(
                code = 200,
                data = SongDownloadUrlItem(
                    id = body.id,
                    url = "http://127.0.0.1:${server.address.port}/song_${body.id}.mp3",
                    level = body.level,
                    type = "mp3",
                    size = audio.size.toLong(),
                    freeTrialInfo = if (trial) FreeTrialInfo() else null
                )
            )
        }
        val repository = Proxy.newProxyInstance(
            PlaybackRepository::class.java.classLoader,
            arrayOf(PlaybackRepository::class.java)
        ) { _, method, _ ->
            if (method.name == "getRawLyrics") flowOf(Result.failure<String>(IllegalStateException("无歌词")))
            else throw UnsupportedOperationException(method.name)
        } as PlaybackRepository
        val settings = SettingsPreferences(com.lin0721.linmusic.core.preferences.PreferencesStores.get(File(root, "settings.preferences_pb")))
        val records = DesktopDownloadPreferences(com.lin0721.linmusic.core.preferences.PreferencesStores.get(File(root, "download.preferences_pb")))
        val folder = File(root, "out")
        runBlocking { settings.saveDownloadFolderUri(folder.absolutePath) }
        val downloader = DesktopSongDownloader(downloadApi, repository, settings, records, File(root, "tmp"), File(root, "default"))
        return Triple(downloader, records, folder)
    }

    private fun track(id: Long) = DownloadTrackInfo(id, "歌名$id", "歌手", "专辑", null, 2020)

    private fun <T> DesktopSongDownloader.await(block: () -> T): String = runBlocking {
        withTimeout(20_000) {
            val message = async(start = CoroutineStart.UNDISPATCHED) { messages.first() }
            block()
            message.await()
        }
    }

    @Test
    fun singleDownloadWritesFileTagsAndRecord() {
        val (downloader, records, folder) = downloader()
        val message = downloader.await { downloader.enqueueSingle(track(1), "standard") }
        assertEquals("《歌名1》下载完成", message)

        val file = File(folder, "歌手 - 歌名1.mp3")
        assertTrue(file.isFile)
        assertEquals("歌名1", AudioFileIO.read(file).tag.getFirst(FieldKey.TITLE))
        assertEquals("专辑", AudioFileIO.read(file).tag.getFirst(FieldKey.ALBUM))
        val record = runBlocking { records.findVerifiedRecord(1) }
        assertNotNull(record)
        assertEquals(file.absolutePath, record!!.mediaStoreUri)
        assertEquals(0, File(root, "tmp").listFiles().orEmpty().size)
    }

    @Test
    fun alreadyDownloadedIsSkippedWithoutNetwork() {
        val (downloader, _, _) = downloader()
        downloader.await { downloader.enqueueSingle(track(2), "standard") }
        val before = requests.size
        val message = downloader.await { downloader.enqueueSingle(track(2), "standard") }
        assertEquals("《歌名2》已下载过", message)
        assertEquals(before, requests.size)
    }

    @Test
    fun resumesFromPartialFile() {
        val (downloader, _, folder) = downloader()
        val tmp = File(root, "tmp").apply { mkdirs() }
        File(tmp, "dl_3_standard_${audio.size}.part").writeBytes(audio.copyOfRange(0, 1000))
        downloader.await { downloader.enqueueSingle(track(3), "standard") }

        assertTrue(requests.any { it.endsWith("range=bytes=1000-") })
        val file = File(folder, "歌手 - 歌名3.mp3")
        assertTrue(file.isFile)
        assertNull(runCatching { AudioFileIO.read(file).tag }.exceptionOrNull())
    }

    @Test
    fun trialOnlyFailsWithReason() {
        trial = true
        val (downloader, records, _) = downloader()
        val message = downloader.await { downloader.enqueueSingle(track(4), "standard") }
        assertTrue(message.startsWith("《歌名4》下载失败：该音质仅支持试听"))
        assertNull(runBlocking { records.findVerifiedRecord(4) })
    }

    @Test
    fun batchWritesIntoLabelFolderAndSummarizesOnce() {
        val (downloader, _, folder) = downloader()
        val message = downloader.await {
            runBlocking {
                val result = downloader.enqueueBatch(listOf(track(5), track(6), track(5)), "standard", "playlist_1", "我的/歌单")
                assertEquals(2, result.enqueuedCount)
            }
        }
        assertEquals("「我的/歌单」下载完成", message)
        assertTrue(File(folder, "我的_歌单/歌手 - 歌名5.mp3").isFile)
        assertTrue(File(folder, "我的_歌单/歌手 - 歌名6.mp3").isFile)
    }

    @Test
    fun deletedFileInvalidatesRecord() {
        val (downloader, records, folder) = downloader()
        downloader.await { downloader.enqueueSingle(track(7), "standard") }
        File(folder, "歌手 - 歌名7.mp3").delete()
        assertNull(runBlocking { records.findVerifiedRecord(7) })
        assertFalse(runBlocking { records.records.first() }.any { it.songId == 7L })
    }
}
