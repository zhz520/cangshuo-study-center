package cn.zhzgo.study.ui.screens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.zhzgo.study.ui.components.*
import cn.zhzgo.study.ui.viewmodels.SearchViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack:()->Unit,onNavigateToQuestion:(Int,String)->Unit,viewModel:SearchViewModel=viewModel()) {
    val state by viewModel.uiState.collectAsState();val focus=LocalFocusManager.current
    var subjectSheet by remember{mutableStateOf(false)}
    StudyPage("搜索题目",onBack,actions={IconButton(onClick={viewModel.search();focus.clearFocus()}){Icon(Icons.Outlined.Search,"执行搜索")}}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=24.dp)) {
            item {
                OutlinedTextField(state.query,viewModel::onQueryChange,Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("题干关键词")},
                    keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={viewModel.search();focus.clearFocus()}),shape=MaterialTheme.shapes.medium,
                    trailingIcon={if(state.query.isNotEmpty())IconButton(onClick={viewModel.onQueryChange("");viewModel.search()}){Icon(Icons.Outlined.Close,"清空搜索")}})
                TextButton(onClick={subjectSheet=true}){Text(state.subjects.find{it.id==state.selectedSubjectId}?.name ?: "全部科目")}
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(listOf("all","single","multiple","judge","fill")){type->FilterChip(state.selectedType==type,{viewModel.setTypeFilter(type)},label={Text(if(type=="all")"全部题型" else questionTypeLabel(type))})}
                }
                if(state.isSearching)cn.zhzgo.study.ui.components.StudyLoading(Modifier.fillMaxWidth())
                state.error?.let{StateMessage("搜索未完成",it,"重试",viewModel::search)}
                if(!state.hasSearched)StateMessage("从一个知识点开始","输入关键词，或按科目和题型查找。")
                else if(!state.isSearching&&state.results.isEmpty()&&state.error==null)StateMessage("没有找到匹配题目","换一组关键词或调整筛选条件。")
                if(state.results.isNotEmpty())SectionLabel("搜索结果","已加载 ${state.results.size} 题")
            }
            items(state.results,key={it.id}){q->
                Column(Modifier.fillMaxWidth().clickable{onNavigateToQuestion(q.id,q.subject_name ?: "题目详情")}.padding(vertical=14.dp)) {
                    Text(listOfNotNull(q.subject_name,questionTypeLabel(q.type)).joinToString(" · "),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(q.content.replace(Regex("<[^>]*>"),""),style=MaterialTheme.typography.bodyMedium,maxLines=3,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=8.dp))
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            }
            if(state.hasSearched&&!state.hasReachedEnd&&state.results.isNotEmpty())item{TextButton(onClick=viewModel::loadMore,enabled=!state.isLoading&&!state.isSearching,modifier=Modifier.fillMaxWidth()){Text(if(state.isLoading)"正在加载…" else "加载更多结果")}}
        }
    }
    if(subjectSheet)ModalBottomSheet(onDismissRequest={subjectSheet=false},containerColor=MaterialTheme.colorScheme.surface) {
        LazyColumn(Modifier.heightIn(max=480.dp),contentPadding=PaddingValues(20.dp)) {
            item{SectionLabel("选择科目");StudyRow("全部科目",onClick={viewModel.setSubjectFilter(0);subjectSheet=false})}
            items(state.subjects,key={it.id}){s->StudyRow(s.name,onClick={viewModel.setSubjectFilter(s.id);subjectSheet=false})}
        }
    }
}
