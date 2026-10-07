package com.lin0721.linmusic.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.model.isLikedSongsPlaylist
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.desktop.ui.palette.FallbackCoverPalette
import com.lin0721.linmusic.desktop.ui.palette.extractCoverPaletteFromUrl
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.playlist.ui.PlaylistUiState
import com.lin0721.linmusic.feature.playlist.ui.PlaylistViewModel
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

// 距离列表底部还剩几项时加载下一页曲目
private const val TRACK_LOAD_MORE_THRESHOLD = 10
private const val HERO_DARKEN_FRACTION = 0.35f
private const val PRIVATE_PLAYLIST = 10

private val HeaderPadding = 24.dp
private val ControlHeight = 36.dp
private val SearchWidth = 240.dp
private val SortMenuWidth = 200.dp

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
    val uiState by viewModel.uiState.collectAsState()

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
            } else {
                PlaylistContent(state, isAlbum, viewModel, controller, modifier)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistContent(
    state: PlaylistUiState.Success,
    isAlbum: Boolean,
    viewModel: PlaylistViewModel,
    controller: PlaybackController,
    modifier: Modifier
) {
    val navigator = LocalDesktopNavigator.current
    val playlist = state.playlist
    val profile by viewModel.userProfile.collectAsState()
    val nowPlaying by controller.nowPlaying.collectAsState()
    val likedSongIds by viewModel.likedSongIds.collectAsState()
    val unplayableIds by viewModel.unplayableIds.collectAsState()
    val collectState by viewModel.collectState.collectAsState()

    val uid = profile?.uid
    val isLikedView = uid != null && !isAlbum && isLikedSongsPlaylist(playlist.name, playlist.id, uid)
    val isOwned = uid != null && !isAlbum && playlist.id > 0 && playlist.creator?.userId == uid && !isLikedView
    val canSubscribe = playlist.id > 0 && !isOwned && !isLikedView

    val removeTrack: ((Track) -> Unit)? = if (isOwned) {
        fun(track: Track) = viewModel.removeTrackFromPlaylist(playlist.id, track.id)
    } else {
        null
    }
    val actions = rememberTrackActions(
        likedSongIds = likedSongIds,
        collectState = collectState,
        onToggleLike = viewModel::toggleLikeSong,
        onPlayNext = viewModel::addTrackToPlayNext,
        onPrepareCollect = viewModel::prepareCollectDialog,
        onSaveCollect = viewModel::savePlaylistCollection,
        onCreateAndAdd = viewModel::createPlaylistAndAddSong,
        onRemove = removeTrack
    )

    var query by remember(playlist.id) { mutableStateOf("") }
    var order by remember(playlist.id) { mutableStateOf(PlaylistSortOrder()) }
    val addedAt = remember(playlist.trackIds) { playlist.trackIds.associate { it.id to it.at } }
    val hasDates = remember(addedAt) { addedAt.values.any { it > 0 } }
    val customView = order.key == PlaylistSortKey.CUSTOM && query.isBlank()
    val displayed = remember(playlist.tracks, query, order, addedAt) {
        sortTracks(filterTracks(playlist.tracks, query), order, addedAt)
    }

    // 排序与搜索针对全部曲目，离开默认视图时把没加载的补齐
    LaunchedEffect(customView, playlist.id, state.hasMoreTracks) {
        if (!customView && state.hasMoreTracks && !state.isLoadingMoreTracks) viewModel.ensureAllTracksLoaded { }
    }

    val viewOf = { all: List<Track> -> sortTracks(filterTracks(all, query), order, addedAt) }
    val playFrom: (Track) -> Unit = { track ->
        if (customView) {
            viewModel.playTrackInPlaylist(track)
        } else {
            viewModel.ensureAllTracksLoaded { all -> viewModel.playSongInList(track, viewOf(all)) }
        }
    }
    val playDisplayed: (Boolean) -> Unit = { shuffle ->
        if (customView) {
            viewModel.playAll(shuffle)
        } else {
            viewModel.ensureAllTracksLoaded { all ->
                val ordered = viewOf(all).let { if (shuffle) it.shuffled() else it }
                ordered.firstOrNull { it.id !in unplayableIds }?.let { viewModel.playSongInList(it, ordered) }
            }
        }
    }

    val count = when {
        playlist.trackIds.isNotEmpty() -> playlist.trackIds.size
        playlist.trackCount > 0 -> playlist.trackCount
        else -> playlist.tracks.size
    }
    val totalMs = if (state.hasMoreTracks) null else playlist.tracks.sumOf { it.dt }

    var heroBase by remember(playlist.id) { mutableStateOf(FallbackCoverPalette.base) }
    LaunchedEffect(playlist.coverImgUrl) {
        if (playlist.coverImgUrl.isNotBlank()) heroBase = extractCoverPaletteFromUrl(playlist.coverImgUrl).base
    }
    val heroColor by animateColorAsState(lerp(heroBase, Color.Black, HERO_DARKEN_FRACTION), label = "playlistHero")

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
                Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(heroColor, DesktopColors.Pane)))) {
                    PlaylistHero(playlist, isAlbum, count, totalMs)
                    PlaylistActionBar(
                        isAlbum = isAlbum,
                        canSubscribe = canSubscribe,
                        isSubscribed = state.isSubscribed,
                        query = query,
                        order = order,
                        showAdded = hasDates,
                        onPlay = { playDisplayed(false) },
                        onShuffle = { playDisplayed(true) },
                        onToggleSubscribe = {
                            if (navigator.isLoggedIn) viewModel.toggleSubscribePlaylist() else navigator.showMessage("请先登录账号")
                        },
                        onDownload = { viewModel.downloadPlaylist(playlist.id, playlist.name, navigator.downloadLevel) },
                        onPlayNextAll = viewModel::addAllTracksToPlayNext,
                        onCopyLink = {
                            navigator.showMessage(if (copyPlaylistLink(isAlbum, playlist.id)) "已复制链接" else "复制失败")
                        },
                        onQueryChange = { query = it },
                        onOrderChange = { order = it }
                    )
                }
            }
            stickyHeader(key = "columns") {
                TrackColumnHeader(order, hasDates) { order = order.toggled(it) }
            }
            itemsIndexed(displayed, key = { index, track -> "${track.id}_$index" }) { index, track ->
                TrackRow(
                    index = index,
                    track = track,
                    isCurrent = nowPlaying?.songId == track.id,
                    onPlay = { playFrom(track) },
                    actions = actions,
                    enabled = track.id !in unplayableIds,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    addedAtText = if (hasDates) formatAddedDate(addedAt[track.id] ?: 0L) else null
                )
            }
            if (displayed.isEmpty() && query.isNotBlank() && !state.isLoadingMoreTracks) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("未找到匹配的歌曲", color = DesktopColors.TextGray, fontSize = 14.sp)
                    }
                }
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

