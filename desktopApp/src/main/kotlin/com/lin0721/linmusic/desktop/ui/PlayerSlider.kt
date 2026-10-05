package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import kotlin.math.roundToInt

private val TrackHeight = 4.dp
private val ThumbSize = 12.dp
private val TouchHeight = 16.dp
private val PreviewGap = 8.dp

// 进度与音量共用的细轨道滑块：已播放部分平时为白色，悬停或拖动时变主题色并显示圆形滑块。
// 传入 previewLabel 时，悬停位置上方显示该位置的提示，且轨道上从当前进度到悬停位置铺一段浅色预览
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun PlayerSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    previewLabel: ((fraction: Float) -> String)? = null
) {
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val dragged by interaction.collectIsDraggedAsState()
    val active = enabled && (hovered || dragged)

    var widthPx by remember { mutableStateOf(0) }
    var hoverX by remember { mutableStateOf<Float?>(null) }
    // 与 Slider 点击取值同一换算：轨道两端各内缩半个滑块
    val thumbPx = with(density) { ThumbSize.toPx() }
    val hoverFraction = hoverX?.takeIf { widthPx > thumbPx }
        ?.let { ((it - thumbPx / 2) / (widthPx - thumbPx)).coerceIn(0f, 1f) }
    val previewing = previewLabel != null && enabled && hoverFraction != null

    Box(
        modifier.height(TouchHeight).onSizeChanged { widthPx = it.width }
            .onPointerEvent(PointerEventType.Enter) { hoverX = it.changes.firstOrNull()?.position?.x }
            .onPointerEvent(PointerEventType.Move) { hoverX = it.changes.firstOrNull()?.position?.x }
            .onPointerEvent(PointerEventType.Exit) { hoverX = null },
        // Slider 自带最小触控高度，比这里的容器高；居中放置才能让轨道与滑块落在容器正中
        contentAlignment = Alignment.Center
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().hoverable(interaction),
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
            interactionSource = interaction,
            thumb = {
                // 未激活时保留同尺寸占位，避免滑块出现瞬间轨道两端位置跳动
                Box(Modifier.size(ThumbSize).then(if (active) Modifier.clip(CircleShape).background(DesktopColors.TextPrimary) else Modifier))
            },
            track = { state -> PlayerTrack(state, active, enabled, if (previewing) hoverFraction else null) }
        )
        if (previewing && previewLabel != null && hoverFraction != null) {
            val x = hoverX ?: 0f
            // 零尺寸锚点：气泡以指针为中心悬在轨道上方，不占布局空间
            Box(
                Modifier.align(Alignment.TopStart).layout { measurable, _ ->
                    val placeable = measurable.measure(Constraints())
                    layout(0, 0) { placeable.place(x.roundToInt() - placeable.width / 2, -placeable.height - PreviewGap.roundToPx()) }
                }
            ) { TooltipLabel(previewLabel(hoverFraction)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerTrack(state: SliderState, active: Boolean, enabled: Boolean, previewFraction: Float?) {
    val range = state.valueRange
    val fraction = ((state.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    val shape = RoundedCornerShape(TrackHeight / 2)
    // 轨道槽与滑块等高，细轨道在槽内居中；Slider 对两者的纵向定位公式不同，等高才能保证中线重合
    Box(Modifier.fillMaxWidth().height(ThumbSize), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth().height(TrackHeight).clip(shape).background(DesktopColors.SurfaceLight),
            contentAlignment = Alignment.CenterStart
        ) {
            if (previewFraction != null && previewFraction > fraction) {
                Box(Modifier.fillMaxWidth(previewFraction).height(TrackHeight).background(DesktopColors.TextGray.copy(alpha = 0.6f)))
            }
            Box(
                Modifier.fillMaxWidth(fraction).height(TrackHeight).background(
                    when {
                        !enabled -> DesktopColors.TextGray
                        active -> DesktopColors.Accent
                        else -> DesktopColors.TextPrimary
                    }
                )
            )
        }
    }
}
