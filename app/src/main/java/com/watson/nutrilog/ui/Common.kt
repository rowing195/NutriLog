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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.db.Meal
import androidx.compose.ui.unit.TextUnit
import com.watson.nutrilog.ui.theme.NumberFontFamily
import com.watson.nutrilog.ui.theme.NumberTracking
import com.watson.nutrilog.ui.theme.numeric
import com.watson.nutrilog.ui.theme.NutrientColors
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.abs
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
 * [letterSpacing] 預設補上跟 `numeric()` 同一個字距下限。傳
 * [TextUnit.Unspecified] 可以改成沿用外層樣式的字距 —— `StampButton` 就是這樣，
 * 它那顆按鍵的 5sp 字距是設計的一部分，數字要跟中文一起被拉開。
 */
fun withNumerals(
    text: String,
    letterSpacing: TextUnit = NumberTracking,
): AnnotatedString = buildAnnotatedString {
    append(text)
    val span = SpanStyle(
        fontFamily = NumberFontFamily,
        fontFeatureSettings = "\"kern\" 0",
        letterSpacing = letterSpacing,
    )
    for (m in NumeralRun.findAll(text)) {
        addStyle(span, m.range.first, m.range.last + 1)
    }
}

// 小數點只有夾在數字中間才算數字的一部分，句尾的句號不要被吃進去
private val NumeralRun = Regex("""\d+(?:\.\d+)*""")

/** 紀錄列的第二行：「大碗 · 蛋白 19 · 脂肪 21 · 碳水 78」。份量沒填就不留空的分隔點。 */
fun detailLine(servingText: String, proteinG: Double, fatG: Double, carbsG: Double): String =
    listOf(
        servingText.takeIf { it.isNotBlank() },
        "蛋白 " + proteinG.fmt(),
        "脂肪 " + fatG.fmt(),
        "碳水 " + carbsG.fmt(),
    ).filterNotNull().joinToString(" · ")

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
                            withNumerals(placeholder),
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
                withNumerals(it),
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) NutrientColors.Over else scheme.outline,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * 點空白處收鍵盤，回到沒在打字的樣子。
 *
 * 搜尋打到一半改變主意時，這套版面沒有明顯的「取消打字」出口：系統返回鍵會把
 * 整個畫面關掉，而使用者其實只是想把鍵盤收起來、看回底下被鍵盤擠掉的清單。
 *
 * 用 [detectTapGestures] 而不是再疊一層 `clickable`：clickable 會把整片區域
 * 都變成可點的，蓋掉底下每一列自己的點擊，還會帶進 ripple 與無障礙焦點。
 * 這裡要的是「沒有人接的那些點擊」—— 手勢在 Main pass 是子先父後，列、按鍵
 * 與輸入框自己會先消費掉，能傳上來的就只剩真正的空白處。
 *
 * 掛在 Scaffold 的 modifier 上（而不是內容那層），報頭那一條空白也才算數。
 * 例外是編輯表單：它底部那塊是自製數字鍵盤，掛整張會連「按鍵之間的縫」都算成
 * 空白，瞄歪一點就把鍵盤關掉，所以那裡只掛在內容那層。
 *
 * [onTap] 給還有別套鍵盤要收的畫面（編輯表單的數字鍵盤不吃焦點，clearFocus
 * 收不到它）。用 rememberUpdatedState 是因為 pointerInput(Unit) 只會啟動一次，
 * 直接捕捉會永遠停在第一次組成的那一份 lambda。
 */
@Composable
fun Modifier.dismissKeyboardOnTap(onTap: () -> Unit = {}): Modifier {
    val focusManager = LocalFocusManager.current
    val latestOnTap = rememberUpdatedState(onTap)
    return this.pointerInput(Unit) {
        detectTapGestures {
            focusManager.clearFocus()
            latestOnTap.value()
        }
    }
}

// ─────────────────────────── 左滑露出動作 ───────────────────────────

/**
 * 左滑把一列推開，右邊露出一塊動作區（今日頁的刪除）。
 *
 * **不用 M3 的 SwipeToDismissBox**：它自帶容器色與動畫曲線，而且是「滑到底就直接
 * 執行」的語意——這裡要的是滑開之後停住、再點一次才算數，誤觸的代價才不會是少一筆
 * 紀錄。
 *
 * **動的是動作區，不是列本身。** 一般 swipe-to-reveal 是把列往左推，但這套版面的
 * 列只內縮 22dp，推開 88dp 之後短名稱會整個被推出左邊界——結果是「知道要刪東西，
 * 但不知道要刪哪一筆」。改成紅塊從右邊滑進來蓋住熱量數字，名稱永遠留在原位。
 *
 * 手勢是自己寫的，因為它要跟今日頁的日分頁器共存。**關著的時候只接往左的拖曳**：
 * 往右不消費，讓事件穿過去給分頁器換到前一天。代價是紀錄列上「往左換下一天」沒了
 * ——那個手勢還可以從週長條、月曆或空白處做，而左滑刪除只有在列上才有意義。
 * 垂直方向先過門檻就整個放手，清單照樣捲得動。
 *
 * [revealed] 由呼叫端持有，這樣同一份清單可以保證「同時只有一列是開的」。
 */
