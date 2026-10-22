package cn.zhzgo.study.ui.theme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontWeight = weight, fontSize = size.sp,
    lineHeight = line.sp, letterSpacing = 0.sp
)
val Typography = Typography(
    displayLarge = type(32,40,FontWeight.SemiBold), displayMedium = type(28,36,FontWeight.SemiBold),
    displaySmall = type(26,34,FontWeight.SemiBold), headlineLarge = type(26,34,FontWeight.SemiBold),
    headlineMedium = type(24,32,FontWeight.SemiBold), headlineSmall = type(22,30,FontWeight.SemiBold),
    titleLarge = type(18,26,FontWeight.SemiBold), titleMedium = type(15,23,FontWeight.Medium),
    titleSmall = type(14,22,FontWeight.Medium), bodyLarge = type(16,26), bodyMedium = type(15,24),
    bodySmall = type(12,19), labelLarge = type(14,20,FontWeight.Medium),
    labelMedium = type(12,18,FontWeight.Medium), labelSmall = type(12,18)
)
