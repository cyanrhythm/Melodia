package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.model.ArtistInfo
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.artist.ui.ArtistUiState
import com.lin0721.linmusic.feature.artist.ui.ArtistViewModel
import java.time.Instant
import java.time.ZoneId

// 距离列表底部还剩几项时加载下一页
private const val LOAD_MORE_THRESHOLD = 6

private val BackdropHeight = 300.dp
private val AlbumCardWidth = 168.dp
private val ContentPadding = 24.dp

private enum class ArtistTab(val label: String) { SONGS("歌曲"), ALBUMS("专辑"), ABOUT("简介") }

private enum class SongTab(val label: String) { HOT("热门"), ALL("全部") }

@Composable
fun ArtistPage(
    artistId: Long,
    viewModel: ArtistViewModel,
    controller: PlaybackController,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(artistId) {
        viewModel.loadArtistData(artistId)
    }
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        ArtistUiState.Loading -> Loading(modifier)
        is ArtistUiState.Error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message, color = DesktopColors.TextGray)
                TextButton(onClick = { viewModel.loadArtistData(artistId) }) {
                    Text("重试", color = DesktopColors.TextPrimary)
                }
            }
        }
        is ArtistUiState.Success -> {
            // 单例 ViewModel 在切换歌手的瞬间仍持有上一位歌手，避免闪现旧内容
            if (state.artist.id != artistId) {
                Loading(modifier)
                return
            }
            ArtistContent(state, viewModel, controller, modifier)
        }
    }
}

@Composable
private fun Loading(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = DesktopColors.Accent)
    }
}

