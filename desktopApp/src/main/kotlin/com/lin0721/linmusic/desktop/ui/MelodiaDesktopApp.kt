package com.lin0721.linmusic.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import com.lin0721.linmusic.core.auth.LoginViewModel
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.download.DownloadTrackInfo
import com.lin0721.linmusic.core.download.SongDownloader
import com.lin0721.linmusic.core.download.yearFromEpochMillis
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.desktop.platform.download.DesktopSongDownloader
import com.lin0721.linmusic.desktop.platform.DesktopPreferences
import com.lin0721.linmusic.desktop.platform.LibraryMode
import com.lin0721.linmusic.desktop.platform.LibraryViewMode
import com.lin0721.linmusic.desktop.ui.lyricsview.CHROME_ANIM_MS
import com.lin0721.linmusic.desktop.ui.lyricsview.LyricsViewOverlay
import com.lin0721.linmusic.desktop.ui.lyricsview.LyricsViewState
import com.lin0721.linmusic.desktop.ui.navigation.BackStack
import com.lin0721.linmusic.desktop.ui.navigation.DesktopFrameHost
import com.lin0721.linmusic.desktop.ui.navigation.DesktopRoute
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.theme.DesktopDimens
import com.lin0721.linmusic.feature.artist.ui.ArtistViewModel
import com.lin0721.linmusic.feature.home.ui.HomeViewModel
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import com.lin0721.linmusic.feature.library.ui.LibraryItemType
import com.lin0721.linmusic.feature.library.ui.LibraryViewModel
import com.lin0721.linmusic.feature.music.ui.MusicViewModel
import com.lin0721.linmusic.feature.music.ui.StyleDetailViewModel
import com.lin0721.linmusic.feature.newworks.ui.NewWorksViewModel
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import com.lin0721.linmusic.feature.playlist.ui.PlaylistViewModel
import com.lin0721.linmusic.feature.podcast.ui.PodcastCategoryViewModel
import com.lin0721.linmusic.feature.podcast.ui.PodcastHomeViewModel
import com.lin0721.linmusic.feature.podcast.ui.PodcastSubscribedViewModel
import com.lin0721.linmusic.feature.podcast.ui.PodcastToplistViewModel
import com.lin0721.linmusic.feature.podcast.ui.RadioDetailViewModel
import com.lin0721.linmusic.feature.search.ui.DiscoveryUiState
import com.lin0721.linmusic.feature.search.ui.PlaylistCategoryViewModel
import com.lin0721.linmusic.feature.search.ui.SearchViewModel
import com.lin0721.linmusic.desktop.player.MpvPlaybackController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext

