package cn.zhzgo.study.ui.screens
import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.zhzgo.study.data.*
import cn.zhzgo.study.network.RetrofitClient
import cn.zhzgo.study.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val api = RetrofitClient.create(application)

    private val _stats = MutableStateFlow<UserStats?>(null)
    val stats = _stats.asStateFlow()

    private val _detailed = MutableStateFlow<DetailedStatsResponse?>(null)
    val detailed = _detailed.asStateFlow()

    private val _aiAnalysis = MutableStateFlow<String?>(null)
    val aiAnalysis = _aiAnalysis.asStateFlow()

    private val _isLoadingAi = MutableStateFlow(false)
    val isLoadingAi = _isLoadingAi.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _loading=MutableStateFlow(false)
    val loading=_loading.asStateFlow()
    private val _progress=MutableStateFlow<List<ProgressResponse>?>(null)
    val progress=_progress.asStateFlow()
    fun load() {
        if(_loading.value)return
        _loading.value=true;_error.value=null
        viewModelScope.launch {
            try {
                val stats=async{api.getUserStats()}
                val detailed=async{api.getDetailedStats()}
                val progress=async{api.getProgress()}
                _stats.value=stats.await();_detailed.value=detailed.await();_progress.value=progress.await()
            }catch(e:CancellationException){throw e}
             catch(e:Exception){_error.value="学习数据更新失败，请稍后重试"}
            finally{_loading.value=false}
        }
    }

    fun requestAiAnalysis() {
        if(_isLoadingAi.value)return
        val s = _stats.value ?: return
        val d = _detailed.value ?: return
        _isLoadingAi.value = true
        _aiAnalysis.value = null

        viewModelScope.launch {
            try {
                // Build structured data for the dedicated analytics endpoint
                val subjectStatsList = d.subjectStats.map { sub ->
                    mapOf(
                        "subject_name" to sub.subject_name,
                        "total" to sub.total,
                        "correct" to sub.correct,
                        "accuracy" to sub.accuracy
                    )
                }
                val typeStatsList = d.typeStats.map { ts ->
                    mapOf(
                        "type" to ts.type,
                        "total" to ts.total,
                        "correct" to ts.correct,
                        "accuracy" to ts.accuracy
                    )
                }

                val result = api.getAiLearningAnalysis(
                    mapOf(
                        "totalQuestions" to s.total_questions,
                        "accuracy" to s.accuracy,
                        "activeDays" to s.active_days,
                        "streak" to d.streak,
                        "subjectStats" to subjectStatsList,
                        "typeStats" to typeStatsList
                    )
                )

                val text = result["analysis"] as? String ?: "AI 分析暂不可用"
                _aiAnalysis.value = text
            }catch(e:CancellationException){throw e}
            catch (e: Exception) {
                _aiAnalysis.value = "学习建议暂时不可用，请稍后重试。"
            } finally {
                _isLoadingAi.value = false
            }
        }

    }

    private fun typeLabel(t: String) = when (t) {
        "single" -> "单选题"
        "multiple" -> "多选题"
        "judge" -> "判断题"
        "fill" -> "填空题"
        "material" -> "材料题"
        "sort" -> "排序题"
        else -> t
    }
}

// ─── Screen ──────────────────────────────────────────────────────────────────

