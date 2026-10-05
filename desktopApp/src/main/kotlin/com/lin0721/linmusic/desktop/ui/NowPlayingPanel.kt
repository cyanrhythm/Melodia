package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.PlaybackController.Companion.CONTEXT_INTELLIGENCE
import com.lin0721.linmusic.core.player.SimilarRoamingController
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.theme.DesktopDimens
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel

private val LyricCardHeight = 280.dp

private val HeaderButtonSize = 32.dp
private val HeaderButtonOffset = 4.dp

private val SleepTimerOptions = listOf(15, 30, 60, 90)

@Composable
fun NowPlayingPanel(
    controller: PlaybackController,
    playerViewModel: PlayerViewModel,
    hovered: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nowPlaying by controller.nowPlaying.collectAsState()
    val track = nowPlaying ?: return
    val detailState by playerViewModel.songDetailState.collectAsState()
    val currentLyricIndex by playerViewModel.currentLyricIndex.collectAsState()

    val scrollState = rememberScrollState()
    HoverScrollbarBox(scrollState) {
        Column(modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp)) {
            PanelHeader(track, controller, playerViewModel, hovered, onClose)
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Cover(track.artworkUri, maxWidth, shape = RoundedCornerShape(8.dp), requestSize = DesktopDimens.NowPlayingMaxWidth)
            }
            Text(
                track.title,
                color = DesktopColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 16.dp)
            )
            NowPlayingArtists(track, playerViewModel, 14.sp)
            LyricPreviewCard(
                lines = detailState.lyrics,
                isLoading = detailState.isLyricsLoading,
                currentIndex = currentLyricIndex,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

// 歌曲详情到达后歌手名可点击跳转，之前先展示队列里的纯文本
@Composable
internal fun NowPlayingArtists(track: NowPlaying, playerViewModel: PlayerViewModel, fontSize: TextUnit) {
    val navigator = LocalDesktopNavigator.current
    val detailState by playerViewModel.songDetailState.collectAsState()
    val artists = detailState.artists
        .takeIf { detailState.songDetail?.id == track.songId }
        ?.map { Artist(id = it.artistId, name = it.artistName) }
        .orEmpty()
    if (artists.isEmpty()) {
        Text(track.artist, color = DesktopColors.TextGray, fontSize = fontSize, maxLines = 1, overflow = TextOverflow.Ellipsis)
    } else {
        Text(
            artistLinks(artists, navigator.openArtist),
            color = DesktopColors.TextGray,
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PanelHeader(
    track: NowPlaying,
    controller: PlaybackController,
    playerViewModel: PlayerViewModel,
    hovered: Boolean,
    onClose: () -> Unit
) {
    val playContext by controller.playContext.collectAsState()
    val sleepRemaining by controller.sleepTimerRemaining.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    val isIntelligence = playContext == CONTEXT_INTELLIGENCE
    val isRoaming = playContext == SimilarRoamingController.CONTEXT_ROAMING
    val sleepActive = sleepRemaining > 0L

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // 按钮尺寸与偏移沿用移动端播放页顶栏，使箭头与封面左缘对齐
        HoverReveal(revealed = hovered, modifier = Modifier.offset(x = (-HeaderButtonOffset)), reserveSpace = false) {
            IconButton(onClick = onClose, modifier = Modifier.size(HeaderButtonSize)) {
                Icon(
                    Icons.Rounded.KeyboardArrowRight,
                    "关闭正在播放",
                    tint = DesktopColors.TextGray,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        PanelTitle(track, controller, playerViewModel, Modifier.weight(1f))
        if (sleepActive) {
            Icon(Icons.Rounded.Bedtime, "睡眠定时", tint = DesktopColors.Accent, modifier = Modifier.size(16.dp))
            Text(
                formatCountdown(sleepRemaining),
                color = DesktopColors.TextGray,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        HoverReveal(revealed = hovered || menuOpen, modifier = Modifier.offset(x = HeaderButtonOffset)) {
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(HeaderButtonSize)) {
                    Icon(Icons.Rounded.MoreHoriz, "更多有关《${track.title}》的选项", tint = DesktopColors.TextGray)
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = DesktopColors.Surface
                ) {
                    val songId = track.songId
                    MenuItem(
                        Icons.Rounded.Favorite,
                        if (isIntelligence) "退出心动模式" else "心动模式",
                        enabled = isIntelligence || songId != null
                    ) {
                        menuOpen = false
                        if (isIntelligence) {
                            controller.disableIntelligence()
                        } else if (songId != null) {
                            playerViewModel.startIntelligenceMode(songId, track.title, track.artist, track.artworkUri.orEmpty())
                        }
                    }
                    MenuItem(
                        Icons.Rounded.Radio,
                        if (isRoaming) "退出相似歌曲漫游" else "相似歌曲漫游",
                        enabled = isRoaming || songId != null
                    ) {
                        menuOpen = false
                        if (isRoaming) {
                            controller.disableRoaming()
                        } else if (songId != null) {
                            playerViewModel.startSimilarSongsRoaming(songId, track.title, track.artist, track.artworkUri.orEmpty())
                        }
                    }
                    HorizontalDivider(color = DesktopColors.SurfaceLight)
                    Text(
                        if (sleepActive) "睡眠定时（剩余 ${formatCountdown(sleepRemaining)}）" else "睡眠定时",
                        color = DesktopColors.TextGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    SleepTimerOptions.forEach { minutes ->
                        MenuItem(Icons.Rounded.Timer, "$minutes 分钟") {
                            menuOpen = false
                            playerViewModel.setSleepTimer(minutes)
                        }
                    }
                    if (sleepActive) {
                        MenuItem(Icons.Rounded.TimerOff, "取消定时") {
                            menuOpen = false
                            playerViewModel.setSleepTimer(0)
                        }
                    }
                }
            }
        }
    }
}

private class TitleTarget(val isAlbum: Boolean, val id: Long, val name: String)

// 优先显示播放来源的歌单/专辑；来源缺失时退回当前歌曲所属专辑，都没有则只显示“正在播放”
@Composable
private fun PanelTitle(track: NowPlaying, controller: PlaybackController, playerViewModel: PlayerViewModel, modifier: Modifier) {
    val navigator = LocalDesktopNavigator.current
    val source by controller.playSource.collectAsState()
    val detailState by playerViewModel.songDetailState.collectAsState()
    val album = detailState.songDetail?.takeIf { it.id == track.songId }?.al
    val target = source?.let { TitleTarget(it.kind == PlaySource.Kind.ALBUM, it.id, it.name) }
        ?: album?.takeIf { it.id > 0 && it.name.isNotBlank() }?.let { TitleTarget(true, it.id, it.name) }

    if (target == null) {
        Text("正在播放", color = DesktopColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = modifier)
        return
    }
    Text(
        target.name,
        color = DesktopColors.TextPrimary,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).pointerHoverIcon(PointerIcon.Hand).clickable {
            if (target.isAlbum) navigator.openAlbum(target.id, target.name) else navigator.openPlaylist(target.id, target.name)
        }
    )
}

@Composable
private fun MenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(text, fontSize = 14.sp) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(18.dp)) },
        enabled = enabled,
        onClick = onClick
    )
}

private fun formatCountdown(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
private fun LyricPreviewCard(
    lines: List<LyricLine>,
    isLoading: Boolean,
    currentIndex: Int,
    modifier: Modifier
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(DesktopColors.Surface).padding(16.dp)
    ) {
        Text("歌词预览", color = DesktopColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Box(Modifier.fillMaxWidth().height(LyricCardHeight).padding(top = 8.dp), contentAlignment = Alignment.Center) {
            when {
                isLoading -> CircularProgressIndicator(color = DesktopColors.Accent)
                lines.isEmpty() -> Text("暂无歌词", color = DesktopColors.TextGray, fontSize = 14.sp)
                else -> LyricList(lines, currentIndex)
            }
        }
    }
}

@Composable
private fun LyricList(lines: List<LyricLine>, currentIndex: Int) {
    val listState = rememberLazyListState()
    // 当前行滚到卡片约三分之一处，上方保留已唱过的一两行作语境
    val offsetPx = with(LocalDensity.current) { (LyricCardHeight / 3).roundToPx() }
    LaunchedEffect(currentIndex, lines) {
        if (currentIndex in lines.indices) listState.animateScrollToItem(currentIndex, -offsetPx)
    }
    HoverScrollbarBox(listState) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // 底部留白让末尾几行也能滚到定位处
            contentPadding = PaddingValues(bottom = LyricCardHeight)
        ) {
            itemsIndexed(lines) { index, line ->
                val active = index == currentIndex
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(
                        line.text,
                        color = if (active) DesktopColors.TextPrimary else DesktopColors.TextGray,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        fontSize = if (active) 18.sp else 16.sp
                    )
                    line.translation?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = DesktopColors.TextGray, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
