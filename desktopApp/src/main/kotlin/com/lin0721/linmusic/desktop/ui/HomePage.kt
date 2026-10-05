package com.lin0721.linmusic.desktop.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.home.domain.HomeCard
import com.lin0721.linmusic.feature.home.domain.HomeShelf
import com.lin0721.linmusic.feature.home.ui.HomeFeedData
import com.lin0721.linmusic.feature.home.ui.HomeUiState
import com.lin0721.linmusic.feature.home.ui.HomeViewModel
import com.lin0721.linmusic.feature.music.ui.MusicViewModel
import com.lin0721.linmusic.feature.newworks.ui.NewWorksViewModel
import com.lin0721.linmusic.feature.podcast.ui.PodcastViewModel

internal val CardWidth = 168.dp

// 距离底部还剩几项时提前拉下一页货架
private const val LOAD_MORE_THRESHOLD = 2

// 最近播放网格：条目数固定，列数随内容区宽度切换，格子不会被侧栏挤窄
private const val RECENT_ITEM_COUNT = 8
private const val RECENT_MIN_COLUMNS = 2
private const val RECENT_MAX_COLUMNS = 4
private val RecentTileMinWidth = 200.dp
private val RecentGridGap = 8.dp
private val RecentGridPadding = 24.dp

// 顶部固定「全部 / 音乐 / 播客」胶囊，下方按选中项切换内容；各 tab 的数据切过去才拉，
// 滚动位置在首页内按 tab 各自保留
@Composable
fun HomePage(
    viewModel: HomeViewModel,
    musicViewModel: MusicViewModel,
    podcastViewModel: PodcastViewModel,
    newWorksViewModel: NewWorksViewModel,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    newWorksSelected: Boolean,
    onNewWorksSelectedChange: (Boolean) -> Unit,
    onPlaylistClick: (id: Long, title: String) -> Unit,
    onStyleClick: (id: Long, name: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val navigator = LocalDesktopNavigator.current
    val musicState by musicViewModel.uiState.collectAsState()
    val podcastState by podcastViewModel.uiState.collectAsState()
    val newWorksState by newWorksViewModel.uiState.collectAsState()
    val newWorksCollectState by newWorksViewModel.collectState.collectAsState()
    val newWorksImportState by newWorksViewModel.importState.collectAsState()
    val newWorksStatus by newWorksViewModel.releaseStatus.collectAsState()
    val allListState = rememberLazyListState()
    val musicListState = rememberLazyListState()
    val podcastListState = rememberLazyListState()
    val newWorksGridState = rememberLazyGridState()

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            HOME_TAB_MUSIC -> musicViewModel.loadIfNeeded()
            HOME_TAB_PODCAST -> podcastViewModel.loadIfNeeded()
        }
    }
    LaunchedEffect(selectedTab, newWorksSelected) {
        if (selectedTab == HOME_TAB_MUSIC && newWorksSelected) newWorksViewModel.loadIfNeeded()
    }

    Column(modifier.fillMaxSize()) {
        HomeTabPills(
            selectedTab = selectedTab,
            onSelect = onTabSelected,
            newWorksSelected = newWorksSelected,
            onNewWorksSelect = { onNewWorksSelectedChange(true) }
        )
        Box(Modifier.weight(1f)) {
            when {
                selectedTab == HOME_TAB_MUSIC && newWorksSelected -> NewWorksTab(
                    uiState = newWorksState,
                    gridState = newWorksGridState,
                    collectState = newWorksCollectState,
                    importState = newWorksImportState,
                    status = newWorksStatus,
                    actions = remember(newWorksViewModel, navigator) {
                        NewWorksTabActions(
                            onAlbumClick = navigator.openAlbum,
                            onTogglePlay = newWorksViewModel::togglePlayRelease,
                            onToggleLibrary = newWorksViewModel::toggleInLibrary,
                            onAddToPlayNext = newWorksViewModel::addToPlayNext,
                            onPrepareCollect = newWorksViewModel::prepareCollectDialog,
                            onSaveCollection = newWorksViewModel::savePlaylistCollection,
                            onSaveNewCollection = newWorksViewModel::createPlaylistAndAddSong,
                            onPrepareImportTargets = newWorksViewModel::prepareImportTargets,
                            onAddToPlaylist = newWorksViewModel::addToPlaylist,
                            onCreatePlaylistAndAdd = newWorksViewModel::createPlaylistAndAdd,
                            onRetry = newWorksViewModel::load,
                            onLoadMore = newWorksViewModel::loadMore
                        )
                    }
                )
                selectedTab == HOME_TAB_MUSIC -> MusicTab(
                    uiState = musicState,
                    listState = musicListState,
                    onStyleClick = onStyleClick,
                    onRetry = musicViewModel::loadStyles
                )
                selectedTab == HOME_TAB_PODCAST -> PodcastTab(
                    uiState = podcastState,
                    listState = podcastListState,
                    onCategorySelect = podcastViewModel::selectCategory,
                    onProgramPlay = podcastViewModel::playProgramAt,
                    onRetry = podcastViewModel::loadFeed
                )
                else -> HomeAllContent(viewModel, allListState, onPlaylistClick)
            }
        }
    }
}

