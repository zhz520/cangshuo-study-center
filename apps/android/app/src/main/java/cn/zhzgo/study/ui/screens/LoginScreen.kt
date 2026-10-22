package cn.zhzgo.study.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.ClickableText
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.zhzgo.study.ui.components.AppTextField
import cn.zhzgo.study.ui.components.PrimaryButton
import cn.zhzgo.study.ui.components.LoadingOverlay
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToBindQQ: (String, String) -> Unit,
    onNavigateToWebView: (String, String) -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity
    val qqHelper = remember { cn.zhzgo.study.utils.QQLoginHelper(context) }
    val username by viewModel.username.collectAsState()
    val password by viewModel.password.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.loginError.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val usernameError by viewModel.usernameError.collectAsState()
    val passwordError by viewModel.passwordError.collectAsState()

    val isForgotPasswordMode by viewModel.isForgotPasswordMode.collectAsState()
    val forgotEmail by viewModel.forgotEmail.collectAsState()
    val forgotCode by viewModel.forgotCode.collectAsState()
    val forgotNewPassword by viewModel.forgotNewPassword.collectAsState()
    val forgotError by viewModel.forgotError.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()

    var countdown by remember { mutableStateOf(0) }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown--
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(successMessage) {
        if (successMessage != null) {
            snackbarHostState.showSnackbar(successMessage!!)
            viewModel.clearSuccessMessage()
        }
    }

    val needBindQQ by viewModel.needBindQQ.collectAsState()
    var isAgreed by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            onLoginSuccess()
        }
    }

    LaunchedEffect(needBindQQ) {
        if (needBindQQ != null) {
            viewModel.pendingQqBindToken?.let { onNavigateToBindQQ(needBindQQ!!, it) }
            viewModel.resetBindQQ()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=24.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Top
            ) {
                // Header
                Text(
                    text = if(isForgotPasswordMode) "找回账号密码" else "登录烁学",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "登录您的账号",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                if (isForgotPasswordMode) {
                    // Forgot Password Form
                    AppTextField(
                        value = forgotEmail,
                        onValueChange = viewModel::onForgotEmailChange,
                        label = "绑定邮箱",
                        leadingIcon = Icons.Default.Person // Optional: Use Email icon
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            AppTextField(
                                value = forgotCode,
                                onValueChange = viewModel::onForgotCodeChange,
                                label = "验证码",
                                leadingIcon = Icons.Default.Lock // Optional: Use VpnKey icon
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        val geetestResult = cn.zhzgo.study.utils.GTCaptchaUtils.verify(context)
                                        viewModel.sendForgotCode(geetestResult)
                                        countdown = 60
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar(e.message ?: "验证取消或失败")
                                    }
                                }
                            },
                            enabled = countdown == 0 && forgotEmail.isNotBlank(),
                            modifier = Modifier.heightIn(min=48.dp)
                        ) {
                            Text(text = if (countdown > 0) "${countdown}s" else "获取验证码")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    AppTextField(
                        value = forgotNewPassword,
                        onValueChange = viewModel::onForgotNewPasswordChange,
                        label = "新密码",
                        visualTransformation = PasswordVisualTransformation(),
                        leadingIcon = Icons.Default.Lock
                    )

                    if (forgotError != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = forgotError!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    PrimaryButton(
                        text = if (isLoading) "重置中..." else "重置密码",
                        onClick = {
                            if (forgotEmail.isNotBlank() && forgotCode.isNotBlank() && forgotNewPassword.isNotBlank()) {
                                scope.launch {
                                    try {
                                        val geetestResult = cn.zhzgo.study.utils.GTCaptchaUtils.verify(context)
                                        viewModel.forgotPassword(geetestResult)
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar(e.message ?: "验证取消或失败")
                                    }
                                }
                            }
                        },
                        enabled = !isLoading && forgotEmail.isNotBlank() && forgotCode.isNotBlank() && forgotNewPassword.isNotBlank()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(onClick = { viewModel.setForgotPasswordMode(false) }) {
                        Text(
                            text = "取消",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    // Login Form
                    AppTextField(
                        value = username,
                        onValueChange = viewModel::onUsernameChange,
                        label = "用户名",
                        leadingIcon = Icons.Default.Person
                    )
                    if (usernameError != null) {
                        Text(
                            text = usernameError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        ClickableText(
                            text = buildAnnotatedString { append("忘记密码？") },
                            style = androidx.compose.ui.text.TextStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            onClick = { viewModel.setForgotPasswordMode(true) }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    AppTextField(
                        value = password,
                        onValueChange = viewModel::onPasswordChange,
                        label = "密码",
                        visualTransformation = PasswordVisualTransformation(),
                        leadingIcon = Icons.Default.Lock
                    )
                    if (passwordError != null) {
                        Text(
                            text = passwordError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp)
                        )
                    }
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = error!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Agreement Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isAgreed,
                        onCheckedChange = { isAgreed = it },
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                    )
                    
                    val annotatedString = buildAnnotatedString {
                        append("我已阅读并同意 ")
                        pushStringAnnotation(tag = "URL", annotation = "https://study.zhzgo.cn/agreement")
                        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                            append("用户协议")
                        }
                        pop()
                        append(" 和 ")
                        pushStringAnnotation(tag = "URL", annotation = "https://study.zhzgo.cn/privacy")
                        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                            append("隐私政策")
                        }
                        pop()
                    }

                    ClickableText(
                        text = annotatedString,
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)),
                        onClick = { offset ->
                            val annotations = annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                            annotations.firstOrNull()?.let { annotation ->
                                val title = if (annotation.item.contains("agreement")) "用户服务协议" else "隐私政策"
                                onNavigateToWebView(annotation.item, title)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                PrimaryButton(
                    text = if (isLoading) "登录中..." else "登录",
                    onClick = {
                        if (username.isNotBlank() && password.isNotBlank() && isAgreed) {
                            scope.launch {
                                try {
                                    val geetestResult = cn.zhzgo.study.utils.GTCaptchaUtils.verify(context)
                                    viewModel.login(geetestResult)
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(e.message ?: "验证取消或失败")
                                }
                            }
                        }
                    },
                    enabled = !isLoading && username.isNotBlank() && password.isNotBlank() && isAgreed
                )

                Spacer(modifier = Modifier.height(24.dp))

                TextButton(onClick = onNavigateToRegister) {
                    Text(
                        text = "没有账号？去注册",
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        text = " 或 ",
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedButton(
                    onClick = {
                        if (!isAgreed) {
                            scope.launch {
                                snackbarHostState.showSnackbar("请先阅读并同意用户协议和隐私政策")
                            }
                            return@OutlinedButton
                        }
                        activity?.let {
                            qqHelper.login(it, object : cn.zhzgo.study.utils.QQLoginHelper.QQLoginListener {
                                override fun onSuccess(openId: String, accessToken: String, nickname: String?, avatarUrl: String?) {
                                    viewModel.qqLogin(openId, accessToken)
                                }

                                override fun onError(code: Int, message: String, detail: String?) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("QQ 登录失败：$message")
                                    }
                                }

                                override fun onCancel() {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("已取消 QQ 登录")
                                    }
                                }
                            })
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    Text("使用 QQ 快捷登录", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        
        LoadingOverlay(isLoading = isLoading, message = "登录中...")
    }
}
