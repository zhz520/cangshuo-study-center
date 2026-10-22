package cn.zhzgo.study.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
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
import cn.zhzgo.study.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MistakesScreen(subjectId: Int? = null, subjectName: String = "错题本", onBack: () -> Unit = {},
    onSubject: (Int, String) -> Unit = { _, _ -> }, onReview: (Int, String, List<Int>) -> Unit,
    onFavorites: () -> Unit = {}, onStats: () -> Unit = {}, onLeaderboard: () -> Unit = {}, onData: () -> Unit = {},
    viewModel: MistakesViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val memory by viewModel.memory.collectAsState()
    val colors = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableIntStateOf(0) }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf(setOf<Int>()) }
    var managing by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    RefreshOnResume { viewModel.resume(subjectId) }
    val wrong = state.progress.filter { it.is_correct == 0 && (subjectId == null || subjectId <= 0 || it.subject_id == subjectId) }
    val mastered = wrong.count { it.question_id in memory.mastered }
    val visible = state.questions.filter { q ->
        (type.isBlank() || q.type == type) && (category.isBlank() || q.category == category) &&
            (status == 0 || (q.id in memory.mastered) == (status == 2)) && q.content.contains(query.trim(), true)
    }
    val grouped = wrong.filter { status == 0 || (it.question_id in memory.mastered) == (status == 2) }.groupBy { it.subject_id }
    Scaffold(containerColor = colors.background, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { if (subjectId != null) TopAppBar(expandedHeight = 56.dp,
            title = { Text(subjectName, style = MaterialTheme.typography.titleLarge) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回错题本") } },
            actions = { TextButton(onClick = { managing = !managing; selected = emptySet(); mode = 1 }) { Text(if (managing) "完成" else "管理") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)) },
        bottomBar = { if (subjectId != null && managing && selected.isNotEmpty()) Surface {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("已选 ${selected.size} 题 · 每批最多 20 题", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.mark(selected, status != 2); selected = emptySet() }, modifier = Modifier.weight(1f)) {
                        Text(if (status == 2) "移回待复习" else "标记掌握")
                    }
                    Button(onClick = { onReview(subjectId, subjectName, selected.toList()) }, modifier = Modifier.weight(1f)) { Text("复习所选") }
                }
            }
        } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).then(if (subjectId == null) Modifier.statusBarsPadding() else Modifier),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = if (subjectId == null) 12.dp else 4.dp, bottom = 28.dp)) {
            if (subjectId == null) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("错题本", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { search = !search }) { Icon(Icons.Outlined.Search, "搜索科目", Modifier.size(22.dp)) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "更多学习工具", Modifier.size(22.dp)) }
                            DropdownMenu(menu, { menu = false }) {
                                listOf("我的收藏" to onFavorites, "学习统计" to onStats, "学习排行" to onLeaderboard, "数据中心" to onData, "返回" to onBack).forEach { (label, action) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; action() })
                                }
                            }
                        }
                    }
                    if (search) { Spacer(Modifier.height(8.dp)); StudySearchField(query, { query = it }, "搜索错题科目") }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 12.dp, bottom = 18.dp)) {
                        items(listOf("全部", "待复习", "已掌握").withIndex().toList()) { (i, label) -> ReferencePill(label, status == i) { status = i } }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("错题总数", style = MaterialTheme.typography.bodySmall)
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text(if (state.loading) "—" else "${wrong.size}", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                                val counts = wrong.groupBy { it.subject_id }.values.map { it.size }.take(7)
                                val peak = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
                                Row(Modifier.height(35.dp).padding(bottom = 5.dp), verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    counts.forEachIndexed { i, n -> Box(Modifier.width(6.dp).height((10f + n * 23f / peak).dp)
                                        .background(colors.primary.copy(alpha = .25f + .1f * (i % 7)), androidx.compose.foundation.shape.RoundedCornerShape(2.dp))) }
                                }
                            }
                        }
                        Column(Modifier.width(112.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                            Row(Modifier.fillMaxWidth()) { Text("待复习", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)); Text("${wrong.size - mastered}", style = MaterialTheme.typography.titleSmall) }
                            Row(Modifier.fillMaxWidth()) { Text("已掌握", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)); Text("$mastered", style = MaterialTheme.typography.titleSmall) }
                        }
                    }
                    Button(onClick = { onSubject(-1, "全部错题") }, enabled = wrong.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.onSurface, contentColor = colors.surface),
                        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 20.dp).heightIn(min = 46.dp)) {
                        Text("开始复习"); Icon(Icons.Outlined.East, null, Modifier.padding(start = 8.dp).size(17.dp))
                    }
                    SectionLabel("按科目查看")
                }
                grouped.forEach { (id, records) ->
                    val title = state.subjects.firstOrNull { it.id == id }?.name ?: "科目 $id"
                    if (title.contains(query.trim(), true)) item(key = id) {
                        val count = records.count { it.question_id in memory.mastered }
                        Row(Modifier.fillMaxWidth().clickable { onSubject(id, title) }.heightIn(min = 72.dp).padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SubjectEmblem(title, 36.dp)
                            Column(Modifier.weight(1f)) {
                                Text(title, style = MaterialTheme.typography.titleMedium)
                                Text("错题 ${records.size}   待巩固 ${records.size - count}   已掌握 $count", style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
                            }
                            Text("${records.size}", style = MaterialTheme.typography.titleSmall)
                            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                        }
                        HorizontalDivider(Modifier.padding(start = 48.dp), color = colors.outlineVariant.copy(alpha = .55f))
                    }
                }
                if (!state.loading && grouped.isEmpty()) item { StateMessage("当前还没有错题", "完成练习后，错题会自动归入这里。") }
                item { Text("掌握标记保存在本机", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp)) }
            } else {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("分类", "题型", "掌握状态").forEachIndexed { i, label -> ReferencePill(label, mode == i) { mode = i } }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("错题 ${wrong.size}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        Text("待巩固 ${wrong.size - mastered}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        Text("已掌握 $mastered", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
                if (mode == 0) {
                    val categories = state.questions.groupBy { it.category?.takeIf { value -> value.isNotBlank() } ?: "全部错题" }
                    categories.forEach { (label, questions) -> item(key = label) {
                        val count = questions.count { it.id in memory.mastered }
                        Row(Modifier.fillMaxWidth().clickable { category = if (label == "全部错题") "" else label; mode = 1 }
                            .heightIn(min = 66.dp).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Outlined.Article, null, Modifier.size(19.dp), tint = colors.primary)
                            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("${questions.size}", style = MaterialTheme.typography.titleSmall, color = colors.primary)
                            QuietProgress(count.toFloat() / questions.size, Modifier.width(44.dp).height(2.dp))
                            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                        }
                        HorizontalDivider(color = colors.outlineVariant.copy(alpha = .5f))
                    } }
                    item { Text("分类统计基于已加载 ${state.questions.size} 题", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp)) }
                } else if (mode == 2) {
                    items(listOf(1 to "待复习", 2 to "已掌握")) { (value, label) ->
                        StudyRow(label, trailing = if (value == 1) "${wrong.size - mastered}" else "$mastered") { status = value; mode = 1 }
                    }
                } else {
                    item {
                        StudySearchField(query, { query = it; selected = emptySet() }, "搜索已加载错题")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            item { ReferencePill("全部", type.isBlank()) { type = ""; selected = emptySet() } }
                            items(state.questions.map { it.type }.distinct()) { t -> ReferencePill(questionTypeLabel(t), type == t) { type = t; selected = emptySet() } }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${visible.size} 题 · 收录顺序倒序", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                            if (category.isNotBlank()) TextButton(onClick = { category = "" }) { Text("全部分类") }
                            TextButton(onClick = { onReview(subjectId, subjectName, visible.filter { it.id !in memory.mastered }.take(20).map { it.id }) }, enabled = visible.any { it.id !in memory.mastered }) { Text("复习本页") }
                        }
                    }
                    items(visible, key = { it.id }) { q ->
                        Column(Modifier.fillMaxWidth().clickable {
                            if (managing) selected = if (q.id in selected) selected - q.id else if (selected.size < 20) selected + q.id else selected
                            else onReview(subjectId, subjectName, listOf(q.id))
                        }.padding(vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (managing) Checkbox(q.id in selected, null)
                                Text(questionTypeLabel(q.type), style = MaterialTheme.typography.labelMedium, color = colors.primary, modifier = Modifier.weight(1f))
                                if (!managing) IconButton(onClick = { viewModel.mark(setOf(q.id), q.id !in memory.mastered) }) {
                                    Icon(if (q.id in memory.mastered) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                        if (q.id in memory.mastered) "移回待复习" else "标记已掌握", Modifier.size(18.dp))
                                }
                            }
                            Text(q.content.replace(Regex("<[^>]*>"), "").replace("\n", " "), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${subjectName} · ${if (q.id in memory.mastered) "已掌握" else "待复习"}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 7.dp))
                        }
                        HorizontalDivider(color = colors.outlineVariant.copy(alpha = .55f))
                    }
                    if (!state.loading && visible.isEmpty()) item { StateMessage("没有匹配的错题", "调整筛选或继续加载下一页。") }
                }
                if (!state.end) item { TextButton(onClick = viewModel::more, enabled = !state.paging, modifier = Modifier.fillMaxWidth()) { Text(if (state.paging) "正在加载…" else "加载更多错题") } }
            }
            if (state.loading) item { cn.zhzgo.study.ui.components.StudyLoading(Modifier.fillMaxWidth()) }
            state.error?.let { message -> item { StateMessage("暂时无法更新", message, "重试", { if (state.questions.isEmpty()) viewModel.refresh(subjectId) else viewModel.more() }) } }
        }
    }
}
