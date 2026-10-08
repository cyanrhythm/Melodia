package com.lin0721.linmusic.core.auth

import com.lin0721.linmusic.core.network.AppError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneLoginControllerTest {

    private val phone = "13800138000"

    @Test
    fun invalidPhoneDoesNotCallServer() = runTest {
        val repo = FakeAuthRepository()
        val controller = PhoneLoginController(this, repo)
        controller.sendCaptcha("12345")
        runCurrent()
        assertEquals("请输入正确的手机号", controller.state.value.message)
        assertTrue(repo.sentPhones.isEmpty())
    }

    @Test
    fun sendSuccessStartsCountdownAndBlocksResend() = runTest {
        val repo = FakeAuthRepository()
        val controller = PhoneLoginController(this, repo)
        controller.sendCaptcha(phone)
        runCurrent()
        assertEquals("验证码已发送", controller.state.value.message)
        assertEquals(60, controller.state.value.countdownSeconds)
        controller.sendCaptcha(phone)
        runCurrent()
        assertEquals(listOf(phone), repo.sentPhones)
        advanceTimeBy(30_001)
        assertEquals(30, controller.state.value.countdownSeconds)
        controller.reset()
    }

    @Test
    fun countdownEndsAndAllowsResend() = runTest {
        val repo = FakeAuthRepository()
        val controller = PhoneLoginController(this, repo)
        controller.sendCaptcha(phone)
        advanceTimeBy(61_000)
        runCurrent()
        assertEquals(0, controller.state.value.countdownSeconds)
        controller.sendCaptcha(phone)
        runCurrent()
        assertEquals(2, repo.sentPhones.size)
        controller.reset()
    }

    @Test
    fun sendFailureShowsServerMessageWithoutCountdown() = runTest {
        val repo = FakeAuthRepository().apply { sendCaptchaResult = Result.failure(AppError.BizError(400, "手机号码不符合规范")) }
        val controller = PhoneLoginController(this, repo)
        controller.sendCaptcha(phone)
        runCurrent()
        assertEquals("手机号码不符合规范", controller.state.value.message)
        assertEquals(0, controller.state.value.countdownSeconds)
        assertTrue(!controller.state.value.isSending)
    }

    @Test
    fun networkFailureUsesFriendlyMessage() = runTest {
        val repo = FakeAuthRepository().apply { sendCaptchaResult = Result.failure(AppError.NetworkError) }
        val controller = PhoneLoginController(this, repo)
        controller.sendCaptcha(phone)
        runCurrent()
        assertEquals("网络异常，请检查网络后重试", controller.state.value.message)
    }

    @Test
    fun submitValidatesCaptchaFormat() = runTest {
        val repo = FakeAuthRepository()
        val controller = PhoneLoginController(this, repo)
        controller.submit(phone, "12", onLoginSuccess = {})
        runCurrent()
        assertEquals("请输入 4 到 6 位数字验证码", controller.state.value.message)
        assertTrue(repo.loginAttempts.isEmpty())
    }

    @Test
    fun submitSuccessDeliversCookies() = runTest {
        val repo = FakeAuthRepository().apply { loginResult = Result.success("MUSIC_U=token") }
        val controller = PhoneLoginController(this, repo)
        var delivered: String? = null
        controller.submit(phone, "1234") { delivered = it }
        runCurrent()
        assertEquals("MUSIC_U=token", delivered)
        assertEquals(listOf(phone to "1234"), repo.loginAttempts)
        assertTrue(!controller.state.value.isSubmitting)
    }

    @Test
    fun submitFailureShowsMessageAndSkipsCallback() = runTest {
        val repo = FakeAuthRepository().apply { loginResult = Result.failure(AppError.BizError(503, "验证码错误")) }
        val controller = PhoneLoginController(this, repo)
        var delivered: String? = null
        controller.submit(phone, "0000") { delivered = it }
        runCurrent()
        assertNull(delivered)
        assertEquals("验证码错误", controller.state.value.message)
    }

    @Test
    fun resetClearsState() = runTest {
        val repo = FakeAuthRepository()
        val controller = PhoneLoginController(this, repo)
        controller.sendCaptcha(phone)
        runCurrent()
        controller.reset()
        assertEquals(PhoneLoginUiState(), controller.state.value)
    }
}
