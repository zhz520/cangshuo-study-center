package cn.zhzgo.study.ui.screens

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
import cn.zhzgo.study.data.MemberAppearance
import cn.zhzgo.study.network.RetrofitClient

@Composable
fun ProfileScreen(onSettings: () -> Unit, onAccount: () -> Unit, onSubscription: () -> Unit,
    onPublicProfile: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }
    val nickname by prefs.nickname.collectAsState(initial = null)
    val name by prefs.userName.collectAsState(initial = null)
    val slogan by prefs.slogan.collectAsState(initial = null)
    val avatar by prefs.qqAvatar.collectAsState(initial = null)
    val role by prefs.userRole.collectAsState(initial = null)
    val endDate by prefs.subscriptionEndDate.collectAsState(initial = null)
    var memberAppearance by remember { mutableStateOf<MemberAppearance?>(null) }
    val scope = rememberCoroutineScope()
    RefreshOnResume { scope.launch {
        try { memberAppearance = RetrofitClient.create(context).getMemberAppearance() }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { memberAppearance = null }
    } }
    val colors = MaterialTheme.colorScheme
    val plus = memberAppearance?.is_plus ?: (role == "admin" || isUserPlus(endDate))
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
            Surface(onClick = onSubscription, color = Color(0xFF141A24), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(36.dp).background(Color.White.copy(alpha = .04f), CircleShape)
                        .border(1.dp, Color(0xFFE4C36B).copy(alpha = .58f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        PlusMark(Modifier.size(14.dp), tint = Color(0xFFE4C36B))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (plus) "Plus 会员特权已启用" else "升级 Plus 会员", style = MaterialTheme.typography.titleSmall,
                            color = Color.White)
                        Text(if (role == "admin") "管理员学习权益" else if (plus) "到期时间：${endDate.orEmpty().take(10)}" else "解锁完整题库与更多学习权益",
                            style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .64f),
                            modifier = Modifier.padding(top = 3.dp))
                    }
                    Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = Color.White.copy(alpha = .58f))
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("账户与设置", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp))
            ProfileLine(Icons.Outlined.PersonOutline, "账号与安全", onClick = onAccount)
            ProfileLine(Icons.Outlined.Settings, "更多设置", "外观、反馈、关于与应用信息", onClick = onSettings)
        }
    }
}

@Composable
private fun ProfileLine(icon: ImageVector, title: String, value: String? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 55.dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(21.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        value?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
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
