package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
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

private enum class LoginMode { CHOICE, QR, COOKIE }

@Composable
fun LoginDialog(
    viewModel: LoginViewModel,
    onLoginSuccess: (cookies: String) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf(LoginMode.CHOICE) }

    // 进入二维码态才开始轮询，离开时停止，覆盖切到 Cookie 或直接关闭弹窗的路径
    LaunchedEffect(mode) {
        if (mode == LoginMode.QR) viewModel.startQrLogin(onLoginSuccess) else viewModel.resetQrState()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.resetQrState() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = DesktopColors.PopupSurface
        ) {
            Column(
                Modifier.width(360.dp).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (mode) {
                    LoginMode.CHOICE -> LoginChoiceContent(
                        onQrLogin = { mode = LoginMode.QR },
                        onCookieLogin = { mode = LoginMode.COOKIE }
                    )
                    LoginMode.QR -> QrLoginContent(
                        viewModel = viewModel,
                        onBack = { mode = LoginMode.CHOICE },
                        onLoginSuccess = onLoginSuccess
                    )
                    LoginMode.COOKIE -> CookieLoginContent(
                        onBack = { mode = LoginMode.CHOICE },
                        onSubmit = { raw -> viewModel.submitCookieLogin(raw, onLoginSuccess) }
                    )
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss) {
                    Text("取消", color = DesktopColors.TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun LoginChoiceContent(onQrLogin: () -> Unit, onCookieLogin: () -> Unit) {
    Text("登录", color = DesktopColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text("登录后享受完整体验", color = DesktopColors.TextGray, fontSize = 13.sp, textAlign = TextAlign.Center)
    Spacer(Modifier.height(20.dp))
    LoginOptionButton("二维码登录", Icons.Rounded.QrCode, primary = true, onClick = onQrLogin)
    Spacer(Modifier.height(10.dp))
    LoginOptionButton("Cookie 登录", Icons.Rounded.ContentPaste, primary = false, onClick = onCookieLogin)
}

@Composable
private fun LoginOptionButton(text: String, icon: ImageVector, primary: Boolean, onClick: () -> Unit) {
    if (primary) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Accent)
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, DesktopColors.SurfaceLight),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DesktopColors.TextPrimary)
        ) {
            Icon(icon, null, tint = DesktopColors.TextGray, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = DesktopColors.TextPrimary, fontSize = 15.sp)
        }
    }
}

@Composable
private fun QrLoginContent(
    viewModel: LoginViewModel,
    onBack: () -> Unit,
    onLoginSuccess: (String) -> Unit
) {
    val state by viewModel.qrState.collectAsState()

    ModeHeader("扫码登录", onBack)
    Text(
        "使用网易云音乐 App 扫描二维码",
        color = DesktopColors.TextGray,
        fontSize = 13.sp,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(16.dp))
    Box(
        Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)).background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        QrContent(state, onRetry = { viewModel.startQrLogin(onLoginSuccess) })
    }
    Spacer(Modifier.height(12.dp))
    Text(statusText(state), color = DesktopColors.TextGray, fontSize = 13.sp)
}

@Composable
private fun CookieLoginContent(onBack: () -> Unit, onSubmit: (String) -> Boolean) {
    var cookieInput by remember { mutableStateOf("") }
    // 校验失败的提示直接内联展示在弹窗里，与移动端保持一致
    var errorText by remember { mutableStateOf<String?>(null) }

    ModeHeader("Cookie 登录", onBack)
    Text(
        "粘贴 Cookie 字符串，或仅粘贴 MUSIC_U 的值",
        color = DesktopColors.TextGray,
        fontSize = 13.sp,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = cookieInput,
        onValueChange = {
            cookieInput = it
            errorText = null
        },
        placeholder = { Text("MUSIC_U=xxxxxx", fontSize = 13.sp) },
        minLines = 3,
        maxLines = 5,
        textStyle = TextStyle(fontSize = 13.sp),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DesktopColors.TextPrimary,
            unfocusedBorderColor = DesktopColors.SurfaceLight,
            cursorColor = DesktopColors.TextPrimary
        ),
        modifier = Modifier.fillMaxWidth()
    )
    if (errorText != null) {
        Spacer(Modifier.height(6.dp))
        Text(
            errorText.orEmpty(),
            color = DesktopColors.Accent,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = {
            if (!onSubmit(cookieInput)) {
                errorText = "格式不正确，请粘贴完整 Cookie 字符串或 MUSIC_U 的值"
            }
        },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Accent)
    ) {
        Text("确认登录", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ModeHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.ArrowBack, "返回", tint = DesktopColors.TextPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text(title, color = DesktopColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
