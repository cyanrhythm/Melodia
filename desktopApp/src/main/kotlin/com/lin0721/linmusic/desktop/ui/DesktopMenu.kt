package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

private val MenuItemHeight = 34.dp
private val MenuShape = RoundedCornerShape(6.dp)
private val ItemShape = RoundedCornerShape(4.dp)
private const val DISABLED_ALPHA = 0.4f

val DefaultMenuWidth = 188.dp
val DefaultSubMenuWidth = 224.dp

// 二级菜单的内容槽：内容用 State 持有，调用方重组后展示的始终是最新内容
internal class SubMenuSlot(val width: Dp, val content: State<@Composable () -> Unit>)

@Stable
class MenuScope internal constructor() {
    internal var activeSub by mutableStateOf<SubMenuSlot?>(null)
}

// 统一的紧凑菜单：34dp 行高、16dp 图标；二级菜单与主菜单同属一个弹层，悬停条目时在右侧展开
@Composable
fun DesktopMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    width: Dp = DefaultMenuWidth,
    content: @Composable MenuScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
        offset = offset,
        shape = MenuShape,
        containerColor = DesktopColors.PopupSurface,
        shadowElevation = 12.dp
    ) {
        val scope = remember { MenuScope() }
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.width(width).padding(horizontal = 4.dp)) { scope.content() }
            scope.activeSub?.let { slot ->
                Column(Modifier.width(slot.width).padding(start = 4.dp, end = 4.dp)) { slot.content.value() }
            }
        }
    }
}

@Composable
fun MenuScope.MenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) activeSub = null }
    MenuRow(text, icon, danger, enabled, hovered, source, onClick, modifier, trailing)
}

@Composable
fun MenuScope.SubMenuItem(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    width: Dp = DefaultSubMenuWidth,
    onOpen: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val contentState = rememberUpdatedState(content)
    val slot = remember { SubMenuSlot(width, contentState) }
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val open = {
        if (activeSub !== slot) {
            activeSub = slot
            onOpen()
        }
    }
    LaunchedEffect(hovered) { if (hovered) open() }
    MenuRow(
        text = text,
        icon = icon,
        danger = false,
        enabled = true,
        highlighted = hovered || activeSub === slot,
        source = source,
        onClick = open,
        modifier = modifier,
        trailing = {
            Icon(Icons.Rounded.ChevronRight, null, tint = DesktopColors.TextGray, modifier = Modifier.size(16.dp))
        }
    )
}

@Composable
fun MenuDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp).height(1.dp).background(DesktopColors.SurfaceLight.copy(alpha = 0.7f)))
}

@Composable
private fun MenuRow(
    text: String,
    icon: ImageVector?,
    danger: Boolean,
    enabled: Boolean,
    highlighted: Boolean,
    source: MutableInteractionSource,
    onClick: () -> Unit,
    modifier: Modifier,
    trailing: (@Composable () -> Unit)?
) {
    val color = if (danger) DangerColor else DesktopColors.TextPrimary
    Row(
        modifier.fillMaxWidth().height(MenuItemHeight).clip(ItemShape)
            .background(if (highlighted && enabled) DesktopColors.SurfaceLight else Color.Transparent)
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = color, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

// 无需 MenuScope 的条目：二级菜单内容、音乐库的弹出菜单等场景使用
@Composable
fun SimpleMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    danger: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    MenuRow(text, icon, danger, enabled, hovered || selected, source, onClick, modifier, trailing)
}
