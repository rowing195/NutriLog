package com.watson.nutrilog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
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
 * 推成主角：報頭式的粗規線、手寫的大數字、朱紅只出現在超標與聚焦兩件事上。
 *
 * 三件跟前一版不一樣、而且會咬人的事：
 *
 * 1. **對比拉開了。** 前一版底 #F8F7F2 vs 浮起 #FDFCF9 只差 3%，是刻意的；
 *    這一版仍然靠線分隔，但線本身分兩級：[Rule] 是 2px 的墨線（報頭、區段），
 *    [Hairline] 才是原本那條 1px。不要把兩者混用，版面的節奏就是靠這兩級撐的。
 *
 * 2. **數字字型是內嵌的 Neucha 手寫體**（`res/font/neucha.ttf`，OFL）。
 *    它沒有中文字符，所以規矩很嚴格：[NumberFontFamily] **只能套在確定不含
 *    中文的 Text 上**。套到中文會 fallback 回系統字型，中英文變成兩種長相
 *    混在同一句裡。中西文混排要拆成兩個 Text 並排，不要整句一起套。
 *    （中間曾經用過 Instrument Serif，換掉的原因見 [NumberFontFamily]。）
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
    // 遮罩永遠用深色的墨：它是「把背景壓暗」的工具，不能跟著主題翻成亮色
    scrim = Paper.Ink,
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

    // 跟淺色用同一個 scrim：遮罩的工作是壓暗，不能跟著主題翻成亮色
    scrim = Paper.Ink,
    inverseSurface = Paper.DarkInk,
    inverseOnSurface = Paper.DarkBg,
)

/**
 * 純數字／英文用的手寫字型：內嵌的 Neucha（OFL，Jovanny Lemonad）。
 *
 * 用內嵌字型而不是系統別名，是因為 Android 沒有任何一支內建的手寫體可以指定；
 * 而挑手寫體是因為這個 app 叫「肥胖日記」—— 每天隨手記一筆的東西，數字長得像
 * 手寫的比像印刷品更貼近它在做的事。
 *
 * 前一版用的是 Instrument Serif（刊物風的襯線數字）。換掉的原因不是它醜，是它
 * **字腔窄**：字級一大，筆畫間的空隙看起來就被壓成一條線，讀起來細長，得靠
 * 額外的字距去補，而補到剛好很難拿捏。Neucha 字腔天生就開，那個問題自己消失，
 * 連帶把那套補償用的浮動字距也拆掉了（見 [numeric]）。
 *
 * **這個字型沒有中文字符**，所以只能套在確定不含中文的 Text 上（大熱量數字、
 * 目標欄位、紀錄與搜尋的熱量、日期格、月曆、鍵盤按鍵、kcal/g/mg 這些單位）。
 * 套到中文會 fallback 回系統字型，中英文會變成兩種長相混在同一句裡。
 * 中西文混排要拆成兩個 Text 並排，不能整個 Text 一起套。
 *
 * **這支 ttf 不是原封不動的 Google Fonts 版本。** 原版 Neucha 的數字側邊留白很
 * 不平均：0/6/8/9 有 41 units，1/2/3/4/5/7 是 0（`two` 甚至是 −1），句點只有 20，
 * 於是 `11` 完全沒有間隙、`0.2` 的點黏在兩邊數字上。已經把 0-9 與 `. , / + -`
 * 全部補成 lsb = rsb = 41 units —— 41 是這支字型自己給 0/6/8/9 的值，等於把其餘
 * 字形補到它原本的標準，不是外加一套新的節奏（0/6/8 的 advance 幾乎沒變動）。
 * 字型本身的 kern 由 [numeric] 關掉，原因見那裡。OFL 允許修改，且它沒有宣告
 * Reserved Font Name，所以檔名與字型名維持不變。重新從 Google Fonts 下載會失去
 * 這個處理。
 */
val NumberFontFamily = FontFamily(Font(R.font.neucha))

/**
 * 套用數字字型。所有純數字／英文的 [Text] 都該用這個，不要自己
 * `.copy(fontFamily = NumberFontFamily)`。
 *
 * 字距明確歸零，不是「沒設」：基礎樣式各自帶著給中文標題用的字距
 * （titleSmall 4sp、labelSmall 2.4sp），不歸零的話數字會跟著被拉開。
 * **不要在這裡加浮動字距**，理由見 [NumberFontFamily]。
 *
 * 關掉 kerning 是為了搭配那支動過側邊留白的 neucha.ttf：字型裡的 kern 是照
 * 原本過緊的 metrics 調的，全是負值（`zero`+`period` 是 −52），補完側邊留白
 * 之後它們會把字又拉回去。側邊留白已經整排補成一致的 41 units，這裡不需要
 * 任何逐對微調。
 */
fun TextStyle.numeric(): TextStyle = copy(
    fontFamily = NumberFontFamily,
    letterSpacing = 0.sp,
    fontFeatureSettings = "\"kern\" 0",
)

private val Base = Typography()

private val NutriTypography = Typography(
    // 今日頁的大熱量數字。行高刻意壓得比字級小，不然數字上下會留出一大片空白
    // （手寫體的行高本來就為了容納上下伸部而留得很寬，但這裡只放數字）。
    // 字距交給套用時的 [numeric]，不在這裡寫死。
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
    val scheme = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalNutriPalette provides if (darkTheme) DarkNutri else LightNutri) {
        MaterialTheme(
            colorScheme = scheme,
            typography = NutriTypography,
        ) {
            // `LocalContentColor` 的預設值是 **純黑**，只有 M3 的 `Surface` 會覆蓋它。
            // 這個 app 不用 M3 的成品容器，畫面是 `Modifier.background()` 疊出來的，
            // 所以只要有 `Text` 沒寫 `color`，拿到的就是黑色。淺色模式下黑字配米底
            // 剛好是對的，所以這件事一直沒被發現；深色模式就變成黑底黑字。
            //
            // 在這裡給一個正確的預設，比在每個 Text 上補 `color` 可靠 —— 後者漏一個
            // 就是一處看不見的文字，而且只在深色模式下才會發現。
            CompositionLocalProvider(LocalContentColor provides scheme.onSurface, content = content)
        }
    }
}
