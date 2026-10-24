package cn.zhzgo.study.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.zhzgo.study.ui.components.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun HomeScreen(onContinue: (Int, String) -> Unit, onBank: () -> Unit, onLearning: () -> Unit,
    onProfile: () -> Unit, onExam: () -> Unit, onSchedule: () -> Unit,
    onFavorites: () -> Unit = {}, onSearch: () -> Unit = {}, onLeaderboard: () -> Unit = {}, viewModel: HomeViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val memory by viewModel.memory.collectAsState()
    val plus by viewModel.plus.collectAsState()
    var showGoal by rememberSaveable { mutableStateOf(false) }
    var showNotice by rememberSaveable { mutableStateOf(false) }
    val announcement = state.home?.settings?.takeIf { it["app_announcement_enabled"] in listOf("true", "1") }
        ?.get("app_announcement")?.trim()?.takeIf { it.isNotEmpty() }
    val colors = MaterialTheme.colorScheme
    val today by produceState(cn.zhzgo.study.data.StudyCalendar.today()) {
        while(true) { kotlinx.coroutines.delay(30_000);value=cn.zhzgo.study.data.StudyCalendar.today() }
    }
    val session = memory.session
    val todayStats = state.detailed?.today?.takeIf { it.day == today.toString() }
    val done = todayStats?.total
        ?: if (state.detailed == null || state.detailed?.today != null) null else state.detailed?.weekHeatmap?.firstOrNull { it.day.take(10) == today.toString() }?.count ?: 0
    val seconds = memory.activity[today.toString()]?.seconds ?: 0L
    val fraction = ((done ?: 0).toFloat() / memory.dailyGoal.coerceAtLeast(1)).coerceIn(0f, 1f)
    RefreshOnResume(viewModel::refresh)
    LaunchedEffect(today) { viewModel.refresh() }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(20.dp, 14.dp, 20.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${cn.zhzgo.study.utils.GreetingUtils.getGreeting()}，", style = MaterialTheme.typography.headlineMedium)
                    Text(if(plus && memory.greeting.isNotBlank())memory.greeting else "保持专注，一起进步", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                }
                IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "搜索题目", Modifier.size(22.dp)) }
                IconButton(onClick = { showNotice = true }) { Icon(Icons.Outlined.NotificationsNone, "公告与通知", Modifier.size(22.dp)) }
            }
        }
        if (announcement != null) item {
            Surface(onClick={showNotice=true}, color=colors.primary.copy(alpha=.05f), shape=RoundedCornerShape(10.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min=44.dp).padding(horizontal=12.dp,vertical=10.dp), verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Campaign,null,Modifier.size(19.dp),tint=colors.primary)
                    Text(announcement,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Icon(Icons.Outlined.ChevronRight,"查看完整公告",Modifier.size(16.dp),tint=colors.onSurfaceVariant)
                }
            }
        }
        if (state.announcementError) item { TextButton(onClick=viewModel::refresh) { Text("公告暂时无法更新 · 重试",style=MaterialTheme.typography.bodySmall) } }
        item {
            MountainBanner(if (session == null) "开始刷题" else "继续刷题",
                session?.let { "${it.subjectName} · 顺序练习" } ?: "选择科目，开始今天的学习") {
                if (session == null) onBank() else onContinue(session.subjectId, session.subjectName)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("今日进度", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 10.dp))
                    Box(Modifier.clickable { showGoal = true }) {
                        ProgressRing(fraction, if (done == null) "—" else "${(fraction * 100).toInt()}")
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row {
                        HomeStat(done?.let { "$it/${memory.dailyGoal}" } ?: "—", "今日完成", Modifier.weight(1f))
                        HomeStat(todayStats?.takeIf { it.total > 0 }?.accuracy?.let { "$it%" } ?: "—", "今日正确率", Modifier.weight(1f))
                    }
                    Row {
                        HomeStat(if (seconds >= 3600) "${seconds / 3600}.${seconds % 3600 / 360}" + " h" else "${seconds / 60} 分", "本机学习时长", Modifier.weight(1f))
                        HomeStat(state.detailed?.streak?.let { "$it 天" } ?: "—", "连续打卡", Modifier.weight(1f))
                    }
                }
            }
            if (state.loading && state.detailed == null) cn.zhzgo.study.ui.components.StudyLoading(Modifier.fillMaxWidth().padding(top = 12.dp))
            state.error?.let { TextButton(onClick = viewModel::refresh) { Text("$it · 重试", color = colors.error) } }
            Text("今日题库提交记录 · 复习与模拟考试单独记录",style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant,modifier=Modifier.padding(top=8.dp))
        }
        item {
            Row(Modifier.fillMaxWidth()) {
                HomeShortcut("错题本", Icons.Outlined.AssignmentLate, Modifier.weight(1f), onLearning)
                HomeShortcut("收藏夹", Icons.Outlined.StarBorder, Modifier.weight(1f), onFavorites)
                HomeShortcut("题库", Icons.Outlined.Article, Modifier.weight(1f), onBank)
                HomeShortcut("学习计划", Icons.Outlined.EventNote, Modifier.weight(1f), onSchedule)
            }
        }
        item {
            StudyRow("学习排行榜", "刷题数量、正确率与练习分数", onClick = onLeaderboard)
        }
        if (state.daily != null && (!plus || memory.showDailyQuote)) item {
            Text("每日一句", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
            Text(state.daily?.proverb?.en ?: state.daily?.poetry?.line ?: state.daily?.quote.orEmpty(),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Text(state.daily?.proverb?.zh ?: state.daily?.poetry?.src.orEmpty(), style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        val countdown = state.home?.countdowns?.mapNotNull { c ->
            runCatching { c to ChronoUnit.DAYS.between(today, LocalDate.parse(c.target_date.orEmpty().take(10))) }.getOrNull()
        }?.filter { it.second >= 0 }?.minByOrNull { it.second }
        if (countdown != null) item {
            HorizontalDivider(color = colors.outlineVariant)
            Row(Modifier.fillMaxWidth().clickable(onClick = onExam).padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(countdown.first.title ?: "模拟考试", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("${countdown.second} 天", style = MaterialTheme.typography.titleSmall)
                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(17.dp), tint = colors.onSurfaceVariant)
            }
        } else item {
            TextButton(onClick = onExam, modifier = Modifier.fillMaxWidth()) { Text("模拟考试", color = colors.onSurfaceVariant) }
        }
    }
    if (showNotice) AlertDialog(onDismissRequest = { showNotice = false }, title = { Text("公告与通知") },
        text = { Column(Modifier.heightIn(max=380.dp).verticalScroll(rememberScrollState())) {
            if (state.loading && state.home == null) StudyLoading(Modifier.fillMaxWidth())
            else if (state.announcementError && announcement == null) {
                Text("公告加载失败，请检查网络后重试。")
                TextButton(onClick=viewModel::refresh) { Text("重新加载") }
            } else {
                if(state.announcementError) Text("暂时显示上次获取的公告",style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
                RenderText(announcement ?: "暂时没有新公告。")
            }
        } },
        confirmButton = { TextButton(onClick = { showNotice = false }) { Text("知道了") } })
    if (showGoal) AlertDialog(onDismissRequest = { showGoal = false }, title = { Text("每日刷题目标") }, text = {
        Column { listOf(10, 20, 30, 50, 100).forEach { goal ->
            TextButton(onClick = { viewModel.setGoal(goal); showGoal = false }, modifier = Modifier.fillMaxWidth()) {
                Text("$goal 题${if (goal == memory.dailyGoal) " · 当前" else ""}")
            }
        } }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = { showGoal = false }) { Text("取消") } })
}

@Composable
private fun HomeStat(value: String, label: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeShortcut(title: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).heightIn(min = 72.dp).padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(25.dp))
        Text(title, style = MaterialTheme.typography.bodySmall)
    }
}
