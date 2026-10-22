package cn.zhzgo.study.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

object PlusConstants {
    val PlusGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF6366F1), Color(0xFFA855F7), Color(0xFFEC4899))
    )
    val PlusGoldenGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFFFD700), Color(0xFFFFA500), Color(0xFFFF8C00))
    )
}

fun isUserPlus(subscriptionEndDate: String?): Boolean {
    if (subscriptionEndDate.isNullOrBlank()) return false
    val formats = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd"
    )
    
    for (format in formats) {
        try {
            val sdf = SimpleDateFormat(format, Locale.getDefault())
            if (format.endsWith("'Z'")) {
                sdf.timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = sdf.parse(subscriptionEndDate)
            if (date != null && date.after(Date())) return true
        } catch (e: Exception) {
            // Try next format
        }
    }
    return false
}

@Composable
fun PlusBadge(modifier:Modifier=Modifier) {
    Text("Plus",modifier=modifier.padding(horizontal=4.dp),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
}
@Composable
fun PlusAvatarWrapper(isPlus:Boolean,avatarSize:Dp,modifier:Modifier=Modifier,gapColor:Color=MaterialTheme.colorScheme.surface,content:@Composable ()->Unit) {
    Box(modifier=modifier.size(avatarSize+(if(isPlus)6.dp else 0.dp)).clip(CircleShape)
        .background(gapColor).then(if(isPlus)Modifier.border(1.dp,MaterialTheme.colorScheme.primary.copy(alpha=.5f),CircleShape)else Modifier),contentAlignment=Alignment.Center) {
        Box(Modifier.size(avatarSize)){content()}
    }
}
