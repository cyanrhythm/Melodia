package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors

// TooltipArea 统一使用的提示气泡
@Composable
fun TooltipLabel(text: String) {
    Text(
        text,
        color = DesktopColors.TextPrimary,
        fontSize = 12.sp,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(DesktopColors.SurfaceLight)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
