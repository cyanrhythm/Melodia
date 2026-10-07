package com.lin0721.linmusic.desktop.platform

import com.lin0721.linmusic.core.log.AppLogger

private const val TAG = "CrashHandler"

// 日志写盘走异步管道，崩溃后留出落盘时间
private const val FLUSH_WAIT_MS = 800L

// 未捕获异常先带环境信息写入日志，再交还原处理器，行为与 Android 端一致
class DesktopCrashHandler(
    private val describeEnvironment: () -> String,
    private val log: (message: String, throwable: Throwable) -> Unit,
    private val flush: () -> Unit,
    private val previous: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            log("================ FATAL CRASH ================\n${describeEnvironment()}\nThread      : ${thread.name}\n=============================================\n异常崩溃堆栈信息:", throwable)
            flush()
        } catch (_: Throwable) {
            // 记录失败不能妨碍原处理器
        } finally {
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        fun install() {
            Thread.setDefaultUncaughtExceptionHandler(
                DesktopCrashHandler(
                    describeEnvironment = ::environmentSummary,
                    log = { message, throwable -> AppLogger.e(TAG, message, throwable) },
                    flush = { Thread.sleep(FLUSH_WAIT_MS) },
                    previous = Thread.getDefaultUncaughtExceptionHandler()
                )
            )
        }

        internal fun environmentSummary(): String = buildString {
            appendLine("App Version : ${AppInfo.version}")
            appendLine("OS          : ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})")
            append("Java        : ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})")
        }
    }
}
