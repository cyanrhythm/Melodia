package com.lin0721.linmusic.desktop.player.mpv

import com.lin0721.linmusic.core.log.AppLogger
import com.sun.jna.Pointer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToLong

private const val TAG = "MpvEngine"

private const val OBSERVE_TIME_POS = 1L
private const val OBSERVE_DURATION = 2L
private const val OBSERVE_PAUSE = 3L

// 纯音频播放内核：只负责“播这个地址/暂停/跳转/音量”，队列与取地址由上层决定
class MpvEngine(private val listener: Listener) {

    interface Listener {
        fun onPositionChanged(positionMs: Long)
        fun onDurationChanged(durationMs: Long)
        fun onPauseChanged(paused: Boolean)
        fun onFileLoaded()

        // 自然播完或解码失败；主动 stop/替换文件不回调
        fun onEnded(isError: Boolean)
    }

    private val mpv: LibMpv = LibMpv.load()
    private val ctx: Pointer = mpv.mpv_create() ?: throw IllegalStateException("mpv_create 返回空句柄")
    private val running = AtomicBoolean(true)
    private val eventThread: Thread

    init {
        setOption("vid", "no")
        setOption("video", "no")
        setOption("terminal", "no")
        setOption("idle", "yes")
        setOption("audio-display", "no")
        setOption("keep-open", "no")
        check(mpv.mpv_initialize(ctx) >= 0) { "mpv_initialize 失败" }
        mpv.mpv_observe_property(ctx, OBSERVE_TIME_POS, "time-pos", MpvConst.FORMAT_DOUBLE)
        mpv.mpv_observe_property(ctx, OBSERVE_DURATION, "duration", MpvConst.FORMAT_DOUBLE)
        mpv.mpv_observe_property(ctx, OBSERVE_PAUSE, "pause", MpvConst.FORMAT_FLAG)
        eventThread = Thread(::eventLoop, "mpv-events").apply {
            isDaemon = true
            start()
        }
    }

    fun load(url: String, startMs: Long) {
        // start 为全局选项，对下一个载入的文件生效
        setProperty("start", if (startMs > 0) "+${startMs / 1000.0}" else "none")
        setProperty("pause", "no")
        command("loadfile", url, "replace")
    }

    fun setPaused(paused: Boolean) = setProperty("pause", if (paused) "yes" else "no")

    fun seekTo(positionMs: Long) = command("seek", (positionMs.coerceAtLeast(0) / 1000.0).toString(), "absolute")

    fun setVolume(percent: Int) = setProperty("volume", percent.coerceIn(0, 100).toString())

    fun stop() = command("stop")

    fun release() {
        if (!running.compareAndSet(true, false)) return
        mpv.mpv_wakeup(ctx)
        eventThread.join(1000)
        mpv.mpv_terminate_destroy(ctx)
    }

    private fun eventLoop() {
        while (running.get()) {
            val event = mpv.mpv_wait_event(ctx, 0.5) ?: continue
            try {
                when (event.getInt(MpvConst.EVENT_OFFSET_ID)) {
                    MpvConst.EVENT_NONE -> Unit
                    MpvConst.EVENT_SHUTDOWN -> running.set(false)
                    MpvConst.EVENT_FILE_LOADED -> listener.onFileLoaded()
                    MpvConst.EVENT_END_FILE -> handleEndFile(event)
                    MpvConst.EVENT_PROPERTY_CHANGE -> handlePropertyChange(event)
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "处理 mpv 事件异常", e)
            }
        }
    }

    private fun handleEndFile(event: Pointer) {
        val data = event.getPointer(MpvConst.EVENT_OFFSET_DATA) ?: return
        when (data.getInt(0)) {
            MpvConst.END_FILE_REASON_EOF -> listener.onEnded(isError = false)
            MpvConst.END_FILE_REASON_ERROR -> listener.onEnded(isError = true)
        }
    }

    private fun handlePropertyChange(event: Pointer) {
        val property = event.getPointer(MpvConst.EVENT_OFFSET_DATA) ?: return
        val value = property.getPointer(MpvConst.PROPERTY_OFFSET_DATA)
        val format = property.getInt(MpvConst.PROPERTY_OFFSET_FORMAT)
        when (event.getLong(MpvConst.EVENT_OFFSET_USERDATA)) {
            OBSERVE_TIME_POS -> if (format == MpvConst.FORMAT_DOUBLE && value != null) {
                listener.onPositionChanged((value.getDouble(0) * 1000).roundToLong())
            }
            OBSERVE_DURATION -> if (format == MpvConst.FORMAT_DOUBLE && value != null) {
                listener.onDurationChanged((value.getDouble(0) * 1000).roundToLong())
            }
            OBSERVE_PAUSE -> if (format == MpvConst.FORMAT_FLAG && value != null) {
                listener.onPauseChanged(value.getInt(0) != 0)
            }
        }
    }

    private fun setOption(name: String, value: String) {
        val code = mpv.mpv_set_option_string(ctx, name, value)
        if (code < 0) AppLogger.w(TAG, "设置选项失败 $name=$value：${mpv.mpv_error_string(code)}")
    }

    private fun setProperty(name: String, value: String) {
        if (!running.get()) return
        val code = mpv.mpv_set_property_string(ctx, name, value)
        if (code < 0) AppLogger.w(TAG, "设置属性失败 $name=$value：${mpv.mpv_error_string(code)}")
    }

    private fun command(vararg args: String) {
        if (!running.get()) return
        val code = mpv.mpv_command(ctx, arrayOf(*args, null))
        if (code < 0) AppLogger.w(TAG, "命令失败 ${args.joinToString(" ")}：${mpv.mpv_error_string(code)}")
    }
}
