package cn.zhzgo.study.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.zhzgo.study.data.StudyPreferences
import cn.zhzgo.study.data.StudyMemory
import cn.zhzgo.study.data.MemberAppearance
import cn.zhzgo.study.data.MemberAppearanceRequest
import androidx.compose.foundation.clickable
import cn.zhzgo.study.data.UserPreferences
import cn.zhzgo.study.network.RetrofitClient
import cn.zhzgo.study.ui.components.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

@Composable
fun PersonalizationScreen(onBack:()->Unit,onSubscription:()->Unit) {
    val context=LocalContext.current
    val prefs=remember{StudyPreferences(context)}
    val user=remember{UserPreferences(context)}
    var identity by remember { mutableStateOf(MemberAppearance()) }
    var avatar by remember { mutableStateOf<String?>(null) }
    var displayName by remember { mutableStateOf("同学") }
    var frame by remember { mutableStateOf("obsidian") }
    var badge by remember { mutableStateOf(true) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var verified by remember{mutableStateOf(false)}
    var allowed by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    var greeting by remember{mutableStateOf("")}
    var quote by remember{mutableStateOf(true)}
    var feedback by remember{mutableStateOf(true)}
    var goal by remember{mutableStateOf("30")}
    var textSize by remember{mutableIntStateOf(18)}
    var saved by remember{mutableStateOf(false)}
    var saving by remember{mutableStateOf(false)}
    var retry by remember{mutableIntStateOf(0)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(Unit) { val initial=prefs.memory.first();greeting=initial.greeting;quote=initial.showDailyQuote;feedback=initial.immediateFeedback;goal=initial.dailyGoal.toString();textSize=initial.questionTextSize.coerceIn(16,22) }
    RefreshOnResume { if(verified || error!=null)retry++ }
    LaunchedEffect(retry) {
        verified=false;error=null
        try {
            val profile=RetrofitClient.create(context).getUserProfile("me").user
            user.setSubscriptionEndDate(profile.subscription_end_date ?: "")
            identity=RetrofitClient.create(context).getMemberAppearance()
            frame=identity.avatar_frame;badge=identity.badge_visible
            avatar=profile.avatar_icon;displayName=profile.nickname ?: profile.username
            allowed=identity.is_plus
            verified=true
        } catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){error="会员状态暂时无法确认，请重试"}
    }
    StudyPage("Plus 个性化",onBack){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            item { Text("让每一次出现，都有你的风格",style=MaterialTheme.typography.titleLarge)
                Text("头像装饰与 Plus 标识会同步展示在个人主页和排行榜。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            if(error!=null)item{StateMessage("状态确认失败",error.orEmpty(),"重试",{retry++})}
            else if(!verified)item{StudyLoading(Modifier.fillMaxWidth())}
            else if(!allowed)item{StateMessage("Plus 个性化","开通会员后解锁专属头像装饰、身份标识与学习偏好。","查看会员权益",onSubscription)}
            else {
                item {
                    Surface(color=MaterialTheme.colorScheme.surface, shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {
                        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            MemberAvatar(avatar, MemberAppearance(true,frame,badge), 64.dp)
                            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                Text(displayName, style=MaterialTheme.typography.titleMedium)
                                MemberLabel(MemberAppearance(true,frame,badge))
                                Text("你的身份预览",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                item {
                    SectionLabel("头像装饰")
                    listOf("obsidian" to "曜石 · 简洁沉稳", "champagne" to "香槟 · 温润金属", "azure" to "雾蓝 · 清透克制", "none" to "原始头像").forEach { (key,title) ->
                        Row(Modifier.fillMaxWidth().clickable(enabled=!saving){frame=key;saved=false}.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            MemberAvatar(avatar,MemberAppearance(true,key,badge),40.dp)
                            Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                            RadioButton(frame==key,null)
                        }
                    }
                }
                item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)){Text("展示 Plus 身份标识");Text("昵称旁显示克制的会员铭牌",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    Switch(badge,{badge=it;saved=false},enabled=!saving)
                } }
                item { HorizontalDivider(); SectionLabel("学习偏好")
                    Text("以下设置保存在当前账户的本机中。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item{OutlinedTextField(greeting,{greeting=it.take(40);saved=false},label={Text("首页专属问候")},placeholder={Text("保持专注，一起进步")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
                item{OutlinedTextField(goal,{goal=it.filter(Char::isDigit).take(3);saved=false},label={Text("每日目标题数（5–200）")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
                item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("首页显示每日一句",Modifier.weight(1f));Switch(quote,{quote=it;saved=false})}}
                item{SectionLabel("阅读与练习")}
                item{
                    Text("题干字号",style=MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        listOf(16,18,20,22).forEach { size -> FilterChip(textSize==size,{textSize=size;saved=false},label={Text("$size")}) }
                    }
                    RenderText("字号预览：阅读舒适，专注每一道题。",fontSize=textSize.sp)
                }
                item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("选择后立即提交");Text("关闭后先保留草稿，点击提交再查看解析。多选题仍需确认提交。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(feedback,{feedback=it;saved=false})}}
                item{
                    saveError?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
                    val validGoal=goal.toIntOrNull()?.let{it in 5..200}==true
                    if(!validGoal)Text("每日目标需要在 5–200 题之间",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                    Button(onClick={scope.launch{
                        saving=true
                        try {
                            saveError=null
                            identity=RetrofitClient.create(context).saveMemberAppearance(MemberAppearanceRequest(frame,badge))
                            allowed=identity.is_plus
                            if(allowed){prefs.personalize(greeting,quote,feedback,goal.toInt(),textSize);saved=true}
                        }catch(e:kotlinx.coroutines.CancellationException){throw e}
                        catch(e:Exception){saveError=if(e is retrofit2.HttpException && e.code()==403) "会员状态已变更，请重新确认会员权益" else "保存失败，你的选择已保留，请重试"}
                        finally{saving=false}
                    }},enabled=validGoal&&!saving,modifier=Modifier.fillMaxWidth().heightIn(min=46.dp)){Text(if(saving)"保存中…" else if(saved)"已保存" else "保存装扮与偏好")}
                }
            }
        }
    }
}