@Composable
internal fun HomeTabLoading(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = DesktopColors.Accent)
    }
}

@Composable
internal fun HomeTabError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = DesktopColors.TextGray)
            TextButton(onClick = onRetry) {
                Text("重试", color = DesktopColors.TextPrimary)
            }
        }
    }
}

@Composable
private fun HomeAllContent(viewModel: HomeViewModel, listState: LazyListState, onPlaylistClick: (id: Long, title: String) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    when (val state = uiState) {
        HomeUiState.Loading -> HomeTabLoading()
        is HomeUiState.Error -> HomeTabError(state.message, viewModel::refreshHomeData)
        is HomeUiState.Success -> HomeFeed(state.data, viewModel, listState, onPlaylistClick, Modifier)
    }
}

@Composable
private fun HomeFeed(
    data: HomeFeedData,
    viewModel: HomeViewModel,
    listState: LazyListState,
    onPlaylistClick: (id: Long, title: String) -> Unit,
    modifier: Modifier
) {
    val shouldLoadMore by remember(data) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            data.hasMore && !data.isLoadingMore &&
                lastVisible >= listState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMoreShelves()
    }

    LookaheadScope {
        CompositionLocalProvider(LocalHomeLookaheadScope provides this) {
            HoverScrollbarBox(listState) {
                LazyColumn(
                    state = listState,
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    homeItems(data, viewModel, onPlaylistClick)
                }
            }
        }
    }
}

// 区块高度随列数变化时，下方区块平滑让位；只做位移，不加淡入淡出
@Composable
private fun LazyItemScope.reflow(): Modifier =
    if (LocalPaneResizing.current) Modifier else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)

