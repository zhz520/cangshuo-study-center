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
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onBack: () -> Unit,
    onNavigateToWebView: (String, String) -> Unit,
    viewModel: RegisterViewModel = viewModel()
) {
    val username by viewModel.username.collectAsState()
    val email by viewModel.email.collectAsState()
    val code by viewModel.code.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var isAgreed by remember { mutableStateOf(false) }
    val password by viewModel.password.collectAsState()
    val confirmPassword by viewModel.confirmPassword.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isRegistered by viewModel.isRegistered.collectAsState()
    val usernameError by viewModel.usernameError.collectAsState()
    val emailError by viewModel.emailError.collectAsState()
    val codeError by viewModel.codeError.collectAsState()
    val passwordError by viewModel.passwordError.collectAsState()
    val confirmPasswordError by viewModel.confirmPasswordError.collectAsState()
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

    LaunchedEffect(isRegistered) {
        if (isRegistered) {
            // Small delay so the user can see the success message
            kotlinx.coroutines.delay(800)
            onRegisterSuccess()
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
                Text(
                    text = "创建账号",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "加入我们，开启学习之旅",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                
                Spacer(modifier = Modifier.height(24.dp))

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

                AppTextField(
                    value = email,
                    onValueChange = viewModel::onEmailChange,
                    label = "邮箱",
                    leadingIcon = Icons.Default.Person // Optional: Use Email icon if available
                )
                if (emailError != null) {
                    Text(
                        text = emailError!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        AppTextField(
                            value = code,
                            onValueChange = viewModel::onCodeChange,
                            label = "验证码",
                            leadingIcon = Icons.Default.Lock // Optional: Use VpnKey icon
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (email.isNotBlank()) {
                                scope.launch {
                                    try {
                                        val geetestResult = cn.zhzgo.study.utils.GTCaptchaUtils.verify(context)
                                        viewModel.sendCode(geetestResult)
                                        countdown = 60
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar(e.message ?: "验证取消或失败")
                                    }
                                }
                            }
                        },
                        enabled = countdown == 0 && email.isNotBlank(),
                        modifier = Modifier.heightIn(min=48.dp)
                    ) {
                        Text(text = if (countdown > 0) "${countdown}s" else "获取验证码")
                    }
                }
                if (codeError != null) {
                    Text(
                        text = codeError!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                } else if (password.isEmpty()) {
                    Text(
                        text = "至少8位，需包含字母和数字",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                AppTextField(
                    value = confirmPassword,
                    onValueChange = viewModel::onConfirmPasswordChange,
                    label = "确认密码",
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = Icons.Default.Lock
                )
                if (confirmPasswordError != null) {
                    Text(
                        text = confirmPasswordError!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp)
                    )
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
                    text = if (isLoading) "注册中..." else "立即注册",
                    onClick = {
                        if (username.isNotBlank() && email.isNotBlank() && code.isNotBlank() && password.isNotBlank() && confirmPassword.isNotBlank() && isAgreed) {
                            scope.launch {
                                try {
                                    val geetestResult = cn.zhzgo.study.utils.GTCaptchaUtils.verify(context)
                                    viewModel.register(geetestResult)
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(e.message ?: "验证取消或失败")
                                }
                            }
                        }
                    },
                    enabled = !isLoading && username.isNotBlank() && email.isNotBlank() && code.isNotBlank() && password.isNotBlank() && confirmPassword.isNotBlank() && isAgreed
                )

                Spacer(modifier = Modifier.height(24.dp))

                TextButton(onClick = onBack) {
                    Text(
                        text = "已有账号？去登录",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        LoadingOverlay(isLoading = isLoading, message = "注册中...")
    }
}

