package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.localmusic.LocalMusicApi
import com.lin0721.linmusic.core.player.LyricsResolver
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.PlayerManager
import com.lin0721.linmusic.core.player.ExternalInterruptionResumeController
import com.lin0721.linmusic.core.player.data.AmllLyricsClient
import com.lin0721.linmusic.core.player.data.LyricsCache
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import kotlinx.coroutines.flow.first
import com.lin0721.linmusic.core.player.external.ExternalLyricCoordinator
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val playerModule = module {
    single { ExternalInterruptionResumeController(androidContext(), get()) }
    single { PlayerManager(androidContext(), get(), get(), get(), get(), get(), get(), get()) }
    single<PlaybackController> { get<PlayerManager>() }
    // AMLL 歌词源：TTML 原文按原始 XML 缓存在 cacheDir 下，解析统一由 shared 的 TtmlLyricParser 负责
    single { AmllLyricsClient(LyricsCache(androidContext().cacheDir)) }
    single {
        val playerManager = get<PlayerManager>()
        val localMusicApi = get<LocalMusicApi>()
        val amllLyricsClient = get<AmllLyricsClient>()
        val settingsPreferences = get<SettingsPreferences>()
        LyricsResolver(
            playbackRepository = get(),
            readLocalLyrics = localMusicApi::readLyrics,
            localUriOf = { songId -> playerManager.queue.value.firstOrNull { it.songId == songId }?.localUri },
            readAmllLyrics = { songId -> amllLyricsClient.fetch(songId) },
            isAmllEnabled = { settingsPreferences.amllLyricsEnabled.first() }
        )
    }
    single { ExternalLyricCoordinator(androidContext(), get(), get(), get()) }
}

