package cn.zhzgo.study.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.zhzgo.study.data.UserPreferences
import cn.zhzgo.study.ui.components.*
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import cn.zhzgo.study.data.MemberAppearance
import cn.zhzgo.study.network.RetrofitClient

@Composable
fun ProfileScreen(onSettings: () -> Unit, onAccount: () -> Unit, onSubscription: () -> Unit,
    onAdmin: () -> Unit, onScore: () -> Unit, onPublicProfile: () -> Unit = {}, onSettingsAction: (String) -> Unit = { onSettings() },onPersonalize:()->Unit={}) {
    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }
    val nickname by prefs.nickname.collectAsState(initial = null)
    val name by prefs.userName.collectAsState(initial = null)
    val slogan by prefs.slogan.collectAsState(initial = null)
    val avatar by prefs.qqAvatar.collectAsState(initial = null)
    val role by prefs.userRole.collectAsState(initial = null)
    val endDate by prefs.subscriptionEndDate.collectAsState(initial = null)
    val appearance by prefs.appearance.collectAsState(initial = "system")
    var appearanceAction by rememberSaveable { mutableStateOf("") }
    var memberAppearance by remember { mutableStateOf<MemberAppearance?>(null) }
    val scope = rememberCoroutineScope()
    RefreshOnResume { scope.launch {
        try { memberAppearance = RetrofitClient.create(context).getMemberAppearance() }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { memberAppearance = null }
    } }
    val colors = MaterialTheme.colorScheme
    val plus = memberAppearance?.is_plus ?: (role == "admin" || isUserPlus(endDate))
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    fun feedback() {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("mailto:14329140@qq.com"))) }
        catch (_: Exception) { android.widget.Toast.makeText(context, "未找到邮件客户端", android.widget.Toast.LENGTH_SHORT).show() }
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().clickable(onClick = onPublicProfile).padding(bottom = 22.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MemberAvatar(avatar, memberAppearance)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(nickname ?: name ?: "同学", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        MemberLabel(memberAppearance)
                    }
                    Text(slogan?.takeIf { it.isNotBlank() } ?: "保持热爱，也保持专注", style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
            }
            Surface(onClick = onSubscription, color = colors.surface, shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (plus) colors.primary.copy(alpha = .22f) else colors.outlineVariant)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(32.dp).background(if (plus) colors.primary.copy(alpha = .06f) else colors.surfaceVariant, RoundedCornerShape(9.dp))
                        .border(1.dp, if (plus) colors.primary.copy(alpha = .16f) else colors.outlineVariant, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center) {
                        PlusMark(Modifier.size(15.dp), tint = if (plus) colors.primary else colors.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(if (plus) "Plus 会员已启用" else "升级 Plus", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(if(role=="admin") "管理员学习权益" else if (plus) "到期时间：${endDate.orEmpty().take(10)}" else "解锁完整题库与更多学习权益",
                            style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
                    }
                    Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(14.dp))
            ProfileLine(Icons.Outlined.PersonOutline, "账号与安全", onClick = onAccount)
            ProfileLine(Icons.Outlined.DarkMode, "主题模式", when (appearance) { "light" -> "浅色"; "dark" -> "深色"; else -> "跟随系统" }) { appearanceAction = "theme" }
            ProfileLine(Icons.Outlined.Palette, "重点色", accent = true) { appearanceAction = "accent" }
            ProfileLine(Icons.Outlined.Tune, "Plus 个性化", if(plus)"已解锁" else "会员权益", onClick=onPersonalize)
            Spacer(Modifier.height(12.dp))
            ProfileLine(Icons.Outlined.MailOutline, "意见反馈", onClick = ::feedback)
            ProfileLine(Icons.Outlined.StarBorder, "给应用评分") { onSettingsAction("rating") }
            ProfileLine(Icons.Outlined.Share, "分享应用") {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "烁学 · 专注每一次进步 https://study.zhzgo.cn/download") }, "分享烁学"))
            }
            ProfileLine(Icons.Outlined.Info, "关于应用", "版本 $version") { onSettingsAction("about") }
            Spacer(Modifier.height(12.dp))
            ProfileLine(Icons.Outlined.ManageSearch, "联考成绩查询", onClick = onScore)
            if (role == "admin" || role == "demo") ProfileLine(Icons.Outlined.AdminPanelSettings, "后台管理", onClick = onAdmin)
            ProfileLine(Icons.Outlined.Settings, "更多设置", onClick = onSettings)
        }
    }
    if (appearanceAction.isNotEmpty()) AppearanceSheet(appearanceAction) { appearanceAction = "" }
}

@Composable
private fun ProfileLine(icon: ImageVector, title: String, value: String? = null, accent: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 55.dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(21.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        value?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
        if (accent) Box(Modifier.size(15.dp).background(colors.primary, CircleShape))
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(17.dp), tint = colors.onSurfaceVariant.copy(alpha = .65f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommonLinksScreen(onBack:()->Unit,onLink:(String,String)->Unit) {
    val context=LocalContext.current
    var data by remember{mutableStateOf<cn.zhzgo.study.data.HomeDataResponse?>(null)}
    var error by remember{mutableStateOf(false)}
    var retry by remember{mutableIntStateOf(0)}
    LaunchedEffect(retry) {
        error=false
        try{data=cn.zhzgo.study.network.RetrofitClient.create(context).getHomeData()}
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){error=true}
    }
    Scaffold(topBar={TopAppBar(title={Text("常用网站")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Outlined.Close,"返回资源")}})}) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(20.dp)) {
            if(error)item {StateMessage("网站目录加载失败","请检查网络后重试。","重试",{retry++})}
            else if(data==null)item{cn.zhzgo.study.ui.components.StudyLoading(Modifier.fillMaxWidth())}
            else if(data?.externalLinks.isNullOrEmpty())item{StateMessage("暂无常用网站","网站目录更新后会显示在这里。")}
            data?.externalLinks?.forEach{link->item(key=link.id){StudyRow(link.name,link.url.substringAfter("://").substringBefore('/'),onClick={onLink(link.url,link.name)})}}
        }
    }
}
