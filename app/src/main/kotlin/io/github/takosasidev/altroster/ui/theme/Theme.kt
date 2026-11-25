package io.github.takosasidev.altroster.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.takosasidev.altroster.core.calc.Urgency
import io.github.takosasidev.altroster.core.model.AppSettings
import io.github.takosasidev.altroster.core.model.DarkMode
import io.github.takosasidev.altroster.core.model.ThemeColorMode

/** 手動のテーマの色と、タイトルの色の候補。実在のゲームの配色は使わず、一般的な色だけを並べる。 */
val PaletteColors: List<Pair<String, Color>> = listOf(
    "インディゴ" to Color(0xFF3F51B5),
    "ブルー" to Color(0xFF1976D2),
    "ティール" to Color(0xFF00796B),
    "グリーン" to Color(0xFF2E7D32),
    "ライム" to Color(0xFF7C8B00),
    "アンバー" to Color(0xFFC77700),
    "オレンジ" to Color(0xFFE07A2F),
    "レッド" to Color(0xFFC62828),
    "ローズ" to Color(0xFFB3325B),
    "パープル" to Color(0xFF6750A4),
    "ブラウン" to Color(0xFF6D4C41),
    "グレー" to Color(0xFF546E7A),
)

private val FallbackLight = lightColorScheme()
private val FallbackDark = darkColorScheme()

@Composable
fun AltRosterTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = when (settings.darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }
    val manual = settings.manualThemeColor
    val scheme = when {
        // Android 12 以降は壁紙に合わせた配色。それより前の端末は、手動の色か標準の配色にする
        settings.themeColorMode == ThemeColorMode.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        settings.themeColorMode == ThemeColorMode.MANUAL && manual != null -> schemeFromSeed(Color(manual), dark)
        else -> if (dark) FallbackDark else FallbackLight
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/**
 * 種の色からの配色。配色のライブラリを足すほどの用途ではないので、primary の系統だけを種の色に
 * 差し替えて、残りは標準の配色を使う。
 * shortcut: 簡易の生成で、色の調和は取っていない。きちんと合わせるなら material-color-utilities を足す。
 */
private fun schemeFromSeed(seed: Color, dark: Boolean) = if (dark) {
    FallbackDark.copy(
        primary = seed.lighten(0.35f),
        onPrimary = Color.Black,
        primaryContainer = seed.darken(0.35f),
        onPrimaryContainer = seed.lighten(0.6f),
        secondary = seed.lighten(0.2f),
    )
} else {
    FallbackLight.copy(
        primary = seed,
        onPrimary = Color.White,
        primaryContainer = seed.lighten(0.7f),
        onPrimaryContainer = seed.darken(0.5f),
        secondary = seed.darken(0.15f),
    )
}

fun Color.lighten(f: Float) = Color(
    red = red + (1f - red) * f,
    green = green + (1f - green) * f,
    blue = blue + (1f - blue) * f,
    alpha = alpha,
)

fun Color.darken(f: Float) = Color(red = red * (1f - f), green = green * (1f - f), blue = blue * (1f - f), alpha = alpha)

/** カウントダウンの色。通常 → 注意 → 緊急で色調を変える。終了は薄い色。 */
@Composable
fun urgencyColor(urgency: Urgency): Color = when (urgency) {
    Urgency.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
    Urgency.WARNING -> MaterialTheme.colorScheme.tertiary
    Urgency.URGENT -> MaterialTheme.colorScheme.error
    Urgency.ENDED -> MaterialTheme.colorScheme.outline
}
