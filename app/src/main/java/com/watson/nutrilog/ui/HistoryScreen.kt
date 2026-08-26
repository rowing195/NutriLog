package com.watson.nutrilog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.NutriSettings
import com.watson.nutrilog.data.db.DayTotal
import com.watson.nutrilog.ui.theme.numeric
import com.watson.nutrilog.ui.theme.NutrientColors
import java.time.LocalDate
import java.time.YearMonth

/**
 * 月曆式歷史。一格一天，格子裡直接寫當天熱量。
 *
 * 比起清單，月曆的價值在於**看得出空白**：哪幾天忘了記、
 * 連續幾天超標，一眼就有形狀。清單只會讓有紀錄的日子擠在一起，
 * 反而看不出中間漏了幾天。
 *
 * 一週從星期日開始（台灣的日曆慣例）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    month: YearMonth,
    totals: Map<String, DayTotal>,
    settings: NutriSettings,
    selectedDate: LocalDate,
    onShiftMonth: (Long) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onClose: () -> Unit,
) {
    val today = LocalDate.now()
    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.history_title),
                closeLabel = stringResource(R.string.close),
                onClose = onClose,
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MonthHeader(month, today, onShiftMonth)
            WeekdayHeader()
            MonthGrid(
                month = month,
                totals = totals,
                settings = settings,
                today = today,
                selectedDate = selectedDate,
                onOpenDay = onOpenDay,
            )
            MonthSummary(month, totals, settings)
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, today: LocalDate, onShiftMonth: (Long) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clickable { onShiftMonth(-1) },
            contentAlignment = Alignment.Center,
        ) { ChevronMark(scheme.onSurfaceVariant, pointsLeft = true, size = 18.dp) }

        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            // 純數字加斜線，套襯線不會碰到中文
            Text(
                month.year.toString() + " / " + "%02d".format(month.monthValue),
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp).numeric(),
            )
            if (month != YearMonth.from(today)) {
                Text(
                    stringResource(R.string.back_to_this_month),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { onShiftMonth(monthsBetween(month, today)) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        Box(
            Modifier.size(36.dp).clickable { onShiftMonth(1) },
            contentAlignment = Alignment.Center,
        ) { ChevronMark(scheme.onSurfaceVariant, pointsLeft = false, size = 18.dp) }
    }
}

@Composable
private fun WeekdayHeader() {
    Column {
        Hairline(Modifier.padding(bottom = 8.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            WEEKDAYS.forEachIndexed { index, label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                    // 週末用淡一點的朱紅，跟平日區隔但不搶戲
                    color = if (index == 0 || index == 6) {
                        NutrientColors.Over.copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    totals: Map<String, DayTotal>,
    settings: NutriSettings,
    today: LocalDate,
    selectedDate: LocalDate,
    onOpenDay: (LocalDate) -> Unit,
) {
    val firstDay = month.atDay(1)
    // ISO 的星期一是 1、星期日是 7。要排成「日一二三四五六」，
    // 星期日的前置空格數就是 0，所以取 value % 7。
    val leadingBlanks = firstDay.dayOfWeek.value % 7
    val daysInMonth = month.lengthOfMonth()
    val totalCells = leadingBlanks + daysInMonth
    val rows = (totalCells + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(rows) { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(7) { column ->
                    val cellIndex = row * 7 + column
                    val dayOfMonth = cellIndex - leadingBlanks + 1
                    if (dayOfMonth in 1..daysInMonth) {
                        val date = month.atDay(dayOfMonth)
                        DayCell(
                            date = date,
                            total = totals[date.toString()],
                            settings = settings,
                            isToday = date == today,
                            isSelected = date == selectedDate,
                            isFuture = date.isAfter(today),
                            onClick = { onOpenDay(date) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        // 佔位讓格線對齊，不然月初月末會歪掉
                        Box(Modifier.weight(1f).aspectRatio(0.85f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    total: DayTotal?,
    settings: NutriSettings,
    isToday: Boolean,
    isSelected: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kcal = total?.kcal ?: 0.0
    val severity = overSeverity(kcal, settings.calorieTarget)
    val severityColor = when (severity) {
        OverSeverity.OVER -> NutrientColors.Over
        OverSeverity.WARNING -> NutrientColors.Warning
        OverSeverity.NORMAL -> null
    }
    val scheme = MaterialTheme.colorScheme

    // 底色深淺代表「吃了多少」，讓整個月一眼看得出鬆緊；
    // 超標另外用朱紅／赭色，因為那是不同性質的資訊，不是「更多一點」而已。
    //
    // 這一版把圓角收成 2dp、每格固定畫一圈細線 —— 整個月看起來像一張印好的
    // 表格而不是一堆圓角磁磚，跟今日頁那些規線是同一套語言。
    val fill = when {
        total == null -> Color.Transparent
        severityColor != null -> severityColor.copy(alpha = 0.16f)
        else -> {
            val ratio = if (settings.calorieTarget > 0) {
                (kcal / settings.calorieTarget).coerceIn(0.0, 1.0).toFloat()
            } else {
                0.5f
            }
            scheme.onSurface.copy(alpha = 0.05f + 0.16f * ratio)
        }
    }

    Box(
        modifier
            .aspectRatio(0.85f)
            .clip(CellShape)
            .background(fill)
            .border(
                if (isSelected) 1.5.dp else 1.dp,
                if (isSelected) scheme.onSurface else scheme.outlineVariant,
                CellShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp).numeric(),
                // 未來的日期壓淡：它們永遠是空的，不該看起來像「忘了記錄」
                color = when {
                    isFuture -> scheme.onSurfaceVariant.copy(alpha = 0.4f)
                    else -> scheme.onSurface
                },
            )
            if (total != null) {
                Text(
                    kcal.fmtInt(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp).numeric(),
                    color = severityColor ?: scheme.onSurfaceVariant,
                )
            }
        }
        // 今天用一條底線標記，跟今日頁那條週長條的選取記號是同一個做法 ——
        // 不用粗體，因為襯線數字加粗在這個字級上幾乎看不出差別。
        if (isToday) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(scheme.onSurface)
            )
        }
    }
}

/** 這個月的概況。逐日看不出來的「平均吃多少、記了幾天」放在這裡。 */
@Composable
private fun MonthSummary(month: YearMonth, totals: Map<String, DayTotal>, settings: NutriSettings) {
    if (totals.isEmpty()) {
        Text(
            stringResource(R.string.history_empty_month),
            Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val loggedDays = totals.size
    val average = totals.values.sumOf { it.kcal } / loggedDays
    val overDays = totals.values.count { settings.calorieTarget > 0 && it.kcal > settings.calorieTarget }
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Hairline()
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SummaryStat(stringResource(R.string.history_stat_days), loggedDays.toString(), null)
            SummaryStat(stringResource(R.string.history_stat_average), average.fmtInt(), null)
            if (overDays > 0) {
                SummaryStat(
                    stringResource(R.string.history_stat_over),
                    overDays.toString(),
                    NutrientColors.Over,
                )
            }
        }
    }
}

/** 月概況的一欄。跟今日頁的三大營養素同一個排法：小標在上、襯線數字在下。 */
@Composable
private fun SummaryStat(label: String, value: String, tint: Color?) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SectionLabel(label)
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp).numeric(),
            color = tint ?: scheme.onSurface,
        )
    }
}

/** 幾乎方角。圓角磁磚會把整個月看成一堆按鈕，這裡要的是一張印好的表格。 */
private val CellShape = RoundedCornerShape(2.dp)

private val WEEKDAYS = listOf("日", "一", "二", "三", "四", "五", "六")

private fun monthsBetween(from: YearMonth, to: LocalDate): Long =
    (YearMonth.from(to).year - from.year) * 12L + (to.monthValue - from.monthValue)
