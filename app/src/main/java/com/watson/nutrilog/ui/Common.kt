package com.watson.nutrilog.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.db.Meal
import com.watson.nutrilog.ui.theme.NumberFontFamily
import com.watson.nutrilog.ui.theme.numeric
import com.watson.nutrilog.ui.theme.NutrientColors
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun Meal.label(): String = stringResource(
    when (this) {
        Meal.BREAKFAST -> R.string.meal_breakfast
        Meal.LUNCH -> R.string.meal_lunch
        Meal.DINNER -> R.string.meal_dinner
        Meal.SNACK -> R.string.meal_snack
    }
)

/** 一個字的餐別。額度條下面那行小計四個並排，放不下「早餐」兩個字。 */
@Composable
fun Meal.shortLabel(): String = stringResource(
    when (this) {
        Meal.BREAKFAST -> R.string.meal_breakfast_short
        Meal.LUNCH -> R.string.meal_lunch_short
        Meal.DINNER -> R.string.meal_dinner_short
        Meal.SNACK -> R.string.meal_snack_short
    }
)

/** 顯示用的數字：整數不拖小數點，其他保留一位。營養素再精確也沒有意義。 */
fun Double.fmt(): String =
    if (this % 1.0 == 0.0) toLong().toString() else String.format(Locale.US, "%.1f", this)

fun Double.fmtInt(): String = roundToInt().toString()

/** 熱量超標的嚴重程度：超過目標 10% 以內用赭色警示，超過 10% 才轉朱紅。 */
enum class OverSeverity { NORMAL, WARNING, OVER }

fun overSeverity(kcal: Double, target: Int): OverSeverity {
    if (target <= 0 || kcal <= target) return OverSeverity.NORMAL
    val excessRatio = (kcal - target) / target
    return if (excessRatio > 0.10) OverSeverity.OVER else OverSeverity.WARNING
}

/** 超標色，沒超標就回傳 null（讓呼叫端沿用中性色）。不要自己寫二分法的紅／不紅。 */
@Composable
fun severityTint(value: Double, target: Int): Color? = when (overSeverity(value, target)) {
    OverSeverity.OVER -> NutrientColors.Over
    OverSeverity.WARNING -> NutrientColors.Warning
    OverSeverity.NORMAL -> null
}

/** 「今天 8月19日（週三）」。有「今天／昨天」就不必自己數日期。 */
fun LocalDate.displayLabel(today: LocalDate = LocalDate.now()): String {
    val prefix = when (this) {
        today -> "今天 "
        today.minusDays(1) -> "昨天 "
        today.plusDays(1) -> "明天 "
        else -> ""
    }
    val week = dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.TRADITIONAL_CHINESE)
    return "$prefix${monthValue}月${dayOfMonth}日（$week）"
}

/**
 * 中文夾數字的那種一行摘要，把**數字段**換成手寫的數字字型，中文留給系統字型。
 *
 * 這是 `numeric()` 的中西文混排版本：`numeric()` 是整個 [Text] 換字型，只能用在
 * 保證不含中文的字串上；這裡是逐段換。
 *
 * 用正規式標「所有數字段」，不是去 `indexOf` 某個值 —— 差別很重要。找特定數值會
 * 被巧合的相同字串框錯段（要標熱量 330，結果框到份量裡的 330）；標所有數字段是
 * 確定的，句子怎麼組都不會標錯。
 *
 * 跟 [numeric] 一樣要關掉 kerning，理由見那裡。
 */
fun withNumerals(text: String): AnnotatedString = buildAnnotatedString {
    append(text)
    for (m in NumeralRun.findAll(text)) {
        addStyle(NumeralSpan, m.range.first, m.range.last + 1)
    }
}

// 小數點只有夾在數字中間才算數字的一部分，句尾的句號不要被吃進去
private val NumeralRun = Regex("""\d+(?:\.\d+)*""")

private val NumeralSpan = SpanStyle(
    fontFamily = NumberFontFamily,
    fontFeatureSettings = "\"kern\" 0",
)

/** 紀錄列的第二行：「大碗 · 蛋白 19 · 脂肪 21 · 碳水 78」。份量沒填就不留空的分隔點。 */
fun detailLine(servingText: String, proteinG: Double, fatG: Double, carbsG: Double): String =
    listOf(
        servingText.takeIf { it.isNotBlank() },
        "蛋白 " + proteinG.fmt(),
        "脂肪 " + fatG.fmt(),
        "碳水 " + carbsG.fmt(),
    ).filterNotNull().joinToString(" · ")

