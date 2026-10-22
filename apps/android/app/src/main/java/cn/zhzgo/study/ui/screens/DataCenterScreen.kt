package cn.zhzgo.study.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataCenterScreen(onBack:()->Unit,viewModel:DataCenterViewModel=viewModel()) {
    val state by viewModel.uiState.collectAsState();val subjects by viewModel.subjects.collectAsState()
    val clipboard=LocalClipboardManager.current
    var clear by remember{mutableStateOf(false)};var importing by remember{mutableStateOf(false)}
    var text by remember{mutableStateOf("")};var subject by remember{mutableStateOf<Int?>(null)};var menu by remember{mutableStateOf(false)}
    val context=androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(state.toastMessage){state.toastMessage?.let{android.widget.Toast.makeText(context,it,android.widget.Toast.LENGTH_LONG).show();viewModel.clearToast()}}
    cn.zhzgo.study.ui.components.StudyPage("学习数据",onBack){padding->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal=20.dp)) {
            Text("备份与恢复",style=MaterialTheme.typography.headlineSmall)
            Text("管理你的作答记录。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp,bottom=24.dp))
            if(state.isLoading)cn.zhzgo.study.ui.components.StudyLoading(Modifier.fillMaxWidth())
            cn.zhzgo.study.ui.components.StudyRow("导出学习记录","将作答记录复制为 JSON 备份",onClick={if(!state.isLoading)viewModel.exportData{clipboard.setText(AnnotatedString(it));android.widget.Toast.makeText(context,"备份已复制到剪贴板",android.widget.Toast.LENGTH_SHORT).show()}})
            cn.zhzgo.study.ui.components.StudyRow("恢复备份","导入现有作答记录",onClick={if(!state.isLoading){viewModel.loadSubjects();importing=true}})
            Spacer(Modifier.height(24.dp));cn.zhzgo.study.ui.components.SectionLabel("重置学习")
            Text("清空会移除云端作答进度，无法撤销。建议先导出备份。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={clear=true},enabled=!state.isLoading,modifier=Modifier.padding(top=8.dp)){Text("清空全部作答进度",color=MaterialTheme.colorScheme.error)}
        }
    }
    if(clear)AlertDialog(onDismissRequest={clear=false},title={Text("清空全部作答进度？")},text={Text("云端作答进度将被清空，此操作无法撤销。收藏题目会保留。")},confirmButton={TextButton(onClick={clear=false;viewModel.clearAllData()}){Text("确认清空",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={clear=false}){Text("取消")}})
    if(importing)AlertDialog(onDismissRequest={if(!state.isLoading)importing=false},title={Text("恢复学习备份")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("新版备份会自动匹配题目。导入旧版备份时，请选择对应科目。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Box {
                TextButton(onClick={menu=true}){Text(subjects.find{it.id==subject}?.name ?: "自动匹配 / 选择科目")}
                DropdownMenu(menu,{menu=false},modifier=Modifier.heightIn(max=240.dp)) {
                    DropdownMenuItem(text={Text("自动匹配")},onClick={subject=null;menu=false})
                    subjects.forEach{s->DropdownMenuItem(text={Text(s.name)},onClick={subject=s.id;menu=false})}
                }
            }
            OutlinedTextField(text,{text=it},Modifier.fillMaxWidth().heightIn(min=140.dp),label={Text("JSON 备份内容")},enabled=!state.isLoading,shape=MaterialTheme.shapes.medium)
        }
    },confirmButton={TextButton(onClick={viewModel.importData(text,subject);importing=false},enabled=text.isNotBlank()&&!state.isLoading){Text("恢复记录")}},dismissButton={TextButton(onClick={importing=false},enabled=!state.isLoading){Text("取消")}})
}
