package com.lin0721.linmusic.core.auth

import com.lin0721.linmusic.core.api.AccountInfoResponse
import com.lin0721.linmusic.core.api.CaptchaSentRequest
import com.lin0721.linmusic.core.api.CellphoneLoginRequest
import com.lin0721.linmusic.core.api.NeteaseApiService
import com.lin0721.linmusic.core.api.QrCheckRequest
import com.lin0721.linmusic.core.api.QrCheckResponse
import com.lin0721.linmusic.core.api.QrKeyResponse
import com.lin0721.linmusic.core.cache.MetadataCache
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.core.network.mapToAppError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion

private const val TAG = "AuthRepositoryImpl"

class AuthRepositoryImpl(
    private val apiService: NeteaseApiService,
    private val metadataCache: MetadataCache
) : AuthRepository {

    override fun getAccountInfo(): Flow<Result<AccountInfoResponse>> = apiFlow(
        request = { apiService.getAccountInfo() },
        isSuccess = { it.code == 200 },
        code = { it.code },
        transform = { it }
    )

    // 调用方无论服务端是否登出成功都会清除本地登录态，离线缓存同步清空
    override fun logout(): Flow<Result<Unit>> = apiFlow(
        request = { apiService.logoutApi() },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { Unit }
    ).onCompletion { metadataCache.clearAll() }

    override fun getQrKey(): Flow<Result<QrKeyResponse>> = apiFlow(
        request = { apiService.getQrKey() },
        isSuccess = { it.code == 200 },
        code = { it.code },
        transform = { it }
    )

    // 800/801/802/803 都是正常的轮询状态，不当业务错误处理；只有网络/解析异常才走 failure
    override fun sendCaptcha(phone: String): Flow<Result<Unit>> = apiFlow(
        request = { apiService.sendCaptcha(CaptchaSentRequest(cellphone = phone)) },
        isSuccess = { it.code == 200 },
        code = { it.code },
        msg = { it.message },
        transform = { Unit }
    )

    override fun loginByCaptcha(phone: String, captcha: String): Flow<Result<String>> = flow {
        val response = apiService.loginCellphone(CellphoneLoginRequest(phone = phone, captcha = captcha))
        val body = response.body()
        when {
            body == null -> emit(Result.failure(AppError.NetworkError))
            body.code != 200 -> {
                AppLogger.w(TAG, "手机验证码登录失败 code=${body.code} message=${body.message}")
                emit(Result.failure(AppError.BizError(body.code, body.message)))
            }
            else -> {
                val cookies = mergeCookies(null, response.headers().values("Set-Cookie"))
                if (cookies.isEmpty()) {
                    AppLogger.e(TAG, "手机验证码登录返回 200 但未解析到 Set-Cookie")
                    emit(Result.failure(AppError.BizError(body.code, "登录成功但未获取到会话，请重试")))
                } else {
                    emit(Result.success(cookies))
                }
            }
        }
    }.catch { e ->
        AppLogger.e(TAG, "手机验证码登录异常: ${e::class.simpleName}", e)
        emit(Result.failure(mapToAppError(e)))
    }

    override fun refreshLogin(): Flow<Result<List<String>>> = flow {
        val response = apiService.refreshLogin()
        val body = response.body()
        when {
            body == null -> emit(Result.failure(AppError.NetworkError))
            body.code == 301 -> emit(Result.failure(AppError.Unauthorized))
            body.code != 200 -> emit(Result.failure(AppError.BizError(body.code, body.message)))
            else -> emit(Result.success(response.headers().values("Set-Cookie")))
        }
    }.catch { e ->
        AppLogger.e(TAG, "登录刷新异常: ${e::class.simpleName}", e)
        emit(Result.failure(mapToAppError(e)))
    }

    override fun checkQrStatus(key: String): Flow<Result<QrCheckResponse>> = flow {
        val response = apiService.checkQrStatus(QrCheckRequest(key = key))
        val body = response.body()
        if (body == null) {
            AppLogger.e(TAG, "二维码状态查询响应体为空，httpCode=${response.code()}")
            emit(Result.failure(AppError.NetworkError))
        } else {
            val cookies = response.headers().values("Set-Cookie")
                .map { it.substringBefore(";").trim() }
                .filter { it.contains("=") }
                .joinToString("; ")
                .takeIf { it.isNotEmpty() }
            emit(Result.success(body.copy(cookies = cookies)))
        }
    }.catch { e ->
        AppLogger.e(TAG, "二维码状态查询异常: ${e::class.simpleName}", e)
        emit(Result.failure(mapToAppError(e)))
    }
}