/** 「蛋白 12 · 脂肪 5 · 碳水 30」這種一行摘要，AI 確認畫面用。 */
@Composable
fun MacroSummaryText(
    proteinG: Double,
    fatG: Double,
    carbsG: Double,
    modifier: Modifier = Modifier,
) {
    Text(
        "蛋白 ${proteinG.fmt()} · 脂肪 ${fatG.fmt()} · 碳水 ${carbsG.fmt()}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = modifier,
    )
}

// ─────────────────────────── 線 ───────────────────────────
//
// 這套版面靠線分隔而不是卡片色塊，線分兩級：Rule 是報頭與區段的 2px 墨線，
// Hairline 是列與列之間的 1px。兩者不要混用 —— 版面的節奏就是靠這兩級撐的。

/** 2px 的重規線。報頭底下、區段之間用。 */
@Composable
fun Rule(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(NutrientColors.Rule)
    )
}

/** 1px 的細規線。列與列之間用。 */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/** 拉開字距的小標。中文不套襯線，所以「這是標題」全靠字距。 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier,
    )
}

// ─────────────────────────── 輸入框 ───────────────────────────

/** 輸入框的圓角。四角都圓 —— 前一版是「頂角圓、底角方」，配的是只有底線的樣式。 */
val NutriFieldShape = RoundedCornerShape(12.dp)

/**
 * 全 app 共用的輸入框：**完整外框 ＋ 一條比其他三邊重的底規線**。
 *
 * 前一版是只有底線、沒有外框（M3 第一版帶起來的樣式）。Google 後來自己的研究
 * 把它改掉了 —— 沒有封閉邊界時，使用者辨認「哪裡可以打字」明顯變慢，M3 現在
 * 建議有框的樣式。這裡留住外框，但底下那條 3px 才是這套設計自己的東西，
 * 跟頁面上分隔區段的 [Rule] 同源，所以不會變成到處都一樣的圓角灰底方塊。
 *
 * 聚焦時外框轉墨色、底線轉朱紅 —— 對比夠強，不必再加一圈發光。
 *
 * 用 [BasicTextField] 自己拼而不是包 M3 的 TextField：M3 的容器高度、label
 * 浮動動畫與 indicator 都改不動到這個樣子，硬改會變成跟它的內建 padding 打架。
 */
@Composable
fun NutriTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    helper: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    numeric: Boolean = false,
    textStyle: TextStyle? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    /** 編輯表單要靠這個在點文字欄位時把自製數字鍵盤收起來，不然兩套鍵盤會疊在一起。 */
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }

    val borderColor = when {
        !enabled -> scheme.outlineVariant
        isError -> NutrientColors.Over
        focused -> scheme.onSurface
        else -> NutrientColors.FieldBorder
    }
    val ruleColor = when {
        !enabled -> NutrientColors.FieldBorder
        isError -> NutrientColors.Over
        focused -> NutrientColors.Accent
        else -> scheme.onSurface
    }
    val containerColor = when {
        !enabled -> scheme.surfaceVariant
        isError -> NutrientColors.Over.copy(alpha = 0.05f)
        focused -> scheme.surfaceContainerLowest
        else -> scheme.surfaceContainerLow
    }

    val resolvedStyle = (textStyle ?: MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp))
        .let { if (numeric) it.numeric() else it }

    androidx.compose.foundation.layout.Column(modifier) {
        label?.let {
            SectionLabel(it, Modifier.padding(bottom = 6.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(NutriFieldShape)
                .background(containerColor)
                .border(1.dp, borderColor, NutriFieldShape)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    // 下面 3dp 是底規線的位置，內容不能壓上去
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                leading?.invoke()
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            placeholder,
                            style = resolvedStyle,
                            color = scheme.outline,
                            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        readOnly = readOnly,
                        singleLine = singleLine,
                        textStyle = resolvedStyle.copy(
                            color = if (enabled) scheme.onSurface else scheme.outline
                        ),
                        cursorBrush = SolidColor(NutrientColors.Accent),
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        visualTransformation = visualTransformation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged {
                                focused = it.isFocused
                                onFocusChanged?.invoke(it.isFocused)
                            },
                    )
                }
                trailing?.invoke()
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // 靜止 2dp、聚焦 3dp。規線是絕對定位在底部的，改高度不會推到
                    // 上面的內容。靜止時全部都是 3px 全黑，疊四五個欄位（設定頁）
                    // 會變成一排黑槓，像舊式報表 —— 那才是「古板」的來源。
                    .height(if (focused || isError) 3.dp else 2.dp)
                    .background(ruleColor)
            )
        }
        helper?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) NutrientColors.Over else scheme.outline,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

