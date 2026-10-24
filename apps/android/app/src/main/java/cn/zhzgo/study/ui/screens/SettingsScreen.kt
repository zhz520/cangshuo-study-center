package cn.zhzgo.study.ui.screens

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import cn.zhzgo.study.ui.components.AppearanceSheet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.zhzgo.study.data.*
import cn.zhzgo.study.network.RetrofitClient
import cn.zhzgo.study.ui.components.PlusBadge
import cn.zhzgo.study.ui.components.isUserPlus
import cn.zhzgo.study.ui.components.PlusConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ────────────────────────────────────────────────────────────
// ViewModel
// ────────────────────────────────────────────────────────────
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = UserPreferences(application)

    private val _userName = MutableStateFlow("—")
    val userName = _userName.asStateFlow()

    private val _userRole = MutableStateFlow("")
    val userRole = _userRole.asStateFlow()

    private val _avatarIcon = MutableStateFlow("Person")
    val avatarIcon = _avatarIcon.asStateFlow()

    private val _appearance = MutableStateFlow("system")
    val appearance = _appearance.asStateFlow()

    private val _primaryColor = MutableStateFlow("#000000")
    val primaryColor = _primaryColor.asStateFlow()

    private val _email = MutableStateFlow<String?>(null)
    val email = _email.asStateFlow()

    private val _nickname = MutableStateFlow<String?>(null)
    val nickname = _nickname.asStateFlow()

    private val _qqOpenId = MutableStateFlow<String?>(null)
    val qqOpenId = _qqOpenId.asStateFlow()

    private val _qqAvatar = MutableStateFlow<String?>(null)
    val qqAvatar = _qqAvatar.asStateFlow()

    private val _slogan = MutableStateFlow<String?>(null)
    val slogan = _slogan.asStateFlow()

    private val _subscriptionEndDate = MutableStateFlow<String?>(null)
    val subscriptionEndDate = _subscriptionEndDate.asStateFlow()

    init {
        viewModelScope.launch { prefs.userName.collect { _userName.value = it ?: "—" } }
        viewModelScope.launch { prefs.userRole.collect { _userRole.value = it ?: "" } }
        viewModelScope.launch { prefs.avatarIcon.collect { _avatarIcon.value = it } }
        viewModelScope.launch { prefs.appearance.collect { _appearance.value = it } }
        viewModelScope.launch { prefs.primaryColor.collect { _primaryColor.value = it } }
        viewModelScope.launch { prefs.email.collect { _email.value = it } }
        viewModelScope.launch { prefs.nickname.collect { _nickname.value = it } }
        viewModelScope.launch { prefs.qqOpenId.collect { _qqOpenId.value = it } }
        viewModelScope.launch { prefs.qqAvatar.collect { _qqAvatar.value = it } }
        viewModelScope.launch { prefs.slogan.collect { _slogan.value = it } }
        viewModelScope.launch { prefs.subscriptionEndDate.collect { _subscriptionEndDate.value = it } }
    }

    fun setAppearance(mode: String) {
        viewModelScope.launch { prefs.setAppearance(mode) }
    }

    fun setAvatarIcon(iconName: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch { 
            try {
                // Save locally first for instant UI feedback
                prefs.setAvatarIcon(iconName)
                
                // Sync to backend
                val response = RetrofitClient.create(getApplication()).updateAvatar(
                    mapOf("avatar_icon" to iconName)
                )
                onSuccess()
            } catch (e: Exception) {
                // If it fails, we might want to log it but local state is already changed
                onError(e.message ?: "Failed to sync avatar to cloud")
            }
        }
    }

    fun updateProfile(nickname: String? = null, slogan: String? = null, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.create(getApplication()).updateProfile(
                    UpdateProfileRequest(nickname, slogan)
                )
                if (nickname != null) {
                    prefs.saveNickname(nickname)
                }
                if (slogan != null) {
                    prefs.saveSlogan(slogan)
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "更新失败")
            }
        }
    }

    fun syncFromQQ(onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val qAvatar = prefs.qqAvatar.first()
                val qNickname = prefs.nickname.first() // This was saved when binding QQ

                if (qAvatar != null) {
                    setAvatarIcon(qAvatar)
                }
                if (qNickname != null) {
                    updateProfile(nickname = qNickname)
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "同步失败")
            }
        }
    }

    fun setPrimaryColorHex(hex: String) {
        viewModelScope.launch { prefs.setPrimaryColor(hex) }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val refreshToken = prefs.refreshToken.first()
                if (!refreshToken.isNullOrEmpty()) {
                    RetrofitClient.create(getApplication()).logout(
                        cn.zhzgo.study.data.LogoutRequest(refreshToken)
                    )
                }
            } catch (_: Exception) {
                // Even if backend logout fails, still clear local state
            }
            prefs.clearAuthToken()
            onDone()
        }
    }

    fun changePassword(oldPw: String, newPw: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val regex = "^(?=.*[a-zA-Z])(?=.*\\d).{8,}\$".toRegex()
        if (!regex.matches(newPw)) {
            onError("新密码长度至少8位，且必须包含字母和数字")
            return
        }
        
        viewModelScope.launch {
            try {
                RetrofitClient.create(getApplication()).changePassword(
                    mapOf("oldPassword" to oldPw, "newPassword" to newPw)
                )
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "修改失败")
            }
        }
    }

    fun sendCode(targetEmail: String, gtResult: cn.zhzgo.study.utils.GeetestResult, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                RetrofitClient.create(getApplication()).sendVerificationCode(
                    cn.zhzgo.study.data.SendCodeRequest(
                        email = targetEmail,
                        lot_number = gtResult.lotNumber,
                        captcha_output = gtResult.captchaOutput,
                        pass_token = gtResult.passToken,
                        gen_time = gtResult.genTime,
                        client_type = gtResult.clientType
                    )
                )
                onSuccess()
            } catch (e: retrofit2.HttpException) {
                onError("发送失败: ${e.response()?.errorBody()?.string() ?: "未知错误"}")
            } catch (e: Exception) {
                onError("发送失败，请检查网络")
            }
        }
    }

    fun changeEmail(newEmail: String, code: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                RetrofitClient.create(getApplication()).changeEmail(cn.zhzgo.study.data.ChangeEmailRequest(newEmail, code))
                // Email changed successfully, requires re-login usually because refresh token is revoked
                onSuccess()
            } catch (e: retrofit2.HttpException) {
                onError("绑定失败: ${e.response()?.errorBody()?.string() ?: "未知错误"}")
            } catch (e: Exception) {
                onError("修改请求失败，请检查网络")
            }
        }
    }

    fun submitRating(rating: Int, feedback: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                RetrofitClient.create(getApplication()).submitRating(
                    mapOf("rating" to rating, "feedback" to feedback)
                )
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "提交失败")
            }
        }
    }

    fun redeemSubscription(code: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.create(getApplication()).redeemSubscription(cn.zhzgo.study.data.SubscriptionRedeemRequest(code))
                // Save new subscription end date locally so UI updates instantly
                response.subscription_end_date?.let { prefs.saveUser(prefs.getUserAsUserObj().copy(subscription_end_date = it)) }
                onSuccess(response.message)
            } catch (e: retrofit2.HttpException) {
                val errorMsg = try {
                    val jObjError = org.json.JSONObject(e.response()?.errorBody()?.string() ?: "")
                    jObjError.getString("error")
                } catch (e: Exception) {
                    "兑换失败"
                }
                onError(errorMsg)
            } catch (e: Exception) {
                onError(e.message ?: "兑换失败")
            }
        }
    }

    private val _versionHistory = MutableStateFlow<List<cn.zhzgo.study.data.AppVersionHistoryItem>>(emptyList())
    val versionHistory = _versionHistory.asStateFlow()

    private val _isLoadingHistory = MutableStateFlow(false)
    val isLoadingHistory = _isLoadingHistory.asStateFlow()

    fun fetchVersionHistory() {
        viewModelScope.launch {
            _isLoadingHistory.value = true
            try {
                val response = RetrofitClient.create(getApplication()).getAppVersionHistory()
                _versionHistory.value = response.data ?: emptyList()
            } catch (e: Exception) {
                // handle error if needed
                e.printStackTrace()
            } finally {
                _isLoadingHistory.value = false
            }
        }
    }

    fun bindQQ(openId: String, accessToken: String, nickname: String?, avatarUrl: String?, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                // Send an authenticated request to bind QQ
                val response = RetrofitClient.create(getApplication()).bindQq(
                    cn.zhzgo.study.data.QQBindRequest(
                        openid = openId,
                        access_token = accessToken,
                        nickname = nickname,
                        avatar_icon = avatarUrl
                    )
                )
                if (response.isSuccessful) {
                    response.body()?.user?.let { prefs.saveUser(it) }
                    onSuccess()
                } else {
                    onError("绑定失败: ${response.message()}")
                }
            } catch (e: Exception) {
                onError("绑定异常: ${e.message}")
            }
        }
    }

    fun unbindQQ(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.create(getApplication()).unbindQq()
                // Clear local QQ info
                prefs.saveUser(prefs.getUserAsUserObj().copy(nickname = null, qq_openid = null))
                onSuccess()
            } catch (e: Exception) {
                onError("解绑失败: ${e.message}")
            }
        }
    }

    // Helper to get current user as object from flows (simulated)
    private suspend fun UserPreferences.getUserAsUserObj() : cn.zhzgo.study.data.User {
        return cn.zhzgo.study.data.User(
            id = 0, // Not strictly needed for UI update
            username = _userName.value,
            role = _userRole.value,
            avatar_icon = _avatarIcon.value,
            email = _email.value,
            nickname = _nickname.value,
            qq_openid = _qqOpenId.value,
            subscription_end_date = _subscriptionEndDate.value
        )
    }
}

