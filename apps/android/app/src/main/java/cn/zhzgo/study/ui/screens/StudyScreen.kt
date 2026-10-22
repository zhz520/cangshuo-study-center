package cn.zhzgo.study.ui.screens

import androidx.compose.foundation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.zhzgo.study.data.Subject
import cn.zhzgo.study.ui.components.*

@Composable
fun StudyScreen(onSubjectSelected: (Subject, Boolean) -> Unit, onNavigateToSearch: () -> Unit = {},
    viewModel: StudyViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val memory by viewModel.memory.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var major by rememberSaveable { mutableStateOf<Int?>(null) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val colors = MaterialTheme.colorScheme
    RefreshOnResume(viewModel::refresh)
    LaunchedEffect(state.majors) { if (major == null && state.majors.isNotEmpty()) major = state.majors.first().id }
    val activeMajor = if (query.isNotBlank()) -1 else major ?: -1
    val recentIds = memory.recent.map { it.id }
    val visible = state.subjects.filter { subject ->
        (activeMajor == -1 || subject.major_id == activeMajor) &&
        (filter != 1 || subject.id in memory.pinned) && (filter != 2 || subject.id in recentIds) &&
        (filter != 3 || state.progress.none { it.subject_id == subject.id }) &&
        (subject.name.contains(query.trim(), true) || state.majors.firstOrNull { it.id == subject.major_id }?.name?.contains(query.trim(), true) == true)
    }.let { list -> if (filter == 2) list.sortedBy { recentIds.indexOf(it.id) } else list }
    fun open(subject: Subject) { viewModel.visit(subject); onSubjectSelected(subject, false) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("题库", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onNavigateToSearch) { Icon(Icons.Outlined.ManageSearch, "搜索题目与知识点", Modifier.size(22.dp)) }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            StudySearchField(query, { query = it }, "搜索科目、专业")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)) {
                items(listOf("全部", "常用", "最近学习", "未开始").withIndex().toList()) { (i, label) ->
                    ReferencePill(label, filter == i) { filter = i }
                }
            }
        }
        Row(Modifier.weight(1f)) {
            LazyColumn(Modifier.width(78.dp).fillMaxHeight().background(colors.onSurface.copy(alpha = .018f))) {
                item { MajorRailItem("全部", activeMajor == -1) { major = -1; query = "" } }
                items(state.majors, key = { it.id }) { item ->
                    MajorRailItem(item.name.removeSuffix("类"), activeMajor == item.id) { major = item.id; query = "" }
                }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 14.dp, end = 20.dp, bottom = 24.dp)) {
                if (state.loading && state.subjects.isEmpty()) item { cn.zhzgo.study.ui.components.StudyLoading(Modifier.fillMaxWidth()) }
                state.error?.let { message -> item { StateMessage("暂时无法更新", message, "重试", viewModel::refresh) } }
                if (!state.loading && visible.isEmpty()) item {
                    StateMessage("暂无匹配科目", "调整分类或关键词，点击书签可设为常用。", "显示全部", { query = ""; major = -1; filter = 0 })
                }
                items(visible, key = { it.id }) { subject ->
                    val count = subject.question_count ?: 0
                    val completed = state.progress.count { it.subject_id == subject.id }
                    Row(Modifier.fillMaxWidth().clickable { open(subject) }.heightIn(min = 76.dp).padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        SubjectEmblem(subject.name)
                        Column(Modifier.weight(1f)) {
                            Text(subject.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("共 $count 题", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp))
                            if (completed > 0 && count > 0) QuietProgress(completed.toFloat() / count,
                                Modifier.padding(top = 7.dp).widthIn(max = 90.dp).height(2.dp))
                        }
                        IconButton(onClick = { viewModel.pin(subject.id) }, modifier = Modifier.size(44.dp)) {
                            Icon(if (subject.id in memory.pinned) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                                if (subject.id in memory.pinned) "取消常用：${subject.name}" else "设为常用：${subject.name}",
                                Modifier.size(16.dp), tint = if (subject.id in memory.pinned) colors.primary else colors.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 49.dp), color = colors.outlineVariant.copy(alpha = .55f))
                }
            }
        }
    }
}

@Composable
private fun MajorRailItem(title: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(22.dp).background(if (selected) colors.primary else androidx.compose.ui.graphics.Color.Transparent))
        Text(title, modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            style = MaterialTheme.typography.bodySmall, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant)
    }
}