// ─────────────────────────── 按鍵 ───────────────────────────
//
// 形狀就是層級，不要用同一種矩形做完所有按鍵：
//   印章 [StampButton]  主要動作，一個畫面只有一顆
//   藥丸 [PillButton]   就地確認（收鍵盤、重試）
//   圓章 [RoundKey]     數字鍵
//   圈選 [MealPicker]   單選
//   線框 [CircleIconButton] 圖示鈕

/** 細線畫的加號。不用 Material 內建圖示 —— 那是最容易讓 app 看起來沒設計過的一件事。 */
@Composable
fun PlusMark(color: Color, size: Dp = 15.dp, stroke: Dp = 1.6.dp) {
    Canvas(Modifier.size(size)) {
        val w = stroke.toPx()
        drawLine(color, Offset(size.toPx() / 2, 0f), Offset(size.toPx() / 2, size.toPx()), w, StrokeCap.Round)
        drawLine(color, Offset(0f, size.toPx() / 2), Offset(size.toPx(), size.toPx() / 2), w, StrokeCap.Round)
    }
}

/**
 * 線性圖示。全部自己畫，不用 Material 內建那套 —— 一眼認得出是 Material 預設圖示，
 * 是最容易讓 app 看起來沒設計過的一件事。
 *
 * 同一套規格：24 格、1.6dp 線寬、圓端點。
 */
@Composable
fun SearchMark(color: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val s = size.toPx()
        val w = 1.6.dp.toPx()
        drawCircle(color, radius = s * 0.27f, center = Offset(s * 0.46f, s * 0.46f), style = Stroke(w))
        drawLine(color, Offset(s * 0.67f, s * 0.67f), Offset(s * 0.92f, s * 0.92f), w, StrokeCap.Round)
    }
}

@Composable
fun CalendarMark(color: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val s = size.toPx()
        val w = 1.6.dp.toPx()
        drawRect(color, Offset(s * 0.12f, s * 0.2f), androidx.compose.ui.geometry.Size(s * 0.76f, s * 0.68f), style = Stroke(w))
        drawLine(color, Offset(s * 0.12f, s * 0.42f), Offset(s * 0.88f, s * 0.42f), w)
        drawLine(color, Offset(s * 0.34f, s * 0.09f), Offset(s * 0.34f, s * 0.28f), w, StrokeCap.Round)
        drawLine(color, Offset(s * 0.66f, s * 0.09f), Offset(s * 0.66f, s * 0.28f), w, StrokeCap.Round)
    }
}

/** 設定。用推桿而不是齒輪：齒輪是所有 app 都一樣的那顆。 */
@Composable
fun SlidersMark(color: Color, size: Dp = 18.dp, background: Color) {
    Canvas(Modifier.size(size)) {
        val s = size.toPx()
        val w = 1.6.dp.toPx()
        drawLine(color, Offset(s * 0.08f, s * 0.31f), Offset(s * 0.92f, s * 0.31f), w, StrokeCap.Round)
        drawLine(color, Offset(s * 0.08f, s * 0.69f), Offset(s * 0.92f, s * 0.69f), w, StrokeCap.Round)
        drawCircle(background, radius = s * 0.15f, center = Offset(s * 0.64f, s * 0.31f))
        drawCircle(color, radius = s * 0.15f, center = Offset(s * 0.64f, s * 0.31f), style = Stroke(w))
        drawCircle(background, radius = s * 0.15f, center = Offset(s * 0.36f, s * 0.69f))
        drawCircle(color, radius = s * 0.15f, center = Offset(s * 0.36f, s * 0.69f), style = Stroke(w))
    }
}

