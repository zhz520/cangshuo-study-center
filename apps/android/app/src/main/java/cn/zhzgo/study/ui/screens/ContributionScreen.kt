package cn.zhzgo.study.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.zhzgo.study.data.Article
import cn.zhzgo.study.ui.viewmodels.ContributionViewModel
import cn.zhzgo.study.data.UserPreferences
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContributionScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ContributionViewModel = viewModel()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val error by viewModel.error.collectAsState()

    val userPreferences = remember { UserPreferences(context) }
    val email by userPreferences.email.collectAsState(initial = null)

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf(TextFieldValue("")) }
    var category by remember { mutableStateOf("投递资料") }
    var coverUrl by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Edit, 1: Preview

    val uploadUrl by viewModel.uploadUrl.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.uploadFile(context, it) }
    }

    LaunchedEffect(uploadUrl) {
        uploadUrl?.let { url ->
            val insertion = "![图片]($url)"
            val newText = content.text.substring(0, content.selection.start) + 
                          insertion + 
                          content.text.substring(content.selection.end)
            content = TextFieldValue(
                text = newText,
                selection = TextRange(content.selection.start + insertion.length)
            )
            viewModel.resetUploadUrl()
        }
    }

    LaunchedEffect(successMessage) {
        successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            onBack()
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
    }

    val isDark = MaterialTheme.colorScheme.background.luminance()<.5f
    val surfaceColor = MaterialTheme.colorScheme.background
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Scaffold(
        containerColor = surfaceColor,
        topBar = {
            cn.zhzgo.study.ui.components.StudyToolTopBar(
                title = { Text("用户投稿", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = surfaceColor,
                    navigationIconContentColor = onSurfaceColor,
                    titleContentColor = onSurfaceColor
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                text = "分享你的知识",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = onSurfaceColor
            )
            Text(
                text = "投稿通过审核后，将显示在资源中心，供所有用户学习。",
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceColor.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
            )

            ContributionTextField(
                value = title,
                onValueChange = { title = it },
                label = "文章标题",
                placeholder = "给文章起一个吸引人的标题",
                icon = Icons.Default.Title
            )

            Spacer(modifier = Modifier.height(20.dp))

            ContributionTextField(
                value = category,
                onValueChange = { category = it },
                label = "文章分类",
                placeholder = "如：学习资料、备考心得",
                icon = Icons.Default.Label
            )

            Spacer(modifier = Modifier.height(20.dp))

            ContributionTextField(
                value = coverUrl,
                onValueChange = { coverUrl = it },
                label = "封面图片链接 (可选)",
                placeholder = "请输入公开的图片 URL",
                icon = Icons.Default.Link
            )

            Spacer(modifier = Modifier.height(20.dp))

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                cn.zhzgo.study.ui.components.ReferencePill("编辑内容",selectedTab==0){selectedTab=0}
                cn.zhzgo.study.ui.components.ReferencePill("实时预览",selectedTab==1){selectedTab=1}
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                MarkdownToolbar(
                    onAction = { action ->
                        val start = content.selection.start
                        val end = content.selection.end
                        val selectedText = content.text.substring(start, end)
                        
                        val (wrapped, offset) = when (action) {
                            "bold" -> "**$selectedText**" to 2
                            "italic" -> "_${selectedText}_" to 1
                            "link" -> "[$selectedText](url)" to 1
                            "h1" -> "# $selectedText" to 2
                            "h2" -> "## $selectedText" to 3
                            "quote" -> "> $selectedText" to 2
                            else -> selectedText to 0
                        }
                        
                        val newText = content.text.substring(0, start) + wrapped + content.text.substring(end)
                        content = TextFieldValue(
                            text = newText,
                            selection = if (selectedText.isEmpty()) TextRange(start + offset) else TextRange(start + wrapped.length)
                        )
                    },
                    onImageClick = { imagePicker.launch("image/*") },
                    isUploading = isUploading
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 300.dp),
                    placeholder = { Text("在这里输入详细内容...", fontSize = 14.sp) },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = onSurfaceColor,
                        unfocusedBorderColor = onSurfaceColor.copy(alpha = 0.1f)
                    )
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = onSurfaceColor.copy(alpha = if (isDark) 0.05f else 0.02f)
                    ),
                    border = BorderStroke(1.dp, onSurfaceColor.copy(alpha = 0.1f))
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        PreviewMarkdown(content.text)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (email.isNullOrEmpty()) {
                        Toast.makeText(context, "为了能及时告知您审核结果，请先前往“设置-账户信息”绑定邮箱", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    if (title.isNotBlank() && content.text.isNotBlank()) {
                        viewModel.submitArticle(context, title, content.text, category, coverUrl.ifBlank { null })
                    } else {
                        Toast.makeText(context, "请完善标题和内容", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min=48.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = onSurfaceColor,
                    contentColor = surfaceColor
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = surfaceColor)
                } else {
                    Text("提交投稿", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MarkdownToolbar(
    onAction: (String) -> Unit,
    onImageClick: () -> Unit,
    isUploading: Boolean
) {
    val isDark = isSystemInDarkTheme()
    val tintColor = MaterialTheme.colorScheme.onSurfaceVariant
    
    LazyRow(
        modifier = Modifier.padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { ToolbarIcon(Icons.Default.FormatBold, "加粗", tintColor) { onAction("bold") } }
        item { ToolbarIcon(Icons.Default.FormatItalic, "斜体", tintColor) { onAction("italic") } }
        item { ToolbarIcon(Icons.Default.FormatQuote, "引用", tintColor) { onAction("quote") } }
        item { ToolbarIcon(Icons.Default.FormatSize, "标题1", tintColor) { onAction("h1") } }
        item { ToolbarIcon(Icons.Default.Link, "链接", tintColor) { onAction("link") } }
        item { 
            Box {
                ToolbarIcon(Icons.Default.Image, "插入图片", tintColor) { onImageClick() }
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp).align(Alignment.TopEnd),
                        strokeWidth = 2.dp,
                        color = if (isSystemInDarkTheme()) Color.White else Color.Black
                    )
                }
            }
        }
    }
}

@Composable
fun ToolbarIcon(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .border(1.dp, tint.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun PreviewMarkdown(content:String) {
    cn.zhzgo.study.ui.components.MathText(content,fontSize=16.sp)
}

@Composable
fun ContributionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: ImageVector
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, fontSize = 14.sp) },
            leadingIcon = { 
                Icon(
                    imageVector = icon, 
                    contentDescription = null, 
                    modifier = Modifier.size(18.dp)
                ) 
            },
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
    }
}
