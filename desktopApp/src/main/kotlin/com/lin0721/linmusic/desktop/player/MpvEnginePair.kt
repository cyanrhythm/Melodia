package com.lin0721.linmusic.desktop.player

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.CrossfadePolicy
import com.lin0721.linmusic.desktop.player.mpv.MpvEngine
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "MpvEnginePair"
private const val RAMP_STEP_MS = 20L

// 渐出引擎淡化结束后等它自然读完的最长时间，读完录制的缓存才完整
private const val OUTGOING_DRAIN_TIMEOUT_MS = 2_500L
private const val OUTGOING_DRAIN_POLL_MS = 50L

private const val DELETE_ATTEMPTS = 30
private const val DELETE_RETRY_MS = 100L

// 边播边录请求：tag 为歌曲 id，path 为录制用的临时文件
internal class RecordRequest(val tag: Long, val path: String)

// 主引擎与备用引擎：备用引擎预载下一首，交叉淡化时角色互换。
// 对外提供与单个 MpvEngine 相同的操作，事件只转发主引擎的。
// onRecordingFinished 在某首歌无拖动地自然播完后回调，此时临时文件可能仍被 mpv 占用
internal class MpvEnginePair(
    private val listener: MpvEngine.Listener,
    private val scope: CoroutineScope,
    private val engineOptions: Map<String, String> = emptyMap(),
    private val onRecordingFinished: (tag: Long, path: String) -> Unit = { _, _ -> }
) {

    // 每台引擎一个事件适配器，记录自身进度，仅主引擎的事件转发给上层
    private inner class Slot : MpvEngine.Listener {
        lateinit var engine: MpvEngine

        @Volatile var position = 0L
        @Volatile var duration = 0L
        @Volatile var loaded = false
        @Volatile var ended = false

        // 拖动过进度的录制不完整，不能作为缓存
        @Volatile var record: RecordRequest? = null
        @Volatile var seeked = false

        private val isActive: Boolean get() = this === active

        fun resetProgress() {
            position = 0L
            duration = 0L
            loaded = false
            ended = false
        }

        // 停引擎后 mpv 异步释放文件，删除需要重试直到释放
        fun discardRecord() {
            val request = record ?: return
            record = null
            engine.stop()
            deleteWhenReleased(File(request.path))
        }

        override fun onPositionChanged(positionMs: Long) {
            position = positionMs
            if (isActive) listener.onPositionChanged(positionMs)
        }

        override fun onDurationChanged(durationMs: Long) {
            duration = durationMs
            if (isActive) listener.onDurationChanged(durationMs)
        }

        override fun onPauseChanged(paused: Boolean) {
            if (isActive) listener.onPauseChanged(paused)
        }

        override fun onFileLoaded() {
            loaded = true
            if (isActive) listener.onFileLoaded()
        }

        override fun onEnded(isError: Boolean) {
            ended = true
            val request = record
            if (request != null) {
                if (!isError && !seeked) {
                    record = null
                    onRecordingFinished(request.tag, request.path)
                } else {
                    discardRecord()
                }
            }
            if (isActive) listener.onEnded(isError)
        }
    }

    // 初始化期间事件线程可能先于赋值回调，此时视为非主引擎直接丢弃
    @Volatile private var active: Slot? = null
    private var spare: Slot? = null
    private var spareArmed = false
    private var spareUnavailable = false
    private var rampJob: Job? = null
    private var userVolume = 100.0
    private var deviceName: String? = null

    private val activeSlot: Slot get() = checkNotNull(active)

    init {
        active = newSlot()
    }

    // 淡化进行中，备用引擎正在渐出
    val isFading: Boolean get() = rampJob != null

    // 供测试核对主引擎与备用引擎的实际音量
    internal fun volumes(): Pair<Double?, Double?> = activeSlot.engine.volume() to spare?.engine?.volume()

    // 备用引擎已载入下一首并可立即接管
    val spareReady: Boolean get() = spareArmed && spare?.let { it.loaded && it.duration > 0L } == true

    private fun newSlot(): Slot {
        val slot = Slot()
        slot.engine = MpvEngine(slot, engineOptions)
        return slot
    }

    // 从中途起播的录制必然不完整，调用方不应传入 record
    fun load(url: String, startMs: Long, record: RecordRequest? = null) {
        reset()
        val slot = activeSlot
        slot.discardRecord()
        slot.resetProgress()
        slot.record = record
        slot.seeked = false
        slot.engine.load(url, startMs, recordPath = record?.path)
    }

    fun setPaused(paused: Boolean) {
        // 淡化期间暂停：直接收尾，避免恢复后音量跳变
        if (paused) reset()
        activeSlot.engine.setPaused(paused)
    }

    fun seekTo(positionMs: Long) {
        activeSlot.seeked = true
        activeSlot.engine.seekTo(positionMs)
    }

    fun stop() {
        reset()
        activeSlot.discardRecord()
        activeSlot.engine.stop()
    }

    fun setVolume(percent: Int) {
        userVolume = percent.toDouble()
        if (rampJob == null) activeSlot.engine.setVolume(userVolume)
    }

    fun audioDevices(): List<AudioDevice> = activeSlot.engine.audioDevices()

    fun setAudioDevice(name: String): Boolean {
        val ok = activeSlot.engine.setAudioDevice(name)
        if (ok) {
            deviceName = name
            spare?.engine?.setAudioDevice(name)
        }
        return ok
    }

    // 预载下一首到备用引擎：静音并停在开头，失败返回 false
    fun prepareSpare(url: String, record: RecordRequest? = null): Boolean {
        if (spareUnavailable || rampJob != null) return false
        val slot = spare ?: try {
            newSlot().also { created ->
                spare = created
                deviceName?.let { created.engine.setAudioDevice(it) }
            }
        } catch (e: LinkageError) {
            return markSpareUnavailable(e)
        } catch (e: IllegalStateException) {
            return markSpareUnavailable(e)
        }
        slot.discardRecord()
        slot.resetProgress()
        slot.record = record
        slot.seeked = false
        slot.engine.setVolume(0.0)
        slot.engine.load(url, 0L, paused = true, recordPath = record?.path)
        spareArmed = true
        return true
    }

    private fun deleteWhenReleased(file: File) {
        scope.launch(Dispatchers.IO) {
            repeat(DELETE_ATTEMPTS) {
                if (!file.exists() || file.delete()) return@launch
                delay(DELETE_RETRY_MS)
            }
        }
    }

    private fun markSpareUnavailable(e: Throwable): Boolean {
        AppLogger.w(TAG, "备用引擎创建失败，淡入淡出不可用", e)
        spareUnavailable = true
        return false
    }

    fun discardSpare() {
        spareArmed = false
        spare?.discardRecord()
        spare?.engine?.stop()
    }

    // 备用引擎升为主引擎并开始交叉淡化，返回新主引擎的当前进度与时长；未就绪返回 null
    fun promote(fadeMs: Long): Pair<Long, Long>? {
        val incoming = spare?.takeIf { spareArmed && it.loaded } ?: return null
        val outgoing = activeSlot
        spare = outgoing
        active = incoming
        spareArmed = false
        incoming.engine.setPaused(false)
        rampJob = scope.launch { ramp(outgoing, incoming, fadeMs) }
        return incoming.position to incoming.duration
    }

    // 等功率交叉；渐出引擎在录制缓存时多等它自然读完，再停止并回到备用位
    private suspend fun ramp(outgoing: Slot, incoming: Slot, fadeMs: Long) {
        val startedAt = System.nanoTime()
        while (true) {
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0
            val progress = (elapsedMs / fadeMs).coerceIn(0.0, 1.0).toFloat()
            outgoing.engine.setVolume(userVolume * CrossfadePolicy.fadeOutGain(progress, 1f))
            incoming.engine.setVolume(userVolume * CrossfadePolicy.fadeInGain(progress))
            if (progress >= 1f) break
            delay(RAMP_STEP_MS)
        }
        incoming.engine.setVolume(userVolume)
        if (outgoing.record != null && !outgoing.seeked) {
            withTimeoutOrNull(OUTGOING_DRAIN_TIMEOUT_MS) {
                while (!outgoing.ended) delay(OUTGOING_DRAIN_POLL_MS)
            }
        }
        outgoing.discardRecord()
        outgoing.engine.stop()
        rampJob = null
    }

    // 取消淡化与预载，主引擎音量恢复为用户音量
    private fun reset() {
        rampJob?.cancel()
        rampJob = null
        spareArmed = false
        spare?.discardRecord()
        spare?.engine?.stop()
        activeSlot.engine.setVolume(userVolume)
    }

    fun release() {
        reset()
        activeSlot.engine.release()
        spare?.engine?.release()
    }
}
