package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.playlist.ui.PlaylistUiState
import com.lin0721.linmusic.feature.playlist.ui.PlaylistViewModel

// 距离列表底部还剩几项时加载下一页曲目
private const val TRACK_LOAD_MORE_THRESHOLD = 10

@Composable
fun PlaylistPage(
    playlistId: Long,
    isAlbum: Boolean,
    viewModel: PlaylistViewModel,
    controller: PlaybackController,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(playlistId, isAlbum) {
        viewModel.loadPlaylistIfNeeded(playlistId, isAlbum)
    }
    val navigator = LocalDesktopNavigator.current
    val uiState by viewModel.uiState.collectAsState()
    val nowPlaying by controller.nowPlaying.collectAsState()
    val likedSongIds by viewModel.likedSongIds.collectAsState()
    val unplayableIds by viewModel.unplayableIds.collectAsState()
    val collectState by viewModel.collectState.collectAsState()
    val actions = rememberTrackActions(
        likedSongIds = likedSongIds,
        collectState = collectState,
        onToggleLike = viewModel::toggleLikeSong,
        onPlayNext = viewModel::addTrackToPlayNext,
        onPrepareCollect = viewModel::prepareCollectDialog,
        onSaveCollect = viewModel::savePlaylistCollection,
        onCreateAndAdd = viewModel::createPlaylistAndAddSong
    )

    when (val state = uiState) {
        PlaylistUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DesktopColors.Accent)
        }
        is PlaylistUiState.Error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message, color = DesktopColors.TextGray)
                TextButton(onClick = { viewModel.loadPlaylist(playlistId, isAlbum) }) {
                    Text("重试", color = DesktopColors.TextPrimary)
                }
            }
        }
        is PlaylistUiState.Success -> {
            // 单例 ViewModel 在切换歌单的瞬间仍持有上一个歌单，避免闪现旧内容
            if (state.playlist.id != playlistId && !isAlbum) {
                Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DesktopColors.Accent)
                }
                return
            }
            val tracks = state.playlist.tracks
            val listState = rememberLazyListState()
            val shouldLoadMore by remember(state) {
                derivedStateOf {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    state.hasMoreTracks && !state.isLoadingMoreTracks &&
                        lastVisible >= listState.layoutInfo.totalItemsCount - TRACK_LOAD_MORE_THRESHOLD
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) viewModel.loadMoreTracks()
            }
            HoverScrollbarBox(listState) {
                LazyColumn(
                    state = listState,
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item(key = "header") {
                        PlaylistHeader(
                            playlist = state.playlist,
                            isAlbum = isAlbum,
                            onPlayAll = { viewModel.playAll(shuffle = false) },
                            onDownloadAll = { viewModel.downloadPlaylist(state.playlist.id, state.playlist.name, navigator.downloadLevel) }
                        )
                    }
                    itemsIndexed(tracks, key = { index, track -> "${track.id}_$index" }) { index, track ->
                        TrackRow(
                            index = index,
                            track = track,
                            isCurrent = nowPlaying?.songId == track.id,
                            onPlay = { viewModel.playTrackInPlaylist(track) },
                            actions = actions,
                            enabled = track.id !in unplayableIds,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    if (state.isLoadingMoreTracks) {
                        item(key = "loading_more") {
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = DesktopColors.Accent)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistHeader(playlist: PlaylistDetail, isAlbum: Boolean, onPlayAll: () -> Unit, onDownloadAll: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Cover(playlist.coverImgUrl, 200.dp, shape = RoundedCornerShape(6.dp))
            Column(Modifier.padding(start = 24.dp)) {
                Text(if (isAlbum) "专辑" else "歌单", color = DesktopColors.TextGray, fontSize = 13.sp)
                Text(
                    playlist.name,
                    color = DesktopColors.TextPrimary,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val creator = playlist.creator?.nickname.orEmpty()
                val count = if (playlist.trackCount > 0) playlist.trackCount else playlist.tracks.size
                Text(
                    listOf(creator, "$count 首歌曲").filter { it.isNotBlank() }.joinToString(" · "),
                    color = DesktopColors.TextGray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        Row(
            Modifier.padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(DesktopColors.Accent).clickable(onClick = onPlayAll),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, "播放全部", tint = DesktopColors.TextPrimary, modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = onDownloadAll) {
                Icon(Icons.Rounded.Download, if (isAlbum) "下载专辑" else "下载歌单", tint = DesktopColors.TextGray, modifier = Modifier.size(28.dp))
            }
            Text("双击歌曲即可播放", color = DesktopColors.TextGray, fontSize = 13.sp)
        }
    }
}
