package com.lin0721.linmusic.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.lin0721.linmusic.desktop.ui.lyricsview.rememberLyricsViewState
import com.lin0721.linmusic.desktop.ui.rememberFullscreenState
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.desktop.di.desktopPlatformModule
import com.lin0721.linmusic.desktop.di.desktopViewModelModule
import com.lin0721.linmusic.desktop.platform.CloseAction
import com.lin0721.linmusic.desktop.platform.DesktopPreferences
import com.lin0721.linmusic.desktop.platform.GlobalHotkeys
import com.lin0721.linmusic.desktop.platform.HotkeyAction
import com.lin0721.linmusic.desktop.platform.smtc.SmtcSession
import com.lin0721.linmusic.desktop.player.MpvPlaybackController
import com.lin0721.linmusic.desktop.ui.MelodiaDesktopApp
import com.lin0721.linmusic.desktop.ui.WindowChromeEffect
import com.lin0721.linmusic.desktop.ui.lyrics.DesktopLyricWindow
import com.lin0721.linmusic.desktop.ui.tray.TrayHost
import com.lin0721.linmusic.desktop.ui.tray.TrayMenuEntry
import com.lin0721.linmusic.desktop.ui.tray.isTraySupported
import com.lin0721.linmusic.desktop.ui.theme.MelodiaDesktopTheme
import com.lin0721.linmusic.di.networkModule
import com.lin0721.linmusic.di.repositoryModule
import com.lin0721.linmusic.di.sourceModule
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import com.lin0721.linmusic.feature.podcast.data.PodcastProgressTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.lin0721.linmusic.desktop.platform.DesktopCacheMigration
import com.lin0721.linmusic.desktop.platform.DesktopImageLoader
import com.lin0721.linmusic.desktop.platform.DesktopLogging
import com.lin0721.linmusic.desktop.platform.DesktopPaths
import com.lin0721.linmusic.desktop.platform.SingleInstance
import org.jetbrains.skia.Image
import org.koin.core.context.startKoin
import kotlin.system.exitProcess
import java.awt.Dimension

private const val VOLUME_STEP = 5
private const val EXIT_ANIMATION_MS = 250L

