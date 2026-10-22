package cn.zhzgo.study.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Delay avoids a flash for cached requests. Refresh keeps existing content visible. */
@Composable
fun StudyLoading(modifier: Modifier = Modifier, message: String = "正在加载…") {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(180); visible = true }
    Box(modifier.fillMaxWidth().heightIn(min = 52.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible, enter = fadeIn(tween(140)), exit = fadeOut(tween(100))) {
            Row(Modifier.padding(vertical = 14.dp).semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
