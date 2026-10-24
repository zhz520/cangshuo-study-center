package cn.zhzgo.study.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.zhzgo.study.data.UserPreferences
import kotlinx.coroutines.launch

/** Shared by Profile and Settings: selection stays on the originating page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSheet(action: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }
    val theme by prefs.appearance.collectAsState("system")
    val accent by prefs.primaryColor.collectAsState("#2563EB")
    val scope = rememberCoroutineScope()
    val options = if (action == "theme") listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")
        else listOf("#2563EB" to "烁学蓝", "#000000" to "经典黑", "#10B981" to "松石绿", "#3B82F6" to "天际蓝", "#F59E0B" to "琥珀橙", "#8B5CF6" to "鸢尾紫")
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Text(if (action == "theme") "主题模式" else "重点色", style = MaterialTheme.typography.titleLarge)
            Text("选择后立即生效", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
            options.forEach { (key, title) ->
                val selected = if (action == "theme") key == theme else key.equals(accent, true)
                Row(Modifier.fillMaxWidth().clickable {
                    scope.launch { if (action == "theme") prefs.setAppearance(key) else prefs.setPrimaryColor(key); onDismiss() }
                }.heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (action != "theme") Box(Modifier.size(22.dp).background(
                        runCatching { Color(android.graphics.Color.parseColor(key)) }.getOrDefault(MaterialTheme.colorScheme.primary), CircleShape))
                    else Icon(when(key) { "light" -> Icons.Outlined.LightMode; "dark" -> Icons.Outlined.DarkMode; else -> Icons.Outlined.BrightnessAuto }, null, Modifier.size(22.dp))
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    RadioButton(selected, onClick = null)
                }
            }
        }
    }
}
