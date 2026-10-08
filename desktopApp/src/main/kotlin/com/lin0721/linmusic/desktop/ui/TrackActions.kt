package com.lin0721.linmusic.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState

// 歌曲行的红心与更多菜单操作，由各页面按自己的 ViewModel 组装
@Immutable
class TrackActions(
    val likedSongIds: Set<Long>,
    val onToggleLike: (Track) -> Unit,
    val onPlayNext: (Track) -> Unit,
    val onCollect: (Track) -> Unit,
    val onDownload: (Track) -> Unit
)

@Composable
fun rememberTrackActions(
    likedSongIds: Set<Long>,
    collectState: PlaylistCollectState,
    onToggleLike: (songId: Long, like: Boolean) -> Unit,
    onPlayNext: (Track) -> Unit,
    onPrepareCollect: (songId: Long) -> Unit,
    onSaveCollect: (songId: Long, items: List<PlaylistCollectItem>) -> Unit,
    onCreateAndAdd: (name: String, songId: Long) -> Unit
): TrackActions {
    val navigator = LocalDesktopNavigator.current
    var collectingSongId by remember { mutableStateOf<Long?>(null) }

    collectingSongId?.let { songId ->
        CollectToPlaylistDialog(
            songId = songId,
            state = collectState,
            onSave = { items ->
                onSaveCollect(songId, items)
                collectingSongId = null
            },
            onCreate = { name ->
                onCreateAndAdd(name, songId)
                collectingSongId = null
            },
            onDismiss = { collectingSongId = null }
        )
    }

    val requireLogin: (() -> Unit) -> Unit = { action ->
        if (navigator.isLoggedIn) action() else navigator.showMessage("请先登录账号")
    }
    return TrackActions(
        likedSongIds = likedSongIds,
        onToggleLike = { track -> requireLogin { onToggleLike(track.id, track.id !in likedSongIds) } },
        onPlayNext = onPlayNext,
        onCollect = { track ->
            requireLogin {
                onPrepareCollect(track.id)
                collectingSongId = track.id
            }
        },
        onDownload = navigator.downloadTrack
    )
}
