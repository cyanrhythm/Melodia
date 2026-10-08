package com.lin0721.linmusic.core.auth

import com.lin0721.linmusic.core.network.AppError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val COUNTDOWN_SECONDS = 60
private val MAINLAND_PHONE = Regex("^1\\d{10}$")
private val CAPTCHA = Regex("^\\d{4,6}$")

data class PhoneLoginUiState(
    val isSending: Boolean = false,
    val isSubmitting: Boolean = false,
    // 大于 0 时不能再次获取验证码
    val countdownSeconds: Int = 0,
    val message: String? = null
)

// 手机号 + 验证码登录：发码带 60 秒冷却，提交成功后把登录 Cookie 交给调用方
class PhoneLoginController(
    private val scope: CoroutineScope,
    private val authRepository: AuthRepository
) {

    private val _state = MutableStateFlow(PhoneLoginUiState())
    val state: StateFlow<PhoneLoginUiState> = _state.asStateFlow()

    private var countdownJob: Job? = null

    fun sendCaptcha(phone: String) {
        val current = _state.value
        if (current.isSending || current.countdownSeconds > 0) return
        if (!MAINLAND_PHONE.matches(phone)) {
            _state.update { it.copy(message = "请输入正确的手机号") }
            return
        }
        _state.update { it.copy(isSending = true, message = null) }
        scope.launch {
            authRepository.sendCaptcha(phone).first().fold(
                onSuccess = {
                    _state.update { it.copy(isSending = false, message = "验证码已发送") }
                    startCountdown()
                },
                onFailure = { error ->
                    _state.update { it.copy(isSending = false, message = error.toLoginMessage("验证码发送失败，请稍后再试")) }
                }
            )
        }
    }

    fun submit(phone: String, captcha: String, onLoginSuccess: (String) -> Unit) {
        if (_state.value.isSubmitting) return
        val message = when {
            !MAINLAND_PHONE.matches(phone) -> "请输入正确的手机号"
            !CAPTCHA.matches(captcha) -> "请输入 4 到 6 位数字验证码"
            else -> null
        }
        if (message != null) {
            _state.update { it.copy(message = message) }
            return
        }
        _state.update { it.copy(isSubmitting = true, message = null) }
        scope.launch {
            authRepository.loginByCaptcha(phone, captcha).first().fold(
                onSuccess = { cookies ->
                    _state.update { it.copy(isSubmitting = false) }
                    onLoginSuccess(cookies)
                },
                onFailure = { error ->
                    _state.update { it.copy(isSubmitting = false, message = error.toLoginMessage("登录失败，请重试")) }
                }
            )
        }
    }

    // 弹窗关闭或切换登录方式时重置，下次进来不带上一次的倒计时与提示
    fun reset() {
        countdownJob?.cancel()
        countdownJob = null
        _state.value = PhoneLoginUiState()
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            for (remaining in COUNTDOWN_SECONDS downTo 1) {
                _state.update { it.copy(countdownSeconds = remaining) }
                delay(1_000)
            }
            _state.update { it.copy(countdownSeconds = 0) }
        }
    }

    private fun Throwable.toLoginMessage(default: String): String = when (this) {
        is AppError.BizError -> rawMsg?.takeIf { it.isNotBlank() } ?: default
        AppError.NetworkError -> "网络异常，请检查网络后重试"
        AppError.RiskControl -> "请求被拦截，请稍后再试"
        else -> default
    }
}
