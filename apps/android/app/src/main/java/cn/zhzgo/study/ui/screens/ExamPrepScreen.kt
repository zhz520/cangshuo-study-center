package cn.zhzgo.study.ui.screens
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.zhzgo.study.ui.components.*
@Composable
fun ExamPrepScreen(paperId:Int,onBack:()->Unit,onStartExam:(Int,Int)->Unit,viewModel:ExamViewModel=viewModel()) {
    val papers by viewModel.papers.collectAsState();val loading by viewModel.isLoading.collectAsState();val error by viewModel.error.collectAsState()
    var confirm by remember{mutableStateOf(false)}
    val paper=papers.find{it.id==paperId}
    LaunchedEffect(paperId){if(paper==null)viewModel.loadPapers()}
    StudyPage("考试须知",onBack,bottomBar={if(paper!=null)Surface {
        Column(Modifier.navigationBarsPadding().padding(horizontal=20.dp,vertical=10.dp)) {
            Button(onClick={confirm=true},enabled=paper.can_take&&!loading,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(if(loading)"正在准备…" else if(paper.can_take)"开始考试" else "当前不可考试")}
        }
    }}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=24.dp)) {
            if(loading)item{cn.zhzgo.study.ui.components.StudyLoading(Modifier.fillMaxWidth())}
            error?.let{item{StateMessage("操作未完成",it,"重新加载",viewModel::loadPapers)}}
            if(paper!=null)item {
                Text(paper.title,style=MaterialTheme.typography.headlineMedium)
                paper.description?.let{Text(it,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp))}
                Row(Modifier.fillMaxWidth().padding(vertical=24.dp)) {
                    Metric("${paper.question_count}","题目",Modifier.weight(1f))
                    Metric("${paper.duration_minutes}","分钟",Modifier.weight(1f))
                    Metric("${paper.total_score}","满分",Modifier.weight(1f))
                }
                DetailPair("及格分数","${paper.pass_score} 分")
                Spacer(Modifier.height(24.dp));SectionLabel("作答规则")
                Text("切换应用、锁屏或退出会中止本次考试，成绩记为 0 分。",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                Text(paper.instructions?.takeIf{it.isNotBlank()} ?: "考试开始后启动倒计时，时间结束自动交卷。提交后无法修改答案。请保持网络稳定；如需重考，请联系管理员。",style=MaterialTheme.typography.bodyLarge)
                if(!paper.can_take)Text("本次考试暂不可开始，请查看考试记录或联系管理员。",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=16.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
            } else if(!loading&&error==null)item{StateMessage("试卷暂不可用","返回目录选择其他试卷。","返回",onBack)}
        }
    }
    if(confirm&&paper!=null)AlertDialog(onDismissRequest={if(!loading)confirm=false},title={Text("准备开始考试？")},text={Text("共 ${paper.question_count} 题，限时 ${paper.duration_minutes} 分钟。开始后切换应用或锁屏会中止考试。")},
        confirmButton={TextButton(onClick={confirm=false;viewModel.startExam(paperId){viewModel.currentExam.value?.let{onStartExam(paperId,it.record_id)}}},enabled=!loading){Text("确认开始")}},dismissButton={TextButton(onClick={confirm=false}){Text("再检查一下")}})
}