@Composable
fun ChevronMark(color: Color, pointsLeft: Boolean, size: Dp = 16.dp) {
    Canvas(Modifier.size(size)) {
        val s = size.toPx()
        val w = 1.6.dp.toPx()
        val tip = if (pointsLeft) s * 0.34f else s * 0.66f
        val tail = if (pointsLeft) s * 0.62f else s * 0.38f
        drawLine(color, Offset(tail, s * 0.2f), Offset(tip, s * 0.5f), w, StrokeCap.Round)
        drawLine(color, Offset(tip, s * 0.5f), Offset(tail, s * 0.8f), w, StrokeCap.Round)
    }
}

@Composable
fun CloseMark(color: Color, size: Dp = 16.dp) {
    Canvas(Modifier.size(size)) {
        val s = size.toPx()
        val w = 1.6.dp.toPx()
        drawLine(color, Offset(s * 0.2f, s * 0.2f), Offset(s * 0.8f, s * 0.8f), w, StrokeCap.Round)
        drawLine(color, Offset(s * 0.8f, s * 0.2f), Offset(s * 0.2f, s * 0.8f), w, StrokeCap.Round)
    }
}

@Composable
fun TrashMark(color: Color, size: Dp = 16.dp) {
    Canvas(Modifier.size(size)) {
        val s = size.toPx()
        val w = 1.6.dp.toPx()
        drawLine(color, Offset(s * 0.14f, s * 0.28f), Offset(s * 0.86f, s * 0.28f), w, StrokeCap.Round)
        drawLine(color, Offset(s * 0.36f, s * 0.28f), Offset(s * 0.36f, s * 0.16f), w, StrokeCap.Round)
        drawLine(color, Offset(s * 0.36f, s * 0.16f), Offset(s * 0.64f, s * 0.16f), w, StrokeCap.Round)
        drawLine(color, Offset(s * 0.64f, s * 0.16f), Offset(s * 0.64f, s * 0.28f), w, StrokeCap.Round)
        drawLine(color, Offset(s * 0.24f, s * 0.28f), Offset(s * 0.31f, s * 0.86f), w, StrokeCap.Round)
        drawLine(color, Offset(s * 0.76f, s * 0.28f), Offset(s * 0.69f, s * 0.86f), w, StrokeCap.Round)
        drawLine(color, Offset(s * 0.31f, s * 0.86f), Offset(s * 0.69f, s * 0.86f), w, StrokeCap.Round)
    }
}

@Composable
fun ListMark(color: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val s = size.toPx()
        val w = 1.6.dp.toPx()
        drawLine(color, Offset(s * 0.16f, s * 0.27f), Offset(s * 0.84f, s * 0.27f), w, StrokeCap.Round)
        drawLine(color, Offset(s * 0.16f, s * 0.5f), Offset(s * 0.84f, s * 0.5f), w, StrokeCap.Round)
        drawLine(color, Offset(s * 0.16f, s * 0.73f), Offset(s * 0.58f, s * 0.73f), w, StrokeCap.Round)
    }
}

/** 手動輸入：四格數字的意思。 */
@Composable
fun GridMark(color: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val s = size.toPx()
        val w = 1.6.dp.toPx()
        listOf(
            Offset(s * 0.12f, s * 0.12f), Offset(s * 0.54f, s * 0.12f),
            Offset(s * 0.12f, s * 0.54f), Offset(s * 0.54f, s * 0.54f),
        ).forEach {
            drawRect(color, it, androidx.compose.ui.geometry.Size(s * 0.34f, s * 0.34f), style = Stroke(w))
        }
    }
}

@Composable
fun CameraMark(color: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val s = size.toPx()
        val w = 1.6.dp.toPx()
        drawRect(color, Offset(s * 0.08f, s * 0.26f), androidx.compose.ui.geometry.Size(s * 0.84f, s * 0.6f), style = Stroke(w))
        drawLine(color, Offset(s * 0.34f, s * 0.26f), Offset(s * 0.42f, s * 0.13f), w, StrokeCap.Round)
        drawLine(color, Offset(s * 0.42f, s * 0.13f), Offset(s * 0.58f, s * 0.13f), w, StrokeCap.Round)
        drawLine(color, Offset(s * 0.58f, s * 0.13f), Offset(s * 0.66f, s * 0.26f), w, StrokeCap.Round)
        drawCircle(color, radius = s * 0.17f, center = Offset(s * 0.5f, s * 0.57f), style = Stroke(w))
    }
}