// ────────────────────────────────────────────────────────────
// Screen
// ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToWebView: (String, String) -> Unit,
    onNavigateToAccountSettings: () -> Unit,
    onNavigateToSubscription: () -> Unit,
    initialAction: String = ""
) {
    val viewModel: SettingsViewModel = viewModel()
    val userName by viewModel.userName.collectAsState()
    val userRole by viewModel.userRole.collectAsState()
    val currentAppearance by viewModel.appearance.collectAsState()
    val currentPrimaryColor by viewModel.primaryColor.collectAsState()
    val subscriptionEndDate by viewModel.subscriptionEndDate.collectAsState()
    
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val qqHelper = remember { cn.zhzgo.study.utils.QQLoginHelper(context) }

    val versionName = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showAppearanceDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showRatingDialog by remember { mutableStateOf(false) }
    
    var showVersionHistoryDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showThirdPartyDialog by remember { mutableStateOf(false) }
    var cacheSize by remember { mutableStateOf("") }
    LaunchedEffect(initialAction) {
        when (initialAction) {
            "theme" -> showAppearanceDialog = true
            "accent" -> showColorDialog = true
            "rating" -> showRatingDialog = true
            "about" -> showAboutDialog = true
        }
    }

    
    LaunchedEffect(Unit) {
        val size = context.cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        cacheSize = android.text.format.Formatter.formatFileSize(context, size)
    }
    
    val updateViewModel: cn.zhzgo.study.ui.viewmodels.UpdateViewModel = viewModel()
    val isCheckingUpdate by updateViewModel.isChecking.collectAsState()
    val updateState by updateViewModel.updateState.collectAsState()

    // Handle the update state triggered from settings
    if (updateState?.hasUpdate == false) {
        LaunchedEffect(updateState) {
            Toast.makeText(context, "当前已是最新版本", Toast.LENGTH_SHORT).show()
            updateViewModel.dismissUpdate()
        }
    } else if (updateState?.hasUpdate == true && updateState?.data != null) {
        cn.zhzgo.study.ui.components.UpdateDialog(
            updateData = updateState!!.data!!,
            onDismissRequest = { updateViewModel.dismissUpdate() }
        )
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("清理缓存", fontWeight = FontWeight.Bold) },
            text = { Text("当前缓存大小：$cacheSize\n确定要清理应用缓存吗？这不会删除您的账户信息。") },
            confirmButton = {
                TextButton(onClick = {
                    context.cacheDir.deleteRecursively()
                    cacheSize = "0 B"
                    Toast.makeText(context, "缓存已清理", Toast.LENGTH_SHORT).show()
                    showClearCacheDialog = false
                }) {
                    Text("清理", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text("取消") }
            }
        )
    }

    if (showLicensesDialog) {
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("开源许可协议", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text("本项目使用了以下开源库：", fontWeight = FontWeight.Bold) }
                    item { 
                        Text("• Jetpack Compose (Apache 2.0)", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, 
                             modifier = Modifier.clickable { 
                                 showLicensesDialog = false
                                 onNavigateToWebView("https://developer.android.com/jetpack/compose", "Jetpack Compose (Apache 2.0)") 
                             }.padding(vertical = 2.dp)) 
                    }
                    item { 
                        Text("• Kotlin Coroutines (Apache 2.0)", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, 
                             modifier = Modifier.clickable { 
                                 showLicensesDialog = false
                                 onNavigateToWebView("https://github.com/Kotlin/kotlinx.coroutines", "Kotlin Coroutines (Apache 2.0)") 
                             }.padding(vertical = 2.dp)) 
                    }
                    item { 
                        Text("• Retrofit & OkHttp (Apache 2.0)", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, 
                             modifier = Modifier.clickable { 
                                 showLicensesDialog = false
                                 onNavigateToWebView("https://github.com/square/retrofit", "Retrofit & OkHttp (Apache 2.0)") 
                             }.padding(vertical = 2.dp)) 
                    }
                    item { 
                        Text("• Tinker Hotfix (BSD 3-Clause)", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, 
                             modifier = Modifier.clickable { 
                                 showLicensesDialog = false
                                 onNavigateToWebView("https://github.com/Tencent/tinker", "Tinker Hotfix (BSD 3-Clause)") 
                             }.padding(vertical = 2.dp)) 
                    }
                    item { 
                        Text("• Coil (Apache 2.0)", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, 
                             modifier = Modifier.clickable { 
                                 showLicensesDialog = false
                                 onNavigateToWebView("https://github.com/coil-kt/coil", "Coil (Apache 2.0)") 
                             }.padding(vertical = 2.dp)) 
                    }
                    item { 
                        Text("• Markwon (Apache 2.0)", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, 
                             modifier = Modifier.clickable { 
                                 showLicensesDialog = false
                                 onNavigateToWebView("https://github.com/noties/Markwon", "Markwon (Apache 2.0)") 
                             }.padding(vertical = 2.dp)) 
                    }
                    item { 
                        Text("• QQ Open SDK (Tencent License)", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, 
                             modifier = Modifier.clickable { 
                                 showLicensesDialog = false
                                 onNavigateToWebView("https://wiki.connect.qq.com/", "QQ Open SDK") 
                             }.padding(vertical = 2.dp)) 
                    }
                    item {
                        Text("• FFmpeg Kit (LGPL v3.0)", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                             modifier = Modifier.clickable {
                                 showLicensesDialog = false
                                 onNavigateToWebView("https://github.com/tanersener/ffmpeg-kit", "FFmpeg Kit")
                             }.padding(vertical = 2.dp))
                    }
                    item {
                        Text("• AndroidX Media3 / ExoPlayer (Apache 2.0)", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                             modifier = Modifier.clickable {
                                 showLicensesDialog = false
                                 onNavigateToWebView("https://github.com/androidx/media", "Media3 / ExoPlayer")
                             }.padding(vertical = 2.dp))
                    }
                    item {
                        Text("• Google ML Kit (Google Link)", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                             modifier = Modifier.clickable {
                                 showLicensesDialog = false
                                 onNavigateToWebView("https://developers.google.com/ml-kit", "Google ML Kit")
                             }.padding(vertical = 2.dp))
                    }
                    item {
                        Text("• Room Database & DataStore (Apache 2.0)", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                             modifier = Modifier.clickable {
                                 showLicensesDialog = false
                                 onNavigateToWebView("https://developer.android.com/jetpack/androidx/releases/room", "Room & DataStore")
                             }.padding(vertical = 2.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) { Text("关闭") }
            }
        )
    }

    if (showThirdPartyDialog) {
        AlertDialog(
            onDismissRequest = { showThirdPartyDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("第三方信息共享清单", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Text("为保障应用稳定运行及实现特定功能，我们接入了以下第三方 SDK。我们仅会出于合法、正当、必要、特定、明确的目的共享您的个人信息。")
                    }
                    item {
                        Column {
                            Text("1. 腾讯 QQ 互联 SDK", fontWeight = FontWeight.Bold)
                            Text("• 共享信息：设备标识符（IMEI、MAC地址、Android ID等）、网络信息、日志信息、应用列表", fontSize = 13.sp)
                            Text("• 使用目的：第三方（QQ）账号授权登录", fontSize = 13.sp)
                            Text("• 隐私政策链接：https://wiki.connect.qq.com/qq互联sdk隐私保护声明", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp,
                                modifier = Modifier.clickable {
                                    showThirdPartyDialog = false
                                    onNavigateToWebView("https://wiki.connect.qq.com/qq互联sdk隐私保护声明", "QQ互联隐私保护声明")
                                })
                        }
                    }
                    item {
                        Column {
                            Text("2. 腾讯 Tinker (热修复)", fontWeight = FontWeight.Bold)
                            Text("• 共享信息：设备型号、操作系统版本、网络状态、App版本日志", fontSize = 13.sp)
                            Text("• 使用目的：应用热更新及崩溃分析", fontSize = 13.sp)
                            Text("• 隐私政策链接：https://github.com/Tencent/tinker", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp,
                                modifier = Modifier.clickable {
                                    showThirdPartyDialog = false
                                    onNavigateToWebView("https://github.com/Tencent/tinker", "Tinker (Github)") // Note: Tinker is open source
                                })
                        }
                    }
                    item {
                        Column {
                            Text("3. Retrofit & OkHttp", fontWeight = FontWeight.Bold)
                            Text("• 共享信息：网络状态信息、基础设备描述", fontSize = 13.sp)
                            Text("• 使用目的：服务端接口数据通信", fontSize = 13.sp)
                        }
                    }
                    item {
                        Column {
                            Text("4. AI 解析与学情分析服务 (DeepSeek)", fontWeight = FontWeight.Bold)
                            Text("• 共享信息：脱敏后的题目 ID、答题对错状态、科目信息、学习概况（不含个人隐私）", fontSize = 13.sp)
                            Text("• 使用目的：为您生成智能题目讲解及深度学习情况分析报告（Plus 用户享有更高频次）", fontSize = 13.sp)
                        }
                    }
                    item {
                        Column {
                            Text("5. Google ML Kit (图像识别)", fontWeight = FontWeight.Bold)
                            Text("• 共享信息：图像特征点（仅在本地设备处理）", fontSize = 13.sp)
                            Text("• 使用目的：实现“背景抠图”及“主体分割”等图像处理工具功能", fontSize = 13.sp)
                        }
                    }
                    item {
                        Column {
                            Text("6. FFmpeg Kit", fontWeight = FontWeight.Bold)
                            Text("• 共享信息：音视频流数据描述符", fontSize = 13.sp)
                            Text("• 使用目的：支持音视频格式转换、压缩及提取音频等媒体工具", fontSize = 13.sp)
                        }
                    }
                    item {
                        Column {
                            Text("7. 极光推送 (JPush)", fontWeight = FontWeight.Bold)
                            Text("• 共享信息：设备标识符（IMEI/MAC/Android ID 等）、网络信息", fontSize = 13.sp)
                            Text("• 使用目的：向用户设备提供消息及应用通知的实时推送服务", fontSize = 13.sp)
                            Text("• 隐私政策链接：https://www.jiguang.cn/license/privacy", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp,
                                modifier = Modifier.clickable {
                                    showThirdPartyDialog = false
                                    onNavigateToWebView("https://www.jiguang.cn/license/privacy", "极光隐私政策")
                                })
                        }
                    }
                    item {
                        Column {
                            Text("8. 极验 (Geetest 4.0)", fontWeight = FontWeight.Bold)
                            Text("• 共享信息：设备基础信息、网络状态及操作行为轨迹", fontSize = 13.sp)
                            Text("• 使用目的：防御自动化攻击，保障应用登录及接口请求的安全性", fontSize = 13.sp)
                            Text("• 隐私政策链接：https://www.geetest.com/Private", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp,
                                modifier = Modifier.clickable {
                                    showThirdPartyDialog = false
                                    onNavigateToWebView("https://www.geetest.com/Private", "极验隐私政策")
                                })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThirdPartyDialog = false }) { Text("关闭") }
            }
        )
    }

    if (showVersionHistoryDialog) {
        val history by viewModel.versionHistory.collectAsState()
        val isLoadingHistory by viewModel.isLoadingHistory.collectAsState()
        AlertDialog(
            onDismissRequest = { showVersionHistoryDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("APP 更新记录", fontWeight = FontWeight.Bold) },
            text = {
                if (isLoadingHistory) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else if (history.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text("暂无更新记录", color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(history.size) { index ->
                            val item = history[index]
                            Column {
                                Text(
                                    text = "${item.version_name} (Build ${item.version_code})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (!item.created_at.isNullOrEmpty()) {
                                    Text(
                                        text = "发布时间: ${item.created_at.take(10)}",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.update_log ?: "无更新日志",
                                    fontSize = 14.sp
                                )
                            }
                            if (index < history.size - 1) {
                                HorizontalDivider(modifier = Modifier.padding(top = 16.dp), color = Color.LightGray.copy(alpha=0.5f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVersionHistoryDialog = false }) { Text("关闭") }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("退出登录", fontWeight = FontWeight.Bold) },
            text = { Text("确定要退出当前账号吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.logout(onLogout)
                }) {
                    Text("退出", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("取消") }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("关于应用", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("烁学 App", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text("版本: $versionName", color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f), fontSize = 13.sp)
                    Text("专注于帮助学生高效备考，支持多专业题库、AI 讲解、学情分析、数据同步等核心功能。", fontSize = 13.sp)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f), RoundedCornerShape(12.dp))
                    ) {
                        SettingsRow(
                            icon = Icons.Default.Update,
                            label = "检查更新",
                            onClick = { 
                                showAboutDialog = false
                                updateViewModel.checkForUpdates(context) 
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        SettingsRow(
                            icon = Icons.Default.History,
                            label = "APP 更新记录",
                            onClick = { 
                                showAboutDialog = false
                                showVersionHistoryDialog = true
                                viewModel.fetchVersionHistory()
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("关闭") }
            }
        )
    }

    if (showChangePasswordDialog) {
        var oldPw by remember { mutableStateOf("") }
        var newPw by remember { mutableStateOf("") }
        var confirmNewPw by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("修改密码", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = oldPw, onValueChange = { oldPw = it }, 
                        label = { Text("当前密码") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = newPw, onValueChange = { newPw = it }, 
                        label = { Text("新密码") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = confirmNewPw, onValueChange = { confirmNewPw = it }, 
                        label = { Text("确认新密码") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPw.isEmpty() || oldPw.isEmpty()) {
                        Toast.makeText(context, "密码不能为空", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    if (newPw != confirmNewPw) {
                        Toast.makeText(context, "两次新密码输入不一致", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    viewModel.changePassword(
                        oldPw, newPw,
                        onSuccess = {
                            Toast.makeText(context, "密码修改成功，请重新登录", Toast.LENGTH_LONG).show()
                            showChangePasswordDialog = false
                            viewModel.logout(onLogout)
                        },
                        onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
                    )
                }) {
                    Text("保存", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) { Text("取消", color = MaterialTheme.colorScheme.onSurface) }
            }
        )
    }

    if (showAppearanceDialog) AppearanceSheet("theme") { showAppearanceDialog = false }
    if (showColorDialog) AppearanceSheet("accent") { showColorDialog = false }

    if (showRatingDialog) {
        var rating by remember { mutableIntStateOf(5) }
        var feedback by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showRatingDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("给应用评分", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (i in 1..5) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Star $i",
                                tint = if (i <= rating) Color(0xFFFFB300) else Color.LightGray,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clickable { rating = i }
                            )
                        }
                    }
                    Text(
                        text = when (rating) {
                            1 -> "非常差"
                            2 -> "较差"
                            3 -> "一般"
                            4 -> "不错"
                            5 -> "非常棒！"
                            else -> ""
                        },
                        fontSize = 16.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    OutlinedTextField(
                        value = feedback,
                        onValueChange = { feedback = it },
                        label = { Text("说说您的建议 (选填)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.submitRating(
                        rating, feedback,
                        onSuccess = {
                            Toast.makeText(context, "感谢您的评价和支持！", Toast.LENGTH_SHORT).show()
                            showRatingDialog = false
                        },
                        onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
                    )
                }) {
                    Text("提交", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRatingDialog = false }) { Text("以后再说", color = MaterialTheme.colorScheme.onSurface) }
            }
        )
    }

    cn.zhzgo.study.ui.components.StudyPage("更多设置",onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            item {
                SettingsSection("账户") {
                    SettingsRow(Icons.Outlined.Person,"账号与安全",subtitle="个人资料、登录与绑定",onClick=onNavigateToAccountSettings)
                    SettingsRow(Icons.Outlined.VerifiedUser,"会员状态",subtitle=if(isUserPlus(subscriptionEndDate)) "Plus · 到期 ${subscriptionEndDate.orEmpty().take(10)}" else "查看会员权益",onClick=onNavigateToSubscription)
                }
            }
            item {
                SettingsSection("外观") {
                    SettingsRow(Icons.Outlined.DarkMode,"主题模式",subtitle=when(currentAppearance){"light"->"浅色";"dark"->"深色";else->"跟随系统"},onClick={showAppearanceDialog=true})
                    SettingsRow(Icons.Outlined.Palette,"重点色",trailingContent={Box(Modifier.size(16.dp).background(MaterialTheme.colorScheme.primary,CircleShape))},onClick={showColorDialog=true})
                }
            }
            item {
                SettingsSection("反馈与支持") {
                    SettingsRow(Icons.Outlined.Feedback,"意见反馈",onClick={
                        try{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("mailto:14329140@qq.com")))}catch(e:Exception){Toast.makeText(context,"未找到邮件客户端",Toast.LENGTH_SHORT).show()}
                    })
                    SettingsRow(Icons.Outlined.Star,"给应用评分",onClick={showRatingDialog=true})
                    SettingsRow(Icons.Outlined.Share,"分享应用",onClick={
                        val intent=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,"烁学 · 专注每一次进步 https://study.zhzgo.cn/download")}
                        context.startActivity(Intent.createChooser(intent,"分享烁学"))
                    })
                }
            }
            item {
                SettingsSection("应用") {
                    SettingsRow(Icons.Outlined.SystemUpdate,"检查更新",subtitle="版本 $versionName",onClick={if(!isCheckingUpdate)updateViewModel.checkForUpdates(context,isAutoCheck=false)})
                    SettingsRow(Icons.Outlined.History,"版本记录",onClick={viewModel.fetchVersionHistory();showVersionHistoryDialog=true})
                    SettingsRow(Icons.Outlined.DeleteOutline,"清理缓存",trailingContent={Text(cacheSize,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)},onClick={showClearCacheDialog=true})
                }
            }
            item {
                SettingsSection("关于") {
                    SettingsRow(Icons.Outlined.Info,"关于烁学",onClick={showAboutDialog=true})
                    SettingsRow(Icons.Outlined.ReceiptLong,"开源许可",onClick={showLicensesDialog=true})
                    SettingsRow(Icons.Outlined.Description,"第三方信息共享清单",onClick={showThirdPartyDialog=true})
                    SettingsRow(Icons.Outlined.Language,"官方网站",onClick={onNavigateToWebView("https://study.zhzgo.cn/","官方网站")})
                    SettingsRow(Icons.Outlined.Download,"应用下载",onClick={onNavigateToWebView("https://study.zhzgo.cn/download","应用下载")})
                    SettingsRow(Icons.Outlined.Description,"用户服务协议",onClick={onNavigateToWebView("https://study.zhzgo.cn/agreement","用户服务协议")})
                    SettingsRow(Icons.Outlined.Security,"隐私政策",onClick={onNavigateToWebView("https://study.zhzgo.cn/privacy","隐私政策")})
                }
            }
            item {TextButton(onClick={showLogoutDialog=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("退出登录",color=MaterialTheme.colorScheme.error)}}
        }
    }
}

// ────────────────────────────────────────────────────────────
// Helpers
// ────────────────────────────────────────────────────────────
@Composable
fun SettingsSection(title:String,content:@Composable ColumnScope.()->Unit) {
    Column {
        Text(title,style=MaterialTheme.typography.titleSmall,modifier=Modifier.padding(bottom=8.dp))
        content()
    }
}
@Composable
fun SettingsRow(icon:ImageVector,label:String,subtitle:String?=null,iconTint:Color=MaterialTheme.colorScheme.onSurface,
    trailingContent:@Composable (()->Unit)?=null,onClick:()->Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick=onClick).heightIn(min=56.dp).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(icon,null,Modifier.size(21.dp),tint=iconTint)
            Column(Modifier.weight(1f)) {
                Text(label,style=MaterialTheme.typography.bodyMedium)
                if(subtitle!=null)Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(trailingContent!=null)Box(Modifier.widthIn(max=140.dp)){trailingContent()}
            Icon(Icons.Outlined.ChevronRight,null,Modifier.size(16.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
    }
}