@Composable
fun SwipeToReveal(
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    actionWidth: Dp = 88.dp,
    action: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val revealPx = with(LocalDensity.current) { actionWidth.toPx() }
    val slop = LocalViewConfiguration.current.touchSlop
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // 外面把它關掉時（點了別列、或這一筆被刪了）要跟著收回去
    LaunchedEffect(revealed, revealPx) {
        offsetX.animateTo(if (revealed) -revealPx else 0f, tween(200))
    }

    Box(
        modifier
            // 收起來時紅塊是停在這一列右邊界外面的，不裁會畫到畫面的右側留白上
            .clipToBounds()
            // 手勢掛在外層而不是另外疊一片透明的攔截層：疊一層會把列自己的點擊
            // 蓋掉，而掛在外層時列的 clickable 先收到 down、拖曳的位移才輪到這裡，
            // 兩者剛好各司其職（一被判定成拖曳，點擊就會自己取消）。
            .pointerInput(revealPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startedOpen = offsetX.value < 0f
                    // **一定要是「真的拖過」才算**，不能因為列已經開著就把觸碰當拖曳：
                    // 那樣每一次點擊在放開時都會重新宣告一次 onRevealedChange(true)，
                    // 蓋掉刪除鍵與列自己剛剛設好的「關起來」——症狀是點了沒反應。
                    var dragging = false
                    var last = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (!dragging) {
                            val dx = change.position.x - down.position.x
                            val dy = change.position.y - down.position.y
                            // 垂直先過門檻 → 這是在捲清單，整個放手
                            if (abs(dy) > slop && abs(dy) > abs(dx)) break
                            if (abs(dx) < slop) {
                                last = change.position
                                continue
                            }
                            // 關著時往右 → 不消費，讓日分頁器去換前一天。
                            // 已經開著時往右是「把它收回去」，那要接。
                            if (dx > 0 && !startedOpen) break
                            dragging = true
                        }
                        val delta = change.position.x - last.x
                        last = change.position
                        change.consume()
                        scope.launch {
                            offsetX.snapTo((offsetX.value + delta).coerceIn(-revealPx, 0f))
                        }
                    }
                    if (dragging) {
                        // 過半就吸開，沒過就收回 —— 停在中間看起來像卡住了
                        val open = offsetX.value < -revealPx / 2
                        onRevealedChange(open)
                        scope.launch {
                            offsetX.animateTo(if (open) -revealPx else 0f, tween(200))
                        }
                    }
                }
            },
    ) {
        content()
        Box(
            Modifier
                .matchParentSize()
                .wrapContentSize(Alignment.CenterEnd)
                // 收起來時整塊停在右邊界外（+revealPx），拖到底才剛好貼齊
                .offset { IntOffset((revealPx + offsetX.value).roundToInt(), 0) },
        ) { action() }
    }
}

/**
 * 左滑露出來的刪除塊。
 *
 * 這裡**刻意用實心紅**，跟「破壞性動作用空心紅」那條規則不一樣：那條講的是常駐在
 * 畫面上的按鍵（設定頁的停止自動備份），實心紅會變成整頁最搶眼的東西。這一塊只在
 * 使用者主動滑開的那幾秒存在，而且那個當下它本來就該是最醒目的——空心紅藏在列的
 * 右邊反而看不出是可以按的。
 */
@Composable
fun DeleteReveal(onClick: () -> Unit, width: Dp = 88.dp) {
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.error)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TrashMark(MaterialTheme.colorScheme.inverseOnSurface)
    }
}

/**
 * 復原視窗的長度。
 *
 * ViewModel 的倒數與 [UndoStamp] 的倒數線讀**同一個值** —— 各寫各的話遲早會漂，
 * 症狀是線走完了章還在（或線還沒走完章就消失），而那正好會讓使用者不敢按。
 */
const val UNDO_WINDOW_MS = 5_000L