private fun LazyListScope.homeItems(
    data: HomeFeedData,
    viewModel: HomeViewModel,
    onPlaylistClick: (id: Long, title: String) -> Unit
) {
    if (data.recentPlaylists.isNotEmpty()) {
        item(key = "recent") {
            RecentGrid(data, reflow()) { id, title -> onPlaylistClick(id, title) }
        }
    }
    if (data.dailySongs.isNotEmpty()) {
        item(key = "daily") {
            Box(reflow()) { DailyBanner(data.dailySongs.size) { viewModel.playDailySong() } }
        }
    }
    // 服务端货架已含推荐歌单，仅在货架缺失时兜底展示
    if (data.shelves.isEmpty() && data.recommendPlaylists.isNotEmpty()) {
        item(key = "recommend") {
            Box(reflow()) {
                ShelfRow("推荐歌单", data.recommendPlaylists) { playlist ->
                    CardTile(playlist.picUrl, playlist.name, "") { onPlaylistClick(playlist.id, playlist.name) }
                }
            }
        }
    }
    items(data.shelves, key = { "shelf_${it.blockCode}_${it.title}" }) { shelf ->
        Box(reflow()) { ServerShelf(shelf, viewModel, onPlaylistClick) }
    }
    if (data.isLoadingMore) {
        item(key = "loading_more") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DesktopColors.Accent, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentGrid(data: HomeFeedData, modifier: Modifier, onClick: (Long, String) -> Unit) {
    BoxWithConstraints(modifier) {
        val available = maxWidth - RecentGridPadding * 2
        val columns = ((available + RecentGridGap) / (RecentTileMinWidth + RecentGridGap)).toInt()
            .coerceIn(RECENT_MIN_COLUMNS, RECENT_MAX_COLUMNS)
        Column(Modifier.padding(horizontal = RecentGridPadding), verticalArrangement = Arrangement.spacedBy(RecentGridGap)) {
            SectionTitle("最近播放", horizontalPadding = 0)
            // 列数变化时格子从旧位置平滑移到新位置，整体高度同步过渡
            FlowRow(
                modifier = Modifier.fillMaxWidth().then(
                    if (LocalPaneResizing.current) Modifier
                    else Modifier.animateContentSize(tween(LAYOUT_REFLOW_MS, easing = FastOutSlowInEasing))
                ),
                horizontalArrangement = Arrangement.spacedBy(RecentGridGap),
                verticalArrangement = Arrangement.spacedBy(RecentGridGap),
                maxItemsInEachRow = columns
            ) {
                val shown = data.recentPlaylists.take(RECENT_ITEM_COUNT)
                shown.forEach { playlist ->
                    key(playlist.id) {
                        Row(
                            Modifier.weight(1f).height(56.dp).homeReflowBounds().clip(RoundedCornerShape(4.dp))
                                .background(DesktopColors.Surface)
                                .clickable { onClick(playlist.id, playlist.name) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Cover(playlist.coverUrl, 56.dp, shape = RoundedCornerShape(0.dp))
                            Text(
                                playlist.name,
                                color = DesktopColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                }
                // 末行不足时补位，保持列宽一致
                repeat((columns - shown.size % columns) % columns) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DailyBanner(songCount: Int, onPlay: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 24.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(DesktopColors.Accent).clickable(onClick = onPlay).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("每日推荐", color = DesktopColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("$songCount 首专属好歌", color = DesktopColors.TextPrimary.copy(alpha = 0.8f), fontSize = 14.sp)
        }
        Text("播放", color = DesktopColors.TextPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ServerShelf(
    shelf: HomeShelf,
    viewModel: HomeViewModel,
    onPlaylistClick: (Long, String) -> Unit
) {
    val songs = shelf.cards.filterIsInstance<HomeCard.Song>()
    val voices = shelf.cards.filterIsInstance<HomeCard.Voice>()
    ShelfRow(shelf.title, shelf.cards) { card ->
        CardTile(card.coverUrl, card.title, card.caption) {
            when (card) {
                is HomeCard.Playlist -> onPlaylistClick(card.id, card.title)
                is HomeCard.Song -> viewModel.playShelfSong(shelf.title, songs, card)
                is HomeCard.Voice -> viewModel.playShelfVoice(shelf.title, voices, card)
                // 专辑页在后续阶段接入
                is HomeCard.Album -> Unit
            }
        }
    }
}

@Composable
internal fun <T> ShelfRow(title: String, items: List<T>, itemContent: @Composable (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items) { itemContent(it) }
        }
    }
}

@Composable
internal fun CardTile(coverUrl: String, title: String, caption: String, onClick: () -> Unit) {
    Column(
        Modifier.width(CardWidth).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick)
    ) {
        Cover(coverUrl, CardWidth, shape = RoundedCornerShape(6.dp))
        Text(
            title,
            color = DesktopColors.TextPrimary,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (caption.isNotBlank()) {
            Text(caption, color = DesktopColors.TextGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun SectionTitle(text: String, horizontalPadding: Int = 24) {
    Text(
        text,
        color = DesktopColors.TextPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = horizontalPadding.dp)
    )
}
