package cn.zhzgo.study.ui.components
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
object StudyLayout {
    val page = 20.dp
    val section = 24.dp
    val gap = 12.dp
    val touch = 48.dp
}
@Composable fun RefreshOnResume(refresh: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val action by rememberUpdatedState(refresh)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if(event == Lifecycle.Event.ON_RESUME) action() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
@Composable fun SectionLabel(title: String, detail: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top=8.dp, bottom=12.dp), verticalAlignment=Alignment.CenterVertically) {
        Text(title, style=MaterialTheme.typography.titleLarge, modifier=Modifier.weight(1f))
        if(detail != null) Text(detail, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun Metric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(value, style=MaterialTheme.typography.headlineMedium)
        Text(label, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun StudyRow(title: String, subtitle: String? = null, icon: ImageVector? = null,
    trailing: String? = null, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick=onClick).heightIn(min=60.dp).padding(vertical=12.dp),
            verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            if(icon != null) Icon(icon, null, Modifier.size(21.dp), tint=MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(title, style=MaterialTheme.typography.bodyMedium, fontWeight=FontWeight.Medium)
                if(!subtitle.isNullOrBlank()) Text(subtitle, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(trailing != null) Text(trailing, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(17.dp), tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
    }
}
@Composable
fun StudySearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value, onValueChange = onValueChange, singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
        decorationBox = { input ->
            Row(Modifier.fillMaxWidth().background(colors.onSurface.copy(alpha = .04f), RoundedCornerShape(10.dp))
                .heightIn(min = 48.dp).padding(start = 13.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, null, Modifier.size(19.dp), tint = colors.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    input()
                }
                if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Outlined.Close, "清空搜索", Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                }
            }
        }
    )
}

@Composable
fun DirectoryRow(
    title: String, detail: String, progress: Float? = null,
    icon: ImageVector = Icons.Outlined.MenuBook,
    showChevron: Boolean = true,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 76.dp).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).background(colors.onSurface.copy(alpha = .04f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))
            if (progress != null && progress > 0f) {
                Spacer(Modifier.height(7.dp))
                QuietProgress(progress, Modifier.width(88.dp).height(2.dp))
            }
        }
        trailing()
        if (showChevron) Icon(Icons.Outlined.ChevronRight, null, Modifier.size(17.dp), tint = colors.onSurfaceVariant)
    }
    HorizontalDivider(Modifier.padding(start = 48.dp), color = colors.outlineVariant)
}
@Composable fun StateMessage(title: String, detail: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical=32.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(title, style=MaterialTheme.typography.titleMedium)
        Text(detail, style=MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(action != null) TextButton(onClick=onAction) { Text(action) }
    }
}
@Composable fun QuietProgress(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(progress={progress.coerceIn(0f,1f)}, modifier=modifier.fillMaxWidth().height(3.dp),
        color=MaterialTheme.colorScheme.primary, trackColor=MaterialTheme.colorScheme.outlineVariant)
}
fun questionTypeLabel(type: String) = when(type) {
    "single" -> "单选"; "multiple" -> "多选"; "judge" -> "判断"; "fill" -> "填空"
    "material" -> "材料"; "sort" -> "排序"; "text" -> "简答"; else -> "其他"
}