/**
 * 刪除之後左下角跳出來的復原章。
 *
 * 跟右下角「記一筆」那顆是同一個長相與同一個尺寸（60dp 方章、內縮 4dp 細框），
 * 只是退一階轉深灰 —— 兩個下角各站一顆章，一個是「加」一個是「收回剛剛那一下」，
 * 對稱本身就在講它們是同一層的東西。左下角是刻意的：右下角被記一筆佔著。
 *
 * 章的**下沿線**是它存在的另一半理由：不畫的話，使用者不知道自己還剩幾秒，猶豫
 * 一下就沒了。它貼著章的下緣、蓋在灰底上，退掉的部分露出原本的灰 —— 用線而不是
 * 進度圈，是因為整個 app 的層次本來就靠 `Rule` 和 `Hairline` 撐著。
 *
 * **線一定要線性收**（[LinearEasing]）：倒數用 ease 會騙人 —— 前面走很快、
 * 最後拖很久，看起來還有時間但其實已經到了。
 *
 * 線的顏色跟著主題走（[NutrientColors.Rule]：淺色是墨、深色是米），不是兩邊都用
 * 墨色。深色模式下這顆章本身是淺灰的，墨色線壓上去對比很硬；米色線和章的關係
 * 比較接近淺色模式那組，整體也安靜一些 —— 這是刻意選的柔和，不是沒注意到對比。
 *
 * 不用 M3 的 Snackbar：它是圓角、有陰影、還會自己排隊，在這套方角紙面上很突兀，
 * 而且橫躺一長條會壓住底下那一列紀錄。
 */
@Composable
fun UndoStamp(
    label: String,
    onClick: () -> Unit,
    /** 換一筆就重新倒數。連續刪兩筆時這顆章不會被重建，只靠這個值認出「換人了」。 */
    countdownKey: Any?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val progress = remember { Animatable(1f) }
    LaunchedEffect(countdownKey) {
        progress.snapTo(1f)
        progress.animateTo(0f, tween(UNDO_WINDOW_MS.toInt(), easing = LinearEasing))
    }

    Box(
        modifier
            .size(60.dp)
            .background(NutrientColors.StampSecondary)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(4.dp)
                .border(1.dp, scheme.onSurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                // 對面那顆章裡的加號是 22dp，字級太小這一邊會顯得沒份量。
                // titleSmall 原本帶著給中文標題用的 4sp 字距，在 52dp 的框裡太寬，收掉。
                style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 1.sp),
                color = scheme.inverseOnSurface,
                // 字距會在最後一個字後面也留一格，靠左推回來才是真的置中
                modifier = Modifier.padding(start = 1.dp),
            )
        }
        // 貼著下緣、剛好填滿內縮那 4dp，看起來就是這顆章的下沿線。
        // 佔的是章自己的空間，所以左右兩顆章的下緣仍然對得齊。
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .width(60.dp * progress.value)
                .height(4.dp)
                .background(NutrientColors.Rule)
        )
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
 * 這一層一個畫面只會有一顆，**唯一的例外是成對的動作**（設定頁的匯出／匯入）：
 * 它們是同一件事的兩個方向，做成不同形狀反而像在說其中一個比較次要。
 * 那種情況兩顆都是章，用 [color] 讓退一階的那顆轉 [NutrientColors.StampSecondary]。
 *
 * [color] 傳 `Color.Transparent` 會得到**空心章**：形狀還在（所以看得出跟上面那幾顆
 * 是同一類東西），但份量退掉。字色會自動跟著翻成墨色 —— 實心章的字是反白的，
 * 直接畫在紙上等於看不見。
 *
 * [destructive] 是空心章再轉朱紅（同 [NutriDialog] 的同名參數）。**不做成實心紅塊**：
 * 朱紅在這套色票裡的意思是「看這裡」，整顆填滿會變成設定頁上最搶眼的東西，
 * 而它其實是個次要動作。空心紅講的是「這顆要想一下再按」，剛好。
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
    color: Color? = null,
    destructive: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val fill = if (destructive) Color.Transparent else color ?: scheme.inverseSurface
    // 底透明就代表字直接畫在紙上，反白的字色在那裡是隱形的
    val onFill = when {
        destructive -> scheme.error
        fill.alpha == 0f -> scheme.onSurface
        else -> scheme.inverseOnSurface
    }
    val labelColor = if (enabled) onFill else scheme.outline
    androidx.compose.foundation.layout.Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(if (enabled) fill else Color.Transparent)
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(4.dp)
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .border(
                        1.dp,
                        when {
                            !enabled -> NutrientColors.FieldBorder
                            destructive -> scheme.error
                            else -> scheme.onSurfaceVariant
                        },
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (withPlus) {
                    PlusMark(labelColor)
                    Box(Modifier.size(11.dp))
                }
                Text(
                    // 沿用外層的 5sp：那是這顆按鍵的設計，數字要跟中文一起被拉開
                    withNumerals(label, letterSpacing = TextUnit.Unspecified),
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
                    // 呼叫端直接傳 String 就好，數字字型在元件內部套 ——
                    // 同 NutriTextField 的 placeholder 與 StampButton 的 label
                    withNumerals(message),
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
