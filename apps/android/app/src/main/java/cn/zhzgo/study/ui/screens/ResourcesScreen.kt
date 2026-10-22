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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.zhzgo.study.R
import cn.zhzgo.study.data.UserPreferences
import cn.zhzgo.study.ui.components.*
import cn.zhzgo.study.ui.viewmodels.ResourcesViewModel
import cn.zhzgo.study.ui.viewmodels.ToolsViewModel
import coil.compose.AsyncImage

@Composable
fun ResourcesScreen(onNavigateToArticle: (String) -> Unit = {}, onNavigateToCategory: (Int) -> Unit = {},
    onNavigateToContribution: () -> Unit = {}, onNavigateToTool: (String) -> Unit = {},
    onNavigateToAllTools: () -> Unit = {}, onNavigateToLinks: () -> Unit = {}) {
    val model: ResourcesViewModel = viewModel()
    val state by model.state.collectAsState()
    val tools: ToolsViewModel = viewModel()
    val allTools by tools.allTools.collectAsState()
    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }
    val selectedTools by prefs.dashboardTools.collectAsState(initial = "")
    val email by prefs.email.collectAsState(initial = null)
    var query by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    RefreshOnResume(model::resume)
    val articles = state.articles.filter { it.title.contains(query.trim(), true) || it.summary.orEmpty().contains(query.trim(), true) }
    val featured = if (query.isBlank() && state.category == null) articles.firstOrNull { it.is_pinned == 1 } ?: articles.firstOrNull() else null
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("资源中心", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { model.load() }, enabled = !state.loading) { Icon(Icons.Outlined.Refresh, "刷新资源", Modifier.size(22.dp)) }
            IconButton(onClick = { search = !search }) { Icon(Icons.Outlined.Search, "搜索资源", Modifier.size(22.dp)) }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item {
                if (search) { Spacer(Modifier.height(10.dp)); StudySearchField(query, { query = it }, "搜索已加载资源") }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 10.dp, bottom = 14.dp)) {
                    item { ReferencePill("推荐", state.category == null) { model.category(null) } }
                    items(state.categories, key = { it }) { category -> ReferencePill(category, state.category == category) { model.category(category) } }
                }
            }
            if (featured != null) item {
                Box(Modifier.fillMaxWidth().heightIn(min = 138.dp).clip(MaterialTheme.shapes.medium).clickable { onNavigateToArticle(featured.id.toString()) }) {
                    if (!featured.getCover().isNullOrBlank()) AsyncImage(featured.getCover(), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    else Image(painterResource(R.drawable.study_landscape), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .36f)))
                    Row(Modifier.fillMaxWidth().heightIn(min = 138.dp).padding(18.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(featured.category ?: "编辑精选", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .85f))
                            Text(featured.title, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium,
                                color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        }
                        Icon(Icons.Outlined.ArrowCircleRight, "阅读精选", Modifier.size(32.dp), tint = Color.White)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (state.loading) item { cn.zhzgo.study.ui.components.StudyLoading(Modifier.fillMaxWidth()) }
            state.error?.let { message -> item { StateMessage("暂时无法更新", message, "重试", { model.load() }) } }
            if (!state.loading && articles.isEmpty()) item { StateMessage("暂无匹配内容", "调整关键词或分类后再试。") }
            items(articles.filter { it.id != featured?.id }, key = { it.id }) { article -> ResourceArticleRow(article) { onNavigateToArticle(article.id.toString()) } }
            if (!state.end) item { TextButton(onClick = { model.load(true) }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text(if (state.loading) "正在加载…" else "加载更多内容") } }
            if (query.isBlank() && state.category == null) {
                item { Spacer(Modifier.height(20.dp)); SectionLabel("工具与服务") }
                val favoriteTools = selectedTools.split(",").mapNotNull { id -> allTools.find { it.id == id } }.take(4)
                items(favoriteTools, key = { "tool_${it.id}" }) { tool -> StudyRow(tool.name, onClick = { onNavigateToTool(tool.route) }) }
                item {
                    StudyRow("全部工具", icon = Icons.Outlined.Widgets, onClick = onNavigateToAllTools)
                    StudyRow("常用网站", icon = Icons.Outlined.Language, onClick = onNavigateToLinks)
                    StudyRow("分享学习资源", "投稿审核结果通过绑定邮箱通知", Icons.Outlined.EditNote, onClick = {
                        if (email.isNullOrBlank()) android.widget.Toast.makeText(context, "请先在账号与安全中绑定邮箱", android.widget.Toast.LENGTH_LONG).show()
                        else onNavigateToContribution()
                    })
                }
            }
        }
    }
}
