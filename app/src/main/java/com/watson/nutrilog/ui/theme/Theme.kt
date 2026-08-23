package com.watson.nutrilog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 紙與墨。刻意**不用** Material You 動態取色 —— 這個 app 用顏色傳達語意
 * （三大營養素各有固定色、超標轉紅），讓桌布決定色相會直接破壞那層意義。
 *
 * 兩件跟前一版不一樣、而且會咬人的事：
 *
 * 1. **表面之間幾乎沒有對比**（底 #F8F7F2 vs 浮起 #FDFCF9 只差 3%）。
 *    這是故意的：版面靠**細線**分隔，不是靠卡片色塊。所以不要為了
 *    「看得出是一張卡」去加深 surfaceContainer —— 那會把整個設計拉回舊樣子。
 *    要分隔就用 outlineVariant 畫線。
 *
 * 2. 深色不是純黑而是**暖灰** #191813。純黑會讓襯線字看起來發灰，
 *    細線也會整條消失。
 */
private object Paper {
    // 淺色：紙
    val Bg = Color(0xFFF8F7F2)
    val Raised = Color(0xFFFDFCF9)
    val Container = Color(0xFFF1EFE7)
    val Track = Color(0xFFE6E3D9)
    val Keypad = Color(0xFFEFEDE5)
    val Ink = Color(0xFF1A1D18)
    val Muted = Color(0xFF6B7168)
    val Faint = Color(0xFFA8ADA4)
    val Hairline = Color(0xFFDBD9CF)

    // 深色：墨
    val DarkBg = Color(0xFF191813)
    val DarkRaised = Color(0xFF211F1A)
    val DarkContainer = Color(0xFF24231D)
    val DarkTrack = Color(0xFF2B2A23)
    val DarkKeypad = Color(0xFF211F1A)
    val DarkInk = Color(0xFFEDEBE3)
    val DarkMuted = Color(0xFFA5A399)
    val DarkFaint = Color(0xFF6E6C62)
    val DarkHairline = Color(0xFF33322B)
}

/**
 * 語意色。M3 的 ColorScheme 裝不下這些 —— 它沒有「碳水」這種角色。
 *
 * 深淺兩套的差別不只是明暗：三大營養素的色在深色底上要**提亮**，
 * 原本的 #3A6EA5 藍在 #191813 上幾乎讀不出來。
 */
@Immutable
data class NutriPalette(
    val calories: Color,
    val protein: Color,
    val fat: Color,
    val carbs: Color,
    val over: Color,
    /** 熱量超標但還在目標 10% 以內：橘色警示，比 [over] 溫和 */
    val warning: Color,
    /** 早／午／晚／點心，依序漸深（深色模式反過來漸亮），額度條靠它分段 */
    val meals: List<Color>,
    val keypad: Color,
)

private val LightNutri = NutriPalette(
    calories = Color(0xFF2E7D5B),
    protein = Color(0xFF3A6EA5),
    fat = Color(0xFFD98324),
    carbs = Color(0xFF9B5DE5),
    over = Color(0xFFC0392B),
    warning = Color(0xFFDB8A12),
    meals = listOf(Color(0xFFA8D9BE), Color(0xFF7FC8A2), Color(0xFF4E9E77), Color(0xFF2E7D5B)),
    keypad = Paper.Keypad,
)

private val DarkNutri = NutriPalette(
    calories = Color(0xFF7FC8A2),
    protein = Color(0xFF7FA8D9),
    fat = Color(0xFFE8A860),
    carbs = Color(0xFFBC9AF0),
    over = Color(0xFFFF8A7A),
    warning = Color(0xFFF0B84A),
    meals = listOf(Color(0xFF3E6B54), Color(0xFF4E8E6B), Color(0xFF62B189), Color(0xFF7FC8A2)),
    keypad = Paper.DarkKeypad,
)

val LocalNutriPalette = staticCompositionLocalOf { LightNutri }

/**
 * 三大營養素的固定色。畫進度條與圖例都從這裡取，不要在各畫面自己寫死色碼。
 *
 * 這些是 `@Composable` getter 而不是常數 —— 深淺兩套的值不一樣，
 * 而正確的那一套只有在 composition 裡（讀得到 [LocalNutriPalette]）才知道。
 */
object NutrientColors {
    val Calories: Color @Composable get() = LocalNutriPalette.current.calories
    val Protein: Color @Composable get() = LocalNutriPalette.current.protein
    val Fat: Color @Composable get() = LocalNutriPalette.current.fat
    val Carbs: Color @Composable get() = LocalNutriPalette.current.carbs

    /** 超過每日目標超過 10% 時轉這個顏色，一眼看得出來吃過頭 */
    val Over: Color @Composable get() = LocalNutriPalette.current.over

    /** 超過目標但還在 10% 以內，橘色警示 */
    val Warning: Color @Composable get() = LocalNutriPalette.current.warning