private val ChromeEnter = fadeIn(tween(CHROME_ANIM_MS)) + expandVertically(tween(CHROME_ANIM_MS))
private val ChromeExit = fadeOut(tween(CHROME_ANIM_MS)) + shrinkVertically(tween(CHROME_ANIM_MS))

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WindowScope.MelodiaDesktopApp(
    windowState: WindowState,
    fullscreen: FullscreenState,
    lyricsView: LyricsViewState,
    onClose: () -> Unit
) {
    val koin = remember { GlobalContext.get() }
    val homeViewModel = remember { koin.get<HomeViewModel>() }
    val musicViewModel = remember { koin.get<MusicViewModel>() }
    val podcastViewModel = remember { koin.get<PodcastHomeViewModel>() }
    val newWorksViewModel = remember { koin.get<NewWorksViewModel>() }
    val libraryViewModel = remember { koin.get<LibraryViewModel>() }
    val loginViewModel = remember { koin.get<LoginViewModel>() }
    val playbackController = remember { koin.get<PlaybackController>() }
    val playerViewModel = remember { koin.get<PlayerViewModel>() }
    val searchViewModel = remember { koin.get<SearchViewModel>() }
    val desktopPreferences = remember { koin.get<DesktopPreferences>() }
    val settingsPreferences = remember { koin.get<SettingsPreferences>() }
    val songDownloader = remember { koin.get<SongDownloader>() }
    val downloader = songDownloader as? DesktopSongDownloader
    val downloadTasks = downloader?.tasks?.collectAsState()?.value.orEmpty()
    val downloadLevel by settingsPreferences.wifiQuality.collectAsState(initial = "standard")
    val showDesktopLyric by settingsPreferences.showDesktopLrc.collectAsState(initial = false)
    val mpvController = playbackController as? MpvPlaybackController

    val backStack = remember { BackStack(DesktopRoute.Home) }
    val userProfile by homeViewModel.userProfile.collectAsState()
    var showLogin by rememberSaveable { mutableStateOf(false) }
    var homeTab by rememberSaveable { mutableStateOf(HOME_TAB_ALL) }
    var showNewWorks by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val isMaximized = windowState.placement == WindowPlacement.Maximized
    val isFullscreen = fullscreen.isFullscreen
    val nowPlaying by playbackController.nowPlaying.collectAsState()
    // 没有曲目时歌词界面无内容可看，自动收起
    LaunchedEffect(nowPlaying == null) { if (nowPlaying == null) lyricsView.close() }
    // 初值为关闭：读到已保存的开启状态后，侧栏随动画展开
    val nowPlayingOpen by desktopPreferences.nowPlayingPanelOpen.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    val setNowPlayingOpen: (Boolean) -> Unit = { open ->
        scope.launch { desktopPreferences.saveNowPlayingPanelOpen(open) }
    }
    // 启动时同步读到上次的形态，避免先按默认宽度再动画到收起
    val initialLibraryMode = remember { runBlocking { desktopPreferences.libraryMode.first() } }
    val libraryMode by desktopPreferences.libraryMode.collectAsState(initial = initialLibraryMode)
    val setLibraryMode: (LibraryMode) -> Unit = { mode ->
        scope.launch { desktopPreferences.saveLibraryMode(mode) }
    }
    // 两侧栏拖动调整过的宽度；拖动期间只改内存值，松手才落盘
    val initialLibraryWidth = remember { runBlocking { desktopPreferences.libraryWidth.first() } }
    val initialDockWidth = remember { runBlocking { desktopPreferences.nowPlayingWidth.first() } }
    var libraryWidthPref by remember { mutableStateOf((initialLibraryWidth ?: DesktopDimens.SidebarWidth.value).dp) }
    var dockWidthPref by remember { mutableStateOf((initialDockWidth ?: DesktopDimens.NowPlayingWidth.value).dp) }
    var resizing by remember { mutableStateOf(false) }
    var libraryDragStart by remember { mutableStateOf(0.dp) }
    var dockDragStart by remember { mutableStateOf(0.dp) }
    val initialViewMode = remember { runBlocking { desktopPreferences.libraryViewMode.first() } }
    val libraryViewMode by desktopPreferences.libraryViewMode.collectAsState(initial = initialViewMode)
    val setLibraryViewMode: (LibraryViewMode) -> Unit = { mode ->
        scope.launch { desktopPreferences.saveLibraryViewMode(mode) }
    }
    // 音乐库展开时右侧栏先收成窄条，期间的开合只在本次展开内有效，不改保存的开关值；
    // 手动打开右侧栏时音乐库保持展开，只是让出宽度
    val isLibraryExpanded = libraryMode == LibraryMode.EXPANDED
    var expandedDockOpen by remember { mutableStateOf(false) }
    LaunchedEffect(isLibraryExpanded) { expandedDockOpen = false }
    val dockOpen = if (isLibraryExpanded) expandedDockOpen else nowPlayingOpen
    val setDockOpen: (Boolean) -> Unit = { open ->
        if (isLibraryExpanded) expandedDockOpen = open else setNowPlayingOpen(open)
    }
    var dockOverlay by remember { mutableStateOf<DockOverlay?>(null) }
    // 右侧栏收起后覆盖面板一并撤掉，下次展开回到正在播放页
    LaunchedEffect(dockOpen) { if (!dockOpen) dockOverlay = null }
    // 覆盖面板已显示则关掉它；否则展开右侧栏并把面板盖上去
    val toggleOverlay: (DockOverlay) -> Unit = { target ->
        if (dockOpen && dockOverlay == target) {
            dockOverlay = null
        } else {
            dockOverlay = target
            setDockOpen(true)
        }
    }
    val searchInput by searchViewModel.inputState.collectAsState()
    val discovery by searchViewModel.discoveryState.collectAsState()
    val defaultKeyword = (discovery as? DiscoveryUiState.Success)?.defaultKeyword.orEmpty()
    val openSearch = { backStack.navigate(DesktopRoute.Search) }
    val navigatorMessages = remember { MutableSharedFlow<String>(extraBufferCapacity = 8) }
    val navigator = DesktopNavigator(
        isLoggedIn = userProfile != null,
        openArtist = { id, name -> backStack.navigate(DesktopRoute.Artist(id, name)) },
        openPlaylist = { id, name -> backStack.navigate(DesktopRoute.Playlist(id, name)) },
        openAlbum = { id, name -> backStack.navigate(DesktopRoute.Playlist(id, name, isAlbum = true)) },
        openRadio = { id -> backStack.navigate(DesktopRoute.Radio(id)) },
        openPodcastSubscribed = { backStack.navigate(DesktopRoute.PodcastSubscribed) },
        openPodcastToplist = { backStack.navigate(DesktopRoute.PodcastToplist) },
        openPodcastCategory = { id, name -> backStack.navigate(DesktopRoute.PodcastCategory(id, name)) },
        openLogin = { showLogin = true },
        downloadLevel = downloadLevel,
        downloadTrack = { track ->
            songDownloader.enqueueSingle(
                DownloadTrackInfo(
                    track.id, track.name, track.ar.joinToString("/") { it.name },
                    track.al.name, track.al.picUrl.takeIf { it.isNotBlank() },
                    yearFromEpochMillis(track.publishTime)
                ),
                downloadLevel
            )
            navigatorMessages.tryEmit("已加入下载队列")
        },
        showMessage = { navigatorMessages.tryEmit(it) }
    )

    val saveableStateHolder = rememberSaveableStateHolder()
    val frameHost = remember(saveableStateHolder) {
        DesktopFrameHost(
            saveableStateHolder = saveableStateHolder,
            liveEntryIds = { backStack.liveEntryIds },
            scope = scope,
            onToast = { navigatorMessages.tryEmit(it) }
        )
    }
    val liveEntryIds = backStack.liveEntryIds
    LaunchedEffect(liveEntryIds) { frameHost.reconcile() }

    LaunchedEffect(Unit) {
        val playbackMessages = mpvController?.messages ?: emptyFlow()
        val downloadMessages = downloader?.messages ?: emptyFlow()
        merge(
            homeViewModel.toastEvent,
            libraryViewModel.toastEvent,
            searchViewModel.toastEvent,
            playerViewModel.toastEvent,
            newWorksViewModel.toastEvent,
            navigatorMessages,
            playbackMessages,
            downloadMessages
        )
            .collect { snackbarHostState.showSnackbar(it) }
    }

    val openLibraryItem: (LibraryItem) -> Unit = { item ->
        item.id.toLongOrNull()?.let { id ->
            when (item.type) {
                LibraryItemType.PLAYLIST -> backStack.navigate(DesktopRoute.Playlist(id, item.title))
                LibraryItemType.ALBUM -> navigator.openAlbum(id, item.title)
                LibraryItemType.ARTIST -> navigator.openArtist(id, item.title)
            }
        }
    }

    CompositionLocalProvider(LocalDesktopNavigator provides navigator) {
        Box(
            Modifier.fillMaxSize().background(DesktopColors.WindowBackground)
                .onPointerEvent(PointerEventType.Enter) { lyricsView.pointerInWindow = true }
                .onPointerEvent(PointerEventType.Exit) { lyricsView.pointerInWindow = false }
        ) {
            Column(Modifier.fillMaxSize()) {
                // 全屏或歌词沉浸态时收起自绘标题栏，Esc 或底栏按钮退出
                AnimatedVisibility(
                    visible = !isFullscreen && !lyricsView.isImmersive,
                    enter = ChromeEnter,
                    exit = ChromeExit
                ) { TitleBar(
                    backStack = backStack,
                    isMaximized = isMaximized,
                    userProfile = userProfile,
                    searchQuery = searchInput.query,
                    searchPlaceholder = defaultKeyword.ifBlank { "想播放什么？" },
                    onSearchQueryChange = { query ->
                        openSearch()
                        // 发现态下开始输入才切到输入态，聚焦本身不切换，保证热搜榜可见
                        searchViewModel.activateSearch()
                        searchViewModel.updateQuery(query)
                    },
                    onSearchFocused = openSearch,
                    onSearchSubmit = {
                        openSearch()
                        searchViewModel.searchWithKeyword(searchInput.query.ifBlank { defaultKeyword })
                    },
                    isBrowseActive = backStack.current == DesktopRoute.Browse,
                    onBrowseClick = { backStack.navigate(DesktopRoute.Browse) },
                    downloadTasks = downloadTasks,
                    downloadsOpen = dockOpen && dockOverlay == DockOverlay.Downloads,
                    onDownloadsClick = downloader?.let { { toggleOverlay(DockOverlay.Downloads) } },
                    onLoginClick = { showLogin = true },
                    onSettingsClick = { backStack.navigate(DesktopRoute.Settings) },
                    onLogoutClick = homeViewModel::logout,
                    onMinimize = { windowState.isMinimized = true },
                    onToggleMaximize = {
                        windowState.placement = if (isMaximized) WindowPlacement.Floating else WindowPlacement.Maximized
                    },
                    onClose = onClose
                ) }
                BoxWithConstraints(Modifier.weight(1f)) {
                    val available = maxWidth - DesktopDimens.PaneGap * 2
                    // 无曲目时下载面板也要能打开，右侧栏只承载它
                    val hasTrack = nowPlaying != null || dockOverlay == DockOverlay.Downloads
                    // 侧栏最宽不超过固定上限，且尽量给中间内容区留出 CenterMinWidth；
                    // 两侧互相让位时，音乐库按正在播放栏的记忆宽度算，正在播放栏按音乐库的实际占用算
                    val dockStaticOccupied = when {
                        !hasTrack -> 0.dp
                        dockOpen -> dockWidthPref.coerceIn(DesktopDimens.NowPlayingMinWidth, DesktopDimens.NowPlayingMaxWidth) +
                            DesktopDimens.PaneGap
                        else -> DesktopDimens.NowPlayingHandleWidth + DesktopDimens.PaneGap
                    }
                    val libraryMax = maxOf(
                        DesktopDimens.LibraryMinWidth,
                        minOf(DesktopDimens.LibraryMaxWidth, available - dockStaticOccupied - DesktopDimens.PaneGap - DesktopDimens.CenterMinWidth)
                    )
                    val libraryDefaultWidth = libraryWidthPref.coerceIn(DesktopDimens.LibraryMinWidth, libraryMax)
                    val libraryOccupied = if (libraryMode == LibraryMode.RAIL) DesktopDimens.LibraryRailWidth else libraryDefaultWidth
                    val dockMax = maxOf(
                        DesktopDimens.NowPlayingMinWidth,
                        minOf(
                            DesktopDimens.NowPlayingMaxWidth,
                            available - libraryOccupied - DesktopDimens.PaneGap * 2 - DesktopDimens.CenterMinWidth
                        )
                    )
                    val dockOpenWidth = dockWidthPref.coerceIn(DesktopDimens.NowPlayingMinWidth, dockMax)
                    val dockState = rememberNowPlayingDockState(hasTrack, dockOpen, dockOpenWidth, resizing)
                    val workspace = rememberWorkspaceLayout(libraryMode, available, dockState, libraryDefaultWidth, resizing)
                    CompositionLocalProvider(
                        LocalPaneWidthExtra provides { workspace.centerWidthExtra },
                        LocalPaneResizing provides resizing
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = DesktopDimens.PaneGap)) {
                            LibraryPane(
                                mode = libraryMode,
                                width = workspace.libraryWidth,
                                defaultWidth = libraryDefaultWidth,
                                expandedWidth = workspace.expandedWidth,
                                viewModel = libraryViewModel,
                                isLoggedIn = userProfile != null,
                                onLoginClick = { showLogin = true },
                                onItemClick = openLibraryItem,
                                onModeChange = setLibraryMode,
                                viewMode = libraryViewMode,
                                onViewModeChange = setLibraryViewMode
                            )
                            // 缝隙即拖动条：拖窄过阈值吸附成窄条，拖宽只停在上限，不会变成展开态
                            PaneResizeHandle(
                                width = workspace.centerGap,
                                enabled = libraryMode != LibraryMode.EXPANDED,
                                onDragStart = {
                                    resizing = true
                                    libraryDragStart = workspace.libraryWidth
                                },
                                onDrag = { delta ->
                                    val proposed = libraryDragStart + delta
                                    if (proposed < DesktopDimens.PaneSnapWidth) {
                                        resizing = false
                                        if (libraryMode != LibraryMode.RAIL) setLibraryMode(LibraryMode.RAIL)
                                    } else {
                                        resizing = true
                                        if (libraryMode != LibraryMode.DEFAULT) setLibraryMode(LibraryMode.DEFAULT)
                                        libraryWidthPref = proposed.coerceIn(DesktopDimens.LibraryMinWidth, libraryMax)
                                    }
                                },
                                onDragEnd = {
                                    resizing = false
                                    scope.launch { desktopPreferences.saveLibraryWidth(libraryWidthPref.value) }
                                }
                            )
                            Pane(Modifier.weight(1f)) {
                                Box(Modifier.settledLayoutWidth(LocalPaneWidthExtra.current).fillMaxSize()) {
                                    val entry = backStack.currentEntry
                                    frameHost.Provide(entry) {
                                        when (val route = entry.route) {
                                            DesktopRoute.Home -> HomePage(
                                                viewModel = homeViewModel,
                                                musicViewModel = musicViewModel,
                                                podcastViewModel = podcastViewModel,
                                                newWorksViewModel = newWorksViewModel,
                                                selectedTab = homeTab,
                                                // 点任意主胶囊都回到该 tab 的默认内容，「最新」只能由二级胶囊单独选中；
                                                // 已在「音乐」默认内容时再点「音乐」则回到「全部」，收起二级胶囊
                                                onTabSelected = {
                                                    homeTab = if (it == HOME_TAB_MUSIC && homeTab == HOME_TAB_MUSIC && !showNewWorks) {
                                                        HOME_TAB_ALL
                                                    } else {
                                                        it
                                                    }
                                                    showNewWorks = false
                                                },
                                                newWorksSelected = showNewWorks,
                                                onNewWorksSelectedChange = { showNewWorks = it },
                                                onPlaylistClick = { id, title -> backStack.navigate(DesktopRoute.Playlist(id, title)) },
                                                onStyleClick = { id, name -> backStack.navigate(DesktopRoute.Style(id, name)) }
                                            )
                                            is DesktopRoute.Style -> StyleDetailPage(
                                                tagId = route.id,
                                                name = route.name,
                                                viewModel = frameHost.viewModelFor(entry.id, StyleDetailViewModel::class, { it.toastEvent }) { koin.get() },
                                                controller = playbackController
                                            )
                                            is DesktopRoute.Playlist -> PlaylistPage(
                                                playlistId = route.id,
                                                isAlbum = route.isAlbum,
                                                viewModel = frameHost.viewModelFor(entry.id, PlaylistViewModel::class, { it.toastEvent }) { koin.get() },
                                                controller = playbackController
                                            )
                                            DesktopRoute.Browse -> BrowsePage(
                                                viewModel = searchViewModel,
                                                onHotSearchClick = { keyword ->
                                                    backStack.navigate(DesktopRoute.Search)
                                                    searchViewModel.searchWithKeyword(keyword)
                                                },
                                                onCategoryClick = { backStack.navigate(DesktopRoute.PlaylistCategory(it)) }
                                            )
                                            is DesktopRoute.PlaylistCategory -> PlaylistCategoryPage(
                                                category = route.name,
                                                viewModel = frameHost.viewModelFor(entry.id, PlaylistCategoryViewModel::class, { it.toastEvent }) { koin.get() },
                                                onPlaylistClick = { id, title -> backStack.navigate(DesktopRoute.Playlist(id, title)) }
                                            )
                                            is DesktopRoute.Artist -> ArtistPage(
                                                artistId = route.id,
                                                viewModel = frameHost.viewModelFor(entry.id, ArtistViewModel::class, { it.toastEvent }) { koin.get() },
                                                controller = playbackController
                                            )
                                            is DesktopRoute.Radio -> RadioDetailPage(
                                                radioId = route.id,
                                                viewModel = frameHost.viewModelFor(entry.id, RadioDetailViewModel::class, { it.toastEvent }) { koin.get() }
                                            )
                                            DesktopRoute.PodcastSubscribed -> PodcastSubscribedPage(
                                                viewModel = frameHost.viewModelFor(entry.id, PodcastSubscribedViewModel::class, { emptyFlow() }) { koin.get() }
                                            )
                                            DesktopRoute.PodcastToplist -> PodcastToplistPage(
                                                viewModel = frameHost.viewModelFor(entry.id, PodcastToplistViewModel::class, { emptyFlow() }) { koin.get() }
                                            )
                                            is DesktopRoute.PodcastCategory -> PodcastCategoryPage(
                                                categoryId = route.id,
                                                name = route.name,
                                                viewModel = frameHost.viewModelFor(entry.id, PodcastCategoryViewModel::class, { emptyFlow() }) { koin.get() }
                                            )
                                            DesktopRoute.Settings -> SettingsPage()
                                            DesktopRoute.Search -> SearchPage(
                                                viewModel = searchViewModel,
                                                controller = playbackController,
                                                onOpenPlaylist = { id, title, isAlbum ->
                                                    backStack.navigate(DesktopRoute.Playlist(id, title, isAlbum))
                                                }
                                            )
                                    }
                                    }
                                }
                            }
                            NowPlayingDock(
                                state = dockState,
                                hasTrack = hasTrack,
                                open = dockOpen,
                                overlay = dockOverlay,
                                audioOutput = mpvController,
                                downloader = downloader,
                                onCloseOverlay = { dockOverlay = null },
                                onOpenComments = { toggleOverlay(DockOverlay.Comments) },
                                onOpenLyricsView = lyricsView::open,
                                onOpenLyricsFullscreen = lyricsView::openWithFullscreen,
                                onOpenChange = setDockOpen,
                                controller = playbackController,
                                playerViewModel = playerViewModel,
                                onResizeStart = {
                                    resizing = true
                                    dockDragStart = dockState.width
                                },
                                onResize = { delta ->
                                    val proposed = dockDragStart - delta
                                    if (proposed < DesktopDimens.PaneSnapWidth) {
                                        resizing = false
                                        if (dockOpen) setDockOpen(false)
                                    } else {
                                        resizing = true
                                        dockWidthPref = proposed.coerceIn(DesktopDimens.NowPlayingMinWidth, dockMax)
                                    }
                                },
                                onResizeEnd = {
                                    resizing = false
                                    scope.launch { desktopPreferences.saveNowPlayingWidth(dockWidthPref.value) }
                                }
                            )
                        }
                    }
                    // 盖住整个工作区的全屏歌词，收起后原样露出下层
                    LyricsViewOverlay(
                        state = lyricsView,
                        controller = playbackController,
                        playerViewModel = playerViewModel,
                        settingsPreferences = settingsPreferences,
                        desktopPreferences = desktopPreferences,
                        isFullscreen = isFullscreen
                    )
                }
                val volume = mpvController?.volume?.collectAsState()?.value
                AnimatedVisibility(
                    visible = !lyricsView.isImmersive,
                    enter = ChromeEnter,
                    exit = ChromeExit
                ) { PlayerBar(
                    controller = playbackController,
                    playerViewModel = playerViewModel,
                    volume = volume,
                    onVolumeChange = { mpvController?.setVolume(it) },
                    nowPlayingOpen = dockOpen && dockOverlay == null,
                    // 盖着覆盖面板时点封面是撤掉面板露出正在播放页，而不是收起右侧栏
                    onToggleNowPlaying = {
                        if (dockOpen && dockOverlay != null) dockOverlay = null else setDockOpen(!dockOpen)
                    },
                    queueOpen = dockOpen && dockOverlay == DockOverlay.Queue,
                    onToggleQueue = { toggleOverlay(DockOverlay.Queue) },
                    devicesOpen = dockOpen && dockOverlay == DockOverlay.Devices,
                    // 占位播放器没有输出设备能力时不显示按钮
                    onToggleDevices = mpvController?.let { { toggleOverlay(DockOverlay.Devices) } },
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = fullscreen::toggle,
                    lyricVisible = showDesktopLyric,
                    onToggleLyric = { scope.launch { settingsPreferences.saveShowDesktopLrc(!showDesktopLyric) } },
                    lyricsViewOpen = lyricsView.isOpen,
                    onToggleLyricsView = { if (lyricsView.isOpen) lyricsView.close() else lyricsView.open() }
                ) }
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)) { data ->
                Snackbar(data, containerColor = DesktopColors.PopupSurface, contentColor = DesktopColors.TextPrimary)
            }
            WindowResizeHandles(enabled = !isMaximized && !isFullscreen)
        }
    }

    if (showLogin) {
        LoginDialog(
            viewModel = loginViewModel,
            onLoginSuccess = { cookies ->
                homeViewModel.handleLoginSuccess(cookies)
                showLogin = false
            },
            onDismiss = { showLogin = false }
        )
    }
}

@Composable
private fun Pane(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.fillMaxHeight().clip(RoundedCornerShape(DesktopDimens.PaneRadius)).background(DesktopColors.Pane)
    ) {
        content()
    }
}
