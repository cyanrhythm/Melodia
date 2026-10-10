package com.lin0721.linmusic.desktop.platform.native.linux

import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.desktop.platform.native.SystemMediaSession
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Linux 尚无系统媒体控制实现；先返回不可用，后续接 MPRIS over D-Bus。
class LinuxSystemMediaSession : SystemMediaSession {

    override val available: StateFlow<Boolean> = MutableStateFlow(false)

    override fun start(): Boolean = false

    override fun setEnabled(value: Boolean) = Unit

    override fun shutdown() = Unit

    override suspend fun bind(controller: PlaybackController, playerViewModel: PlayerViewModel) = Unit
}
