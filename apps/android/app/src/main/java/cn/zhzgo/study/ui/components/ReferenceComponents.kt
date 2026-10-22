package cn.zhzgo.study.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.zhzgo.study.R

@Composable
fun ReferencePill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.heightIn(min = 44.dp).selectable(selected, role = Role.Tab, onClick = onClick)
        .padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = if (selected) colors.onSurface else Color.Transparent) {
            Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) colors.surface else colors.onSurfaceVariant)
        }
    }
}

@Composable
fun SubjectEmblem(name: String, size: Dp = 38.dp) {
    val icon = when {
        name.contains("网络") -> Icons.Outlined.Language
        name.contains("数据库") -> Icons.Outlined.Storage
        name.contains("操作") -> Icons.Outlined.Terminal
        name.contains("结构") -> Icons.Outlined.AccountTree
        name.contains("管理") -> Icons.Outlined.Groups
        name.contains("物理") -> Icons.Outlined.Science
        else -> Icons.Outlined.Computer
    }
    val accent = when {
        name.contains("管理") -> Color(0xFF43A68A)
        name.contains("物理") -> Color(0xFFD6A34A)
        name.contains("操作") -> Color(0xFFB48C6A)
        else -> MaterialTheme.colorScheme.primary
    }
    Box(Modifier.size(size).background(accent.copy(alpha = .10f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * .54f), tint = accent)
    }
}

@Composable
fun ProgressRing(progress: Float, value: String, suffix: String = "%", caption: String? = null,
    size: Dp = 96.dp, color: Color = MaterialTheme.colorScheme.primary) {
    val track = color.copy(alpha = .12f)
    Box(Modifier.size(size).semantics(mergeDescendants = true) { contentDescription = "$value$suffix ${caption.orEmpty()}" },
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val stroke = 5.dp.toPx()
            val inset = stroke / 2
            val diameter = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, -90f, 360f, false, topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = diameter, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color, -90f, progress.coerceIn(0f, 1f) * 360f, false, topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = diameter, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = if (size > 140.dp) 38.sp else 26.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.alignByBaseline())
                if (suffix.isNotBlank()) Text(suffix, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.alignByBaseline().padding(start = 2.dp))
            }
            caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp)) }
        }
    }
}

@Composable
fun MountainBanner(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxWidth().heightIn(min = 118.dp)) {
            Image(painterResource(R.drawable.study_landscape), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop,
                alignment = Alignment.Center)
            Box(Modifier.matchParentSize().background(Color.White.copy(alpha = .22f)))
            Row(Modifier.fillMaxWidth().heightIn(min = 118.dp).padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF253347),
                        modifier = Modifier.padding(top = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(42.dp).background(Color.White.copy(alpha = .96f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(21.dp), tint = Color(0xFF111827))
                }
            }
        }
    }
}
