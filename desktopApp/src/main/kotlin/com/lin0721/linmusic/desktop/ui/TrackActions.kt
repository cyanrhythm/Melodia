package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.ui.components.PlaylistCollectItem
import com.lin0721.linmusic.core.ui.components.PlaylistCollectState
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

private const val PLAYLIST_NAME_MAX_LENGTH = 40

// 歌曲行的红心与更多菜单操作，由各页面按自己的 ViewModel 组装
@Immutable
class TrackActions(
    val likedSongIds: Set<Long>,
    val onToggleLike: (Track) -> Unit,
    val onPlayNext: (Track) -> Unit,
    val onCollect: (Track) -> Unit
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
        }
    )
}

@Composable
internal fun CollectToPlaylistDialog(
    songId: Long,
    state: PlaylistCollectState,
    onSave: (List<PlaylistCollectItem>) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // 用户勾选单独记录，后台回填包含状态时不覆盖用户操作
    val overrides = remember(songId) { mutableStateMapOf<Long, Boolean>() }
    var newName by remember(songId) { mutableStateOf("") }
    val ready = state.songId == songId && !state.isLoading
    val collectListState = rememberLazyListState()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = AlertDialogDefaults.shape,
        containerColor = DesktopColors.Surface,
        title = { Text("收藏到歌单", color = DesktopColors.TextPrimary) },
        text = {
            Column(Modifier.width(380.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.take(PLAYLIST_NAME_MAX_LENGTH) },
                        placeholder = { Text("新建歌单名称", fontSize = 14.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DesktopColors.TextPrimary,
                            unfocusedBorderColor = DesktopColors.SurfaceLight,
                            cursorColor = DesktopColors.TextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onCreate(newName.trim()) }, enabled = newName.isNotBlank()) {
                        Text("新建并添加", color = if (newName.isNotBlank()) DesktopColors.TextPrimary else DesktopColors.TextGray)
                    }
                }
                when {
                    !ready -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DesktopColors.Accent)
                    }
                    state.collectItems.isEmpty() -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text("还没有自建歌单", color = DesktopColors.TextGray, fontSize = 14.sp)
                    }
                    else -> HoverScrollbarBox(collectListState) {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp).padding(top = 12.dp), state = collectListState) {
                            items(state.collectItems, key = { it.playlistId }) { item ->
                                val checked = overrides[item.playlistId] ?: item.isContains
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                                        .clickable { overrides[item.playlistId] = !checked }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { overrides[item.playlistId] = it },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = DesktopColors.Accent,
                                            uncheckedColor = DesktopColors.TextGray
                                        )
                                    )
                                    Cover(item.coverUrl, 40.dp, shape = RoundedCornerShape(4.dp))
                                    Text(
                                        item.playlistName,
                                        color = DesktopColors.TextPrimary,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(start = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(state.collectItems.map { it.copy(isContains = overrides[it.playlistId] ?: it.isContains) })
                },
                enabled = ready
            ) {
                Text("保存", color = if (ready) DesktopColors.TextPrimary else DesktopColors.TextGray)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = DesktopColors.TextGray)
            }
        }
    )
}