@Composable
fun ImageMark(color: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val s = size.toPx()
        val w = 1.6.dp.toPx()
        drawRect(color, Offset(s * 0.1f, s * 0.18f), androidx.compose.ui.geometry.Size(s * 0.8f, s * 0.64f), style = Stroke(w))
        drawCircle(color, radius = s * 0.07f, center = Offset(s * 0.32f, s * 0.36f), style = Stroke(w))
        drawLine(color, Offset(s * 0.14f, s * 0.78f), Offset(s * 0.42f, s * 0.48f), w, StrokeCap.Round)
        drawLine(color, Offset(s * 0.42f, s * 0.48f), Offset(s * 0.86f, s * 0.78f), w, StrokeCap.Round)
    }
}

@Composable
fun BarcodeMark(color: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val s = size.toPx()
        val w = 1.5.dp.toPx()
        listOf(0.14f, 0.30f, 0.40f, 0.58f, 0.74f, 0.86f).forEach { x ->
            drawLine(color, Offset(s * x, s * 0.16f), Offset(s * x, s * 0.84f), w, StrokeCap.Round)
        }
    }
}

/**
 * 不定長度的進度線。取代 M3 的 CircularProgressIndicator ——
 * 那顆轉圈是所有 app 都一樣的那顆，而且圓形在這個滿是規線的版面上很突兀。
 */
@Composable
fun IndeterminateRule(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "loading")
    val head by transition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "head",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(scheme.outlineVariant)
    ) {
        BoxWithConstraints {
            val width = maxWidth
            Box(
                Modifier
                    .offset(x = width * head)
                    .width(width * 0.35f)
                    .height(2.dp)
                    .background(scheme.onSurface)
            )
        }
    }
}

/**
 * 複選用的方框打勾。
 *
 * 刻意是**方的**，而餐別那種單選是**圓的** —— 形狀本身就在講「這裡可以複選」
 * 還是「只能挑一個」，不必等使用者點下去才發現。
 */
@Composable
fun SquareCheck(checked: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .size(20.dp)
            .background(if (checked) scheme.onSurface else Color.Transparent, RoundedCornerShape(2.dp))
            .border(
                1.5.dp,
                if (checked) scheme.onSurface else NutrientColors.FieldBorder,
                RoundedCornerShape(2.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) CheckMark(scheme.inverseOnSurface, size = 12.dp)
    }
}

@Composable
fun CheckMark(color: Color, size: Dp = 14.dp) {
    Canvas(Modifier.size(size)) {
        val s = size.toPx()
        val w = 2f * 1.1.dp.toPx()
        drawLine(color, Offset(s * 0.14f, s * 0.54f), Offset(s * 0.40f, s * 0.80f), w, StrokeCap.Round)
        drawLine(color, Offset(s * 0.40f, s * 0.80f), Offset(s * 0.86f, s * 0.24f), w, StrokeCap.Round)
    }
}

/** 退格。同樣自己畫：一個尖端朝左的六邊形加一個叉。 */
@Composable
fun BackspaceMark(color: Color, size: Dp = 26.dp) {
    Canvas(Modifier.size(size)) {
        val s = size.toPx()
        val w = 1.5.dp.toPx()
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(s * 0.88f, s * 0.23f)
            lineTo(s * 0.38f, s * 0.23f)
            lineTo(s * 0.12f, s * 0.5f)
            lineTo(s * 0.38f, s * 0.77f)
            lineTo(s * 0.88f, s * 0.77f)
            close()
        }
        drawPath(path, color, style = Stroke(width = w))
        drawLine(color, Offset(s * 0.52f, s * 0.40f), Offset(s * 0.73f, s * 0.61f), w, StrokeCap.Round)
        drawLine(color, Offset(s * 0.73f, s * 0.40f), Offset(s * 0.52f, s * 0.61f), w, StrokeCap.Round)
    }
}

