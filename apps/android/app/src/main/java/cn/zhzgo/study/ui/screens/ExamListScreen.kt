package cn.zhzgo.study.ui.screens
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.zhzgo.study.ui.components.*
@Composable
fun ExamListScreen(onBack:()->Unit,onNavigateToPrep:(Int)->Unit,onNavigateToResult:(Int)->Unit,viewModel:ExamViewModel=viewModel()) {
    val papers by viewModel.papers.collectAsState();val records by viewModel.records.collectAsState()
    val loading by viewModel.isLoading.collectAsState();val recordsLoading by viewModel.recordsLoading.collectAsState();val error by viewModel.error.collectAsState()
    var tab by rememberSaveable{mutableIntStateOf(0)};var query by rememberSaveable{mutableStateOf("")}
    RefreshOnResume{viewModel.loadPapers();viewModel.loadRecords()}
    val visible=papers.filter{it.title.contains(query.trim(),true)}
    StudyPage("模拟考试",onBack){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=24.dp)) {
            item {
                Text("检验准备，找到差距",style=MaterialTheme.typography.headlineSmall)
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(vertical=12.dp)) {
                    item{FilterChip(tab==0,{tab=0},label={Text("试卷目录")})}
                    item{FilterChip(tab==1,{tab=1},label={Text("考试记录")})}
                }
                if(tab==0)OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("搜索试卷")},shape=MaterialTheme.shapes.medium)
                if(loading||recordsLoading)cn.zhzgo.study.ui.components.StudyLoading(Modifier.fillMaxWidth().padding(top=12.dp))
                error?.let{StateMessage("暂时无法更新",it,"重试",{if(tab==0)viewModel.loadPapers()else viewModel.loadRecords()})}
            }
            if(tab==0) {
                if(!loading&&visible.isEmpty())item{StateMessage("暂无匹配试卷","调整关键词，或稍后查看新开放的试卷。")}
                items(visible,key={it.id}){paper->
                    Column(Modifier.padding(vertical=16.dp)) {
                        Text(paper.title,style=MaterialTheme.typography.titleMedium)
                        Text("${paper.question_count} 题 · ${paper.duration_minutes} 分钟 · 满分 ${paper.total_score}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=4.dp))
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.padding(top=8.dp)) {
                            OutlinedButton(onClick={onNavigateToPrep(paper.id)}){Text(if(paper.can_take)"查看并开始" else "查看须知")}
                            paper.latest_record?.let{record->TextButton(onClick={onNavigateToResult(record.id)}){Text(if(paper.has_completed)"查看成绩" else "查看记录")}}
                        }
                        if(!paper.can_take)Text(if(paper.is_in_progress)"已有进行中的考试记录" else if(paper.has_abandoned)"考试已中止，如需重考请联系管理员" else "当前不可重考",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                }
            } else {
                if(!recordsLoading&&records.isEmpty())item{StateMessage("还没有考试记录","完成一次模拟考试后，在这里回顾成绩。")}
                items(records,key={it.id}){r->StudyRow(r.paper_title ?: "考试记录 ${r.id}","${r.finished_at?.take(10) ?: r.started_at?.take(10).orEmpty()} · ${when(r.status){"completed"->"已完成";"abandoned"->"已中止";else->"进行中"}}",trailing=if(r.status=="completed")"${r.score} 分" else null,onClick={onNavigateToResult(r.id)})}
            }
        }
    }
}