fun main() {
    DesktopLogging.install()
    DesktopImageLoader.install()
    // 已有实例时只通知它显示窗口，本进程不再启动，避免两个进程争用数据文件与播放设备
    val activationRequests = Channel<Unit>(Channel.CONFLATED)
    if (!SingleInstance(DesktopPaths.dataDir).acquire { activationRequests.trySend(Unit) }) {
        exitProcess(0)
    }
    // 须先于缓存对象创建
    DesktopCacheMigration.migrateAll()
    val koin = startKoin {
        modules(desktopPlatformModule, networkModule, repositoryModule, sourceModule, desktopViewModelModule)
    }.koin
    val controller = koin.get<PlaybackController>()
    // 进程内常驻记录播客收听进度
    PodcastProgressTracker(controller, koin.get()).start(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    val mpvController = controller as? MpvPlaybackController
    val settingsPreferences = koin.get<SettingsPreferences>()
    val playerViewModel = koin.get<PlayerViewModel>()
    val desktopPreferences = koin.get<DesktopPreferences>()
    val hotkeys = koin.get<GlobalHotkeys>()
    val smtc = koin.get<SmtcSession>()
    // 系统媒体卡片可用时由它接管媒体键，否则回退全局热键
    val smtcActive = smtc.start()

    application {
        val scope = rememberCoroutineScope()
        var isMainVisible by remember { mutableStateOf(true) }
        // 已可见但被遮挡或最小化时，靠计数触发再次置前
        var bringToFrontRequest by remember { mutableStateOf(0) }
        val showMainWindow: () -> Unit = {
            isMainVisible = true
            bringToFrontRequest++
        }
        var isLyricLocked by remember { mutableStateOf(false) }
        val showDesktopLyric by settingsPreferences.showDesktopLrc.collectAsState(initial = false)
        val closeAction by desktopPreferences.closeAction.collectAsState(initial = CloseAction.TRAY)
        val isPlaying by controller.playWhenReady.collectAsState()
        val nowPlaying by controller.nowPlaying.collectAsState()

        val setDesktopLyric: (Boolean) -> Unit = { enabled ->
            scope.launch { settingsPreferences.saveShowDesktopLrc(enabled) }
        }
        // 先隐藏窗口等消失动画播完，再补报当前曲目播放时长、销毁 mpv 句柄并退出
        var isExiting by remember { mutableStateOf(false) }
        val exit: () -> Unit = {
            if (!isExiting) {
                isExiting = true
                isMainVisible = false
                scope.launch {
                    delay(EXIT_ANIMATION_MS)
                    smtc.shutdown()
                    mpvController?.release()
                    exitApplication()
                }
            }
        }

        // 主窗口关闭按钮与 Alt+F4 统一按设置处理；系统不支持托盘时隐藏后无法找回，只能退出
        val closeMainWindow = {
            if (closeAction == CloseAction.EXIT || !isTraySupported) exit() else isMainVisible = false
        }

        // 其他实例要求激活：从托盘恢复并置前
        LaunchedEffect(Unit) {
            for (request in activationRequests) showMainWindow()
        }

        hotkeys.onAction = { action ->
            when (action) {
                HotkeyAction.PlayPause -> controller.togglePlayPause()
                HotkeyAction.Previous -> controller.skipToPrevious()
                HotkeyAction.Next -> controller.playNext()
                HotkeyAction.VolumeUp -> mpvController?.let { it.setVolume(it.volume.value + VOLUME_STEP) }
                HotkeyAction.VolumeDown -> mpvController?.let { it.setVolume(it.volume.value - VOLUME_STEP) }
                HotkeyAction.ToggleDesktopLyric -> setDesktopLyric(!showDesktopLyric)
            }
        }
        DisposableEffect(Unit) {
            hotkeys.start()
            onDispose { hotkeys.stop() }
        }
        LaunchedEffect(Unit) {
            combine(desktopPreferences.hotkeys, desktopPreferences.mediaKeysEnabled, ::Pair)
                .collect { (custom, mediaKeys) ->
                    smtc.setEnabled(mediaKeys)
                    hotkeys.apply(custom, mediaKeys && !smtcActive)
                }
        }
        LaunchedEffect(Unit) {
            smtc.bind(controller, playerViewModel)
        }

        val appIcon = remember { loadAppIcon() }
        val trackText = nowPlaying?.let { "${it.title} - ${it.artist}" }
        TrayHost(
            tooltip = trackText?.let { "Melodia - $it" } ?: "Melodia",
            header = trackText?.let { "正在播放：$it" },
            entries = listOf(
                TrayMenuEntry.Action("显示主窗口", showMainWindow),
                TrayMenuEntry.Divider,
                TrayMenuEntry.Action(if (isPlaying) "暂停" else "播放", controller::togglePlayPause),
                TrayMenuEntry.Action("上一首", controller::skipToPrevious),
                TrayMenuEntry.Action("下一首", controller::playNext),
                TrayMenuEntry.Divider,
                TrayMenuEntry.Toggle("桌面歌词", showDesktopLyric, setDesktopLyric),
                TrayMenuEntry.Toggle("锁定桌面歌词", isLyricLocked) { isLyricLocked = it },
                TrayMenuEntry.Divider,
                TrayMenuEntry.Action("退出", exit)
            ),
            onOpenMain = showMainWindow
        )

        val windowState = rememberWindowState(
            size = DpSize(1280.dp, 800.dp),
            position = WindowPosition(Alignment.Center)
        )
        val fullscreen = rememberFullscreenState(windowState)
        val lyricsView = rememberLyricsViewState(fullscreen)
        Window(
            onCloseRequest = closeMainWindow,
            visible = isMainVisible,
            state = windowState,
            title = "Melodia",
            icon = appIcon,
            undecorated = true,
            onPreviewKeyEvent = { event ->
                if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                    // 先收起全屏歌词；其后才轮到退出窗口全屏
                    when {
                        lyricsView.isOpen -> {
                            lyricsView.close()
                            true
                        }
                        fullscreen.isFullscreen -> {
                            fullscreen.exit()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(960, 600)
            }
            LaunchedEffect(isMainVisible, bringToFrontRequest) {
                if (isMainVisible) {
                    windowState.isMinimized = false
                    window.toFront()
                }
            }
            // 全屏与最大化一样不要圆角和边框线
            WindowChromeEffect(maximized = windowState.placement != WindowPlacement.Floating)
            MelodiaDesktopTheme {
                MelodiaDesktopApp(
                    windowState = windowState,
                    fullscreen = fullscreen,
                    lyricsView = lyricsView,
                    onClose = closeMainWindow
                )
            }
        }

        DesktopLyricWindow(
            visible = showDesktopLyric && !isExiting,
            locked = isLyricLocked,
            playerViewModel = playerViewModel,
            controller = controller,
            settingsPreferences = settingsPreferences,
            onHide = { setDesktopLyric(false) }
        )
    }
}

// 窗口与托盘共用的应用图标；资源缺失时退回空白图标而不中断启动
private fun loadAppIcon(): Painter {
    val bytes = Thread.currentThread().contextClassLoader?.getResourceAsStream("melodia.png")?.use { it.readBytes() }
        ?: return BitmapPainter(ImageBitmap(32, 32))
    return BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
}
