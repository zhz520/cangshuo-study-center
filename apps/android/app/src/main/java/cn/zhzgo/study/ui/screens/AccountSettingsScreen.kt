package cn.zhzgo.study.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

import cn.zhzgo.study.utils.QQLoginHelper
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.CrueltyFree
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.launch

val AVAILABLE_AVATARS = listOf(
    "Felix", "Aneka", "Jack", "Luna", "Bella", "Max", "Oliver", "Chloe", "Leo", "Milo", "Charlie", "Simba"
).map { "https://api.dicebear.com/9.x/fun-emoji/png?seed=$it&size=120" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val qqHelper = remember { QQLoginHelper(context) }

    val userName by viewModel.userName.collectAsState()
    val userRole by viewModel.userRole.collectAsState()
    val avatarIcon by viewModel.avatarIcon.collectAsState()
    val email by viewModel.email.collectAsState()
    val nickname by viewModel.nickname.collectAsState()
    val qqOpenId by viewModel.qqOpenId.collectAsState()
    val qqAvatar by viewModel.qqAvatar.collectAsState()
    val slogan by viewModel.slogan.collectAsState()

    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showChangeEmailDialog by remember { mutableStateOf(false) }
    var showAvatarDialog by remember { mutableStateOf(false) }
    var showEditNicknameDialog by remember { mutableStateOf(false) }
    var showEditSloganDialog by remember { mutableStateOf(false) }

    var emailCountdown by remember { mutableStateOf(0) }
    LaunchedEffect(emailCountdown) {
        if (emailCountdown > 0) {
            kotlinx.coroutines.delay(1000)
            emailCountdown--
        }
    }

    if (showAvatarDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("选择头像", fontWeight = FontWeight.Bold) },
            text = {
                val combinedAvatars = remember(qqAvatar) {
                    if (qqAvatar != null) listOf(qqAvatar!!) + AVAILABLE_AVATARS else AVAILABLE_AVATARS
                }
                Column {
                    if (!qqOpenId.isNullOrEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.syncFromQQ(
                                        onSuccess = { Toast.makeText(context, "同步成功", Toast.LENGTH_SHORT).show() },
                                        onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                                    )
                                    showAvatarDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("同步 QQ 头像与昵称", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("点击直接使用 QQ 的资料", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        Text(
                            "提示：绑定 QQ 后可同步头像和昵称",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                        )
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        items(combinedAvatars) { url ->
                            val isSelected = url == avatarIcon
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        CircleShape
                                    )
                                    .clickable {
                                        viewModel.setAvatarIcon(url)
                                        showAvatarDialog = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(url)
                                        .crossfade(true)
                                        .placeholder(cn.zhzgo.study.R.drawable.ic_launcher_foreground)
                                        .error(cn.zhzgo.study.R.drawable.ic_launcher_foreground)
                                        .build(),
                                    contentDescription = "Avatar Option",
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAvatarDialog = false }) { Text("关闭", color = MaterialTheme.colorScheme.onSurface) }
            }
        )
    }

    if (showChangePasswordDialog) {
        var oldPw by remember { mutableStateOf("") }
        var newPw by remember { mutableStateOf("") }
        var confirmPw by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("修改密码", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = oldPw,
                        onValueChange = { oldPw = it },
                        label = { Text("原密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPw,
                        onValueChange = { newPw = it },
                        label = { Text("新密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPw,
                        onValueChange = { confirmPw = it },
                        label = { Text("确认新密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPw != confirmPw) {
                        Toast.makeText(context, "两次输入的新密码不一致", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    viewModel.changePassword(
                        oldPw, newPw,
                        onSuccess = {
                            Toast.makeText(context, "密码修改成功，请重新登录", Toast.LENGTH_SHORT).show()
                            showChangePasswordDialog = false
                            viewModel.logout { /* handled by main listener usually */ }
                        },
                        onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
                    )
                }) {
                    Text("确认修改", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) { Text("取消", color = MaterialTheme.colorScheme.onSurface) }
            }
        )
    }

    if (showChangeEmailDialog) {
        var newEmailInput by remember { mutableStateOf("") }
        var codeInput by remember { mutableStateOf("") }
        var isSendingCode by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        AlertDialog(
            onDismissRequest = { showChangeEmailDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("绑定/修改邮箱", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = newEmailInput,
                        onValueChange = { newEmailInput = it },
                        label = { Text("新邮箱地址") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = { codeInput = it },
                            label = { Text("验证码") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newEmailInput.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(newEmailInput).matches()) {
                                    Toast.makeText(context, "请输入有效的邮箱", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                scope.launch {
                                    try {
                                        val gtResult = cn.zhzgo.study.utils.GTCaptchaUtils.verify(context)
                                        isSendingCode = true
                                        viewModel.sendCode(
                                            targetEmail = newEmailInput,
                                            gtResult = gtResult,
                                            onSuccess = {
                                                isSendingCode = false
                                                emailCountdown = 60
                                                Toast.makeText(context, "验证码已发送", Toast.LENGTH_SHORT).show()
                                            },
                                            onError = {
                                                isSendingCode = false
                                                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    } catch (e: Exception) {
                                        Toast.makeText(context, e.message ?: "验证取消或失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = emailCountdown == 0 && newEmailInput.isNotBlank() && !isSendingCode,
                            modifier = Modifier.height(56.dp)
                        ) {
                            if (isSendingCode) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text(text = if (emailCountdown > 0) "${emailCountdown}s" else "获取验证码")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newEmailInput.isBlank() || codeInput.isBlank()) {
                        Toast.makeText(context, "请填写完整信息", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    viewModel.changeEmail(
                        newEmailInput, codeInput,
                        onSuccess = {
                            Toast.makeText(context, "邮箱绑定成功，请重新登录", Toast.LENGTH_LONG).show()
                            showChangeEmailDialog = false
                            viewModel.logout { /* handled by main listener usually */ }
                        },
                        onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
                    )
                }) {
                    Text("确认修改", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangeEmailDialog = false }) { Text("取消", color = MaterialTheme.colorScheme.onSurface) }
            }
        )
    }

    if (showEditNicknameDialog) {
        var nicknameInput by remember { mutableStateOf(nickname ?: "") }
        AlertDialog(
            onDismissRequest = { showEditNicknameDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("修改昵称", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = nicknameInput,
                    onValueChange = { nicknameInput = it },
                    label = { Text("新昵称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (nicknameInput.isBlank()) {
                        Toast.makeText(context, "昵称不能为空", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    viewModel.updateProfile(nickname = nicknameInput, onSuccess = {
                        Toast.makeText(context, "昵称修改成功", Toast.LENGTH_SHORT).show()
                        showEditNicknameDialog = false
                    }, onError = {
                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                    })
                }) {
                    Text("保存", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNicknameDialog = false }) { Text("取消") }
            }
        )
    }

    if (showEditSloganDialog) {
        var sloganInput by remember { mutableStateOf(slogan ?: "") }
        AlertDialog(
            onDismissRequest = { showEditSloganDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("修改个性签名", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = sloganInput,
                    onValueChange = { sloganInput = it },
                    label = { Text("个性签名") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateProfile(slogan = sloganInput, onSuccess = {
                        Toast.makeText(context, "签名修改成功", Toast.LENGTH_SHORT).show()
                        showEditSloganDialog = false
                    }, onError = {
                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                    })
                }) {
                    Text("保存", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditSloganDialog = false }) { Text("取消") }
            }
        )
    }

    cn.zhzgo.study.ui.components.StudyPage("个人资料与安全",onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().clickable{showAvatarDialog=true}.heightIn(min=80.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    if(avatarIcon.startsWith("http"))AsyncImage(avatarIcon,"更换头像",Modifier.size(48.dp).clip(CircleShape))
                    else Icon(Icons.Default.Person,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f)) {
                        Text(nickname?.takeIf{it.isNotBlank()} ?: userName,style=MaterialTheme.typography.headlineSmall)
                        Text("更换头像",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
                    }
                    Icon(Icons.Default.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                SettingsSection("个人资料") {
                    cn.zhzgo.study.ui.components.DetailPair("登录账号",userName)
                    SettingsRow(Icons.Default.Person,"昵称",subtitle=nickname?.takeIf{it.isNotBlank()} ?: "未设置",onClick={showEditNicknameDialog=true})
                    SettingsRow(Icons.Default.Edit,"个性签名",subtitle=slogan?.takeIf{it.isNotBlank()} ?: "未设置",onClick={showEditSloganDialog=true})
                }
            }
            item {
                SettingsSection("账号安全") {
                    SettingsRow(Icons.Default.Lock,"修改密码",onClick={showChangePasswordDialog=true})
                    SettingsRow(Icons.Default.Email,if(email.isNullOrBlank())"绑定邮箱" else "更换邮箱",subtitle=email ?: "接收账号验证与审核通知",onClick={showChangeEmailDialog=true})
                    var showUnbindDialog by remember { mutableStateOf(false) }
                    if (showUnbindDialog) {
                        AlertDialog(
                            onDismissRequest = { showUnbindDialog = false },
                            title = { Text("确认解绑 QQ？", fontWeight = FontWeight.Bold) },
                            text = { Text("解绑后你将无法使用该 QQ 账号直接登录。") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showUnbindDialog = false
                                    viewModel.unbindQQ(
                                        onSuccess = { Toast.makeText(context, "QQ 已解绑", Toast.LENGTH_SHORT).show() },
                                        onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                                    )
                                }) {
                                    Text("确认解绑", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showUnbindDialog = false }) { Text("取消") }
                            }
                        )
                    }

                    val isBound = !qqOpenId.isNullOrEmpty()
                    SettingsRow(
                        icon = if (isBound) Icons.Default.LinkOff else Icons.Default.Link,
                        label = if (isBound) "解绑 QQ" else "绑定 QQ",
                        onClick = {
                            if (isBound) {
                                showUnbindDialog = true
                            } else {
                                activity?.let { ctx ->
                                    qqHelper.login(ctx, object : cn.zhzgo.study.utils.QQLoginHelper.QQLoginListener {
                                        override fun onSuccess(openId: String, accessToken: String, nickname: String?, avatarUrl: String?) {
                                            viewModel.bindQQ(openId, accessToken, nickname, avatarUrl,
                                                onSuccess = { Toast.makeText(context, "QQ 绑定成功", Toast.LENGTH_SHORT).show() },
                                                onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                                            )
                                        }
                                        override fun onError(code: Int, message: String, detail: String?) {
                                            Toast.makeText(context, "QQ 授权失败: $message", Toast.LENGTH_SHORT).show()
                                        }
                                        override fun onCancel() {}
                                    })
                                }
                            }
                        }
                    )

                }
            }
        }
    }
}
