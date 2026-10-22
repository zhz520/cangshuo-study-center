package cn.zhzgo.study.ui.screens
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.zhzgo.study.data.*
import cn.zhzgo.study.ui.components.*
import kotlinx.coroutines.delay
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamTakingScreen(paperId:Int,recordId:Int,onExamFinished:(Int)->Unit,onBack:()->Unit,viewModel:ExamViewModel=viewModel()) {
    val exam by viewModel.currentExam.collectAsState();val answers by viewModel.userAnswers.collectAsState()
    val result by viewModel.submitResult.collectAsState();val abandoned by viewModel.isAbandoned.collectAsState()
    val abandoning by viewModel.isAbandoning.collectAsState();val submitting by viewModel.isSubmitting.collectAsState();val error by viewModel.error.collectAsState()
    var index by rememberSaveable(recordId){mutableIntStateOf(0)}
    var sheet by remember{mutableStateOf(false)};var quit by remember{mutableStateOf(false)};var submit by remember{mutableStateOf(false)}
    var marked by rememberSaveable(recordId){mutableStateOf(listOf<Int>())}
    var image by remember{mutableStateOf<String?>(null)}
    var remaining by remember(recordId){mutableIntStateOf(viewModel.remainingSeconds())}
    TrackStudyTime(exam?.record_id==recordId&&!abandoned&&!submitting&&remaining>0)
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner,recordId) {
        val observer=LifecycleEventObserver{_,event->
            if(event==Lifecycle.Event.ON_STOP && viewModel.currentExam.value?.record_id==recordId && !viewModel.isAbandoned.value && !viewModel.isSubmitting.value && viewModel.submitResult.value==null)viewModel.abandonExam(recordId)
        }
        owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(recordId,exam?.record_id) {
        if(exam?.record_id==recordId) {
            remaining=viewModel.remainingSeconds()
            while(remaining>0 && !viewModel.isAbandoned.value && viewModel.currentExam.value?.record_id==recordId) {delay(250);remaining=viewModel.remainingSeconds()}
            if(remaining==0 && !viewModel.isAbandoned.value)viewModel.submitExam(recordId){}
        }
    }
    LaunchedEffect(result?.record_id,abandoning) {if(result?.record_id==recordId&&!abandoning)onExamFinished(recordId)}
    BackHandler{if(!submitting&&!abandoning){if(exam==null||abandoned)onBack()else quit=true}}
    val current=exam?.takeIf{it.record_id==recordId}
    if(current==null||abandoned) {
        StudyPage("模拟考试",onBack={if(!submitting&&!abandoning)onBack()}){padding->
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                if(abandoning) {cn.zhzgo.study.ui.components.StudyLoading(Modifier.fillMaxWidth());StateMessage("正在中止考试","正在同步考试状态…")}
                else if(abandoned)StateMessage("本次考试已中断",error ?: "正在获取考试状态。","重新同步",{viewModel.abandonExam(recordId)})
                else StateMessage("考试会话不可用","返回试卷目录查看考试记录。","返回目录",onBack)
            }
        }
        return
    }
    val questions=current.questions
    val question=questions.getOrNull(index)
    val answered=ExamWorkflow.answeredIds(questions.map{it.id},answers)
    val blocked=submitting || remaining<=0
    val scroll=rememberScrollState()
    LaunchedEffect(question?.id){scroll.scrollTo(0)}
    StudyPage(current.paper.title,onBack={if(!submitting)quit=true},actions={
        Text("%02d:%02d".format(remaining/60,remaining%60),style=MaterialTheme.typography.titleMedium,color=if(remaining<300)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        if(question!=null)IconButton(onClick={marked=if(question.id in marked)marked-question.id else marked+question.id},enabled=!blocked){Icon(if(question.id in marked)Icons.Outlined.Flag else Icons.Outlined.OutlinedFlag,"标记此题")}
    },bottomBar={Surface {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                IconButton(onClick={sheet=true},enabled=!submitting){Icon(Icons.Outlined.GridView,"答题卡")}
                TextButton(onClick={index--},enabled=index>0&&!submitting){Text("上一题")}
                Button(onClick={if(index<questions.lastIndex)index++ else submit=true},enabled=!submitting&&questions.isNotEmpty(),modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                    Text(if(submitting)"正在交卷…" else if(index<questions.lastIndex)"下一题" else "检查并交卷")
                }
            }
        }
    }}){padding->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(scroll).padding(horizontal=20.dp).padding(bottom=24.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("${index+1} / ${questions.size}",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                Text("已答 ${answered.size}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            QuietProgress((index+1).toFloat()/questions.size.coerceAtLeast(1))
            error?.let{StateMessage("交卷未完成",it,"重试交卷",{viewModel.submitExam(recordId){}})}
            if(remaining<=0)Text("时间已到，答案已锁定。若交卷失败，请重试。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(top=12.dp))
            if(question!=null) {
                Spacer(Modifier.height(24.dp))
                val type=if(question.type=="multi")"multiple" else question.type
                Text("${questionTypeLabel(type)} · ${question.score} 分",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                RenderText(question.content,fontSize=MaterialTheme.typography.bodyLarge.fontSize*1.125f,onImageClick={image=it})
                Spacer(Modifier.height(24.dp))
                val options=remember(question.id){QuestionOptions.parse(question.options)}
                val answer=answers[question.id.toString()].orEmpty()
                when(type) {
                    "single"->SingleChoiceQuestion(options,answer,{if(!blocked)viewModel.setAnswer(question.id,it)},false,null)
                    "multiple"->MultiChoiceQuestion(options,answer.split(",").filter{it.isNotBlank()}.toSet(),{if(!blocked)viewModel.setAnswer(question.id,ExamWorkflow.toggleMultiple(answer,it))},false,null)
                    "judge"->JudgeQuestion(answer,{if(!blocked)viewModel.setAnswer(question.id,it)},false,null)
                    "fill","text","sort"->OutlinedTextField(answer,{viewModel.setAnswer(question.id,it)},Modifier.fillMaxWidth(),readOnly=blocked,label={Text("填写答案")},textStyle=MaterialTheme.typography.bodyLarge,shape=MaterialTheme.shapes.medium)
                    else->StateMessage("暂不支持此题型","请联系管理员检查试卷内容。")
                }
                TextButton(onClick={sheet=true},enabled=!submitting,modifier=Modifier.padding(top=16.dp)){Text("检查全卷作答情况")}
            } else StateMessage("试卷没有可作答题目","请返回目录联系管理员。")
        }
    }
    if(sheet)ModalBottomSheet(onDismissRequest={sheet=false},containerColor=MaterialTheme.colorScheme.surface) {
        AnswerSheetDrawer(questions.map{Question(it.id,null,it.type,it.content,it.options,null,null,null,null,null)},index,
            answers.mapNotNull{(key,value)->key.toIntOrNull()?.let{it to value as Any}}.toMap(),{index=it;sheet=false},marked=marked.toSet())
        TextButton(onClick={questions.indices.firstOrNull{questions[it].id !in answered}?.let{index=it};sheet=false},enabled=answered.size<questions.size,modifier=Modifier.fillMaxWidth()){Text("跳到未作答题目")}
        Button(onClick={sheet=false;submit=true},enabled=!submitting,modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp).heightIn(min=48.dp)){Text("检查并交卷")}
        Spacer(Modifier.height(24.dp))
    }
    if(quit)AlertDialog(onDismissRequest={quit=false},title={Text("退出本次考试？")},text={Text("退出将中止本次考试，成绩记为 0 分。")},
        confirmButton={TextButton(onClick={quit=false;viewModel.abandonExam(recordId)}){Text("中止考试",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={quit=false}){Text("继续考试")}})
    if(submit)AlertDialog(onDismissRequest={submit=false},title={Text("确认交卷")},text={Text("已答 ${answered.size} 题，未答 ${questions.size-answered.size} 题。提交后无法修改答案。")},
        confirmButton={TextButton(onClick={submit=false;viewModel.submitExam(recordId){}},enabled=!submitting){Text("确认交卷")}},dismissButton={TextButton(onClick={submit=false;sheet=true}){Text("检查答题卡")}})
    image?.let{ImageViewerDialog(it){image=null}}
}
