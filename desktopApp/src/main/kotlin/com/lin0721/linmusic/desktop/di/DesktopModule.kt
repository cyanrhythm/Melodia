package com.lin0721.linmusic.desktop.di

import com.lin0721.linmusic.core.auth.LoginViewModel
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.cache.MetadataCache
import com.lin0721.linmusic.core.contentfilter.ContentFilter
import com.lin0721.linmusic.core.download.SongDownloader
import com.lin0721.linmusic.core.network.NetworkStateProvider
import com.lin0721.linmusic.core.network.OnlineStateProvider
import com.lin0721.linmusic.core.offline.CachedAudioIndex
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.crypto.XeapiKeyStore
import com.lin0721.linmusic.core.network.crypto.XeapiKeyStoreImpl
import com.lin0721.linmusic.core.player.LyricsResolver
import com.lin0721.linmusic.core.player.PlaybackPreferences
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.data.AmllLyricsClient
import com.lin0721.linmusic.core.player.data.LyricsCache
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.desktop.player.MpvPlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.desktop.platform.DesktopLibraryPreferences
import com.lin0721.linmusic.desktop.platform.DesktopOnlineStateProvider
import com.lin0721.linmusic.desktop.platform.DesktopPreferences
import com.lin0721.linmusic.desktop.platform.GlobalHotkeys
import com.lin0721.linmusic.desktop.platform.smtc.SmtcSession
import com.lin0721.linmusic.desktop.platform.DesktopPaths
import com.lin0721.linmusic.desktop.platform.DesktopResourceProvider
import com.lin0721.linmusic.desktop.platform.SilentPlaybackController
import com.lin0721.linmusic.desktop.platform.UnsupportedSongDownloader
import com.lin0721.linmusic.feature.artist.ui.ArtistViewModel
import com.lin0721.linmusic.feature.home.ui.HomeViewModel
import com.lin0721.linmusic.feature.library.data.LibraryPreferences
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
import com.lin0721.linmusic.feature.profile.ui.ProfileViewModel
import com.lin0721.linmusic.core.source.AudioSourceProvider
import com.lin0721.linmusic.core.source.SourcePreferences
import com.lin0721.linmusic.feature.podcast.data.PodcastProgressPreferences
import com.lin0721.linmusic.feature.podcast.data.PodcastSeenPreferences
import com.lin0721.linmusic.feature.search.data.SearchHistoryPreferences
import com.lin0721.linmusic.feature.search.ui.PlaylistCategoryViewModel
import com.lin0721.linmusic.feature.search.ui.SearchViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import java.io.File

private const val TAG = "DesktopModule"

// libmpv 缺失或加载失败时退回不出声的占位实现，保证界面仍可使用
private fun createPlaybackController(
    repository: PlaybackRepository,
    settingsPreferences: SettingsPreferences,
    playbackPreferences: PlaybackPreferences,
    desktopPreferences: DesktopPreferences
): PlaybackController = try {
    MpvPlaybackController(repository, settingsPreferences, playbackPreferences, desktopPreferences, CoroutineScope(SupervisorJob() + Dispatchers.Main))
} catch (e: LinkageError) {
    AppLogger.e(TAG, "libmpv 加载失败，播放不可用", e)
    SilentPlaybackController()
} catch (e: IllegalStateException) {
    AppLogger.e(TAG, "libmpv 初始化失败，播放不可用", e)
    SilentPlaybackController()
}

private fun store(name: String) = PreferencesStores.get(DesktopPaths.preferencesFile(name))

val desktopPlatformModule = module {
    single { UserPreferences(store(PreferencesStores.USER)) }
    single { SettingsPreferences(store(PreferencesStores.SETTINGS)) }
    single { SourcePreferences(store(PreferencesStores.SOURCE)) }
    single { SearchHistoryPreferences(store(PreferencesStores.SEARCH_HISTORY)) }
    single { PlaybackPreferences(store(PreferencesStores.PLAYBACK)) }
    single { PodcastProgressPreferences(store(PreferencesStores.PODCAST)) }
    single { PodcastSeenPreferences(store(PreferencesStores.PODCAST)) }
    single { DesktopPreferences(store(DesktopPreferences.STORE_NAME)) }
    single { GlobalHotkeys() }
    single { SmtcSession() }
    single<XeapiKeyStore> { XeapiKeyStoreImpl(store(PreferencesStores.XEAPI_KEY)) }
    single { ContentFilter(get()) }
    single<ResourceProvider> { DesktopResourceProvider() }
    // 桌面端不区分 Wi-Fi 与移动网络，统一按 Wi-Fi 音质
    single<NetworkStateProvider> { NetworkStateProvider { true } }
    single<OnlineStateProvider> { DesktopOnlineStateProvider() }
    single {
        val userPreferences = get<UserPreferences>()
        MetadataCache(File(DesktopPaths.dataDir, "meta_cache")) { userPreferences.userProfile.first()?.uid ?: 0L }
    }
    // 桌面端没有音频缓存也不支持下载，离线时所有歌曲都不可播放
    single<CachedAudioIndex> { CachedAudioIndex { emptySet() } }
    single<LibraryPreferences> { DesktopLibraryPreferences() }
    single<SongDownloader> { UnsupportedSongDownloader() }
    single<PlaybackController> { createPlaybackController(get(), get(), get(), get()) }
    single { AmllLyricsClient(LyricsCache(File(DesktopPaths.dataDir, "cache"))) }
    // 桌面第一版没有本地音乐，只取在线歌词
    single {
        val amllLyricsClient = get<AmllLyricsClient>()
        val settingsPreferences = get<SettingsPreferences>()
        LyricsResolver(
            playbackRepository = get(),
            readLocalLyrics = { null },
            localUriOf = { null },
            readAmllLyrics = { songId -> amllLyricsClient.fetch(songId) },
            isAmllEnabled = { settingsPreferences.amllLyricsEnabled.first() }
        )
    }
}

// 单窗口应用，页面级 ViewModel 随窗口常驻
val desktopViewModelModule = module {
    singleOf(::LoginViewModel)
    singleOf(::HomeViewModel)
    singleOf(::MusicViewModel)
    factoryOf(::StyleDetailViewModel)
    singleOf(::PodcastHomeViewModel)
    factoryOf(::PodcastSubscribedViewModel)
    factoryOf(::PodcastCategoryViewModel)
    factoryOf(::PodcastToplistViewModel)
    factoryOf(::RadioDetailViewModel)
    singleOf(::NewWorksViewModel)
    singleOf(::LibraryViewModel)
    singleOf(::ProfileViewModel)
    single {
        SearchViewModel(
            repository = get(),
            historyPreferences = get(),
            playerManager = get(),
            userPreferences = get(),
            resourceProvider = get(),
            songCollectDelegate = get(),
            loadLikedSongIdsUseCase = get(),
            songLikeRepository = get(),
            syncProfileAfterLoginUseCase = get(),
            sourceProviders = getAll<AudioSourceProvider>(),
            settingsPreferences = getOrNull(),
            sourcePreferences = getOrNull()
        )
    }
    factoryOf(::PlaylistCategoryViewModel)
    factoryOf(::PlaylistViewModel)
    singleOf(::PlayerViewModel)
    factoryOf(::ArtistViewModel)
}
