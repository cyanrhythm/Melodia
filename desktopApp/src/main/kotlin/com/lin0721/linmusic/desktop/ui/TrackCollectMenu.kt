package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.userplaylist.UserPlaylist
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.playlist.ui.PlaylistImportState

private val ListMaxHeight = 264.dp

// “收藏到歌单”二级菜单内容：查找框、新建歌单、歌单列表（已包含的打勾，点选即加入或移出）
@Composable
internal fun CollectSubmenuContent(track: Track, actions: TrackActions, onDismissMenu: () -> Unit) {
    var query by remember { mutableStateOf("") }
    // 点选后的勾选态先在本地翻转，服务端结果以失败提示为准
    val overrides = remember { mutableStateMapOf<Long, Boolean>() }
    val state = actions.collectState
    val items = if (state.songId == track.id) state.collectItems else emptyList()
    val visible = items.filter { query.isBlank() || it.playlistName.contains(query.trim(), ignoreCase = true) }

    Column(Modifier.padding(vertical = 2.dp)) {
        DialogTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "查找歌单",
            modifier = Modifier.padding(bottom = 4.dp)
        )
        SimpleMenuItem("新建歌单", icon = Icons.Rounded.Add, onClick = {
            onDismissMenu()
            actions.onCreateCollect(track)
        })
        MenuDivider()
        when {
            state.isLoading && items.isEmpty() -> Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(18.dp), color = DesktopColors.TextGray, strokeWidth = 2.dp)
            }
            visible.isEmpty() -> Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                Text(if (items.isEmpty()) "还没有自建歌单" else "没有匹配的歌单", color = DesktopColors.TextGray, fontSize = 12.sp)
            }
            else -> Column(Modifier.heightIn(max = ListMaxHeight).verticalScroll(rememberScrollState())) {
                visible.forEach { item ->
                    val contains = overrides[item.playlistId] ?: item.isContains
                    SimpleMenuItem(
                        text = item.playlistName,
                        onClick = {
                            overrides[item.playlistId] = !contains
                            actions.onToggleCollect(track, item.copy(isInitiallyContains = contains, isContains = !contains))
                        },
                        trailing = if (contains) {
                            { Icon(Icons.Rounded.Check, "已在歌单中", tint = DesktopColors.Accent, modifier = Modifier.size(16.dp)) }
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }
}

// “添加到歌单”二级菜单内容：查找框、新建歌单、自建歌单列表，点选即整张导入
@Composable
internal fun ImportSubmenuContent(state: PlaylistImportState, onImportTo: (UserPlaylist) -> Unit, onCreate: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val visible = state.items.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    Column(Modifier.padding(vertical = 2.dp)) {
        DialogTextField(value = query, onValueChange = { query = it }, placeholder = "查找歌单", modifier = Modifier.padding(bottom = 4.dp))
        SimpleMenuItem("新建歌单", icon = Icons.Rounded.Add, onClick = onCreate)
        MenuDivider()
        when {
            state.isLoading -> Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(18.dp), color = DesktopColors.TextGray, strokeWidth = 2.dp)
            }
            visible.isEmpty() -> Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                Text(if (state.items.isEmpty()) "还没有自建歌单" else "没有匹配的歌单", color = DesktopColors.TextGray, fontSize = 12.sp)
            }
            else -> Column(Modifier.heightIn(max = ListMaxHeight).verticalScroll(rememberScrollState())) {
                visible.forEach { playlist -> SimpleMenuItem(playlist.name, onClick = { onImportTo(playlist) }) }
            }
        }
    }
}
