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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.NutriSettings
import com.watson.nutrilog.data.db.DayTotal
import com.watson.nutrilog.ui.theme.NumberFontFamily
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
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, stringResource(R.string.close))
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onShiftMonth(-1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.prev_month))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                month.year.toString() + "年" + month.monthValue + "月",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (month != YearMonth.from(today)) {
                TextButton(onClick = { onShiftMonth(monthsBetween(month, today)) }) {
                    Text(stringResource(R.string.back_to_this_month))
                }
            }
        }
        IconButton(onClick = { onShiftMonth(1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_month))
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(Modifier.fillMaxWidth()) {
        WEEKDAYS.forEachIndexed { index, label ->
            Text(
                label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                // 週末用淡一點的紅，跟平日區隔但不搶戲
                color = if (index == 0 || index == 6) {
                    NutrientColors.Over.copy(alpha = 0.75f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
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

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
    // 超標另外用紅／橘，因為那是不同性質的資訊，不是「更多一點」而已。
    val fill = when {
        total == null -> Color.Transparent
        severityColor != null -> severityColor.copy(alpha = 0.18f)
        else -> {
            val ratio = if (settings.calorieTarget > 0) {
                (kcal / settings.calorieTarget).coerceIn(0.0, 1.0).toFloat()
            } else {
                0.5f
            }
            scheme.primary.copy(alpha = 0.08f + 0.22f * ratio)
        }
    }

    Box(
        modifier
            .aspectRatio(0.85f)
            .clip(CellShape)
            .background(fill)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, scheme.primary, CellShape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = NumberFontFamily),
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                // 未來的日期壓淡：它們永遠是空的，不該看起來像「忘了記錄」
                color = when {
                    isToday -> scheme.primary
                    isFuture -> scheme.onSurfaceVariant.copy(alpha = 0.4f)
                    else -> scheme.onSurface
                },
            )
            if (total != null) {
                Text(
                    kcal.fmtInt(),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = NumberFontFamily),
                    fontSize = 10.sp,
                    color = severityColor ?: scheme.onSurfaceVariant,
                )
            }
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
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            stringResource(R.string.history_month_summary, loggedDays, average.fmtInt()),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (overDays > 0) {
            Text(
                stringResource(R.string.history_month_over, overDays),
                style = MaterialTheme.typography.bodySmall,
                color = NutrientColors.Over,
            )
        }
    }
}

private val CellShape = RoundedCornerShape(10.dp)

private val WEEKDAYS = listOf("日", "一", "二", "三", "四", "五", "六")

private fun monthsBetween(from: YearMonth, to: LocalDate): Long =
    (YearMonth.from(to).year - from.year) * 12L + (to.monthValue - from.monthValue)
