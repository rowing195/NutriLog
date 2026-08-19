package com.watson.nutrilog.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 固定的綠色系配色，刻意**不用** Material You 動態取色。
 *
 * 這個 app 用顏色傳達語意（三大營養素各有固定色、超標會轉紅），
 * 讓桌布去決定色相會直接破壞這層意義。
 */
private val Green = Color(0xFF2E7D5B)
private val GreenLight = Color(0xFF7FC8A2)

/** 三大營養素的固定色。畫進度條與圖例都從這裡取，不要各處自己寫死。 */
object NutrientColors {
    val Calories = Color(0xFF2E7D5B)
    val Protein = Color(0xFF3A6EA5)
    val Fat = Color(0xFFD98324)
    val Carbs = Color(0xFF9B5DE5)
    /** 超過每日目標時進度條轉這個顏色，一眼看得出來吃過頭 */
    val Over = Color(0xFFC0392B)
}

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3EFE0),
    onPrimaryContainer = Color(0xFF0C2A1D),
    secondary = Green,
    onSecondary = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = GreenLight,
    onPrimary = Color(0xFF0C2A1D),
    primaryContainer = Color(0xFF1E4C38),
    onPrimaryContainer = Color(0xFFD3EFE0),
    secondary = GreenLight,
    onSecondary = Color(0xFF0C2A1D),
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
