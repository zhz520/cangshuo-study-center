package cn.zhzgo.study.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.jpush.android.api.JPushInterface
import cn.zhzgo.study.data.FcmTokenRequest
import cn.zhzgo.study.data.LoginRequest
import cn.zhzgo.study.data.UserPreferences
import cn.zhzgo.study.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val apiService = RetrofitClient.create(application)
    private val userPreferences = UserPreferences(application)

    private val _username = MutableStateFlow("")
    val username = _username.asStateFlow()

    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _isForgotPasswordMode = MutableStateFlow(false)
    val isForgotPasswordMode = _isForgotPasswordMode.asStateFlow()

    private val _forgotEmail = MutableStateFlow("")
    val forgotEmail = _forgotEmail.asStateFlow()

    private val _forgotCode = MutableStateFlow("")
    val forgotCode = _forgotCode.asStateFlow()

    private val _forgotNewPassword = MutableStateFlow("")
    val forgotNewPassword = _forgotNewPassword.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError = _loginError.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn = _isLoggedIn.asStateFlow()

    private val _needBindQQ = MutableStateFlow<String?>(null)
    val needBindQQ = _needBindQQ.asStateFlow()
    var pendingQqBindToken: String? = null
        private set

    // Field-level validation hints
    private val _usernameError = MutableStateFlow<String?>(null)
    val usernameError = _usernameError.asStateFlow()

    private val _passwordError = MutableStateFlow<String?>(null)
    val passwordError = _passwordError.asStateFlow()

    private val _forgotError = MutableStateFlow<String?>(null)
    val forgotError = _forgotError.asStateFlow()
    
    // Success message for Snackbar
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage = _successMessage.asStateFlow()

    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    fun setForgotPasswordMode(isForgot: Boolean) {
        _isForgotPasswordMode.value = isForgot
        _forgotError.value = null
        _loginError.value = null
    }

    fun onForgotEmailChange(newValue: String) {
        _forgotEmail.value = newValue
        _forgotError.value = null
    }

    fun onForgotCodeChange(newValue: String) {
        _forgotCode.value = newValue
        _forgotError.value = null
    }

    fun onForgotNewPasswordChange(newValue: String) {
        _forgotNewPassword.value = newValue
        _forgotError.value = null
    }

    fun resetBindQQ() {
        _needBindQQ.value = null
    }

    fun onUsernameChange(newValue: String) {
        _username.value = newValue
        _usernameError.value = null
        _loginError.value = null
    }

    fun onPasswordChange(newValue: String) {
        _password.value = newValue
        _passwordError.value = null
        _loginError.value = null
    }

    private fun validateInputs(): Boolean {
        var valid = true
        if (_username.value.isBlank()) {
            _usernameError.value = "请输入用户名"
            valid = false
        } else if (_username.value.length < 2) {
            _usernameError.value = "用户名至少2个字符"
            valid = false
        }
        if (_password.value.isBlank()) {
            _passwordError.value = "请输入密码"
            valid = false
        } else if (_password.value.length < 6) {
            _passwordError.value = "密码至少6位"
            valid = false
        }
        return valid
    }

    fun login(geetestData: cn.zhzgo.study.utils.GeetestResult) {
        if (!validateInputs()) return

        viewModelScope.launch {
            _isLoading.value = true
            _loginError.value = null
            try {
                val request = LoginRequest(
                    username = _username.value,
                    password = _password.value,
                    lot_number = geetestData.lotNumber,
                    captcha_output = geetestData.captchaOutput,
                    pass_token = geetestData.passToken,
                    gen_time = geetestData.genTime,
                    client_type = geetestData.clientType
                )
                val response = apiService.login(request)
                userPreferences.saveAuthToken(response.token)
                userPreferences.saveRefreshToken(response.refreshToken)
                userPreferences.saveUser(response.user)
                _isLoggedIn.value = true
                
                // Sync Push Token
                val rid = JPushInterface.getRegistrationID(getApplication())
                if (!rid.isNullOrEmpty()) {
                    try {
                        apiService.postFcmToken(FcmTokenRequest(rid))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: retrofit2.HttpException) {
                val code = e.code()
                _loginError.value = when (code) {
                    401 -> "用户名或密码错误，请重新输入"
                    403 -> "账号已被禁用，请联系管理员"
                    404 -> "账号不存在，请先注册"
                    429 -> "登录尝试过于频繁，请稍后再试"
                    500 -> "服务器异常，请稍后重试"
                    else -> "登录失败 ($code)，请稍后重试"
                }
            } catch (e: java.net.UnknownHostException) {
                _loginError.value = "无法连接到服务器，请检查网络"
            } catch (e: java.net.SocketTimeoutException) {
                _loginError.value = "连接超时，请检查网络后重试"
            } catch (e: Exception) {
                _loginError.value = "登录失败：${e.localizedMessage ?: "未知错误"}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun qqLogin(openId: String, accessToken: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _loginError.value = null
            try {
                val response = apiService.qqLogin(cn.zhzgo.study.data.QQLoginRequest(openId, accessToken))
                if (response.isSuccessful) {
                    if (response.code() == 202) {
                        // Needs to bind
                        pendingQqBindToken = response.body()?.bind_token
                        if (pendingQqBindToken != null) _needBindQQ.value = openId
                        else _loginError.value = "QQ 授权响应无效，请重新授权"
                    } else {
                        val body = response.body()
                        if (body?.token != null) {
                            userPreferences.saveAuthToken(body.token)
                            userPreferences.saveRefreshToken(body.refreshToken ?: "")
                            body.user?.let { userPreferences.saveUser(it) }
                            _isLoggedIn.value = true
                            
                            // Sync Push Token
                            val rid = JPushInterface.getRegistrationID(getApplication())
                            if (!rid.isNullOrEmpty()) {
                                try {
                                    apiService.postFcmToken(FcmTokenRequest(rid))
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        } else {
                            _loginError.value = "QQ 登录失败，服务器返回异常"
                        }
                    }
                } else {
                    _loginError.value = when (response.code()) {
                        401 -> "QQ 授权已过期，请重新授权"
                        403 -> "该 QQ 账号已被禁用"
                        else -> "QQ 登录失败，请稍后重试"
                    }
                }
            } catch (e: java.net.UnknownHostException) {
                _loginError.value = "无法连接到服务器，请检查网络"
            } catch (e: Exception) {
                _loginError.value = "QQ 登录失败：${e.localizedMessage ?: "未知错误"}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendForgotCode(geetestData: cn.zhzgo.study.utils.GeetestResult) {
        if (_forgotEmail.value.isBlank()) {
            _forgotError.value = "请先输入邮箱"
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(_forgotEmail.value).matches()) {
            _forgotError.value = "请输入有效的邮箱"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val request = cn.zhzgo.study.data.SendCodeRequest(
                    email = _forgotEmail.value,
                    lot_number = geetestData.lotNumber,
                    captcha_output = geetestData.captchaOutput,
                    pass_token = geetestData.passToken,
                    gen_time = geetestData.genTime,
                    client_type = geetestData.clientType
                )
                apiService.sendVerificationCode(request)
                _successMessage.value = "验证码已发送"
            } catch (e: retrofit2.HttpException) {
                _forgotError.value = "发送失败: ${e.response()?.errorBody()?.string() ?: "未知错误"}"
            } catch (e: Exception) {
                _forgotError.value = "发送请求失败，请检查网络"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun forgotPassword(geetestData: cn.zhzgo.study.utils.GeetestResult) {
        if (_forgotEmail.value.isBlank() || _forgotCode.value.isBlank() || _forgotNewPassword.value.isBlank()) {
            _forgotError.value = "请填写所有字段"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                apiService.forgotPassword(
                    cn.zhzgo.study.data.ForgotPasswordRequest(
                        email = _forgotEmail.value,
                        code = _forgotCode.value,
                        newPassword = _forgotNewPassword.value,
                        lot_number = geetestData.lotNumber,
                        captcha_output = geetestData.captchaOutput,
                        pass_token = geetestData.passToken,
                        gen_time = geetestData.genTime,
                        client_type = geetestData.clientType
                    )
                )
                _successMessage.value = "密码重置成功，请使用新密码登录"
                _isForgotPasswordMode.value = false
                _username.value = ""
                _password.value = ""
            } catch (e: retrofit2.HttpException) {
                _forgotError.value = "重置失败: ${e.response()?.errorBody()?.string() ?: "未知错误"}"
            } catch (e: Exception) {
                _forgotError.value = "重置失败，请检查网络"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
