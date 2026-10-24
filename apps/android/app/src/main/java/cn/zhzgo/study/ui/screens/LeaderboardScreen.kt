package cn.zhzgo.study.ui.screens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import cn.zhzgo.study.ui.viewmodels.LeaderboardViewModel
import coil.compose.AsyncImage
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(onBack:()->Unit,onUserClick:(Int)->Unit) {
    val model:LeaderboardViewModel=viewModel();val context=LocalContext.current
    val total by model.totalQuestionsRanking.collectAsState();val accuracy by model.accuracyRanking.collectAsState();val score by model.scoreRanking.collectAsState()
    val currentUser by model.currentUser.collectAsState();val subjects by model.subjects.collectAsState();val subject by model.selectedSubjectId.collectAsState()
    val loading by model.isLoading.collectAsState();val error by model.error.collectAsState()
    val moreTypes by model.moreTypes.collectAsState()
    var tab by rememberSaveable{mutableIntStateOf(0)};var sheet by remember{mutableStateOf(false)}
    RefreshOnResume{model.loadSubjects(context);model.resume(context)}
    val ranking=when(tab){0->total;1->accuracy;else->score}
    val mine=ranking.firstOrNull{it.username==currentUser}
    StudyPage("学习排行",onBack,actions={TextButton(onClick={model.loadRankings(context)},enabled=!loading){Text("刷新")};TextButton(onClick={sheet=true}){Text(subjects.firstOrNull{it.first==subject}?.second ?: "选择科目")}}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=24.dp)) {
            item {
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(listOf("刷题数量","正确率","练习分数").withIndex().toList()){(i,label)->FilterChip(tab==i,{tab=i},label={Text(label)})}
                }
                Text(mine?.let{"你在当前榜单排名第 ${it.rank} 位"} ?: "当前榜单暂无你的排名记录",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=16.dp))
                if(tab==1)Text(if(subject=="all")"累计完成至少 20 题后进入正确率榜" else "本科完成至少 5 题后进入正确率榜",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(loading)cn.zhzgo.study.ui.components.StudyLoading(Modifier.fillMaxWidth())
                error?.let{StateMessage("榜单暂未更新",it,"重试",{model.loadRankings(context)})}
            }
            if(!loading&&ranking.isEmpty())item{StateMessage("还没有排行记录","完成练习后再来看看。")}
            items(ranking,key={it.user_id}){user->
                Row(Modifier.fillMaxWidth().clickable{onUserClick(user.user_id)}.padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("${user.rank}",style=MaterialTheme.typography.titleMedium,color=if(user.username==currentUser)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.widthIn(min=24.dp))
                    MemberAvatar(user.avatar_icon, user.appearance, 40.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(user.nickname ?: user.username, style=MaterialTheme.typography.bodyMedium, maxLines=1, overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        MemberLabel(user.appearance)
                    }
                    Text("${user.score}${when(tab){0->" 题";1->"%";else->" 分"}}",style=MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            }
            if(tab in moreTypes)item{TextButton(onClick={model.loadMore(context)},enabled=!loading,modifier=Modifier.fillMaxWidth()){Text(if(loading)"加载中…" else "加载下一页")}}
        }
    }
    if(sheet)ModalBottomSheet(onDismissRequest={sheet=false},containerColor=MaterialTheme.colorScheme.surface) {
        LazyColumn(Modifier.heightIn(max=480.dp),contentPadding=PaddingValues(20.dp)) {
            item{SectionLabel("选择科目")}
            items(subjects,key={it.first}){(id,name)->StudyRow(name,trailing=if(id==subject)"当前" else null,onClick={model.selectSubject(id,context);sheet=false})}
        }
    }
}