@Composable
fun StatsScreen(onBack:()->Unit,viewModel:StatsViewModel=viewModel(),root:Boolean=false) {
    val stats by viewModel.stats.collectAsState()
    val detailed by viewModel.detailed.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val ai by viewModel.aiAnalysis.collectAsState()
    val loadingAi by viewModel.isLoadingAi.collectAsState()
    val context=androidx.compose.ui.platform.LocalContext.current
    val prefs=remember{StudyPreferences(context)}
    val memory by prefs.memory.collectAsState(initial=StudyMemory())
    RefreshOnResume(viewModel::load)
    val recentDays=(6 downTo 0).map{StudyCalendar.today().minusDays(it.toLong()).toString()}
    val seconds=recentDays.sumOf{memory.activity[it]?.seconds ?: 0L}
    val wrong=progress?.filter{it.is_correct==0}
    val mastered=wrong?.count{it.question_id in memory.mastered}
    StudyPage("学习统计",if(root)null else onBack,actions={TextButton(onClick=viewModel::load,enabled=!loading){Text("刷新")}}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=24.dp)) {
            item {
                Text("看见积累，找到下一步",style=MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(20.dp))
                Metric(if(seconds>=3600)"${seconds/3600} 小时 ${seconds%3600/60} 分" else if(seconds>=60)"${seconds/60} 分钟" else "$seconds 秒","近 7 天本机学习时长")
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth()) {
                    Metric(stats?.total_questions?.toString() ?: "—","累计作答",Modifier.weight(1f))
                    Metric(stats?.accuracy?.let{"$it%"} ?: "—","整体正确率",Modifier.weight(1f))
                    Metric(detailed?.streak?.toString() ?: "—","连续学习 / 天",Modifier.weight(1f))
                }
                Spacer(Modifier.height(20.dp))
                if(loading)cn.zhzgo.study.ui.components.StudyLoading(Modifier.fillMaxWidth())
                error?.let{StateMessage("数据暂未更新",it,"重新加载",viewModel::load)}
            }
            if(detailed!=null)item {
                SectionLabel("最近 7 天","作答题数")
                ActivityBars(detailed!!.weekHeatmap)
                Spacer(Modifier.height(20.dp))
            }
            item {
                SectionLabel("复习进展")
                Row(Modifier.fillMaxWidth().padding(bottom=16.dp)) {
                    Metric(wrong?.size?.toString() ?: "—","错题",Modifier.weight(1f))
                    Metric(mastered?.toString() ?: "—","已掌握",Modifier.weight(1f))
                    Metric(if(wrong==null)"—" else "${wrong!!.size-(mastered?:0)}","待复习",Modifier.weight(1f))
                }
                if(wrong!=null && wrong!!.isNotEmpty())QuietProgress((mastered?:0).toFloat()/wrong!!.size)
                Text("掌握标记按本机复习记录统计。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp,bottom=20.dp))
            }
            item{SectionLabel("主要科目表现")}
            if(detailed?.subjectStats.isNullOrEmpty()&&!loading)item{StateMessage("还没有科目表现记录","完成练习后，可以在这里比较各科的正确率。")}
            items(detailed?.subjectStats.orEmpty(),key={it.subject_id}){s->
                Column(Modifier.padding(vertical=12.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(s.subject_name,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                        Text("${s.accuracy}%",style=MaterialTheme.typography.titleMedium)
                    }
                    Text("已答 ${s.total} · 正确 ${s.correct}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=4.dp,bottom=10.dp))
                    QuietProgress(s.accuracy/100f)
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            }
            item{Spacer(Modifier.height(20.dp));SectionLabel("题型表现")}
            items(detailed?.typeStats.orEmpty(),key={it.type}){t->DetailPair(questionTypeLabel(t.type),"${t.accuracy}% · 已答 ${t.total}")}
            item {
                Spacer(Modifier.height(24.dp));SectionLabel("学习建议")
                OutlinedButton(onClick=viewModel::requestAiAnalysis,enabled=!loadingAi&&stats!=null&&detailed!=null){Text(if(loadingAi)"正在分析…" else "生成 AI 学习建议")}
                ai?.let{Spacer(Modifier.height(12.dp));RenderText(it)}
                Spacer(Modifier.height(20.dp))
                SectionLabel("正确率趋势","近 7 天 · 本机记录")
                AccuracyTrend(recentDays.map{date->date to memory.activity[date]})
                Text("记录本机当日首次作答；学习时长仅计算前台练习，不回填过去记录。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=12.dp))
            }
        }
    }
}
@Composable
private fun ActivityBars(data:List<WeekDay>) {
    val days=(6 downTo 0).map{StudyCalendar.today().minusDays(it.toLong())}
    val counts=days.map{day->data.firstOrNull{it.day.take(10)==day.toString()}?.count ?: 0}
    val maximum=counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    Row(Modifier.fillMaxWidth().semantics{contentDescription=days.zip(counts).joinToString{(date,count)->"$date，$count 题"}},horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Bottom) {
        days.forEachIndexed{i,day->
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                Text("${counts[i]}",style=MaterialTheme.typography.labelMedium)
                Box(Modifier.height(100.dp).padding(vertical=8.dp),contentAlignment=Alignment.BottomCenter) {
                    Box(Modifier.width(14.dp).height(maxOf(2f,84f*counts[i]/maximum).dp).background(if(counts[i]>0)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,MaterialTheme.shapes.small))
                }
                Text("${day.monthValue}/${day.dayOfMonth}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AccuracyTrend(days:List<Pair<String,DailyPractice?>>) {
    val values=days.map{(_,day)->day?.outcomes?.takeIf{it.isNotEmpty()}?.let{answers->answers.values.count{it}.toFloat()/answers.size}}
    if(values.all{it==null}) {
        StateMessage("还没有本机趋势记录","完成练习后，查看每天的首次作答正确率。")
        return
    }
    val brand=MaterialTheme.colorScheme.primary
    val line=MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(96.dp).semantics {contentDescription=days.mapIndexed{i,day->"${day.first}，${values[i]?.let{(it*100).toInt().toString()+"%"} ?: "暂无记录"}"}.joinToString("；")}) {
        val inset=8.dp.toPx();val width=(size.width-inset*2)/(days.size-1).coerceAtLeast(1)
        for(ratio in listOf(0f,.5f,1f))drawLine(line,Offset(inset,inset+(1-ratio)*(size.height-inset*2)),Offset(size.width-inset,inset+(1-ratio)*(size.height-inset*2)),1.dp.toPx())
        values.forEachIndexed{i,value->if(value!=null) {
            val point=Offset(inset+i*width,inset+(1-value)*(size.height-inset*2))
            if(i>0&&values[i-1]!=null)drawLine(brand,Offset(inset+(i-1)*width,inset+(1-values[i-1]!!)*(size.height-inset*2)),point,2.dp.toPx(),StrokeCap.Round)
            drawCircle(brand,3.dp.toPx(),point)
        }}
    }
    Row(Modifier.fillMaxWidth()) {days.forEachIndexed{i,(date,_)->Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
        Text(values[i]?.let{"${(it*100).toInt()}%"} ?: "—",style=MaterialTheme.typography.labelSmall)
        Text(date.takeLast(5).replace('-','/'),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }}}
}
