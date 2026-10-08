package com.lin0721.linmusic.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.comment.data.CommentSortType
import com.lin0721.linmusic.core.comment.domain.CommentFloorState
import com.lin0721.linmusic.core.comment.ui.CommentsState
import com.lin0721.linmusic.core.model.CommentItem
import com.lin0721.linmusic.desktop.ui.nowplaying.CommentInputBar
import com.lin0721.linmusic.desktop.ui.nowplaying.CommentRow
import com.lin0721.linmusic.desktop.ui.nowplaying.allComments
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel

private const val FLOOR_FADE_MS = 150

// 距列表末尾还剩这么多条时开始加载下一页
private const val LOAD_MORE_THRESHOLD = 3

private val SortTypes = listOf(CommentSortType.RECOMMEND, CommentSortType.HOT, CommentSortType.LATEST)

private fun sortLabel(type: CommentSortType): String = when (type) {
    CommentSortType.RECOMMEND -> "推荐"
    CommentSortType.HOT -> "最热"
    CommentSortType.LATEST -> "最新"
}

// 右侧栏的完整评论：排序、评论流与加载更多
@Composable
fun CommentsPanel(
    playerViewModel: PlayerViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navigator = LocalDesktopNavigator.current
    val state by playerViewModel.commentsState.collectAsState()
    val composerState by playerViewModel.composerState.collectAsState()
    val floorState by playerViewModel.floorState.collectAsState()
    var floorOwner by remember { mutableStateOf<CommentItem?>(null) }
    val currentUserId = playerViewModel.userProfile.collectAsState().value?.uid
    val total = state.totalCount ?: 0
    var replyTarget by remember { mutableStateOf<CommentItem?>(null) }
    var deleteTarget by remember { mutableStateOf<CommentItem?>(null) }
    val focusRequester = remember { FocusRequester() }
    val requireLogin = { navigator.showMessage("请先登录账号") }
    // 面板撤掉时一并收起楼层，下次打开回到评论列表
    DisposableEffect(Unit) { onDispose { playerViewModel.closeCommentFloor() } }
    val startReply: (CommentItem) -> Unit = { comment ->
        if (currentUserId == null) {
            requireLogin()
        } else {
            replyTarget = comment
            focusRequester.requestFocus()
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = 16.dp)) {
            OverlayPanelHeader(
                title = if (total > 0) "评论 ($total)" else "评论",
                closeDescription = "关闭评论",
                onClose = onClose,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            // 排序栏常驻顶部，不受内容区加载态影响
            TabBar(
                tabs = SortTypes,
                selected = state.sortType,
                label = ::sortLabel,
                onSelect = playerViewModel::changeCommentSort,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                small = true
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val current = state) {
                    is CommentsState.Loading -> CenteredSpinner()
                    is CommentsState.Error -> Column(
                        Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("加载失败: ${current.message}", color = DesktopColors.TextGray, fontSize = 14.sp, textAlign = TextAlign.Center)
                        Button(
                            onClick = playerViewModel::retryComments,
                            colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Accent),
                            modifier = Modifier.padding(top = 12.dp)
                        ) { Text("重试", color = DesktopColors.TextPrimary) }
                    }
                    is CommentsState.Success -> CommentList(
                        state = current,
                        playerViewModel = playerViewModel,
                        currentUserId = currentUserId,
                        onReply = startReply,
                        onExpandFloor = { comment ->
                            floorOwner = comment
                            playerViewModel.openCommentFloor(comment)
                        },
                        onDelete = { comment -> deleteTarget = comment }
                    )
                }
            }
            // 输入栏常驻底部，不因中间内容状态消失
            CommentInputBar(
                replyTarget = replyTarget,
                composerState = composerState,
                focusRequester = focusRequester,
                onClearReplyTarget = { replyTarget = null },
                onSubmit = { content ->
                    if (currentUserId == null) {
                        requireLogin()
                    } else {
                        val target = replyTarget
                        if (target != null) {
                            playerViewModel.submitCommentReply(target.commentId, content)
                        } else {
                            playerViewModel.submitComment(content)
                        }
                        replyTarget = null
                    }
                }
            )
        }
        // 楼层详情盖在评论列表之上，不透明底并吞掉点击，避免操作穿透到下层
        AnimatedVisibility(
            visible = floorState !is CommentFloorState.Idle,
            enter = fadeIn(tween(FLOOR_FADE_MS)),
            exit = fadeOut(tween(FLOOR_FADE_MS))
        ) {
            CommentFloorPanel(
                floorState = floorState,
                owner = floorOwner,
                composerState = composerState,
                currentUserId = currentUserId,
                playerViewModel = playerViewModel,
                onRequestDelete = { comment -> deleteTarget = comment },
                modifier = Modifier.background(DesktopColors.Pane).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
            )
        }
    }

    deleteTarget?.let { comment ->
        DeleteCommentDialog(
            onConfirm = {
                playerViewModel.deleteCommentItem(comment)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun DeleteCommentDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = AlertDialogDefaults.shape,
        containerColor = DesktopColors.PopupSurface,
        title = { Text("删除评论", color = DesktopColors.TextPrimary) },
        text = { Text("确定要删除这条评论吗？删除后无法恢复。", color = DesktopColors.TextGray, fontSize = 13.sp) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("删除", color = DesktopColors.Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = DesktopColors.TextGray) } }
    )
}

@Composable
private fun CommentList(
    state: CommentsState.Success,
    playerViewModel: PlayerViewModel,
    currentUserId: Long?,
    onReply: (CommentItem) -> Unit,
    onExpandFloor: (CommentItem) -> Unit,
    onDelete: (CommentItem) -> Unit
) {
    val comments = remember(state.hotComments, state.comments) { allComments(state) }
    if (comments.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无评论", color = DesktopColors.TextGray, fontSize = 14.sp)
        }
        return
    }

    val listState = rememberLazyListState()
    val nearEnd by remember(listState, comments.size) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= comments.size - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd, state.hasMore, state.isLoadingMore) {
        if (nearEnd && state.hasMore && !state.isLoadingMore) playerViewModel.loadMoreComments()
    }

    HoverScrollbarBox(listState) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp)
        ) {
            items(comments, key = { it.commentId }) { comment ->
                CommentRow(
                    comment = comment,
                    onLike = { playerViewModel.likeComment(comment) },
                    onReply = { onReply(comment) },
                    onExpandFloor = { onExpandFloor(comment) },
                    onDelete = if (comment.user.userId == currentUserId) ({ onDelete(comment) }) else null
                )
            }
            if (state.isLoadingMore) {
                item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { Spinner(20) } }
            }
        }
    }
}

@Composable
private fun CenteredSpinner() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(24) }
}

@Composable
private fun Spinner(size: Int) {
    CircularProgressIndicator(color = DesktopColors.Accent, modifier = Modifier.size(size.dp), strokeWidth = 2.dp)
}
