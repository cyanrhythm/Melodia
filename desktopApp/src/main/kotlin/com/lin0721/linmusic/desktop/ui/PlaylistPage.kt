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
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.zIndex
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
import java.util.Collections

// 距离列表底部还剩几项时加载下一页曲目
private const val TRACK_LOAD_MORE_THRESHOLD = 10
private const val HERO_DARKEN_FRACTION = 0.35f
private const val PRIVATE_PLAYLIST = 10

// 表头项与吸顶列头占用的列表下标
private const val LEADING_LIST_ITEMS = 2

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

    val commentsHost = remember(viewModel) { viewModel.asCommentsHost() }
    val commentsOpen = navigator.isCommentsPanelOpen(commentsHost)
    val isSavingInfo by viewModel.isSavingInfo.collectAsState()
    var showEdit by remember(playlist.id) { mutableStateOf(false) }
    var showAddSongs by remember(playlist.id) { mutableStateOf(false) }
    var showDelete by remember(playlist.id) { mutableStateOf(false) }
    var showImport by remember(playlist.id) { mutableStateOf(false) }
    var showCreateImport by remember(playlist.id) { mutableStateOf(false) }
    val importState by viewModel.importState.collectAsState()
    var query by remember(playlist.id) { mutableStateOf("") }
    var order by remember(playlist.id) { mutableStateOf(PlaylistSortOrder()) }
    val addedAt = remember(playlist.trackIds) { playlist.trackIds.associate { it.id to it.at } }
    val hasDates = remember(addedAt) { addedAt.values.any { it > 0 } }
    val customView = order.key == PlaylistSortKey.CUSTOM && query.isBlank()
    val displayed = remember(playlist.tracks, query, order, addedAt) {
        sortTracks(filterTracks(playlist.tracks, query), order, addedAt)
    }

    // 排序与搜索针对全部曲目，自建歌单的拖动调序按全量提交，都需要把没加载的补齐
    LaunchedEffect(customView, isOwned, playlist.id, state.hasMoreTracks) {
        if ((!customView || isOwned) && state.hasMoreTracks && !state.isLoadingMoreTracks) viewModel.ensureAllTracksLoaded { }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var draft by remember(playlist.id) { mutableStateOf<List<Track>?>(null) }
    var savingOrder by remember(playlist.id) { mutableStateOf(false) }
    val canReorder = isOwned && customView && !state.hasMoreTracks && !state.isLoadingMoreTracks && !savingOrder
    val shown = draft ?: displayed
    val reorder = rememberQueueReorderState(
        listState = listState,
        upcomingStart = 0,
        queueSize = shown.size,
        upcomingFirstLazyIndex = LEADING_LIST_ITEMS,
        onMove = { from, to -> draft = (draft ?: displayed).toMutableList().also { Collections.swap(it, from, to) } }
    )
    LaunchedEffect(reorder.isDragging) {
        if (reorder.isDragging) reorder.runEdgeScroll()
    }
    val commitOrder = {
        val reordered = draft
        if (reordered != null && reordered != playlist.tracks) {
            savingOrder = true
            viewModel.updateTrackOrder(playlist.id, reordered) {
                savingOrder = false
                draft = null
            }
        } else {
            draft = null
        }
    }
    // 同一首歌不会重复入歌单，但按出现次数区分以防服务端数据异常
    val rowKeys = remember(shown) {
        val seen = HashMap<Long, Int>()
        shown.map { track ->
            val occurrence = seen.getOrElse(track.id) { 0 }
            seen[track.id] = occurrence + 1
            "track_${track.id}_$occurrence"
        }
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
                        canDelete = isOwned,
                        onDelete = { showDelete = true },
                        showComments = playlist.id > 0,
                        commentsOpen = commentsOpen,
                        onToggleComments = {
                            if (!commentsOpen) viewModel.loadPlaylistComments(playlist.id)
                            navigator.toggleCommentsPanel(commentsHost)
                        },
                        onPlay = { playDisplayed(false) },
                        onShuffle = { playDisplayed(true) },
                        onToggleSubscribe = {
                            if (navigator.isLoggedIn) viewModel.toggleSubscribePlaylist() else navigator.showMessage("请先登录账号")
                        },
                        onDownload = { viewModel.downloadPlaylist(playlist.id, playlist.name, navigator.downloadLevel) },
                        onPlayNextAll = viewModel::addAllTracksToPlayNext,
                        onImport = {
                            if (navigator.isLoggedIn) {
                                viewModel.prepareImportTargets(playlist.id)
                                showImport = true
                            } else {
                                navigator.showMessage("请先登录账号")
                            }
                        },
                        onCopyLink = {
                            navigator.showMessage(if (copyPlaylistLink(isAlbum, playlist.id)) "已复制链接" else "复制失败")
                        },
                        onQueryChange = { query = it },
                        onOrderChange = { order = it }
                    )
                    if (isOwned) {
                        Row(
                            Modifier.padding(start = HeaderPadding, end = HeaderPadding, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PlaylistPill(Icons.Rounded.Add, "添加") { showAddSongs = true }
                            PlaylistPill(Icons.Rounded.Edit, "名称和详情") { showEdit = true }
                        }
                    }
                }
            }
            stickyHeader(key = "columns") {
                TrackColumnHeader(order, hasDates) { order = order.toggled(it) }
            }
            itemsIndexed(shown, key = { index, _ -> rowKeys[index] }) { index, track ->
                val dragging = reorder.draggedIndex == index
                val settling = reorder.settlingIndex == index
                TrackRow(
                    index = index,
                    track = track,
                    isCurrent = nowPlaying?.songId == track.id,
                    onPlay = { playFrom(track) },
                    actions = actions,
                    enabled = track.id !in unplayableIds,
                    // 抬起中的行自己负责位移，交给 animateItem 会和手动位移打架
                    modifier = Modifier.padding(horizontal = 16.dp)
                        .then(if (dragging || settling) Modifier.zIndex(1f) else Modifier.animateItem()),
                    addedAtText = if (hasDates) formatAddedDate(addedAt[track.id] ?: 0L) else null,
                    reorder = if (canReorder || dragging) {
                        TrackReorder(
                            dragging = dragging,
                            offsetY = reorder.offsetOf(index),
                            onDragStart = { reorder.start(index) },
                            onDrag = { delta -> reorder.drag(index, delta) },
                            onDragEnd = {
                                reorder.end(index, scope)
                                commitOrder()
                            }
                        )
                    } else {
                        null
                    }
                )
            }
            if (shown.isEmpty() && query.isNotBlank() && !state.isLoadingMoreTracks) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("未找到匹配的歌曲", color = DesktopColors.TextGray, fontSize = 14.sp)
                    }
                }
            }
            if (isOwned && query.isBlank() && state.recommendedSongs.isNotEmpty()) {
                item(key = "recommend_header") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = HeaderPadding, end = HeaderPadding, top = 32.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("推荐歌曲", color = DesktopColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("根据歌单里的歌挑选", color = DesktopColors.TextGray, fontSize = 13.sp)
                        }
                        TextButton(onClick = viewModel::refreshRecommendations) {
                            Text("换一批", color = DesktopColors.TextGray, fontSize = 13.sp)
                        }
                    }
                }
                itemsIndexed(state.recommendedSongs, key = { _, track -> "recommend_${track.id}" }) { index, track ->
                    TrackRow(
                        index = index,
                        track = track,
                        isCurrent = nowPlaying?.songId == track.id,
                        onPlay = { viewModel.playRecommendedTrack(track) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                        trailingAction = {
                            DesktopTooltip("添加到歌单") {
                                IconButton(
                                    onClick = { viewModel.addRecommendSongToPlaylist(playlist.id, track) },
                                    modifier = Modifier.padding(start = 4.dp).size(32.dp)
                                ) {
                                    Icon(Icons.Rounded.Add, "添加到歌单", tint = DesktopColors.TextGray, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    )
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

    if (showEdit) {
        EditPlaylistDialog(
            initialName = playlist.name,
            initialDescription = playlist.description.orEmpty(),
            coverUrl = playlist.coverImgUrl,
            isSaving = isSavingInfo,
            onDismiss = { showEdit = false },
            onConfirm = { name, description, coverBytes ->
                viewModel.updatePlaylistInfo(
                    playlist.id, playlist.name, name, playlist.description, description, coverBytes
                ) { success -> if (success) showEdit = false }
            }
        )
    }
    if (showAddSongs) {
        AddSongsDialog(
            playlistId = playlist.id,
            existingIds = remember(playlist.tracks) { playlist.tracks.mapTo(HashSet()) { it.id } },
            viewModel = viewModel,
            onDismiss = { showAddSongs = false }
        )
    }
    if (showImport) {
        ImportToPlaylistDialog(
            state = importState,
            onPick = { target ->
                showImport = false
                viewModel.importAllTracksTo(target.id)
            },
            onCreate = {
                showImport = false
                showCreateImport = true
            },
            onDismiss = { showImport = false }
        )
    }
    if (showCreateImport) {
        CreatePlaylistDialog(
            onDismiss = { showCreateImport = false },
            onCreate = { name, isPrivate ->
                showCreateImport = false
                viewModel.createPlaylistAndImportAll(name, isPrivate)
            },
            onEmptyName = { navigator.showMessage(EMPTY_NAME_MESSAGE) }
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            shape = AlertDialogDefaults.shape,
            containerColor = DesktopColors.PopupSurface,
            title = { Text("删除歌单", color = DesktopColors.TextPrimary) },
            text = { Text("确定要删除歌单「${playlist.name}」吗？", color = DesktopColors.TextGray, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.deletePlaylist(playlist.id) { navigator.goBack() }
                }) { Text("删除", color = DangerColor, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消", color = DesktopColors.TextGray) }
            }
        )
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
    canDelete: Boolean,
    onDelete: () -> Unit,
    showComments: Boolean,
    commentsOpen: Boolean,
    onToggleComments: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onDownload: () -> Unit,
    onPlayNextAll: () -> Unit,
    onImport: () -> Unit,
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
        if (showComments) {
            ActionIcon(
                Icons.Rounded.ChatBubbleOutline,
                "${resourceLabel}评论",
                tint = if (commentsOpen) DesktopColors.TextPrimary else DesktopColors.TextGray,
                onClick = onToggleComments
            )
        }
        ActionIcon(Icons.Rounded.Download, "下载$resourceLabel", onClick = onDownload)
        PlaylistMoreMenu(resourceLabel, canDelete, onPlayNextAll, onImport, onCopyLink, onDelete)
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
private fun PlaylistMoreMenu(
    resourceLabel: String,
    canDelete: Boolean,
    onPlayNextAll: () -> Unit,
    onImport: () -> Unit,
    onCopyLink: () -> Unit,
    onDelete: () -> Unit
) {
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
                text = { Text("添加到歌单", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    expanded = false
                    onImport()
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
            if (canDelete) {
                DropdownMenuItem(
                    text = { Text("删除歌单", fontSize = 14.sp, color = DangerColor) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = DangerColor, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        expanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun PlaylistPill(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(DesktopColors.Surface)
            .pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = DesktopColors.TextPrimary, modifier = Modifier.size(18.dp))
        Text(
            text,
            color = DesktopColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 6.dp)
        )
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
