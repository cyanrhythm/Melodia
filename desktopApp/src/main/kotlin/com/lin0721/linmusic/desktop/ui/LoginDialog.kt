package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.zxing.common.BitMatrix
import com.lin0721.linmusic.core.auth.LoginViewModel
import com.lin0721.linmusic.core.auth.QrLoginState
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import java.awt.image.BufferedImage

@Composable
fun LoginDialog(
    viewModel: LoginViewModel,
    onLoginSuccess: (cookies: String) -> Unit,
    onDismiss: () -> Unit
) {
    val state by viewModel.qrState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startQrLogin(onLoginSuccess)
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.resetQrState() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = DesktopColors.Surface
        ) {
            Column(
                Modifier.width(360.dp).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("扫码登录", color = DesktopColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "使用网易云音乐 App 扫描二维码",
                    color = DesktopColors.TextGray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)).background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    QrContent(state, onRetry = { viewModel.startQrLogin(onLoginSuccess) })
                }
                Spacer(Modifier.height(16.dp))
                Text(statusText(state), color = DesktopColors.TextGray, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("取消", color = DesktopColors.TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun QrContent(state: QrLoginState, onRetry: () -> Unit) {
    when (state) {
        QrLoginState.Idle, QrLoginState.Loading -> CircularProgressIndicator(color = DesktopColors.Accent)
        is QrLoginState.WaitingScan -> QrImage(state.qrMatrix, dimmed = false)
        is QrLoginState.WaitingConfirm -> Box(contentAlignment = Alignment.Center) {
            QrImage(state.qrMatrix, dimmed = true)
            CircularProgressIndicator(color = DesktopColors.Accent)
        }
        QrLoginState.Expired, is QrLoginState.Error -> TextButton(onClick = onRetry) {
            Text("刷新二维码", color = DesktopColors.Accent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QrImage(matrix: BitMatrix, dimmed: Boolean) {
    val image = remember(matrix) { matrix.toImageBitmap() }
    Image(
        bitmap = image,
        contentDescription = "登录二维码",
        modifier = Modifier.fillMaxSize().padding(12.dp).alpha(if (dimmed) 0.3f else 1f)
    )
}

private fun statusText(state: QrLoginState): String = when (state) {
    QrLoginState.Idle, QrLoginState.Loading -> "正在生成二维码"
    is QrLoginState.WaitingScan -> "等待扫码"
    is QrLoginState.WaitingConfirm -> "已扫码，请在手机上确认登录"
    QrLoginState.Expired -> "二维码已过期"
    is QrLoginState.Error -> state.message
}

private fun BitMatrix.toImageBitmap(): ImageBitmap {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until height) {
        for (x in 0 until width) {
            image.setRGB(x, y, if (get(x, y)) 0x000000 else 0xFFFFFF)
        }
    }
    return image.toComposeImageBitmap()
}
