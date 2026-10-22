package cn.zhzgo.study.ui.theme
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
private val LightColors = lightColorScheme(
    primary = BrandBlue, onPrimary = Color.White, primaryContainer = Color(0xFFEFF6FF), onPrimaryContainer = Color(0xFF1D4ED8),
    secondary = LightTextSecondary, onSecondary = Color.White, secondaryContainer = Color(0xFFF1F5F9), onSecondaryContainer = LightText,
    tertiary = LightTextSecondary, background = LightBg, onBackground = LightText,
    surface = LightSurface, onSurface = LightText, surfaceVariant = Color(0xFFF1F5F9), onSurfaceVariant = LightTextSecondary,
    surfaceContainerLow = LightBg, surfaceContainer = Color(0xFFF1F5F9), surfaceContainerHigh = Color(0xFFE8EDF4),
    surfaceContainerHighest = Color(0xFFE2E8F0), outline = Color(0xFF64748B), outlineVariant = LightOutline,
    error = ErrorColor, onError = Color.White, errorContainer = Color(0xFFFEF2F2), onErrorContainer = Color(0xFF991B1B)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4FF), onPrimary = Color(0xFF102B57), primaryContainer = Color(0xFF1A3154), onPrimaryContainer = Color(0xFFBBD5FF),
    secondary = DarkTextSecondary, onSecondary = DarkBg, secondaryContainer = Color(0xFF253144), onSecondaryContainer = DarkText,
    tertiary = DarkTextSecondary, background = DarkBg, onBackground = DarkText,
    surface = DarkSurface, onSurface = DarkText, surfaceVariant = Color(0xFF253144), onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLow = DarkSurface, surfaceContainer = Color(0xFF202B3B), surfaceContainerHigh = Color(0xFF283548),
    surfaceContainerHighest = Color(0xFF334155), outline = Color(0xFF8B9BB1), outlineVariant = DarkOutline,
    error = Color(0xFFFF9696), onError = Color(0xFF561616), errorContainer = Color(0xFF3C2028), onErrorContainer = Color(0xFFFFCCCC)
)
@Composable
fun MyApplicationTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = false,
    primaryColorOverride: Color? = null, content: @Composable () -> Unit) {
    var colors = if (darkTheme) DarkColors else LightColors
    if (primaryColorOverride != null && primaryColorOverride != BrandBlue) {
        // Keep user-selected accents; derive readable text and a quiet container from the same color.
        fun contrast(a: Color, b: Color): Float {
            val x=a.luminance(); val y=b.luminance()
            return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)
        }
        var accent: Color = primaryColorOverride
        val target=if(darkTheme) Color.White else LightText
        for(step in 1..10) {
            if(contrast(accent,colors.background)>=4.5f) break
            accent=androidx.compose.ui.graphics.lerp(primaryColorOverride,target,step/10f)
        }
        colors = colors.copy(primary = accent,
            onPrimary = if(contrast(accent,LightText)>contrast(accent,Color.White)) LightText else Color.White,
            primaryContainer = androidx.compose.ui.graphics.lerp(colors.surface, accent, if(darkTheme) .22f else .08f),
            onPrimaryContainer = if(darkTheme) DarkText else LightText)
    }
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = colors, typography = Typography,
        shapes = Shapes(small=RoundedCornerShape(10.dp), medium=RoundedCornerShape(12.dp), large=RoundedCornerShape(16.dp)), content = content)
}
