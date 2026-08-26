package com.watson.nutrilog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.watson.nutrilog.R

/**
 * 紙與墨（第二版：刊物）。
 *
 * 前一版也叫紙與墨，但那是「不填色只畫線」的低對比版面；這一版把**排版**
 * 推成主角：報頭式的粗規線、襯線的大數字、朱紅只出現在超標與聚焦兩件事上。
 *
 * 三件跟前一版不一樣、而且會咬人的事：
 *
 * 1. **對比拉開了。** 前一版底 #F8F7F2 vs 浮起 #FDFCF9 只差 3%，是刻意的；
 *    這一版仍然靠線分隔，但線本身分兩級：[Rule] 是 2px 的墨線（報頭、區段），
 *    [Hairline] 才是原本那條 1px。不要把兩者混用，版面的節奏就是靠這兩級撐的。
 *
 * 2. **數字字型改成內嵌的 Instrument Serif**（`res/font/instrument_serif.ttf`，OFL）。
 *    它沒有中文字符，所以規矩跟前一版一樣、而且更嚴格：[NumberFontFamily]
 *    **只能套在確定不含中文的 Text 上**。套到中文會 fallback 到系統襯線，
 *    那正是之前「讀起來像新細明體」的老問題。中西文混排要用
 *    `buildAnnotatedString` 只框住數字那一段。
 *
 * 3. **中文一律無襯線。** 設計稿上的小標是 Noto Serif TC，但 Android 沒有那套
 *    可以只帶標題字重的做法（全字集動輒十幾 MB），落到系統襯線就會踩到第 2 點。
 *    所以小標改成無襯線＋拉開字距，靠字距而不是字族做出「這是標題」的感覺。
 */
private object Paper {
    // 淺色：紙
    val Bg = Color(0xFFF7F3E9)
    val Raised = Color(0xFFFDFBF5)
    val Container = Color(0xFFF0EADC)
    val Track = Color(0xFFE9E1D0)
    val Ink = Color(0xFF16130E)
    val Ink2 = Color(0xFF4A4437)
    val Muted = Color(0xFF6E6455)
    val Faint = Color(0xFF9C9484)
    val Pale = Color(0xFFB9B0A0)
    val Hairline = Color(0xFFE0D8C7)
    val FieldBorder = Color(0xFFC9C0AE)
    val Vermilion = Color(0xFFD8462A)
    val Ochre = Color(0xFFB8791F)

    // 深色：墨。不是純黑而是暖黑，純黑會讓襯線數字看起來發灰、細線整條消失。
    val DarkBg = Color(0xFF17150F)
    val DarkRaised = Color(0xFF1E1B14)
    val DarkContainer = Color(0xFF232016)
    val DarkTrack = Color(0xFF2C2820)
    val DarkInk = Color(0xFFEFE9DC)
    val DarkInk2 = Color(0xFFBDB5A2)
    val DarkMuted = Color(0xFFA8A08C)
    val DarkFaint = Color(0xFF7C7565)
    val DarkPale = Color(0xFF625C4D)
    val DarkHairline = Color(0xFF2F2B21)
    val DarkFieldBorder = Color(0xFF4A4636)
    val DarkVermilion = Color(0xFFF2705A)
    val DarkOchre = Color(0xFFD9A24E)
}

/**
 * 語意色。M3 的 ColorScheme 裝不下這些 —— 它沒有「碳水」這種角色。
 *
 * 深淺兩套的差別不只是明暗：三大營養素在深色底上要提亮，
 * 原本的 #3D5A6C 在 #17150F 上幾乎讀不出來。
 */
@Immutable
data class NutriPalette(
    val calories: Color,
    val protein: Color,
    val fat: Color,
    val carbs: Color,
    val over: Color,
    /** 超標但還在目標 10% 以內：比 [over] 溫和的赭色 */
    val warning: Color,
    /** 早／午／晚／點心，額度條分段用。由重到輕，早餐最重。 */
    val meals: List<Color>,
    /** 聚焦時的書寫線與游標色。整個 app 只有「正在編輯這一格」用得到。 */
    val accent: Color,
    /** 輸入框的外框（非聚焦） */
    val fieldBorder: Color,
    /** 報頭與區段用的 2px 重規線 */
    val rule: Color,
    val keypad: Color,
)

private val LightNutri = NutriPalette(
    calories = Paper.Ink,
    protein = Color(0xFF3D5A6C),
    fat = Color(0xFFA8722B),
    carbs = Color(0xFF6B4E7D),
    over = Paper.Vermilion,
    warning = Paper.Ochre,
    meals = listOf(Paper.Ink, Paper.Ink2, Paper.Muted, Paper.Faint),
    accent = Paper.Vermilion,
    fieldBorder = Paper.FieldBorder,
    rule = Paper.Ink,
    keypad = Paper.Container,
)

