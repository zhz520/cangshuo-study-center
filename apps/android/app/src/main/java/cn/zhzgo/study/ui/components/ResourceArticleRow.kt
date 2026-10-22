package cn.zhzgo.study.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.zhzgo.study.R
import cn.zhzgo.study.data.Article
import coil.compose.AsyncImage

@Composable
fun ResourceArticleRow(article: Article, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 84.dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val imageModifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.small)
        if (!article.getCover().isNullOrBlank()) AsyncImage(article.getCover(), null, imageModifier, contentScale = ContentScale.Crop)
        else Image(painterResource(R.drawable.study_landscape), null, imageModifier, contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(article.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(article.getTime()?.take(10).orEmpty(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                article.view_count?.let { count ->
                    Icon(Icons.Outlined.Visibility, null, Modifier.size(13.dp), tint = colors.onSurfaceVariant)
                    Text("$count", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
        }
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
    }
    HorizontalDivider(Modifier.padding(start = 76.dp), color = colors.outlineVariant.copy(alpha = .5f))
}
