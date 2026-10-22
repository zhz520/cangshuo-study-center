package cn.zhzgo.study.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.IntegrationInstructions
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.saveable.rememberSaveable
import cn.zhzgo.study.ui.components.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.zhzgo.study.data.ToolEntity
import cn.zhzgo.study.data.UserPreferences
import cn.zhzgo.study.ui.viewmodels.ToolsViewModel
import kotlinx.coroutines.launch

@Composable
fun ToolsListScreen(onNavigateToTool:(String)->Unit,onBack:()->Unit) {
    val model:ToolsViewModel=viewModel()
    val tools by model.allTools.collectAsState()
    val context=LocalContext.current
    val prefs=remember{UserPreferences(context)}
    val pinnedText by prefs.dashboardTools.collectAsState(initial="")
    val mode by prefs.toolsViewMode.collectAsState(initial="list")
    val pinned=pinnedText.split(",").filter{it.isNotBlank()}
    val scope=rememberCoroutineScope()
    var query by rememberSaveable{mutableStateOf("")}
    var managing by rememberSaveable{mutableStateOf(false)}
    var pinnedOnly by rememberSaveable{mutableStateOf(false)}
    val groups=tools.filter{it.name.contains(query.trim(),true)&&(!pinnedOnly||it.id in pinned)}.groupBy{it.category}
    val categories=tools.map{it.category}.distinct()
    fun togglePin(tool:ToolEntity) {scope.launch{prefs.setDashboardTools((if(tool.id in pinned)pinned-tool.id else pinned+tool.id).joinToString(","))}}
    fun moveCategory(index:Int,delta:Int) {
        val reordered=categories.toMutableList();val value=reordered.removeAt(index);reordered.add(index+delta,value);model.updateCategoryOrder(reordered)
    }
    fun moveTool(id:String,delta:Int) {
        val ids=tools.map{it.id}.toMutableList();val index=ids.indexOf(id)
        if(index+delta in ids.indices){val value=ids.removeAt(index);ids.add(index+delta,value);model.updateToolsOrder(ids)}
    }
    StudyPage(if(managing)"整理工具" else "全部工具",onBack={if(managing)managing=false else onBack()},actions={
        if(!managing)IconButton(onClick={scope.launch{prefs.setToolsViewMode(if(mode=="grid")"list" else "grid")}}){Icon(if(mode=="grid")Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,"切换列表或紧凑网格")}
        TextButton(onClick={managing=!managing;query="";pinnedOnly=false}){Text(if(managing)"完成" else "整理")}
    }){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=24.dp)) {
            if(!managing)item {
                OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("搜索工具")},leadingIcon={Icon(Icons.Default.Search,null)},shape=MaterialTheme.shapes.medium)
                FilterChip(pinnedOnly,{pinnedOnly=!pinnedOnly},label={Text("常用工具")})
            }
            if(tools.isEmpty())item{StateMessage("正在准备工具目录","工具加载后即可使用。")}
            if(tools.isNotEmpty()&&groups.isEmpty())item{StateMessage("没有匹配的工具","调整关键词，或取消常用筛选。")}
            groups.forEach{(category,items)->
                item {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(getCategoryName(category),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f).padding(top=20.dp,bottom=12.dp))
                        if(managing) {
                            val index=categories.indexOf(category)
                            IconButton(onClick={moveCategory(index,-1)},enabled=index>0){Icon(Icons.Default.KeyboardArrowUp,"上移分类")}
                            IconButton(onClick={moveCategory(index,1)},enabled=index<categories.lastIndex){Icon(Icons.Default.KeyboardArrowDown,"下移分类")}
                        }
                    }
                }
                if(mode=="grid"&&!managing) {
                    items(items.chunked(2)){pair->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            pair.forEach{tool->
                                Column(Modifier.weight(1f).padding(vertical=6.dp)) {
                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                        TextButton(onClick={onNavigateToTool(tool.route)},modifier=Modifier.weight(1f),contentPadding=PaddingValues(0.dp)) {
                                            Icon(getIconForTool(tool.iconResName),null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.width(8.dp));Text(tool.name,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurface)
                                        }
                                        IconButton(onClick={togglePin(tool)}){Icon(if(tool.id in pinned)Icons.Default.PushPin else Icons.Outlined.PushPin,if(tool.id in pinned)"取消常用" else "设为常用",Modifier.size(18.dp))}
                                    }
                                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                            if(pair.size==1)Spacer(Modifier.weight(1f))
                        }
                    }
                } else items(items,key={it.id}){tool->
                    Row(Modifier.fillMaxWidth().heightIn(min=60.dp),verticalAlignment=Alignment.CenterVertically) {
                        TextButton(onClick={onNavigateToTool(tool.route)},enabled=!managing,modifier=Modifier.weight(1f),contentPadding=PaddingValues(0.dp)) {
                            Icon(getIconForTool(tool.iconResName),null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp));Text(tool.name,modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurface)
                        }
                        if(managing) {
                            val localIndex=items.indexOf(tool)
                            IconButton(onClick={moveTool(tool.id,-1)},enabled=localIndex>0){Icon(Icons.Default.KeyboardArrowUp,"上移工具")}
                            IconButton(onClick={moveTool(tool.id,1)},enabled=localIndex<items.lastIndex){Icon(Icons.Default.KeyboardArrowDown,"下移工具")}
                        }else IconButton(onClick={togglePin(tool)}){Icon(if(tool.id in pinned)Icons.Default.PushPin else Icons.Outlined.PushPin,if(tool.id in pinned)"取消常用" else "设为常用",Modifier.size(20.dp),tint=if(tool.id in pinned)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
                    }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

fun getIconForTool(iconName: String): ImageVector {
    return when (iconName) {
        "icon_base_converter" -> Icons.Filled.Build
        "icon_calculator" -> Icons.Filled.Calculate
        "icon_bmi" -> Icons.Filled.MonitorWeight
        "icon_date" -> Icons.Filled.DateRange
        "icon_random" -> Icons.Filled.Shuffle
        "icon_color" -> Icons.Filled.ColorLens
        "icon_password" -> Icons.Filled.VpnKey
        "icon_text" -> Icons.Filled.TextFields
        "icon_timer" -> Icons.Filled.Timer
        "icon_swap" -> Icons.Filled.SwapHoriz
        "icon_clock" -> Icons.Filled.Schedule
        "icon_family" -> Icons.Filled.FamilyRestroom
        "icon_image" -> Icons.Filled.Image
        "icon_auto_fix_high" -> Icons.Filled.AutoFixHigh
        "icon_brush" -> Icons.Filled.Brush
        "icon_picture_as_pdf" -> Icons.Filled.PictureAsPdf
        "icon_collections" -> Icons.Filled.Collections
        "icon_integration_instructions" -> Icons.Filled.IntegrationInstructions
        "icon_music_note" -> Icons.Filled.MusicNote
        "icon_video_file" -> Icons.Filled.VideoFile
        "icon_graphic_eq" -> Icons.Filled.GraphicEq
        "icon_pin" -> Icons.Filled.Pin
        "icon_security" -> Icons.Filled.Security
        "icon_key" -> Icons.Filled.Key
        "icon_code" -> Icons.Filled.Code
        "icon_http" -> Icons.Filled.Http
        "icon_video_parser" -> Icons.Filled.VideoFile
        "icon_assignment" -> Icons.Filled.Assignment
        else -> Icons.Filled.Build
    }
}


@Composable
fun getCategoryName(category: String): String {
    return when (category) {
        "calc" -> "计算工具"
        "health", "life" -> "生活与健康"
        "time" -> "时间与日期"
        "general" -> "通用工具"
        "dev" -> "开发工具"
        "security" -> "安全与密码"
        "text" -> "文本工具"
        "image" -> "图像工具"
        "document" -> "文档处理"
        "media" -> "影音与解析"
        else -> "其他工具"
    }
}
