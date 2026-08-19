package com.watson.nutrilog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.db.Meal
import com.watson.nutrilog.ui.theme.NutrientColors
import java.time.LocalDate
import java.time.format.TextStyle
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

/** 顯示用的數字：整數不拖小數點，其他保留一位。營養素再精確也沒有意義。 */
fun Double.fmt(): String =
    if (this % 1.0 == 0.0) toLong().toString() else String.format(Locale.US, "%.1f", this)

fun Double.fmtInt(): String = roundToInt().toString()

/** 「今天 8月19日（週三）」。有「今天／昨天」就不必自己數日期。 */
fun LocalDate.displayLabel(today: LocalDate = LocalDate.now()): String {
    val prefix = when (this) {
        today -> "今天 "
        today.minusDays(1) -> "昨天 "
        today.plusDays(1) -> "明天 "
        else -> ""
    }
    val week = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.TRADITIONAL_CHINESE)
    return "$prefix${monthValue}月${dayOfMonth}日（$week）"
}

/**
 * 一條營養素進度條。
 *
 * 超標時整條轉紅：這個 app 的重點是「今天還能吃多少」，
 * 進度條停在 100% 看起來就像剛好達標，那是完全相反的意思。
 */
@Composable
fun NutrientBar(
    label: String,
    value: Double,
    target: Int,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val fraction = if (target > 0) (value / target).toFloat() else 0f
    val over = fraction > 1f
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                "${value.fmtInt()} / $target $unit",
                style = MaterialTheme.typography.labelMedium,
                color = if (over) NutrientColors.Over else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            color = if (over) NutrientColors.Over else color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    }
}

/** 「蛋白 12 · 脂肪 5 · 碳水 30」這種一行摘要，紀錄列與歷史列共用。 */
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

val CardShape = RoundedCornerShape(16.dp)
