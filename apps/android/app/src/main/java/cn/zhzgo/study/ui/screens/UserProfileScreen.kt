package cn.zhzgo.study.ui.screens
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.zhzgo.study.ui.components.*
import cn.zhzgo.study.ui.viewmodels.UserProfileViewModel
import coil.compose.AsyncImage
@Composable
fun UserProfileScreen(userId:String,onBack:()->Unit,onArticleClick:(Int)->Unit,onNavigateToSubscription:()->Unit) {
    val context=LocalContext.current
    val model:UserProfileViewModel=viewModel()
    val data by model.profile.collectAsState()
    val loading by model.isLoading.collectAsState()
    val error by model.error.collectAsState()
    var tab by rememberSaveable{mutableIntStateOf(0)}
    RefreshOnResume{model.fetchProfile(context,userId)}
    StudyPage(if(userId=="me")"个人主页" else "作者主页",onBack){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=24.dp)) {
            if(loading)item{cn.zhzgo.study.ui.components.StudyLoading(Modifier.fillMaxWidth())}
            error?.let{msg->item{StateMessage("主页加载失败",msg,"重试",{model.fetchProfile(context,userId)})}}
            data?.let{profile->
                item {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        if(profile.user.avatar_icon?.startsWith("http")==true)AsyncImage(profile.user.avatar_icon,null,Modifier.size(52.dp).clip(CircleShape))
                        else Icon(Icons.Outlined.PersonOutline,null,Modifier.size(44.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f)) {
                            Text(profile.user.nickname ?: profile.user.username,style=MaterialTheme.typography.headlineSmall)
                            Text("UID ${profile.user.id}${if(profile.user.role=="admin")" · 官方账号" else ""}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    profile.user.slogan?.takeIf{it.isNotBlank()}?.let{Text(it,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(top=16.dp))}
                    if(isUserPlus(profile.user.subscription_end_date))Text("Plus 会员",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(top=8.dp))
                    if(userId=="me")TextButton(onClick=onNavigateToSubscription){Text("会员与权益")}
                    Spacer(Modifier.height(24.dp))
                    Row {
                        Metric("${profile.articles.size}","发布内容",Modifier.weight(1f))
                        Metric("${profile.dynamics.size}","互动记录",Modifier.weight(1f))
                    }
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(top=20.dp)) {
                        item{FilterChip(tab==0,{tab=0},label={Text("发布内容")})}
                        item{FilterChip(tab==1,{tab=1},label={Text("最近互动")})}
                    }
                }
                if(tab==0) {
                    if(profile.articles.isEmpty())item{StateMessage("还没有发布内容","发布的文章与资料会展示在这里。")}
                    items(profile.articles,key={it.id}){a->ResourceArticleRow(a){onArticleClick(a.id)}}
                } else {
                    if(profile.dynamics.isEmpty())item{StateMessage("还没有互动记录","参与内容讨论后，记录会展示在这里。")}
                    items(profile.dynamics,key={it.id}){comment->
                        Column(Modifier.padding(vertical=14.dp)) {
                            Text(comment.content,style=MaterialTheme.typography.bodyMedium)
                            Text(listOfNotNull(comment.article_title,comment.created_at.take(10)).joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=6.dp))
                        }
                        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}