@Composable
private fun ArtistContent(
    state: ArtistUiState.Success,
    viewModel: ArtistViewModel,
    controller: PlaybackController,
    modifier: Modifier
) {
    val navigator = LocalDesktopNavigator.current
    val nowPlaying by controller.nowPlaying.collectAsState()
    val blockedIds by viewModel.blockedArtistIds.collectAsState()
    val likedSongIds by viewModel.likedSongIds.collectAsState()
    val collectState by viewModel.collectState.collectAsState()
    var tab by remember(state.artist.id) { mutableStateOf(ArtistTab.SONGS) }
    var songTab by remember(state.artist.id) { mutableStateOf(SongTab.HOT) }

    val actions = rememberTrackActions(
        likedSongIds = likedSongIds,
        collectState = collectState,
        onToggleLike = viewModel::toggleLikeSong,
        onPlayNext = viewModel::addTrackToPlayNext,
        onPrepareCollect = viewModel::prepareCollectDialog,
        onSaveCollect = viewModel::savePlaylistCollection,
        onCreateAndAdd = viewModel::createPlaylistAndAddSong
    )

    val listState = rememberLazyListState()
    val shouldLoadMore by remember(state, tab, songTab) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(shouldLoadMore, tab, songTab) {
        if (!shouldLoadMore) return@LaunchedEffect
        when {
            tab == ArtistTab.ALBUMS -> viewModel.loadMoreAlbums()
            tab == ArtistTab.SONGS && songTab == SongTab.ALL -> viewModel.loadMoreAllSongs()
        }
    }
    LaunchedEffect(tab, songTab) {
        if (tab == ArtistTab.SONGS && songTab == SongTab.ALL) viewModel.loadAllSongsIfNeeded()
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val columns = ((maxWidth - ContentPadding * 2) / (AlbumCardWidth + 16.dp)).toInt().coerceAtLeast(2)
        HoverScrollbarBox(listState) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item(key = "header") {
                    ArtistHeader(
                        state = state,
                        isBlocked = state.artist.id in blockedIds,
                        onPlayHot = { state.topSongs.firstOrNull()?.let { viewModel.playSongInList(it, state.topSongs) } },
                        onToggleFollow = {
                            if (navigator.isLoggedIn) viewModel.toggleFollow(state.artist.id) else navigator.showMessage("请先登录账号")
                        },
                        onToggleBlock = { viewModel.toggleBlockArtist(state.artist.id) }
                    )
                }
                item(key = "tabs") {
                    TabBar(ArtistTab.entries, tab, { it.label }, { tab = it }, Modifier.padding(horizontal = ContentPadding))
                }
                when (tab) {
                    ArtistTab.SONGS -> {
                        item(key = "song_tabs") {
                            TabBar(
                                SongTab.entries,
                                songTab,
                                { it.label },
                                { songTab = it },
                                Modifier.padding(horizontal = ContentPadding, vertical = 8.dp),
                                small = true
                            )
                        }
                        val tracks = if (songTab == SongTab.HOT) state.topSongs else state.allSongs
                        trackItems(tracks, nowPlaying?.songId, actions) { viewModel.playSongInList(it, tracks) }
                        if (songTab == SongTab.ALL && state.allSongsLoadingMore) loadingItem("songs_loading")
                        if (songTab == SongTab.ALL && state.allSongsLoaded && tracks.isEmpty()) emptyItem("songs_empty", "暂无歌曲")
                    }
                    ArtistTab.ALBUMS -> {
                        val rows = state.albums.chunked(columns)
                        items(rows.size, key = { "album_row_$it" }) { rowIndex ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = ContentPadding, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                rows[rowIndex].forEach { album ->
                                    AlbumCard(album) { navigator.openAlbum(album.id, album.name) }
                                }
                            }
                        }
                        if (state.albumsLoadingMore) loadingItem("albums_loading")
                        if (state.albums.isEmpty()) emptyItem("albums_empty", "暂无专辑")
                    }
                    ArtistTab.ABOUT -> {
                        item(key = "about") {
                            AboutSection(state.artist.briefDesc, state.similarArtists) { navigator.openArtist(it.id, it.name) }
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.trackItems(
    tracks: List<Track>,
    currentSongId: Long?,
    actions: TrackActions,
    onPlay: (Track) -> Unit
) {
    itemsIndexed(tracks, key = { index, track -> "track_${track.id}_$index" }) { index, track ->
        TrackRow(
            index = index,
            track = track,
            isCurrent = currentSongId == track.id,
            onPlay = { onPlay(track) },
            actions = actions,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

private fun LazyListScope.loadingItem(key: String) {
    item(key = key) {
        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DesktopColors.Accent)
        }
    }
}

private fun LazyListScope.emptyItem(key: String, text: String) {
    item(key = key) {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(text, color = DesktopColors.TextGray, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ArtistHeader(
    state: ArtistUiState.Success,
    isBlocked: Boolean,
    onPlayHot: () -> Unit,
    onToggleFollow: () -> Unit,
    onToggleBlock: () -> Unit
) {
    val artist = state.artist
    Box(Modifier.fillMaxWidth().height(BackdropHeight)) {
        val backdrop = artist.cover.ifBlank { artist.avatar }
        if (backdrop.isNotBlank()) {
            AsyncImage(
                model = if (backdrop.startsWith("http") && !backdrop.contains("?")) "$backdrop?param=1200y600" else backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, DesktopColors.Pane.copy(alpha = 0.6f), DesktopColors.Pane))
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(ContentPadding)) {
            Text(
                artist.name,
                color = DesktopColors.TextPrimary,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = listOfNotNull(
                artist.trans?.takeIf { it.isNotBlank() },
                artist.alias?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.joinToString(" / "),
                state.fansCount.takeIf { it > 0 }?.let { "${formatCount(it)} 粉丝" }
            ).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = DesktopColors.TextGray, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    Row(
        Modifier.padding(horizontal = ContentPadding, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(DesktopColors.Accent)
                .clickable(enabled = state.topSongs.isNotEmpty(), onClick = onPlayHot),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.PlayArrow, "播放热门歌曲", tint = DesktopColors.TextPrimary, modifier = Modifier.size(32.dp))
        }
        HeaderButton(if (state.isFollowed) "已关注" else "关注", onToggleFollow)
        HeaderButton(if (isBlocked) "取消屏蔽" else "屏蔽", onToggleBlock)
    }
}

@Composable
private fun HeaderButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = DesktopColors.TextPrimary)
    ) {
        Text(text, fontSize = 14.sp)
    }
}

@Composable
private fun <T> TabBar(
    tabs: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tabs.forEach { tab ->
            val active = tab == selected
            Box(
                Modifier.clip(RoundedCornerShape(16.dp))
                    .background(if (active) DesktopColors.TextPrimary else DesktopColors.Surface)
                    .clickable { onSelect(tab) }
                    .padding(horizontal = if (small) 12.dp else 16.dp, vertical = if (small) 4.dp else 6.dp)
            ) {
                Text(
                    label(tab),
                    color = if (active) DesktopColors.Pane else DesktopColors.TextPrimary,
                    fontSize = if (small) 13.sp else 14.sp
                )
            }
        }
    }
}

@Composable
private fun AlbumCard(album: ArtistAlbum, onClick: () -> Unit) {
    Column(
        Modifier.width(AlbumCardWidth).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(8.dp)
    ) {
        Cover(album.picUrl, AlbumCardWidth - 16.dp, shape = RoundedCornerShape(4.dp))
        Text(
            album.name,
            color = DesktopColors.TextPrimary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        val year = album.publishTime.takeIf { it > 0 }?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).year.toString()
        }
        Text(
            listOfNotNull(year, album.size.takeIf { it > 0 }?.let { "$it 首" }).joinToString(" · "),
            color = DesktopColors.TextGray,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun AboutSection(briefDesc: String, similar: List<ArtistInfo>, onArtistClick: (ArtistInfo) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = ContentPadding, vertical = 8.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(DesktopColors.Surface).padding(20.dp)
        ) {
            Text("简介", color = DesktopColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                briefDesc.trim().ifBlank { "暂无艺人简介信息" },
                color = DesktopColors.TextGray,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        if (similar.isNotEmpty()) {
            Text(
                "相似歌手",
                color = DesktopColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(similar, key = { it.id }) { artist ->
                    SimilarArtistCard(artist, 128.dp) { onArtistClick(artist) }
                }
            }
        }
    }
}

@Composable
private fun SimilarArtistCard(artist: ArtistInfo, size: Dp, onClick: () -> Unit) {
    Column(
        Modifier.width(size + 16.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Cover(artist.avatarUrl, size, shape = CircleShape)
        Text(
            artist.name,
            color = DesktopColors.TextPrimary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

private fun formatCount(count: Long): String = when {
    count >= 100_000_000 -> "%.1f 亿".format(count / 100_000_000.0)
    count >= 10_000 -> "%.1f 万".format(count / 10_000.0)
    else -> count.toString()
}
