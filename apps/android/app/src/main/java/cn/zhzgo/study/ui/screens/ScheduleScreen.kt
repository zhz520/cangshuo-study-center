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
import java.time.LocalDate
@Composable
fun ScheduleScreen(onBack:()->Unit,viewModel:ScheduleViewModel=viewModel()) {
    val state by viewModel.uiState.collectAsState()
    var day by rememberSaveable{mutableIntStateOf(LocalDate.now().dayOfWeek.value)}
    val labels=listOf("周一","周二","周三","周四","周五","周六","周日")
    StudyPage("课程表",onBack,actions={TextButton(onClick=viewModel::fetchSchedule){Text("刷新")}}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=24.dp)) {
            item {
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {items(labels.withIndex().toList()){(index,label)->FilterChip(day==index+1,{day=index+1},label={Text(label)})}}
                Spacer(Modifier.height(16.dp));SectionLabel(labels[day-1])
            }
            when(val value=state) {
                is ScheduleUiState.Loading->item{cn.zhzgo.study.ui.components.StudyLoading(Modifier.fillMaxWidth())}
                is ScheduleUiState.Error->item{StateMessage("课程加载失败",value.message,"重试",viewModel::fetchSchedule)}
                is ScheduleUiState.Success->{
                    val cells=value.cells.filter{it.day_of_week==day}.sortedBy{it.sort_order}
                    if(cells.isEmpty())item{StateMessage("今天没有课程安排","可以为自主学习留出一点时间。")}
                    items(cells,key={it.id}){cell->DetailPair(cell.period,listOfNotNull(cell.subject,cell.location?.takeIf{it.isNotBlank()}).joinToString("\n"))}
                }
            }
        }
    }
}
