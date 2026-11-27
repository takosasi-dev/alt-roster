package io.github.takosasidev.altroster.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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

/**
 * 「注意」の色(満タン間近・3 日以内)。危険(満タン・24 時間以内)は error を使う。
 * tertiary は壁紙の色で変わり、注意に見えないことがあるので、決まった琥珀色にする。
 */
data class StatusColors(val warning: Color)

val LocalStatusColors = staticCompositionLocalOf { StatusColors(warning = Color(0xFF8A6100)) }

private val FallbackLight = lightColorScheme()
private val FallbackDark = darkColorScheme()

/** 色を選んでいないとき(と、壁紙の色が無い古い端末)の色。標準の紫に戻らないよう、アプリの色を決めておく。 */
private const val DefaultSeed = 0xFF3F51B5.toInt()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AltRosterTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = when (settings.darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }
    val manual = settings.manualThemeColor
    val scheme = if (settings.themeColorMode == ThemeColorMode.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12 以降は壁紙に合わせた配色
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        schemeFromSeed(Color(manual ?: DefaultSeed), dark)
    }
    CompositionLocalProvider(
        LocalStatusColors provides StatusColors(warning = if (dark) Color(0xFFF5C518) else Color(0xFF8A6100)),
    ) {
        MaterialExpressiveTheme(colorScheme = scheme, content = content)
    }
}

/**
 * タイトルの色を「淡い地」と「濃い前景(アイコン・文字・棒)」の組にする。濃いベタ塗りより落ち着いて見え、
 * 前景は地に対して読める濃さにする(ライトは黒へ、ダークは白へ寄せる)。
 */
@Composable
fun titleTones(color: Color): Pair<Color, Color> {
    val surface = MaterialTheme.colorScheme.surface
    return if (surface.luminance() < 0.5f) {
        lerp(surface, color, 0.3f) to lerp(color, Color.White, 0.45f)
    } else {
        lerp(surface, color, 0.14f) to lerp(color, Color.Black, 0.2f)
    }
}

/**
 * 種の色からの配色。primary の系統に加えて、背景・カード・トーンのボタンの地にも種の色を薄く混ぜる
 * (混ぜないと標準の紫がかった灰色が残り、カードが沈んで見える)。
 * shortcut: 色相を保った正式なトーンの計算はしない。そろえたくなったら material-color-utilities を足す
 */
private fun schemeFromSeed(seed: Color, dark: Boolean): ColorScheme {
    val base = if (dark) Color(0xFF111318) else Color.White
    fun tint(f: Float) = lerp(base, seed, f)
    return if (dark) {
        FallbackDark.copy(
            primary = seed.lighten(0.4f),
            onPrimary = Color.Black,
            primaryContainer = seed.darken(0.35f),
            onPrimaryContainer = seed.lighten(0.65f),
            secondary = seed.lighten(0.25f),
            secondaryContainer = tint(0.32f),
            onSecondaryContainer = seed.lighten(0.75f),
            tertiary = seed.lighten(0.3f),
            onSurface = Color(0xFFE2E2E9),
            onBackground = Color(0xFFE2E2E9),
            onSurfaceVariant = lerp(Color(0xFFC4C6D0), seed, 0.15f),
            outline = lerp(Color(0xFF8E9099), seed, 0.2f),
            outlineVariant = tint(0.28f),
            background = tint(0.03f),
            surface = tint(0.03f),
            surfaceVariant = tint(0.16f),
            surfaceTint = seed.lighten(0.4f),
            surfaceContainerLowest = base,
            surfaceContainerLow = tint(0.06f),
            surfaceContainer = tint(0.09f),
            surfaceContainerHigh = tint(0.12f),
            surfaceContainerHighest = tint(0.16f),
        )
    } else {
        FallbackLight.copy(
            primary = seed,
            onPrimary = Color.White,
            primaryContainer = seed.lighten(0.7f),
            onPrimaryContainer = seed.darken(0.5f),
            secondary = seed.darken(0.15f),
            secondaryContainer = tint(0.2f),
            onSecondaryContainer = seed.darken(0.6f),
            tertiary = seed.darken(0.2f),
            onSurface = Color(0xFF1A1B21),
            onBackground = Color(0xFF1A1B21),
            onSurfaceVariant = lerp(Color(0xFF44464F), seed, 0.2f),
            outline = lerp(Color(0xFF757780), seed, 0.2f),
            outlineVariant = tint(0.22f),
            background = tint(0.02f),
            surface = tint(0.02f),
            surfaceVariant = tint(0.1f),
            surfaceTint = seed,
            surfaceContainerLowest = base,
            surfaceContainerLow = tint(0.04f),
            surfaceContainer = tint(0.06f),
            surfaceContainerHigh = tint(0.08f),
            surfaceContainerHighest = tint(0.1f),
        )
    }
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
    Urgency.WARNING -> LocalStatusColors.current.warning
    Urgency.URGENT -> MaterialTheme.colorScheme.error
    Urgency.ENDED -> MaterialTheme.colorScheme.outline
}
