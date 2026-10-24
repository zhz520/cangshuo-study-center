package cn.zhzgo.study.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.zhzgo.study.data.MemberAppearance
import coil.compose.AsyncImage

@Composable
private fun memberTone(style: String) = when (style) {
    "champagne" -> Color(0xFF8A7146)
    "azure" -> Color(0xFF58799F)
    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = .62f)
}

/** A restrained four-point mark that matches the product's star/book logo. */
@Composable
fun PlusMark(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val path = Path().apply {
            moveTo(cx, 0f)
            quadraticTo(cx + size.width * .07f, cy - size.height * .07f, size.width, cy)
            quadraticTo(cx + size.width * .07f, cy + size.height * .07f, cx, size.height)
            quadraticTo(cx - size.width * .07f, cy + size.height * .07f, 0f, cy)
            quadraticTo(cx - size.width * .07f, cy - size.height * .07f, cx, 0f)
            close()
        }
        drawPath(path, tint)
    }
}

@Composable
fun MemberAvatar(url: String?, appearance: MemberAppearance? = null, size: Dp = 52.dp) {
    val decorated = appearance?.is_plus == true && appearance.avatar_frame != "none"
    val tone = memberTone(appearance?.avatar_frame.orEmpty())
    val badgeSize = if (size > 44.dp) 17.dp else 14.dp
    Box(Modifier.size(size).semantics { contentDescription = if (decorated) "Plus 会员头像" else "个人头像" }) {
        Box(
            Modifier.fillMaxSize()
                .then(if (decorated) Modifier.border(1.dp, tone.copy(alpha = .52f), CircleShape).padding(3.dp) else Modifier)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (url?.startsWith("https://") == true || url?.startsWith("http://") == true)
                AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Outlined.PersonOutline, null, Modifier.size(size * .55f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (decorated) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).size(badgeSize),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, tone.copy(alpha = .38f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    PlusMark(Modifier.size(badgeSize * .5f), tint = tone)
                }
            }
        }
    }
}

@Composable
fun MemberLabel(appearance: MemberAppearance?) {
    if (appearance?.is_plus != true || !appearance.badge_visible) return
    val tone = memberTone(appearance.avatar_frame)
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, tone.copy(alpha = .30f)),
    ) {
        Row(
            Modifier.padding(horizontal = 5.dp, vertical = 2.dp).semantics { contentDescription = "Plus 会员" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            PlusMark(Modifier.size(8.dp), tint = tone)
            Text("PLUS", color = tone, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .55.sp)
        }
    }
}