private val DarkNutri = NutriPalette(
    calories = Paper.DarkInk,
    protein = Color(0xFF7BA3BC),
    fat = Color(0xFFD9A867),
    carbs = Color(0xFFA98FD0),
    over = Paper.DarkVermilion,
    warning = Paper.DarkOchre,
    meals = listOf(Paper.DarkInk, Paper.DarkInk2, Paper.DarkMuted, Paper.DarkFaint),
    accent = Paper.DarkVermilion,
    fieldBorder = Paper.DarkFieldBorder,
    rule = Paper.DarkInk,
    keypad = Paper.DarkContainer,
)

val LocalNutriPalette = staticCompositionLocalOf { LightNutri }

/**
 * 固定的語意色。畫進度條與圖例都從這裡取，不要在各畫面自己寫死色碼。
 *
 * 這些是 `@Composable` getter 而不是常數 —— 深淺兩套的值不一樣，
 * 而正確的那一套只有在 composition 裡（讀得到 [LocalNutriPalette]）才知道。
 */
object NutrientColors {
    val Calories: Color @Composable get() = LocalNutriPalette.current.calories
    val Protein: Color @Composable get() = LocalNutriPalette.current.protein
    val Fat: Color @Composable get() = LocalNutriPalette.current.fat
    val Carbs: Color @Composable get() = LocalNutriPalette.current.carbs

    /** 超過每日目標 10% 以上時轉這個顏色，一眼看得出吃過頭 */
    val Over: Color @Composable get() = LocalNutriPalette.current.over

    /** 超過目標但還在 10% 以內 */
    val Warning: Color @Composable get() = LocalNutriPalette.current.warning

    val Meals: List<Color> @Composable get() = LocalNutriPalette.current.meals

    /** 聚焦（正在編輯的那一格）。跟 [Over] 同色是刻意的 —— 兩者都是「看這裡」。 */
    val Accent: Color @Composable get() = LocalNutriPalette.current.accent

    val FieldBorder: Color @Composable get() = LocalNutriPalette.current.fieldBorder

    /** 報頭與區段的 2px 重規線 */
    val Rule: Color @Composable get() = LocalNutriPalette.current.rule
}

private val LightColors = lightColorScheme(
    // primary 是「選中／目前這個」的墨色，不是彩色強調 —— 這套設計的強調色
    // 只有朱紅，而朱紅只給超標與聚焦，不能拿去畫選取狀態。
    primary = Paper.Ink,
    onPrimary = Paper.Bg,
    primaryContainer = Paper.Container,
    onPrimaryContainer = Paper.Ink,
    secondary = Paper.Ink,
    onSecondary = Paper.Bg,
    secondaryContainer = Paper.Container,
    onSecondaryContainer = Paper.Ink,
    tertiary = Paper.Ink,
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
    surfaceVariant = Paper.Container,
    onSurfaceVariant = Paper.Muted,

    outline = Paper.Faint,
    outlineVariant = Paper.Hairline,
    error = Paper.Vermilion,
    onError = Color(0xFFFFFFFF),

    // 印章鈕（記一筆／儲存）用「墨底紙字」，是這套設計的主要對比來源
    inverseSurface = Paper.Ink,
    inverseOnSurface = Paper.Bg,
)

private val DarkColors = darkColorScheme(
    primary = Paper.DarkInk,
    onPrimary = Paper.DarkBg,
    primaryContainer = Paper.DarkContainer,
    onPrimaryContainer = Paper.DarkInk,
    secondary = Paper.DarkInk,
    onSecondary = Paper.DarkBg,
    secondaryContainer = Paper.DarkContainer,
    onSecondaryContainer = Paper.DarkInk,
    tertiary = Paper.DarkInk,
    onTertiary = Paper.DarkBg,

    background = Paper.DarkBg,
    onBackground = Paper.DarkInk,
    surface = Paper.DarkBg,
    onSurface = Paper.DarkInk,

    surfaceContainerLowest = Color(0xFF100E09),
    surfaceContainerLow = Paper.DarkRaised,
    surfaceContainer = Paper.DarkContainer,
    surfaceContainerHigh = Paper.DarkTrack,
    surfaceContainerHighest = Paper.DarkTrack,
    surfaceVariant = Paper.DarkContainer,
    onSurfaceVariant = Paper.DarkMuted,

    outline = Paper.DarkFaint,
    outlineVariant = Paper.DarkHairline,
    error = Paper.DarkVermilion,
    onError = Color(0xFF3A0D07),

    inverseSurface = Paper.DarkInk,
    inverseOnSurface = Paper.DarkBg,
)

