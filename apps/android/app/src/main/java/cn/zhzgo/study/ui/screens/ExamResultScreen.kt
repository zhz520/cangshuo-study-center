package cn.zhzgo.study.ui.screens
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.zhzgo.study.data.ExamAnswerDetail
import cn.zhzgo.study.ui.components.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
@Composable
fun ExamResultScreen(recordId:Int,onBack:()->Unit,viewModel:ExamViewModel=viewModel()) {
    val resultState by viewModel.submitResult.collectAsState();val records by viewModel.records.collectAsState();val papers by viewModel.papers.collectAsState()
    val loading by viewModel.recordsLoading.collectAsState();val error by viewModel.error.collectAsState()
    val result=resultState?.takeIf{it.record_id==recordId};val record=records.find{it.id==recordId}
    var onlyWrong by rememberSaveable{mutableStateOf(false)}
    LaunchedEffect(recordId){viewModel.loadRecords();if(papers.isEmpty())viewModel.loadPapers()}
    val details=remember(result,record){result?.details ?: runCatching{Gson().fromJson<List<ExamAnswerDetail>>(record?.answers,object:TypeToken<List<ExamAnswerDetail>>(){}.type)}.getOrNull()}
    val abandoned=record?.status=="abandoned" || (result!=null&&viewModel.isAbandoned.value)
    val inProgress=record?.status=="in_progress"&&result==null
    val passScore=papers.find{it.id==record?.paper_id}?.pass_score
    val score=result?.score ?: record?.score
    val total=result?.total_score ?: record?.total_score
    val passed=result?.passed ?: passScore?.let{score!=null&&score>=it}
    val shown=details.orEmpty().filter{!onlyWrong||!it.is_correct}
    fun finish(){viewModel.clearCurrentExam();onBack()}
    StudyPage("考试结果",::finish){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=24.dp)) {
            if(loading&&result==null)item{cn.zhzgo.study.ui.components.StudyLoading(Modifier.fillMaxWidth())}
            error?.let{msg->item{StateMessage("记录暂未更新",msg,"重试",viewModel::loadRecords)}}
            if(result==null&&record==null&&!loading)item{StateMessage("暂未找到考试结果","稍后刷新考试记录，或返回目录。","刷新",viewModel::loadRecords)}
            if(result!=null||record!=null)item {
                Text(record?.paper_title ?: "本次考试",style=MaterialTheme.typography.headlineSmall)
                Text(when{abandoned->"考试已中止";inProgress->"考试尚未完成";passed==true->"已通过";passed==false->"未通过";else->"已完成"},style=MaterialTheme.typography.bodyMedium,color=if(abandoned)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp,bottom=24.dp))
                if(!inProgress) {
                    Box(Modifier.fillMaxWidth(),contentAlignment=androidx.compose.ui.Alignment.Center) {
                        ProgressRing(if(total!=null&&total>0&&score!=null)score.toFloat()/total else 0f,score?.toString() ?: "—","分",if(total!=null)"总分 $total" else "得分",184.dp,androidx.compose.ui.graphics.Color(0xFF68CDB6))
                    }
                    Spacer(Modifier.height(24.dp))
                    if(!abandoned) {
                        Row {
                            Metric("${result?.correct_count ?: record?.correct_count ?: 0}","正确",Modifier.weight(1f))
                            Metric("${result?.incorrect_count ?: record?.incorrect_count ?: 0}","错误",Modifier.weight(1f))
                            Metric("${result?.unanswered_count ?: record?.unanswered_count ?: 0}","未答",Modifier.weight(1f))
                        }
                    }
                }
                record?.finished_at?.let{DetailPair("完成时间",it.take(19).replace('T',' '))}
                Spacer(Modifier.height(24.dp))
                if(!details.isNullOrEmpty()) {
                    SectionLabel("答题明细")
                    FilterChip(onlyWrong,{onlyWrong=!onlyWrong},label={Text("只看未答对")})
                }
            }
            items(shown,key={it.question_id}){d->
                Column(Modifier.padding(vertical=16.dp)) {
                    Row {
                        Text("第 ${d.question_index} 题",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                        Text(if(d.is_correct)"正确" else if(d.user_answer.isBlank())"未作答" else "错误",style=MaterialTheme.typography.bodySmall,color=if(d.is_correct)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                    DetailPair("我的答案",d.user_answer.ifBlank{"未作答"})
                    d.correct_answer?.let{DetailPair("参考答案",it)}
                    d.analysis?.takeIf{it.isNotBlank()}?.let{Spacer(Modifier.height(8.dp));RenderText(it)}
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            }
            item{Spacer(Modifier.height(24.dp));OutlinedButton(onClick=::finish,modifier=Modifier.fillMaxWidth().heightIn(min=44.dp)){Text("返回考试目录")}}
        }
    }
}
