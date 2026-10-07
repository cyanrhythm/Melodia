package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.positionChange
import java.awt.HeadlessException
import java.awt.MouseInfo
import java.awt.Window
import kotlinx.coroutines.withTimeoutOrNull

// 拖动期间指针是否已在活动窗口之外；窗口外没有指针事件，边缘自动滚动不能再按最后一次位置空转
internal fun isPointerOutsideActiveWindow(): Boolean {
    val pointer = try {
        MouseInfo.getPointerInfo()?.location
    } catch (_: HeadlessException) {
        null
    } ?: return false
    val window = Window.getWindows().firstOrNull { it.isActive } ?: return false
    return !window.bounds.contains(pointer)
}

// 长按后纵向拖动。鼠标在窗口外松开时收不到抬起事件，Compose 仍认为按键按着，
// 所以回到窗口后的第一次移动若已没有主键按下，先把这段位移跟上再当作松手结束，
// 让行落在指针当前所在的位置
suspend fun PointerInputScope.detectLongPressReorder(
    onDragStart: () -> Unit,
    onDrag: (deltaY: Float) -> Unit,
    onDragEnd: () -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        val longPressed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed || change.isConsumed ||
                    (change.position - down.position).getDistance() > slop
                ) {
                    return@withTimeoutOrNull
                }
            }
        } == null
        if (!longPressed) return@awaitEachGesture

        onDragStart()
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val deltaY = change.positionChange().y
                if (deltaY != 0f) {
                    change.consume()
                    onDrag(deltaY)
                }
                if (!change.pressed || !event.buttons.isPrimaryPressed) break
            }
        } finally {
            onDragEnd()
        }
    }
}
