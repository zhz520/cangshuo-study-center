package cn.zhzgo.study.ui.screens
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.repeatOnLifecycle
import cn.zhzgo.study.data.UserPreferences
import cn.zhzgo.study.ui.components.*
import androidx.compose.foundation.clickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionScreen(subjectId:Int,subjectName:String,isFavorites:Boolean=false,isMistakes:Boolean=false,questionId:Int=0,
    onBack:()->Unit,onNavigateToSubscription:()->Unit={},onNavigateToResult:(Int,Int,Int,Int,Int,Int)->Unit={_,_,_,_,_,_->},
    reviewIds:List<Int> = emptyList(),viewModel:QuestionViewModel=viewModel()) {
    LaunchedEffect(subjectId,isFavorites,isMistakes,questionId,reviewIds) {viewModel.loadQuestions(subjectId,isFavorites,isMistakes,questionId,reviewIds)}
    RefreshOnResume(viewModel::refreshAccess)
    val uiState by viewModel.uiState.collectAsState()
    val currentQuestion=uiState.questions.getOrNull(uiState.currentIndex)
    val isSubmitted=currentQuestion?.id in uiState.submittedQuestions
    val hasAnswer=when(val answer=currentQuestion?.let{uiState.userAnswers[it.id]}) {is String->answer.isNotBlank();is Collection<*>->answer.isNotEmpty();else->false}
    val saving=uiState.pendingSubmissionIds.isNotEmpty()
    var elapsedSeconds by rememberSaveable(subjectId,isFavorites,isMistakes,questionId) { mutableIntStateOf(0) }
    val lifecycleOwner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    val readable by rememberUpdatedState(currentQuestion!=null&&!uiState.isInitialLoading)
    LaunchedEffect(lifecycleOwner,subjectId,isFavorites,isMistakes,questionId) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while(true) { kotlinx.coroutines.delay(1000); if(readable)elapsedSeconds++ }
        }
    }
    TrackStudyTime(currentQuestion!=null&&!uiState.isInitialLoading)
    val context=androidx.compose.ui.platform.LocalContext.current
    val prefs=remember{UserPreferences(context)}
    val email by prefs.email.collectAsState(initial=null)
    val FREE_QUESTION_LIMIT=20
    val isPlus=!uiState.previewOnly
    var showAnswerSheet by remember{mutableStateOf(false)}
    var showResetConfirmDialog by remember{mutableStateOf(false)}
    var showLockedDialog by remember{mutableStateOf(false)}
    var showReportDialog by remember{mutableStateOf(false)}
    var showFinish by remember{mutableStateOf(false)}
    var menu by remember{mutableStateOf(false)}
    var viewerImageUrl by remember{mutableStateOf<String?>(null)}
    var reportReason by remember{mutableStateOf("")}
    var reportDetail by remember{mutableStateOf("")}
    var isSubmittingReport by remember{mutableStateOf(false)}
    var marked by rememberSaveable{mutableStateOf(listOf<Int>())}
    BackHandler(enabled=saving) { android.widget.Toast.makeText(context,"答案正在保存，请稍候",android.widget.Toast.LENGTH_SHORT).show() }
    val scroll=rememberScrollState()
    LaunchedEffect(currentQuestion?.id) {scroll.scrollTo(0)}
    LaunchedEffect(currentQuestion?.id,uiState.pageOffset) {viewModel.checkpoint(subjectName)}
    fun next() {if(uiState.previewOnly && uiState.currentIndex+uiState.pageOffset+1>=20)showLockedDialog=true else viewModel.nextQuestion()}
    fun finish() {onNavigateToResult(uiState.currentScore,uiState.maxScore,viewModel.getCorrectCount(),viewModel.getIncorrectCount(),uiState.questions.size-uiState.submittedQuestions.size,elapsedSeconds)}
    Scaffold(topBar={
        TopAppBar(expandedHeight=56.dp,title={Text(subjectName,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)},navigationIcon={IconButton(onClick=onBack,enabled=!saving){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}},actions={
            if(currentQuestion!=null) {
                Box {
                    IconButton(onClick={menu=true}){Icon(Icons.Default.MoreHoriz,"更多练习操作")}
                    DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                        DropdownMenuItem(text={Text(if(uiState.isShuffleMode) "切换顺序练习" else "随机练习")},onClick={menu=false;viewModel.toggleShuffleMode()},enabled=!saving)
                        DropdownMenuItem(text={Text(if(uiState.immediateFeedback) "改为提交后反馈" else "改为即时反馈")},onClick={menu=false;viewModel.setFeedback(!uiState.immediateFeedback)},enabled=!saving)
                        DropdownMenuItem(text={Text(if(currentQuestion.id in marked) "取消标记" else "标记此题")},onClick={menu=false;marked=if(currentQuestion.id in marked)marked-currentQuestion.id else marked+currentQuestion.id})
                        if(isSubmitted)DropdownMenuItem(text={Text("重新作答此题")},onClick={menu=false;viewModel.retryQuestion(currentQuestion.id)},enabled=!saving)
                        DropdownMenuItem(text={Text("题目纠错")},onClick={menu=false;showReportDialog=true})
                        if(!isFavorites && !isMistakes && subjectId>0)DropdownMenuItem(text={Text("重置科目进度")},onClick={menu=false;showResetConfirmDialog=true},enabled=!saving)
                        DropdownMenuItem(text={Text("跳过此题")},onClick={menu=false;next()},enabled=!saving && !uiState.isLoadingMore && !(uiState.currentIndex==uiState.questions.lastIndex && uiState.isLastPage))
                        DropdownMenuItem(text={Text("结束本次练习")},onClick={menu=false;showFinish=true},enabled=!saving)
                    }
                }
            }
        },colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background))
    },bottomBar={if(currentQuestion!=null)Surface(color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                IconButton(onClick={showAnswerSheet=true},enabled=!saving) {Icon(Icons.Default.GridView,"答题卡")}
                TextButton(onClick=viewModel::prevQuestion,enabled=!saving && !uiState.isLoadingMore && (uiState.currentIndex>0 || uiState.pageOffset>0)){Text("上一题")}
                val atEnd=uiState.currentIndex==uiState.questions.lastIndex && uiState.isLastPage
                Button(onClick={when { !isSubmitted->viewModel.submitCurrentQuestion();atEnd->showFinish=true;else->next() }},
                    enabled=!saving && !uiState.isLoadingMore && (isSubmitted || hasAnswer),modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=RoundedCornerShape(12.dp)) {
                    Text(when {saving->"正在保存…";!isSubmitted->"提交答案";atEnd->"查看结果";else->"下一题"})
                }
            }
        }
    }},containerColor=MaterialTheme.colorScheme.background) { padding ->
        if(currentQuestion==null)Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            if(uiState.isInitialLoading) StudyLoading(message="正在恢复答题记录…")
            else StateMessage("暂时没有可练习的题目",uiState.errorMessage ?: "可以返回题库选择其他科目。",if(uiState.errorMessage!=null) "重新加载" else "返回",{
                if(uiState.errorMessage!=null)viewModel.loadQuestions(subjectId,isFavorites,isMistakes,questionId,reviewIds)else onBack()
            })
        } else Column(Modifier.fillMaxSize().padding(padding).verticalScroll(scroll).padding(horizontal=20.dp).padding(bottom=24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top=2.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("${uiState.pageOffset+uiState.currentIndex+1} / ${uiState.totalCount ?: uiState.questions.size}",style=MaterialTheme.typography.bodySmall)
                Box(Modifier.weight(1f)) {
                    QuietProgress((uiState.pageOffset+uiState.currentIndex+1).toFloat()/(uiState.totalCount ?: (uiState.pageOffset+uiState.questions.size)).coerceAtLeast(1),Modifier.widthIn(max=90.dp))
                }
                Icon(Icons.Default.Timer,null,Modifier.size(14.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("%02d:%02d".format(elapsedSeconds/60,elapsedSeconds%60),style=MaterialTheme.typography.bodySmall)
                val favorite=currentQuestion.id in uiState.favoriteQuestionIds
                IconButton(onClick={viewModel.toggleFavorite(currentQuestion.id)},enabled=!uiState.isLoadingFavorites && currentQuestion.id !in uiState.updatingFavorites) {
                    Icon(if(favorite)Icons.Default.Star else Icons.Default.StarBorder,if(favorite)"取消收藏" else "收藏题目",Modifier.size(22.dp),tint=if(favorite)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
            if(uiState.previewOnly)TextButton(onClick=onNavigateToSubscription,contentPadding=PaddingValues(0.dp)){Text("免费预览前 20 题 · 查看会员权益",style=MaterialTheme.typography.bodySmall)}
            uiState.errorMessage?.let { message ->
                StateMessage("操作未完成",message,if(!isSubmitted && hasAnswer) "重试保存" else "重试加载",{if(!isSubmitted && hasAnswer)viewModel.submitCurrentQuestion() else viewModel.loadNextPage()})
            }
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(5.dp)) {
                    Text(questionTypeLabel(currentQuestion.type),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary,
                        modifier=Modifier.padding(horizontal=7.dp,vertical=3.dp))
                }
                Text(if(currentQuestion.type=="multiple") "选择所有正确选项" else if(uiState.immediateFeedback) "选择后即时反馈" else "提交后显示解析",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            QuestionContent(currentQuestion,onImageClick={viewerImageUrl=it})
            val options=uiState.parsedOptionsMap[currentQuestion.id] ?: emptyMap()
            val correct=viewModel.normalizeAnswer(currentQuestion.type,currentQuestion.answer)
            when(currentQuestion.type) {
                "single"->SingleChoiceQuestion(options,uiState.userAnswers[currentQuestion.id] as? String,{viewModel.selectSingleOption(currentQuestion.id,it)},isSubmitted,correct)
                "multiple"->{
                    @Suppress("UNCHECKED_CAST") val selected=uiState.userAnswers[currentQuestion.id] as? Set<String> ?: emptySet()
                    MultiChoiceQuestion(options,selected,{viewModel.toggleMultiOption(currentQuestion.id,it)},isSubmitted,correct)
                }
                "judge"->JudgeQuestion(uiState.userAnswers[currentQuestion.id] as? String,{viewModel.selectJudgeOption(currentQuestion.id,it)},isSubmitted,correct)
                "fill","text","sort"->FillBlankQuestion(uiState.userAnswers[currentQuestion.id] as? String ?: "",{viewModel.enterFillBlank(currentQuestion.id,it)},isSubmitted,currentQuestion.answer)
                else->Text("此题型请在网页端作答",style=MaterialTheme.typography.bodyMedium)
            }
            if(isSubmitted) {
                Spacer(Modifier.height(24.dp));HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                SectionLabel("答案与解析")
                Text("参考答案  $correct",style=MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                RenderText(currentQuestion.analysis?.takeIf{it.isNotBlank()} ?: "这道题暂时没有解析。")
                Row(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={viewModel.requestAiExplanation(currentQuestion)},enabled=!uiState.isLoadingAi && uiState.aiExplanation==null){Text(if(uiState.isLoadingAi) "正在讲解…" else "AI 讲解")}
                    if(isMistakes)TextButton(onClick={viewModel.retryQuestion(currentQuestion.id)}){Text("重新作答")}
                }
                if(isMistakes)TextButton(onClick={viewModel.markMastered(currentQuestion.id);android.widget.Toast.makeText(context,"已标记掌握",android.widget.Toast.LENGTH_SHORT).show()}){Text("我已掌握此题")}
                uiState.aiExplanation?.let { explanation ->
                    Spacer(Modifier.height(16.dp));SectionLabel("AI 讲解");RenderText(explanation)
                    uiState.aiRemaining?.let {Text("今日剩余 $it 次",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                }
            }
            if(uiState.isLoadingMore)cn.zhzgo.study.ui.components.StudyLoading(Modifier.fillMaxWidth().padding(top=16.dp))
        }
        if(showAnswerSheet)ModalBottomSheet(onDismissRequest={showAnswerSheet=false},containerColor=MaterialTheme.colorScheme.surface) {
            AnswerSheetDrawer(uiState.questions,uiState.currentIndex,uiState.userAnswers,{i->viewModel.jumpToQuestion(i);showAnswerSheet=false},isPlus,FREE_QUESTION_LIMIT,uiState.submittedQuestions,marked.toSet(),uiState.pageOffset,onClose={showAnswerSheet=false})
        }
        if(showFinish)AlertDialog(onDismissRequest={showFinish=false},title={Text("结束本次练习？")},text={Text("本次已加载 ${uiState.questions.size} 题，已答 ${uiState.submittedQuestions.size} 题，未答 ${uiState.questions.size-uiState.submittedQuestions.size} 题。结果仅统计本次加载的题目，进度会保留。")},
            confirmButton={TextButton(onClick={showFinish=false;finish()}){Text("查看本次结果")}},dismissButton={TextButton(onClick={showFinish=false;showAnswerSheet=true}){Text("检查答题卡")}})
        if (showResetConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showResetConfirmDialog = false },
                title = { Text("重置进度") },
                text = { Text("确定要清空该科目的所有刷题记录吗？此操作不可恢复。") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.resetProgress(subjectId)
                        showResetConfirmDialog = false
                    }) {
                        Text("确定", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirmDialog = false }) {
                        Text("取消", color = MaterialTheme.colorScheme.onSurface)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        if (viewerImageUrl != null) {
            ImageViewerDialog(
                imageUrl = viewerImageUrl!!,
                onDismiss = { viewerImageUrl = null }
            )
        }

        // Locked question subscription prompt
        if (showLockedDialog) {
            AlertDialog(
                onDismissRequest = { showLockedDialog = false },
                icon = {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        "此题目需要 Plus 订阅",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        "免费用户每科可做前 ${FREE_QUESTION_LIMIT} 道题。\n升级 Plus 解锁全部题库，畅享无限刷题体验。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showLockedDialog = false
                            onNavigateToSubscription()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("查看 Plus 订阅", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLockedDialog = false }) {
                        Text("暂不需要", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        // Report Error Dialog
        if (showReportDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!isSubmittingReport) {
                        showReportDialog = false
                        reportReason = ""
                        reportDetail = ""
                    }
                },
                title = { Text("题目报错", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("选择报错类型:", fontSize = 14.sp)
                        val reasons = listOf("题目有误", "答案或解析有误", "选项有误", "排版乱码", "其他")
                        reasons.forEach { reason ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { reportReason = reason }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = reportReason == reason,
                                    onClick = { reportReason = reason }
                                )
                                Text(reason, fontSize = 14.sp)
                            }
                        }

                        OutlinedTextField(
                            value = reportDetail,
                            onValueChange = { reportDetail = it },
                            label = { Text("补充说明(选填)", fontSize = 14.sp) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                        )

                        if (email.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "为了能及时给您发送报错处理结果，请先前往“设置-账户信息”绑定邮箱。",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (email.isNullOrEmpty()) {
                                android.widget.Toast.makeText(context, "请先绑定邮箱再提交报错", android.widget.Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            if (reportReason.isEmpty()) {
                                android.widget.Toast.makeText(context, "请选择报错类型", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isSubmittingReport = true
                            viewModel.submitReport(currentQuestion!!.id, reportReason, reportDetail) { success, msg ->
                                isSubmittingReport = false
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                if (success) {
                                    showReportDialog = false
                                    reportReason = ""
                                    reportDetail = ""
                                }
                            }
                        },
                        enabled = !isSubmittingReport,
                        shape = RoundedCornerShape(8.dp),
                        colors = if (email.isNullOrEmpty()) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)) else ButtonDefaults.buttonColors()
                    ) {
                        if (isSubmittingReport) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(if (email.isNullOrEmpty()) "请先绑定邮箱" else "提交报错")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        if (!isSubmittingReport) {
                            showReportDialog = false
                            reportReason = ""
                            reportDetail = ""
                        }
                    }) {
                        Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}
