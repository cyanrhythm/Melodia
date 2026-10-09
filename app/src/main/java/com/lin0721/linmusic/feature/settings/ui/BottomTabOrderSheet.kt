package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lin0721.linmusic.core.preferences.BottomTabOrder
import com.lin0721.linmusic.core.ui.components.BottomTabCatalog
import com.lin0721.linmusic.core.ui.components.MelodiaButton
import com.lin0721.linmusic.core.ui.components.MelodiaDragHandle
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import kotlinx.coroutines.launch

// 底栏 tab 顺序调整弹层：交互与「歌单排序」一致——长按右侧手柄拖拽，落点后其余行平滑让位。
// 编辑结果只在点「完成」时落库，直接关闭视为放弃
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomTabOrderSheet(
    currentOrder: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val order = remember { mutableStateListOf<String>() }
    // 弹层随开关重建，首次组合时用当前偏好初始化临时顺序
    LaunchedEffect(Unit) {
        order.clear()
        order.addAll(BottomTabOrder.normalize(currentOrder))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // 关闭下拉手势，避免与行内长按拖拽抢事件
        sheetGesturesEnabled = false,
        containerColor = MaterialTheme.colorScheme.background,
        shape = BottomSheetShape,
        dragHandle = { MelodiaDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = MelodiaSpacing.lg, end = MelodiaSpacing.lg, bottom = MelodiaSpacing.lg)
        ) {
            Text(
                text = "调整底栏顺序",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "长按右侧手柄拖动排序；创建入口不参与排序，始终排在最后",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = MelodiaSpacing.xs, bottom = MelodiaSpacing.sm)
            )

            TabOrderReorderList(items = order)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MelodiaSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(MelodiaSpacing.sm)
            ) {
                MelodiaButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text(
                        text = "取消",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                MelodiaButton(
                    onClick = { onSave(order.toList()) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text(
                        text = "完成",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// 三个 tab 数量少、无需滚动，故不做边缘自动滚屏，其余拖拽手感与歌单排序保持一致
@Composable
private fun TabOrderReorderList(items: MutableList<String>) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    val fallbackItemHeightPx = with(density) { 56.dp.toPx() }
    var itemHeightPx by remember { mutableFloatStateOf(fallbackItemHeightPx) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0 }
            .collect { if (it > 0) itemHeightPx = it.toFloat() }
    }

    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var settlingIndex by remember { mutableIntStateOf(-1) }
    val settleOffset = remember { Animatable(0f) }

    fun clampDragOffset(offset: Float): Float = when {
        draggedIndex <= 0 && offset < 0f -> maxOf(offset, -itemHeightPx)
        draggedIndex >= items.lastIndex && offset > 0f -> minOf(offset, itemHeightPx)
        else -> offset
    }

    fun swap(from: Int, to: Int) {
        val tmp = items[from]
        items[from] = items[to]
        items[to] = tmp
    }

    fun advanceSwaps() {
        var swapped = false
        while (dragOffset > itemHeightPx && draggedIndex in 0 until items.lastIndex) {
            swap(draggedIndex, draggedIndex + 1)
            draggedIndex += 1
            dragOffset = clampDragOffset(dragOffset - itemHeightPx)
            swapped = true
        }
        while (dragOffset < -itemHeightPx && draggedIndex > 0) {
            swap(draggedIndex, draggedIndex - 1)
            draggedIndex -= 1
            dragOffset = clampDragOffset(dragOffset + itemHeightPx)
            swapped = true
        }
        if (swapped) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    LazyColumn(
        state = listState,
        userScrollEnabled = draggedIndex < 0,
        modifier = Modifier.fillMaxWidth()
    ) {
        itemsIndexed(
            items = items,
            key = { _, id -> id }
        ) { index, id ->
            val isDragging = draggedIndex == index
            val isSettling = settlingIndex == index
            Box(modifier = if (isDragging || isSettling) Modifier.zIndex(1f) else Modifier.animateItem()) {
                DraggableTabRow(
                    label = BottomTabCatalog.firstOrNull { it.id == id }?.label ?: id,
                    isDragging = isDragging,
                    dragOffsetY = when {
                        isDragging -> dragOffset
                        isSettling -> settleOffset.value
                        else -> 0f
                    },
                    onDragStart = {
                        if (draggedIndex < 0) {
                            settlingIndex = -1
                            draggedIndex = index
                            dragOffset = 0f
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDrag = { delta ->
                        if (draggedIndex == index) {
                            dragOffset = clampDragOffset(dragOffset + delta)
                            advanceSwaps()
                        }
                    },
                    onDragEnd = {
                        if (draggedIndex == index) {
                            val releasedOffset = dragOffset
                            draggedIndex = -1
                            dragOffset = 0f
                            if (releasedOffset != 0f) {
                                settlingIndex = index
                                scope.launch {
                                    settleOffset.snapTo(releasedOffset)
                                    settleOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                    settlingIndex = -1
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

// 可拖拽的底栏 tab 行：长按右侧手柄开始拖拽，拖动中抬起阴影
@Composable
private fun DraggableTabRow(
    label: String,
    isDragging: Boolean,
    dragOffsetY: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tab_drag_elevation"
    )
    val isLifted = isDragging || dragOffsetY != 0f

    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isLifted) {
                    Modifier
                        .zIndex(1f)
                        .graphicsLayer { translationY = dragOffsetY }
                        .shadow(elevation, RoundedCornerShape(10.dp))
                } else {
                    Modifier
                }
            )
            .background(
                if (isDragging) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .padding(vertical = MelodiaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = "长按拖拽调整顺序",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(44.dp)
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { currentOnDragStart() },
                        onDrag = { change, offset ->
                            change.consume()
                            currentOnDrag(offset.y)
                        },
                        onDragEnd = { currentOnDragEnd() },
                        onDragCancel = { currentOnDragEnd() }
                    )
                }
                .padding(10.dp)
        )
    }
}