/**
 * 印章：主要動作（記一筆／儲存）。
 *
 * 滿版墨底，裡面再退 4dp 畫一條細框 —— 是印章的雙框，不是一塊填滿的色。
 * 這一層一個畫面只會有一顆。
 *
 * 不能按的時候整顆退成**外框章**而不是灰掉的實心塊：灰掉的實心塊看起來像壞了，
 * 外框章看得出它還在、只是還沒輪到它。理由用 [helper] 講，不彈東西。
 */
@Composable
fun StampButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    withPlus: Boolean = false,
    helper: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val labelColor = if (enabled) scheme.inverseOnSurface else scheme.outline
    androidx.compose.foundation.layout.Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(if (enabled) scheme.inverseSurface else Color.Transparent)
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(4.dp)
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .border(1.dp, if (enabled) scheme.onSurfaceVariant else NutrientColors.FieldBorder),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (withPlus) {
                    PlusMark(labelColor)
                    Box(Modifier.size(11.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 5.sp),
                    color = labelColor,
                    // 字距會在最後一個字後面也留一格，靠左推回來才是真的置中
                    modifier = Modifier.padding(start = 5.dp),
                )
            }
        }
        helper?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp),
            )
        }
    }
}

/** 藥丸：就地確認（收鍵盤、重試、去設定）。比印章小一號、圓端。 */
@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(if (filled) scheme.inverseSurface else Color.Transparent)
            .then(if (filled) Modifier else Modifier.border(1.dp, NutrientColors.FieldBorder, CircleShape))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 3.sp),
            color = if (filled) scheme.inverseOnSurface else scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 3.dp),
        )
    }
}

/**
 * 圓章：自製數字鍵盤的按鍵。
 *
 * 圓的，因為整頁其他東西都是方的與線性的 —— 一片全是方格的鍵盤在紙感版面上
 * 會像試算表。圓章也呼應打字機鍵帽，跟襯線數字是同一個時代的東西。
 */
@Composable
fun RoundKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ringed: Boolean = true,
    dimmed: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.height(56.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .then(
                    if (ringed) {
                        Modifier
                            .background(scheme.surfaceContainerLow)
                            .border(
                                1.5.dp,
                                if (dimmed) scheme.outlineVariant else NutrientColors.FieldBorder,
                                CircleShape,
                            )
                    } else {
                        Modifier
                    }
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

/** 線框圖示鈕。實線圈（要跟整列點擊分開的動作）或淡框（只是提示可以點）。 */
@Composable
fun CircleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    borderColor: Color? = null,
    borderWidth: Dp = 1.5.dp,
    content: @Composable () -> Unit,
) {
    val color = borderColor ?: MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .border(borderWidth, color, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * 餐別選擇：印刷表單的圈選，不是四個色塊。
 *
 * 原本是四個一模一樣的方塊、選中的填色 —— 在這種低對比紙色上很難一眼看出選了
 * 哪個。空心圈 vs 實心圈的差別遠比「淡色塊 vs 深色塊」明顯，選中的字再加粗一級。
 *
 * 圈只有 16dp，但整列 42dp 高都是可點區，不必點準它。
 */
@Composable
fun MealPicker(selected: Meal, modifier: Modifier = Modifier, onSelect: (Meal) -> Unit) {
    val meals = Meal.entries
    BallotRow(
        labels = meals.map { it.label() },
        selectedIndex = meals.indexOf(selected),
        modifier = modifier,
        onSelect = { onSelect(meals[it]) },
    )
}

/**
 * 附屬畫面共用的頂欄：離開 ／ 標題 ／ 選用的右側動作，底下一條 2px 重規線。
 *
 * 不用 M3 的 `TopAppBar`：它自帶容器色、標題字級與左側 navigationIcon 的位置，
 * 改到這個樣子等於整組覆寫，還會跟它內建的 padding 打架。**自己拼的頂欄不會
 * 自動避開狀態列**，所以 [statusBarsPadding] 這行不能少 —— 少了標題會直接畫在時鐘上。
 */
@Composable
fun ScreenTopBar(
    title: String,
    closeLabel: String,
    onClose: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier
            .statusBarsPadding()
            .padding(horizontal = 22.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                closeLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onClose)
                    .padding(vertical = 10.dp, horizontal = 2.dp),
            )
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            // 右側沒有動作時也要留位子，不然標題會因為有沒有那顆鈕而左右跳動
            if (trailing != null) trailing() else Box(Modifier.size(30.dp))
        }
        Rule(Modifier.padding(top = 6.dp))
    }
}

