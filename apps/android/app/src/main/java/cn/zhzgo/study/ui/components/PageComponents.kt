package cn.zhzgo.study.ui.components
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.RowScope
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyToolTopBar(title:@Composable ()->Unit,navigationIcon:@Composable ()->Unit={},actions:@Composable RowScope.()->Unit={},
    colors:TopAppBarColors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background)) {
    Column {
        TopAppBar(title={ androidx.compose.runtime.CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleLarge){title()} },
            navigationIcon=navigationIcon,actions=actions,colors=colors,expandedHeight=56.dp)
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f))
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyPage(title:String,onBack:(()->Unit)?=null,actions:@Composable RowScope.()->Unit={},
    bottomBar:@Composable ()->Unit={},content:@Composable (PaddingValues)->Unit) {
    Scaffold(containerColor=MaterialTheme.colorScheme.background,topBar={
        TopAppBar(expandedHeight=56.dp,title={Text(title,style=MaterialTheme.typography.titleLarge)},navigationIcon={
            if(onBack!=null) IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}
        },actions=actions,colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background))
    },bottomBar=bottomBar,content=content)
}
@Composable fun DetailPair(label:String,value:String) {
    Row(Modifier.fillMaxWidth().padding(vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(label,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
        Text(value,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.weight(1f))
    }
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
}
