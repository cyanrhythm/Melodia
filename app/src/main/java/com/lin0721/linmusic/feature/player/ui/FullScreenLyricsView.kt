package com.lin0721.linmusic.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.domain.LyricLine
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaWindowSizeClass
import com.lin0721.linmusic.core.ui.theme.MelodiaWindowSizeClass
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import org.koin.compose.koinInject

// ────────────────────────────────────────────────────────────────────────────
// 全屏歌词页：承载下拉关闭手势与滚动跟随状态，装配顶栏、歌词列表与播放控制
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun FullScreenLyricsView(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    // 同时需要高亮的行（对唱/背景和声的重叠区间）；留空时退回只高亮 currentIndex
    activeIndices: Set<Int> = emptySet(),
    isLoading: Boolean,
    title: String,
    artist: String,
    base: Color,
    highlightColor: Color,
    onSeek: (Long) -> Unit,
    hazeState: HazeState,
    onClose: () -> Unit,
    onDragClose: () -> Unit = onClose,
    isPlaying: Boolean,
    currentPositionProvider: () -> Long,
    lyricPositionProvider: () -> Long = currentPositionProvider,
    duration: Long,
    onTogglePlay: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    playMode: PlayMode,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onMoreClick: () -> Unit,
    onControlsVisibilityChange: (Boolean) -> Unit = {}
) {
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var timerJob by remember { mutableStateOf<Job?>(null) }

    val settingsPreferences: SettingsPreferences = koinInject()
    val fullScreenLyricTextSize by settingsPreferences.fullScreenLyricTextSize.collectAsStateWithLifecycle(initialValue = 22)
    val isExpanded = LocalMelodiaWindowSizeClass.current == MelodiaWindowSizeClass.Expanded
    val effectiveLyricTextSize = if (isExpanded) fullScreenLyricTextSize.coerceIn(20, 40) else fullScreenLyricTextSize
    val fullScreenLyricAlignment by settingsPreferences.fullScreenLyricAlignment.collectAsStateWithLifecycle(initialValue = "left")
    val fullScreenLyricSecondaryMode by settingsPreferences.fullScreenLyricSecondaryMode.collectAsStateWithLifecycle(initialValue = "translation")
    val fullScreenKaraokeAdvancedEffect by settingsPreferences.fullScreenKaraokeAdvancedEffect.collectAsStateWithLifecycle(initialValue = true)
    val fullScreenKaraokeGlowEffect by settingsPreferences.fullScreenKaraokeGlowEffect.collectAsStateWithLifecycle(initialValue = false)
    val amllLyricsEnabled by settingsPreferences.amllLyricsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val fullScreenLyricLineSpacing by settingsPreferences.fullScreenLyricLineSpacing.collectAsStateWithLifecycle(initialValue = 24)
    val fullScreenLyricSecondarySpacing by settingsPreferences.fullScreenLyricSecondarySpacing.collectAsStateWithLifecycle(initialValue = 6)
    val fullScreenLyricAutoHideControls by settingsPreferences.fullScreenLyricAutoHideControls.collectAsStateWithLifecycle(initialValue = false)
    var areControlsVisible by remember { mutableStateOf(true) }
    var autoHideJob by remember { mutableStateOf<Job?>(null) }

    val hasTranslation = remember(lyrics) { lyrics.any { it.translation != null } }
    val hasRoma = remember(lyrics) { lyrics.any { it.roma != null } }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val scheduleAutoHide: () -> Unit = {
        autoHideJob?.cancel()
        if (fullScreenLyricAutoHideControls && !showSettingsSheet) {
            autoHideJob = scope.launch {
                delay(5000L)
                areControlsVisible = false
            }
        }
    }

    val revealControls: () -> Unit = {
        areControlsVisible = true
        scheduleAutoHide()
    }

    val toggleControls: () -> Unit = {
        if (fullScreenLyricAutoHideControls) {
            if (areControlsVisible) {
                autoHideJob?.cancel()
                areControlsVisible = false
            } else {
                revealControls()
            }
        }
    }

    LaunchedEffect(fullScreenLyricAutoHideControls, showSettingsSheet) {
        if (!fullScreenLyricAutoHideControls || showSettingsSheet) {
            autoHideJob?.cancel()
            areControlsVisible = true
        } else {
            scheduleAutoHide()
        }
    }

    LaunchedEffect(areControlsVisible) {
        onControlsVisibilityChange(areControlsVisible)
    }

    DisposableEffect(Unit) {
        onDispose {
            onControlsVisibilityChange(true)
        }
    }
    val context = LocalContext.current

    val handleShareLyrics: () -> Unit = {
        val currentLyricText = lyrics.getOrNull(currentIndex)?.text?.takeIf { it.isNotBlank() }
        val shareText = if (currentLyricText != null) {
            "「$currentLyricText」\n——《$title》$artist"
        } else {
            "《$title》- $artist"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(intent, "分享歌词"))
    }

    val handleToggleSecondaryMode: () -> Unit = {
        val nextMode = when {
            hasTranslation && hasRoma -> when (fullScreenLyricSecondaryMode) {
                "translation" -> "roma"
                "roma" -> "none"
                else -> "translation"
            }
            hasTranslation -> when (fullScreenLyricSecondaryMode) {
                "translation" -> "none"
                else -> "translation"
            }
            hasRoma -> when (fullScreenLyricSecondaryMode) {
                "roma" -> "none"
                else -> "roma"
            }
            else -> "none"
        }
        scope.launch {
            settingsPreferences.saveFullScreenLyricSecondaryMode(nextMode)
        }
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = remember(configuration.screenHeightDp, density) {
        with(density) { configuration.screenHeightDp.dp.toPx() }
    }

    val dragState = rememberFullScreenLyricsDragState(
        lazyListState = lazyListState,
        onClose = onClose,
        onDragClose = onDragClose,
        screenHeightPx = screenHeightPx
    )

    LaunchedEffect(Unit) {
        dragState.reset()
    }

    DisposableEffect(Unit) {
        onDispose {
            dragState.reset()
        }
    }

    // 歌词页手势拖动的纯 UI 交互态，不涉及业务数据，只在本组件内部使用
    var isUserScrolling by remember { mutableStateOf(false) }

    val isPlayingState = rememberUpdatedState(isPlaying)

    // 拖动/点击跳转播放进度后，同时结束用户滚动态，恢复自动跟随当前歌词行
    val handleSeek: (Long) -> Unit = { timeMs ->
        isUserScrolling = false
        onSeek(timeMs)
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            if (isUserScrolling) {
                isUserScrolling = false
            }
        } else {
            timerJob?.cancel()
        }
    }

    // 仅在真实触摸手势按下与抬起时维护用户滚动状态，避免程序自动平滑居中滚动时被误判为用户拖拽
    val gestureModifier = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Press) {
                    timerJob?.cancel()
                    isUserScrolling = true
                    if (fullScreenLyricAutoHideControls) {
                        autoHideJob?.cancel()
                    }
                } else if (event.type == PointerEventType.Release) {
                    timerJob?.cancel()
                    if (isPlayingState.value) {
                        timerJob = scope.launch {
                            delay(3000L)
                            isUserScrolling = false
                        }
                    }
                    if (fullScreenLyricAutoHideControls && areControlsVisible) {
                        scheduleAutoHide()
                    }
                }
            }
        }
    }

    val topCornerRadius by remember {
        derivedStateOf {
            if (dragState.offsetY > 0f) 24.dp else 0.dp
        }
    }

    PlayerBackdrop(
        base = base,
        mode = BackdropMode.Immersive,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { dragState.onScreenHeightChange(it.height.toFloat()) }
            .nestedScroll(dragState.nestedScrollConnection)
            .graphicsLayer {
                translationY = dragState.offsetY
            }
            .clip(RoundedCornerShape(topStart = topCornerRadius, topEnd = topCornerRadius))
            // 拦截全屏歌词页空白处点击，轻触切换控制组件显隐
            .pointerInput(fullScreenLyricAutoHideControls) {
                if (fullScreenLyricAutoHideControls) {
                    detectTapGestures {
                        toggleControls()
                    }
                }
            }
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeChild(state = hazeState, style = HazeStyle(blurRadius = 40.dp, noiseFactor = 0.02f))
                .statusBarsPadding()
        ) {
            AnimatedVisibility(
                visible = areControlsVisible,
                enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { -it / 3 },
                exit = fadeOut(tween(220)) + slideOutVertically(tween(220)) { -it / 3 },
                modifier = Modifier.pointerInput(fullScreenLyricAutoHideControls) {
                    if (fullScreenLyricAutoHideControls) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent()
                                scheduleAutoHide()
                            }
                        }
                    }
                }
            ) {
                FullScreenLyricsHeader(
                    title = title,
                    artist = artist,
                    onClose = onClose,
                    onMoreClick = onMoreClick,
                    onDragDelta = { delta -> dragState.onHeaderDrag(delta) },
                    onDragStart = { dragState.onHeaderDragStart() },
                    onDragRelease = { velocity -> dragState.handleDragRelease(velocity = velocity) }
                )
            }

            FullScreenLyricsList(
                lyrics = lyrics,
                currentIndex = currentIndex,
                activeIndices = activeIndices,
                isLoading = isLoading,
                isUserScrolling = isUserScrolling,
                highlightColor = highlightColor,
                currentPositionProvider = lyricPositionProvider,
                lazyListState = lazyListState,
                viewportHeightPx = dragState.viewportHeightPx,
                onViewportHeightChange = { height -> dragState.onViewportHeightChange(height) },
                gestureModifier = gestureModifier,
                fontSize = effectiveLyricTextSize,
                alignment = fullScreenLyricAlignment,
                secondaryMode = fullScreenLyricSecondaryMode,
                lineSpacing = fullScreenLyricLineSpacing,
                secondarySpacing = fullScreenLyricSecondarySpacing,
                advancedKaraokeEffect = fullScreenKaraokeAdvancedEffect,
                karaokeGlowEffect = fullScreenKaraokeGlowEffect,
                isPlaying = isPlaying,
                onSeek = handleSeek,
                onLyricClick = { line ->
                    if (fullScreenLyricAutoHideControls && areControlsVisible) {
                        // 当控制栏显示时，轻触屏幕任意区域（包括歌词行）均立即隐藏控制栏，且不误触跳转进度
                        autoHideJob?.cancel()
                        areControlsVisible = false
                    } else {
                        // 控制栏处于隐藏状态（或未开启自动隐藏）时，点击歌词行正常跳转播放进度
                        timerJob?.cancel()
                        handleSeek(line.timeMs)
                    }
                }
            )

            AnimatedVisibility(
                visible = areControlsVisible,
                enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 3 },
                exit = fadeOut(tween(220)) + slideOutVertically(tween(220)) { it / 3 },
                modifier = Modifier.pointerInput(fullScreenLyricAutoHideControls) {
                    if (fullScreenLyricAutoHideControls) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent()
                                scheduleAutoHide()
                            }
                        }
                    }
                }
            ) {
                FullScreenControls(
                    isPlaying = isPlaying,
                    currentPositionProvider = currentPositionProvider,
                    duration = duration,
                    onSeek = handleSeek,
                    onTogglePlay = onTogglePlay,
                    onPlayNext = onPlayNext,
                    onPlayPrevious = onPlayPrevious,
                    playMode = playMode,
                    onToggleShuffle = onToggleShuffle,
                    onToggleRepeat = onToggleRepeat,
                    secondaryMode = fullScreenLyricSecondaryMode,
                    hasTranslation = hasTranslation,
                    hasRoma = hasRoma,
                    onToggleSecondaryMode = handleToggleSecondaryMode,
                    onShareLyrics = handleShareLyrics,
                    onLyricsSettingsClick = { showSettingsSheet = true }
                )
            }
        }

        if (showSettingsSheet) {
            FullScreenLyricsSettingsSheet(
                fontSize = fullScreenLyricTextSize,
                onFontSizeChange = { size ->
                    scope.launch { settingsPreferences.saveFullScreenLyricTextSize(size) }
                },
                lineSpacing = fullScreenLyricLineSpacing,
                onLineSpacingChange = { spacing ->
                    scope.launch { settingsPreferences.saveFullScreenLyricLineSpacing(spacing) }
                },
                secondarySpacing = fullScreenLyricSecondarySpacing,
                onSecondarySpacingChange = { spacing ->
                    scope.launch { settingsPreferences.saveFullScreenLyricSecondarySpacing(spacing) }
                },
                alignment = fullScreenLyricAlignment,
                onAlignmentChange = { align ->
                    scope.launch { settingsPreferences.saveFullScreenLyricAlignment(align) }
                },
                secondaryMode = fullScreenLyricSecondaryMode,
                onSecondaryModeChange = { mode ->
                    scope.launch { settingsPreferences.saveFullScreenLyricSecondaryMode(mode) }
                },
                hasTranslation = hasTranslation,
                hasRoma = hasRoma,
                advancedKaraokeEffect = fullScreenKaraokeAdvancedEffect,
                onAdvancedKaraokeEffectChange = { enabled ->
                    scope.launch { settingsPreferences.saveFullScreenKaraokeAdvancedEffect(enabled) }
                },
                karaokeGlowEffect = fullScreenKaraokeGlowEffect,
                onKaraokeGlowEffectChange = { enabled ->
                    scope.launch { settingsPreferences.saveFullScreenKaraokeGlowEffect(enabled) }
                },
                amllLyricsEnabled = amllLyricsEnabled,
                onAmllLyricsEnabledChange = { enabled ->
                    scope.launch { settingsPreferences.saveAmllLyricsEnabled(enabled) }
                },
                autoHideControls = fullScreenLyricAutoHideControls,
                onAutoHideControlsChange = { enabled ->
                    scope.launch { settingsPreferences.saveFullScreenLyricAutoHideControls(enabled) }
                },
                onDismiss = { showSettingsSheet = false }
            )
        }
    }
}