    /** 早／午／晚／點心依序的色，額度條分段與餐別標記共用 */
    val Meals: List<Color> @Composable get() = LocalNutriPalette.current.meals
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D5B),
    onPrimary = Paper.Bg,
    primaryContainer = Color(0xFFDCEDE3),
    onPrimaryContainer = Color(0xFF16301F),
    secondary = Color(0xFF2E7D5B),
    onSecondary = Paper.Bg,
    secondaryContainer = Color(0xFFDCEDE3),
    onSecondaryContainer = Color(0xFF16301F),
    tertiary = Color(0xFF2E7D5B),
    onTertiary = Paper.Bg,

    background = Paper.Bg,
    onBackground = Paper.Ink,
    surface = Paper.Bg,
    onSurface = Paper.Ink,

    // 整族 surface 都要自己指定，少蓋一個就會有元件固執地維持 M3 預設的紫。
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Paper.Raised,
    surfaceContainer = Paper.Container,
    surfaceContainerHigh = Paper.Track,
    surfaceContainerHighest = Paper.Track,
    surfaceVariant = Paper.Keypad,
    onSurfaceVariant = Paper.Muted,

    outline = Paper.Faint,
    outlineVariant = Paper.Hairline,
    error = Color(0xFFC0392B),
    onError = Color(0xFFFFFFFF),

    // FAB 與主要按鈕用「墨底紙字」，是這套設計的主要對比來源
    inverseSurface = Paper.Ink,
    inverseOnSurface = Paper.Bg,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FC8A2),
    onPrimary = Color(0xFF10281C),
    primaryContainer = Color(0xFF24302A),
    onPrimaryContainer = Color(0xFFCDEBDC),
    secondary = Color(0xFF7FC8A2),
    onSecondary = Color(0xFF10281C),
    secondaryContainer = Color(0xFF24302A),
    onSecondaryContainer = Color(0xFFCDEBDC),
    tertiary = Color(0xFF7FC8A2),
    onTertiary = Color(0xFF10281C),

    background = Paper.DarkBg,
    onBackground = Paper.DarkInk,
    surface = Paper.DarkBg,
    onSurface = Paper.DarkInk,

    surfaceContainerLowest = Color(0xFF0F0F0B),
    surfaceContainerLow = Paper.DarkRaised,
    surfaceContainer = Paper.DarkContainer,
    surfaceContainerHigh = Paper.DarkTrack,
    surfaceContainerHighest = Paper.DarkTrack,
    surfaceVariant = Paper.DarkKeypad,
    onSurfaceVariant = Paper.DarkMuted,

    outline = Paper.DarkFaint,
    outlineVariant = Paper.DarkHairline,
    error = Color(0xFFFF8A7A),
    onError = Color(0xFF3A0D07),

    inverseSurface = Paper.DarkInk,
    inverseOnSurface = Paper.DarkBg,
)

/**
 * 中文全部用無襯線（Roboto + 落到 Noto Sans CJK）。
 *
 * 純數字／英文字元不會觸發 CJK 字型 fallback，所以 [NumberFontFamily] 可以
 * 放心用系統襯線別名 `FontFamily.Serif`，不會重演「中文襯線讀起來像新細明體」
 * 的問題——那個問題是之前把 serif 套在整個 Typography（連食物名稱這種中文
 * 字串也一起套到）才出現的。現在只在確定是純數字/英文的 Text() 上單獨套用，
 * 例如 [NutriLog/app/src/main/java/com/watson/nutrilog/ui/TodayScreen.kt] 裡
 * 「已經吃」旁邊的大數字，中文文字（食物名稱、標題、標籤）維持無襯線不動。
 */
val NumberFontFamily = FontFamily.Serif

private val Base = Typography()

private val NutriTypography = Typography(
    // 主數字（還可以吃 490）
    displayLarge = Base.displayLarge.copy(
        fontSize = 46.sp, lineHeight = 50.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-1.5).sp,
    ),
    // 表單裡 2×2 的數字
    headlineSmall = Base.headlineSmall.copy(
        fontSize = 26.sp, lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp,
    ),
    // 名稱輸入
    titleLarge = Base.titleLarge.copy(
        fontSize = 22.sp, lineHeight = 30.sp,
        fontWeight = FontWeight.Medium,
    ),
    // 紀錄列的食物名稱與熱量
    titleMedium = Base.titleMedium.copy(
        fontSize = 16.sp, lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
    ),
    // 餐別標題（早餐／午餐…），字距拉開當作小標
    titleSmall = Base.titleSmall.copy(
        fontSize = 14.sp, lineHeight = 19.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp,
    ),
    bodyLarge = Base.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = Base.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = Base.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
    labelLarge = Base.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = Base.labelMedium.copy(fontSize = 11.sp, lineHeight = 15.sp),
    labelSmall = Base.labelSmall.copy(fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 1.sp),
)

/**
 * 全 app 輸入框共用的樣式：拿掉整圈外框，只留一條聚焦時會變粗變綠的底線，
 * 跟 [Hairline] 分隔線是同一套邏輯 —— 比 OutlinedTextField 的滿框更貼近
 * 「不填色只畫線」的頁面語言。頂角保留圓角、底角不圓，呼應底線是唯一邊界。
 */
val NutriFieldShape: Shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)

@Composable
fun nutriFieldColors(): TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    return TextFieldDefaults.colors(
        focusedContainerColor = scheme.primary.copy(alpha = 0.06f),
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        focusedIndicatorColor = scheme.primary,
        unfocusedIndicatorColor = scheme.outlineVariant,
        cursorColor = scheme.primary,
    )
}

@Composable
fun NutriLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalNutriPalette provides if (darkTheme) DarkNutri else LightNutri) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = NutriTypography,
            content = content,
        )
    }
}
