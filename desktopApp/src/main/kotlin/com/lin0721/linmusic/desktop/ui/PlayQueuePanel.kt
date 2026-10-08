package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.player.SimilarRoamingController
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

private val HeaderButtonSize = 32.dp
private val RowButtonSize = 28.dp
private val DragHandleWidth = 20.dp

// 右侧栏的播放队列：已播放 / 正在播放 / 接下来播放，仅“接下来播放”段可拖动排序；
// 相似歌曲漫游时队列由系统接管，只显示当前曲
@Composable
fun PlayQueuePanel(
    controller: PlaybackController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val queue by controller.queue.collectAsState()
    val currentIndex by controller.currentIndex.collectAsState()
    val playContext by controller.playContext.collectAsState()
    val playMode by controller.playMode.collectAsState()
    val isRoaming = playContext == SimilarRoamingController.CONTEXT_ROAMING
    var showClearConfirm by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val hasCurrent = currentIndex in queue.indices
    val upcomingStart = currentIndex + 1
    // 列表内部下标：已播放段 = 标题 + 已播放曲目，正在播放段 = 标题 + 当前曲，再加“接下来播放”标题
    val playedBlockCount = if (!isRoaming && currentIndex > 0) 1 + currentIndex else 0
    val currentBlockCount = if (hasCurrent) 2 else 0
    val upcomingFirstLazyIndex = playedBlockCount + currentBlockCount + 1
    val reorder = rememberQueueReorderState(
        listState = listState,
        upcomingStart = upcomingStart,
        queueSize = queue.size,
        upcomingFirstLazyIndex = upcomingFirstLazyIndex,
        onMove = controller::moveInQueue
    )

    // 同一首歌可能重复入队，按出现次数区分 key
    val upcomingKeys = remember(queue, upcomingStart) {
        val seen = mutableMapOf<Long, Int>()
        queue.drop(upcomingStart).map { item ->
            val occurrence = seen.getOrElse(item.songId) { 0 }
            seen[item.songId] = occurrence + 1
            "upcoming_${item.songId}_$occurrence"
        }
    }

    LaunchedEffect(currentIndex, isRoaming) {
        if (isRoaming) {
            listState.scrollToItem(0)
        } else if (hasCurrent) {
            listState.scrollToItem(if (currentIndex > 0) currentIndex + 1 else 0)
        }
    }
    LaunchedEffect(reorder.isDragging) {
        if (reorder.isDragging) reorder.runEdgeScroll()
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            shape = AlertDialogDefaults.shape,
            containerColor = DesktopColors.PopupSurface,
            title = { Text("清空播放队列", color = DesktopColors.TextPrimary) },
            text = { Text("确定要清空播放队列吗？当前播放的歌曲会保留。", color = DesktopColors.TextGray, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    controller.clearQueue()
                }) { Text("清空", color = DesktopColors.TextPrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消", color = DesktopColors.TextGray) }
            }
        )
    }

    Column(modifier.fillMaxSize().padding(top = 16.dp)) {
        QueueHeader(
            queueSize = queue.size,
            onClear = { showClearConfirm = true },
            onClose = onClose,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        HoverScrollbarBox(listState, Modifier.weight(1f).padding(top = 8.dp)) {
            LazyColumn(
                state = listState,
                // 拖动期间滚动完全交给边缘自动滚屏接管
                userScrollEnabled = !reorder.isDragging,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp)
            ) {
                if (!isRoaming && currentIndex > 0) {
                    item(key = "header_played") { SectionLabel("已播放") }
                    itemsIndexed(
                        items = queue.subList(0, currentIndex),
                        key = { index, item -> "played_${item.songId}_$index" }
                    ) { index, item ->
                        QueueRow(
                            item = item,
                            isCurrent = false,
                            isPlayed = true,
                            canDrag = false,
                            canRemove = queue.size > 1,
                            dragging = false,
                            offsetY = 0f,
                            onPlay = { controller.playAtIndex(index) },
                            onRemove = { controller.removeFromQueue(index) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }

                if (hasCurrent) {
                    item(key = "header_current") { SectionLabel("正在播放") }
                    item(key = "current_${queue[currentIndex].songId}") {
                        QueueRow(
                            item = queue[currentIndex],
                            isCurrent = true,
                            isPlayed = false,
                            canDrag = false,
                            canRemove = !isRoaming && queue.size > 1,
                            dragging = false,
                            offsetY = 0f,
                            onPlay = { controller.playAtIndex(currentIndex) },
                            onRemove = { controller.removeFromQueue(currentIndex) }
                        )
                    }
                }

                if (!isRoaming && upcomingStart < queue.size) {
                    item(key = "header_upcoming") {
                        SectionLabel(
                            if (playMode == PlayMode.SHUFFLE) "随机播放来源：${playContext.orEmpty()}" else "接下来播放"
                        )
                    }
                    itemsIndexed(
                        items = queue.subList(upcomingStart, queue.size),
                        key = { index, item -> upcomingKeys.getOrElse(index) { "upcoming_${item.songId}_$index" } }
                    ) { index, item ->
                        val actualIndex = upcomingStart + index
                        val dragging = reorder.draggedIndex == actualIndex
                        val settling = reorder.settlingIndex == actualIndex
                        QueueRow(
                            item = item,
                            isCurrent = false,
                            isPlayed = false,
                            canDrag = true,
                            canRemove = true,
                            dragging = dragging,
                            offsetY = reorder.offsetOf(actualIndex),
                            onPlay = { controller.playAtIndex(actualIndex) },
                            onRemove = { controller.removeFromQueue(actualIndex) },
                            onDragStart = { reorder.start(actualIndex) },
                            onDrag = { delta -> reorder.drag(actualIndex, delta) },
                            onDragEnd = { reorder.end(actualIndex, scope) },
                            // 抬起中的行自己负责位移，交给 animateItem 会和手动 translationY 打架
                            modifier = if (dragging || settling) Modifier.zIndex(1f) else Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueHeader(
    queueSize: Int,
    onClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    OverlayPanelHeader("播放队列", "关闭播放队列", onClose, modifier) {
        Text("共 $queueSize 首", color = DesktopColors.TextGray, fontSize = 12.sp)
        if (queueSize > 1) {
            IconButton(onClick = onClear, modifier = Modifier.padding(start = 4.dp).size(HeaderButtonSize)) {
                Icon(Icons.Rounded.DeleteOutline, "清空队列", tint = DesktopColors.TextGray, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = DesktopColors.TextGray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
}

// 双击跳播；悬停时显示拖动把手与移除按钮，拖动中保持显示
@Composable
private fun QueueRow(
    item: QueueItem,
    isCurrent: Boolean,
    isPlayed: Boolean,
    canDrag: Boolean,
    canRemove: Boolean,
    dragging: Boolean,
    offsetY: Float,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {}
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val revealed = hovered || dragging
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val lifted = dragging || offsetY != 0f
    val shape = RoundedCornerShape(4.dp)

    Row(
        modifier.fillMaxWidth()
            .then(
                if (lifted) {
                    Modifier.zIndex(1f).graphicsLayer { translationY = offsetY }
                        .then(if (dragging) Modifier.shadow(8.dp, shape) else Modifier)
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(
                when {
                    dragging -> DesktopColors.Surface
                    revealed -> DesktopColors.PaneHover
                    else -> Color.Transparent
                }
            )
            .hoverable(hoverSource)
            .onDoubleClick(onPlay)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .then(if (isPlayed) Modifier.alpha(0.5f) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(DragHandleWidth, RowButtonSize), contentAlignment = Alignment.Center) {
            if (canDrag) {
                HoverReveal(revealed = revealed) {
                    Icon(
                        Icons.Rounded.DragIndicator,
                        "拖动排序",
                        tint = DesktopColors.TextGray,
                        modifier = Modifier.size(20.dp).pointerHoverIcon(PointerIcon.Hand).pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { currentOnDragStart() },
                                onDrag = { change, amount ->
                                    change.consume()
                                    currentOnDrag(amount.y)
                                },
                                onDragEnd = { currentOnDragEnd() },
                                onDragCancel = { currentOnDragEnd() }
                            )
                        }
                    )
                }
            }
        }
        Cover(item.coverUrl, 40.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                item.title,
                color = if (isCurrent) DesktopColors.Accent else DesktopColors.TextPrimary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(item.artist, color = DesktopColors.TextGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (canRemove) {
            HoverReveal(revealed = revealed && !dragging) {
                IconButton(onClick = onRemove, modifier = Modifier.size(RowButtonSize)) {
                    Icon(Icons.Rounded.Close, "从队列中移除", tint = DesktopColors.TextGray, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
