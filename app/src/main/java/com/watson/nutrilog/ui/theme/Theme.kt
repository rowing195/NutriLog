package com.watson.nutrilog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 固定的綠色系配色，刻意**不用** Material You 動態取色。
 *
 * 這個 app 用顏色傳達語意（三大營養素各有固定色、超標轉紅），
 * 讓桌布去決定色相會直接破壞這層意義。
 *
 * 注意：**整族 surface 都要自己指定**。只覆蓋 primary 的話，
 * 背景與卡片會沿用 Material3 預設的淡紫灰底，整個 app 看起來是紫的，
 * 跟綠色主色互相打架（第一版就是這樣）。
 */
private object Palette {
    val Green = Color(0xFF2E7D5B)
    val GreenLight = Color(0xFF7FC8A2)

    // 淺色：帶一點綠的中性灰，不是純白，長時間看比較不刺眼
    val LightBackground = Color(0xFFF6FAF7)
    val LightSurfaceLow = Color(0xFFF0F5F1)
    val LightSurface = Color(0xFFE9F0EB)
    val LightSurfaceHigh = Color(0xFFE1EAE4)
    val LightOnSurface = Color(0xFF191D1A)
    val LightOnSurfaceVariant = Color(0xFF4C5A52)
    val LightOutline = Color(0xFF7C8A82)

    // 深色：偏綠的深灰，純黑會讓卡片層次完全消失
    val DarkBackground = Color(0xFF0E1311)
    val DarkSurfaceLow = Color(0xFF161C19)
    val DarkSurface = Color(0xFF1C2320)
    val DarkSurfaceHigh = Color(0xFF232B27)
    val DarkOnSurface = Color(0xFFE0E5E2)
    val DarkOnSurfaceVariant = Color(0xFFB2BFB8)
    val DarkOutline = Color(0xFF7C8A82)
}

/** 三大營養素的固定色。畫進度條與圖例都從這裡取，不要各處自己寫死。 */
object NutrientColors {
    val Calories = Color(0xFF2E7D5B)
    val Protein = Color(0xFF3A6EA5)
    val Fat = Color(0xFFD98324)
    val Carbs = Color(0xFF9B5DE5)
    /** 超過每日目標時轉這個顏色，一眼看得出來吃過頭 */
    val Over = Color(0xFFC0392B)
}

private val LightColors = lightColorScheme(
    primary = Palette.Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBDC),
    onPrimaryContainer = Color(0xFF0B2A1C),
    secondary = Palette.Green,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEBDC),
    onSecondaryContainer = Color(0xFF0B2A1C),
    tertiary = Palette.Green,
    onTertiary = Color.White,

    background = Palette.LightBackground,
    onBackground = Palette.LightOnSurface,
    surface = Palette.LightBackground,
    onSurface = Palette.LightOnSurface,

    // 由淺到深，讓浮起來的層次（卡片、面板、對話框）自然比底色重一點。
    // 少蓋一個就會有某個元件固執地維持預設紫。
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Palette.LightSurfaceLow,
    surfaceContainer = Palette.LightSurface,
    surfaceContainerHigh = Palette.LightSurfaceHigh,
    surfaceContainerHighest = Palette.LightSurfaceHigh,
    surfaceVariant = Palette.LightSurface,
    onSurfaceVariant = Palette.LightOnSurfaceVariant,

    outline = Palette.LightOutline,
    outlineVariant = Color(0xFFC7D3CC),
    error = NutrientColors.Over,
)

private val DarkColors = darkColorScheme(
    primary = Palette.GreenLight,
    onPrimary = Color(0xFF0B2A1C),
    primaryContainer = Color(0xFF1E4C38),
    onPrimaryContainer = Color(0xFFCDEBDC),
    secondary = Palette.GreenLight,
    onSecondary = Color(0xFF0B2A1C),
    secondaryContainer = Color(0xFF1E4C38),
    onSecondaryContainer = Color(0xFFCDEBDC),
    tertiary = Palette.GreenLight,
    onTertiary = Color(0xFF0B2A1C),

    background = Palette.DarkBackground,
    onBackground = Palette.DarkOnSurface,
    surface = Palette.DarkBackground,
    onSurface = Palette.DarkOnSurface,

    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Palette.DarkSurfaceLow,
    surfaceContainer = Palette.DarkSurface,
    surfaceContainerHigh = Palette.DarkSurfaceHigh,
    surfaceContainerHighest = Palette.DarkSurfaceHigh,
    surfaceVariant = Palette.DarkSurface,
    onSurfaceVariant = Palette.DarkOnSurfaceVariant,

    outline = Palette.DarkOutline,
    outlineVariant = Color(0xFF3A443E),
    error = Color(0xFFFF8A7A),
)

@Composable
fun NutriLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