@Composable
private fun PlaylistHero(playlist: PlaylistDetail, isAlbum: Boolean, count: Int, totalMs: Long?) {
    val typeLabel = when {
        isAlbum -> "专辑"
        playlist.privacy == PRIVATE_PLAYLIST -> "隐私歌单"
        else -> "公开歌单"
    }
    val owner = if (isAlbum) playlist.artists.joinToString(" / ") { it.name } else playlist.creator?.nickname.orEmpty()
    val avatarUrl = if (isAlbum) null else playlist.creator?.avatarUrl
    val summary = buildString {
        append("$count 首歌曲")
        if (totalMs != null) append("，${formatTotalDuration(totalMs)}")
    }
    Row(Modifier.fillMaxWidth().padding(HeaderPadding), verticalAlignment = Alignment.Bottom) {
        Cover(playlist.coverImgUrl, 200.dp, shape = RoundedCornerShape(6.dp))
        Column(Modifier.padding(start = HeaderPadding)) {
            Text(typeLabel, color = DesktopColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                playlist.name,
                color = DesktopColors.TextPrimary,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!avatarUrl.isNullOrBlank()) {
                    Cover(avatarUrl, 24.dp, shape = CircleShape, modifier = Modifier.padding(end = 8.dp))
                }
                if (owner.isNotBlank()) {
                    Text(owner, color = DesktopColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(" · ", color = DesktopColors.TextGray, fontSize = 14.sp)
                }
                Text(summary, color = DesktopColors.TextGray, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun PlaylistActionBar(
    isAlbum: Boolean,
    canSubscribe: Boolean,
    isSubscribed: Boolean,
    query: String,
    order: PlaylistSortOrder,
    showAdded: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onDownload: () -> Unit,
    onPlayNextAll: () -> Unit,
    onCopyLink: () -> Unit,
    onQueryChange: (String) -> Unit,
    onOrderChange: (PlaylistSortOrder) -> Unit
) {
    val resourceLabel = if (isAlbum) "专辑" else "歌单"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = HeaderPadding, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DesktopTooltip("播放") {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(DesktopColors.Accent)
                    .pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, "播放全部", tint = DesktopColors.TextPrimary, modifier = Modifier.size(32.dp))
            }
        }
        ActionIcon(Icons.Rounded.Shuffle, "随机播放", onClick = onShuffle)
        if (canSubscribe) {
            ActionIcon(
                if (isSubscribed) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (isSubscribed) "取消收藏$resourceLabel" else "收藏$resourceLabel",
                tint = if (isSubscribed) DesktopColors.Accent else DesktopColors.TextGray,
                onClick = onToggleSubscribe
            )
        }
        ActionIcon(Icons.Rounded.Download, "下载$resourceLabel", onClick = onDownload)
        PlaylistMoreMenu(resourceLabel, onPlayNextAll, onCopyLink)
        Spacer(Modifier.weight(1f))
        PlaylistSearchBox(query, onQueryChange)
        PlaylistSortMenu(order, showAdded) { key ->
            onOrderChange(if (key == PlaylistSortKey.CUSTOM) PlaylistSortOrder() else PlaylistSortOrder(key, true))
        }
    }
}

@Composable
private fun ActionIcon(
    icon: ImageVector,
    description: String,
    tint: Color = DesktopColors.TextGray,
    onClick: () -> Unit
) {
    DesktopTooltip(description) {
        IconButton(onClick = onClick) {
            Icon(icon, description, tint = tint, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun PlaylistMoreMenu(resourceLabel: String, onPlayNextAll: () -> Unit, onCopyLink: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ActionIcon(Icons.Rounded.MoreHoriz, "更多") { expanded = true }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = DesktopColors.PopupSurface
        ) {
            DropdownMenuItem(
                text = { Text("全部加入下一首播放", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    expanded = false
                    onPlayNextAll()
                }
            )
            DropdownMenuItem(
                text = { Text("复制${resourceLabel}链接", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Rounded.Link, null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    expanded = false
                    onCopyLink()
                }
            )
        }
    }
}

// 点图标展开成输入框，失焦且为空时收回；Esc 清空并收回
@Composable
private fun PlaylistSearchBox(query: String, onQueryChange: (String) -> Unit) {
    var searching by remember { mutableStateOf(false) }
    var focusedOnce by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val width by animateDpAsState(
        if (searching) SearchWidth else ControlHeight,
        tween(SEARCH_ANIMATION_MS, easing = FastOutSlowInEasing),
        label = "playlistSearchWidth"
    )
    val background by animateColorAsState(
        if (searching) DesktopColors.Surface else Color.Transparent,
        tween(SEARCH_ANIMATION_MS),
        label = "playlistSearchBackground"
    )
    LaunchedEffect(searching) {
        if (searching) {
            focusedOnce = false
            runCatching { focusRequester.requestFocus() }
        }
    }
    Box(
        Modifier.width(width).height(ControlHeight).clip(RoundedCornerShape(ControlHeight / 2)).background(background)
            .then(if (searching) Modifier else Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { searching = true })
    ) {
        if (searching) {
            LibrarySearchInput(
                query = query,
                startPadding = 10.dp,
                showClear = true,
                onQueryChange = onQueryChange,
                onClear = {
                    onQueryChange("")
                    searching = false
                },
                focusRequester = focusRequester,
                onFocusChange = { focused ->
                    if (focused) focusedOnce = true else if (focusedOnce && query.isEmpty()) searching = false
                },
                placeholder = "在歌单内搜索"
            )
        } else {
            DesktopTooltip("在歌单内搜索", modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Search, "搜索", tint = DesktopColors.TextGray, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun PlaylistSortMenu(order: PlaylistSortOrder, showAdded: Boolean, onSelect: (PlaylistSortKey) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    Box(Modifier.onSizeChanged { anchorWidth = with(density) { it.width.toDp() } }) {
        Row(
            Modifier.height(ControlHeight).clip(RoundedCornerShape(ControlHeight / 2)).pointerHoverIcon(PointerIcon.Hand)
                .clickable { expanded = true }.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(order.key.label, color = DesktopColors.TextGray, fontSize = 13.sp, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, null, tint = DesktopColors.TextGray, modifier = Modifier.size(18.dp))
        }
        LibraryPopupMenu(expanded, { expanded = false }, anchorWidth, SortMenuWidth) {
            MenuHeader("排序依据")
            PlaylistSortKey.entries.filter { it != PlaylistSortKey.ADDED || showAdded }.forEach { key ->
                MenuOption(key.label, selected = key == order.key) {
                    expanded = false
                    onSelect(key)
                }
            }
        }
    }
}

// 列宽与 TrackRow 逐项对齐：序号 32、封面槽 56、标题 0.45、专辑 0.35、[添加日期]、红心槽 32、时长 48、更多槽 36
@Composable
private fun TrackColumnHeader(order: PlaylistSortOrder, showAdded: Boolean, onSort: (PlaylistSortKey) -> Unit) {
    Column(Modifier.fillMaxWidth().background(DesktopColors.Pane)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "#",
                color = DesktopColors.TextGray,
                fontSize = 13.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.width(32.dp).pointerHoverIcon(PointerIcon.Hand).clickable { onSort(PlaylistSortKey.CUSTOM) }
            )
            Spacer(Modifier.width(56.dp))
            HeaderCell("标题", PlaylistSortKey.TITLE, order, Modifier.weight(0.45f).padding(start = 12.dp), onSort)
            HeaderCell("专辑", PlaylistSortKey.ALBUM, order, Modifier.weight(0.35f).padding(horizontal = 12.dp), onSort)
            if (showAdded) HeaderCell("添加日期", PlaylistSortKey.ADDED, order, Modifier.width(ADDED_COLUMN_WIDTH), onSort)
            Spacer(Modifier.width(32.dp))
            Row(
                Modifier.width(48.dp).pointerHoverIcon(PointerIcon.Hand).clickable { onSort(PlaylistSortKey.DURATION) },
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SortArrow(order, PlaylistSortKey.DURATION)
                Icon(
                    Icons.Rounded.AccessTime,
                    "时长",
                    tint = if (order.key == PlaylistSortKey.DURATION) DesktopColors.TextPrimary else DesktopColors.TextGray,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(36.dp))
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = DesktopColors.SurfaceLight.copy(alpha = 0.6f))
    }
}

@Composable
private fun HeaderCell(
    label: String,
    key: PlaylistSortKey,
    order: PlaylistSortOrder,
    modifier: Modifier,
    onSort: (PlaylistSortKey) -> Unit
) {
    val active = order.key == key
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.clip(RoundedCornerShape(4.dp)).pointerHoverIcon(PointerIcon.Hand).clickable { onSort(key) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                color = if (active) DesktopColors.TextPrimary else DesktopColors.TextGray,
                fontSize = 13.sp,
                maxLines = 1,
                softWrap = false
            )
            SortArrow(order, key)
        }
    }
}

@Composable
private fun SortArrow(order: PlaylistSortOrder, key: PlaylistSortKey) {
    if (order.key != key) return
    Icon(
        if (order.ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
        if (order.ascending) "升序" else "降序",
        tint = DesktopColors.TextPrimary,
        modifier = Modifier.padding(horizontal = 2.dp).size(14.dp)
    )
}

private fun copyPlaylistLink(isAlbum: Boolean, id: Long): Boolean {
    val path = if (isAlbum) "album" else "playlist"
    return runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection("https://music.163.com/$path?id=$id"), null)
    }.isSuccess
}
