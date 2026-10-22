package cn.zhzgo.study.ui.screens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.zhzgo.study.ui.components.*
import cn.zhzgo.study.ui.viewmodels.FavoritesViewModel
@Composable
fun FavoritesScreen(onNavigateToPracticeMode:(List<Int>)->Unit,onBack:()->Unit,viewModel:FavoritesViewModel=viewModel()) {
    val state by viewModel.state.collectAsState()
    var subject by rememberSaveable{mutableStateOf("")}
    var type by rememberSaveable{mutableStateOf("")}
    var query by rememberSaveable{mutableStateOf("")}
    var managing by rememberSaveable{mutableStateOf(false)}
    var selected by remember{mutableStateOf(setOf<Int>())}
    var remove by remember{mutableStateOf(setOf<Int>())}
    RefreshOnResume(viewModel::resume)
    val visible=state.questions.filter{(subject.isBlank() || it.subject_name==subject)&&(type.isBlank()||it.type==type)&&it.content.contains(query.trim(),true)}
    StudyPage("我的收藏",onBack,actions={IconButton(onClick={viewModel.load()},enabled=!state.loading&&!state.removing){Icon(Icons.Outlined.Refresh,"刷新收藏")};TextButton(onClick={managing=!managing;selected=emptySet()},enabled=!state.removing){Text(if(managing)"完成" else "管理")}},bottomBar={
        if(visible.isNotEmpty() || selected.isNotEmpty())Surface {
            Column(Modifier.navigationBarsPadding().padding(horizontal=20.dp,vertical=8.dp)) {
                if(managing)Text("已选 ${selected.size} 题 · 每批最多 20 题",style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(managing)OutlinedButton(onClick={remove=selected},enabled=selected.isNotEmpty()&&!state.removing,modifier=Modifier.weight(1f)){Text("取消收藏")}
                    Button(onClick={onNavigateToPracticeMode(if(managing)selected.toList() else visible.take(20).map{it.id})},
                        enabled=!state.removing&&(!managing||selected.isNotEmpty()),modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Text(if(managing)"练习所选" else "练习当前列表")}
                }
            }
        }
    }){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=24.dp)) {
            item {
                Text("共 ${state.total?.toString() ?: "—"} 题 · 已加载 ${state.questions.size} 题",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp,bottom=14.dp))
                StudySearchField(query,{query=it;selected=emptySet()},"搜索已加载收藏")
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(top=8.dp)) {
                    item{ReferencePill("全部科目",subject.isBlank()){subject="";selected=emptySet()}}
                    items(state.questions.mapNotNull{it.subject_name}.distinct()){s->ReferencePill(s,subject==s){subject=s;selected=emptySet()}}
                }
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    item{ReferencePill("全部题型",type.isBlank()){type="";selected=emptySet()}}
                    items(state.questions.map{it.type}.distinct()){t->ReferencePill(questionTypeLabel(t),type==t){type=t;selected=emptySet()}}
                }
                if(state.loading)cn.zhzgo.study.ui.components.StudyLoading(Modifier.fillMaxWidth())
            }
            state.error?.let{message->item{StateMessage("操作未完成",message,"重试",{viewModel.load()})}}
            if(!state.loading&&visible.isEmpty())item{StateMessage(if(state.questions.isEmpty())"还没有收藏题目" else "没有匹配的收藏",if(state.questions.isEmpty())"在刷题时点击书签，保存想再次回顾的题目。" else "调整筛选，或继续加载更多收藏。")}
            visible.groupBy{it.subject_name ?: "未分类科目"}.forEach{(name,questions)->
                item{SectionLabel(name,"${questions.size} 题")}
                items(questions,key={it.id}){q->
                    Column(Modifier.fillMaxWidth().clickable(enabled=!state.removing){
                        if(managing)selected=if(q.id in selected)selected-q.id else if(selected.size<20)selected+q.id else selected
                        else onNavigateToPracticeMode(listOf(q.id))
                    }.padding(vertical=12.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            if(managing)Checkbox(q.id in selected,onCheckedChange=null)
                            Text(questionTypeLabel(q.type),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
                            if(!managing)IconButton(onClick={remove=setOf(q.id)},enabled=!state.removing&&!state.loading){Icon(Icons.Outlined.BookmarkRemove,"取消收藏",Modifier.size(20.dp))}
                        }
                        Text(q.content.replace(Regex("<[^>]*>"),""),style=MaterialTheme.typography.bodyMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                    }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                }
            }
            if(!state.end)item{TextButton(onClick={viewModel.load(true)},enabled=!state.loading&&!state.removing,modifier=Modifier.fillMaxWidth()){Text(if(state.loading)"正在加载…" else "加载更多收藏")}}
        }
    }
    if(remove.isNotEmpty())AlertDialog(onDismissRequest={if(!state.removing)remove=emptySet()},title={Text("取消 ${remove.size} 题收藏？")},text={Text("取消后，仍可在原题库中找到这些题目。")},
        confirmButton={TextButton(onClick={viewModel.remove(remove){selected=selected-remove;remove=emptySet()}},enabled=!state.removing&&!state.loading){Text(if(state.removing)"正在处理…" else "取消收藏")}},
        dismissButton={TextButton(onClick={remove=emptySet()},enabled=!state.removing){Text("保留")}})
}