/**
 * 單選一列（圈選）。餐別與外觀設定共用。
 *
 * 空心圈 vs 實心圈的差別，遠比在這種低對比紙色上「淡色塊 vs 深色塊」明顯 ——
 * 這也是為什麼不用 M3 的 SegmentedButton：它靠容器色分辨選中與否，
 * 在這套色票上兩個狀態幾乎看不出差別。
 */
@Composable
fun BallotRow(
    labels: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Row(
                Modifier
                    .clickable { onSelect(index) }
                    .padding(vertical = 13.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (active) scheme.onSurface else Color.Transparent)
                        .border(
                            1.5.dp,
                            if (active) scheme.onSurface else NutrientColors.FieldBorder,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (active) {
                        Box(
                            Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(scheme.inverseOnSurface)
                        )
                    }
                }
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) scheme.onSurface else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 開關。
 *
 * 方的滑軌配方的滑塊 —— M3 那顆膠囊軌道加圓球在這個滿是直角規線的版面上，
 * 是唯一一個圓頭的東西。開＝墨底紙塊、關＝空框淡塊，靠明暗而不是靠位置分辨，
 * 位置只是輔助。
 */
@Composable
fun NutriSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 21.dp else 3.dp,
        animationSpec = tween(160),
        label = "switchKnob",
    )
    Box(
        Modifier
            .size(width = 46.dp, height = 26.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (checked) scheme.onSurface else Color.Transparent)
            .border(
                1.5.dp,
                if (checked) scheme.onSurface else NutrientColors.FieldBorder,
                RoundedCornerShape(3.dp),
            )
            .clickable { onCheckedChange(!checked) },
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = knobOffset)
                .size(18.dp)
                .clip(RoundedCornerShape(2.dp))
                // 兩個狀態都是實心塊。關的時候如果畫成空心框，會變成「空框裡套一個
                // 空框」，看不出哪個是軌道哪個是滑塊。
                .background(if (checked) scheme.inverseOnSurface else NutrientColors.FieldBorder)
        )
    }
}

/**
 * 分頁標籤（常吃／最近）。選中的那個下面一條 3px 墨線，跟輸入框的書寫線同源。
 *
 * 不用 M3 的 `TabRow`：它的指示器是圓角膠囊、還自帶 ripple 與容器色。
 */
@Composable
fun NutriTabs(
    labels: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selectedIndex
            // 寬度要收到文字本身：直接給底線 fillMaxWidth 的話，它會吃到 Row 傳
            // 下來的最大寬度，第一個分頁就把整列撐滿，後面的分頁被擠出畫面。
            Column(
                Modifier
                    .width(IntrinsicSize.Max)
                    .clickable { onSelect(index) },
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 3.sp),
                    color = if (active) scheme.onSurface else scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 7.dp),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(if (active) scheme.onSurface else Color.Transparent)
                )
            }
        }
    }
}

/** 次要動作：純文字，不加框。一個畫面只有一顆印章，其餘的出路都長這樣。 */
@Composable
fun TextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
    )
}

/**
 * 確認對話框。
 *
 * 自己拼而不是用 M3 的 `AlertDialog`：它是圓角 28dp 的容器、按鈕在右下角並排，
 * 整個是 Material 的長相。這裡沿用頁面的語言 —— 方角紙面、2px 重規線壓頂、
 * 破壞性動作用印章（暖紅底），取消退成純文字。
 */
@Composable
fun NutriDialog(
    title: String,
    message: String,
    confirmLabel: String,
    cancelLabel: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(scheme.background)
                .border(1.dp, NutrientColors.FieldBorder, RoundedCornerShape(6.dp))
        ) {
            Rule()
            Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .height(48.dp)
                        .background(if (destructive) scheme.error else scheme.inverseSurface)
                        .clickable(onClick = onConfirm)
                        .padding(4.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .border(1.dp, scheme.inverseOnSurface.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            confirmLabel,
                            style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 5.sp),
                            color = scheme.inverseOnSurface,
                            modifier = Modifier.padding(start = 5.dp),
                        )
                    }
                }
                TextAction(
                    cancelLabel,
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 6.dp),
                )
            }
        }
    }
}

val PillShape = CircleShape
val CardShape = RoundedCornerShape(6.dp)
