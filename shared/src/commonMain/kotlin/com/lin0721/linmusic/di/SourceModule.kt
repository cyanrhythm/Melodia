package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.data.PlaybackRepositoryImpl
import com.lin0721.linmusic.core.source.AudioSourceProvider
import com.lin0721.linmusic.core.source.AudioSourceRouter
import com.lin0721.linmusic.core.source.UnmApiClient
import com.lin0721.linmusic.core.source.provider.KugouSourceProvider
import com.lin0721.linmusic.core.source.provider.KuwoSourceProvider
import com.lin0721.linmusic.core.source.provider.QQSourceProvider
import org.koin.core.qualifier.named
import org.koin.dsl.module

// 多音源换源能力依赖注入模块
val sourceModule = module {

    // UNM API 客户端单例
    single { UnmApiClient() }

    // 第三方聚合音源提供者
    single<AudioSourceProvider>(named("kuwo")) { KuwoSourceProvider() }
    single<AudioSourceProvider>(named("kugou")) { KugouSourceProvider() }
    single<AudioSourceProvider>(named("qq")) { QQSourceProvider() }

    // AudioSourceRouter 包装 PlaybackRepositoryImpl，实现透明换源
    single<PlaybackRepository> {
        AudioSourceRouter(
            delegate = get<PlaybackRepositoryImpl>(),
            unmApiClient = get<UnmApiClient>(),
            sourcePreferences = get(),
            settingsPreferences = get(),
            networkStateProvider = get(),
            providers = getAll<AudioSourceProvider>()
        )
    }
}
