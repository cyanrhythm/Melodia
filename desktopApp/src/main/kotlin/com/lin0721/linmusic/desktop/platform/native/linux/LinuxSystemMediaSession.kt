package com.lin0721.linmusic.desktop.platform.native.linux

import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.desktop.platform.native.SystemMediaSession
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.lin0721.linmusic.desktop.platform.native.DesktopPlatform
import com.lin0721.linmusic.desktop.platform.native.PlatformStub

// Linux 尚无系统媒体控制实现；先返回不可用，后续接 MPRIS over D-Bus。
@PlatformStub(DesktopPlatform.LINUX, "MPRIS over D-Bus 待实现")
class LinuxSystemMediaSession : SystemMediaSession {

    override val available: StateFlow<Boolean> = MutableStateFlow(false)

    override fun start(): Boolean = false

    override fun setEnabled(value: Boolean) = Unit

    override fun shutdown() = Unit

    override suspend fun bind(controller: PlaybackController, playerViewModel: PlayerViewModel) = Unit
}