/**
 * 純數字／英文用的襯線字型：內嵌的 Instrument Serif（OFL）。
 *
 * 換成內嵌字型而不是系統別名 `FontFamily.Serif`，是因為系統襯線在多數 Android
 * 上就是 Noto Serif，字面接近 Times，看起來是「沒挑過字型」而不是「挑了襯線」。
 *
 * **這個字型沒有中文字符**，所以只能套在確定不含中文的 Text 上（大熱量數字、
 * 目標欄位、紀錄與搜尋的熱量、日期格、月曆、鍵盤按鍵）。套到中文會 fallback
 * 回系統襯線，那正是要避開的東西。中西文混排在同一句要用 `buildAnnotatedString`
 * 只框住數字那一段，不能整個 Text 一起套。
 */
val NumberFontFamily = FontFamily(Font(R.font.instrument_serif))

/**
 * 數字字距的公式：比例項 + 固定底量。Instrument Serif 字腔本來就比中文筆畫窄，
 * 字級一大，筆畫間的空隙看起來就像被壓扁成一條線 —— 這是「細長難讀」的真正原因，
 * 不是字重或字級的問題。
 *
 * **純比例（只乘一個百分比）在小字級上會失效**：11–20sp 這個範圍（目標／還有
 * 那兩行、三大營養素的 `/60` 分母、月曆格數字）乘 3% 算出來不到 1sp，肉眼幾乎
 * 看不出差別，使用者回報「還是有點擠」就是在講這些小數字，不是那兩個大數字
 * （那兩個 66sp／46sp 光比例項就有將近 2sp，已經夠開）。字距這種東西本來就
 * 該用「一個小字級也扛得住的固定量」加上「大字級才需要的比例量」一起算，
 * 不是單純跟著字級等比縮小 —— 兩者都要用只改比例項，小數字會維持擠；
 * 只改固定量，大數字又會拉得太開。
 */
private const val NUMBER_TRACKING_RATIO = 0.025f
private const val NUMBER_TRACKING_BASE = 0.4f

/**
 * 套用數字字型並拉開字距。所有純數字／英文的 [Text] 都該用這個，不要自己
 * `.copy(fontFamily = NumberFontFamily)` 再各自猜一個字距 —— 兩個大數字
 * （今日頁熱量、表單熱量格）原本套的是**負字距**（-1.5sp／-0.5sp），是抄一般
 * 無襯線標題「收緊變醒目」那招，但窄字腔的襯線數字禁不起再往內壓，那正是
 * 使用者看到「細長」的原因。
 *
 * 呼叫順序：字級要先確定（用基礎樣式本身的，或 `.copy(fontSize = ...)` 先設好）
 * 再呼叫這個，因為字距是從 `fontSize` 算出來的。
 */
fun TextStyle.numeric(): TextStyle = copy(
    fontFamily = NumberFontFamily,
    letterSpacing = (fontSize.value * NUMBER_TRACKING_RATIO + NUMBER_TRACKING_BASE).sp,
)

private val Base = Typography()

private val NutriTypography = Typography(
    // 今日頁的大熱量數字。Instrument Serif 的字腔比 Roboto 開，行高要壓得比字級小
    // 才不會在數字上下留出一大片空白。字距交給套用時的 [numeric]，不在這裡寫死
    // ——這裡原本是負字距，是造成「細長難讀」的原因，見 [numeric] 的說明。
    displayLarge = Base.displayLarge.copy(
        fontSize = 66.sp, lineHeight = 60.sp,
        fontWeight = FontWeight.Normal,
    ),
    // 編輯表單的熱量（滿版那一格）
    displaySmall = Base.displaySmall.copy(
        fontSize = 46.sp, lineHeight = 46.sp,
        fontWeight = FontWeight.Normal,
    ),
    // 三大營養素那三格
    headlineSmall = Base.headlineSmall.copy(
        fontSize = 26.sp, lineHeight = 28.sp,
        fontWeight = FontWeight.Normal, letterSpacing = 0.sp,
    ),
    // 名稱輸入
    titleLarge = Base.titleLarge.copy(
        fontSize = 22.sp, lineHeight = 30.sp,
        fontWeight = FontWeight.Medium,
    ),
    // 紀錄列的食物名稱
    titleMedium = Base.titleMedium.copy(
        fontSize = 15.sp, lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
    ),
    // 餐別標題（早餐／午餐…）。字距是這套設計裡「這是標題」的唯一訊號，
    // 因為中文不套襯線，只能靠字距把它和內文分開。
    titleSmall = Base.titleSmall.copy(
        fontSize = 12.sp, lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp,
    ),
    bodyLarge = Base.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = Base.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = Base.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
    labelLarge = Base.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = Base.labelMedium.copy(fontSize = 11.sp, lineHeight = 15.sp),
    // 欄位標籤與小標
    labelSmall = Base.labelSmall.copy(fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 2.4.sp),
)

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
