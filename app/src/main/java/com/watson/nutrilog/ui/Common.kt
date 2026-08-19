package com.watson.nutrilog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** 每種餐別一個符號。純文字清單掃起來很吃力，有個記號就容易定位。 */
fun Meal.symbol(): String = when (this) {
    Meal.BREAKFAST -> "🌅"
    Meal.LUNCH -> "🍱"
    Meal.DINNER -> "🍽"
    Meal.SNACK -> "🍪"
}

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
 * 熱量環。
 *
 * 中間顯示的是**還可以吃多少**，不是已經吃多少 —— 使用者在餐前打開 app
 * 想知道的是「還剩多少額度」，「已攝取 1850」還要自己減一次。
 * 超標就改成顯示超出多少並整圈轉紅。
 */
@Composable
fun CalorieRing(
    consumed: Double,
    target: Int,
    modifier: Modifier = Modifier,
    diameter: Int = 156,
    stroke: Int = 13,
) {
    val fraction = if (target > 0) (consumed / target).toFloat() else 0f
    val over = fraction > 1f
    val remaining = target - consumed
    val ringColor = if (over) NutrientColors.Over else NutrientColors.Calories
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(modifier.size(diameter.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = stroke.dp.toPx()
            val inset = width / 2
            val arcSize = Size(size.width - width, size.height - width)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = width, cap = StrokeCap.Round),
            )
            // 超標時整圈畫滿：停在某個角度看起來像還沒吃完，意思正好相反
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = if (over) 360f else fraction.coerceIn(0f, 1f) * 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = width, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(if (over) R.string.calories_over else R.string.calories_remaining),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                kotlin.math.abs(remaining).fmtInt(),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = if (over) NutrientColors.Over else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.unit_kcal),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 一個營養素的小卡：名稱、數值、細進度條。
 *
 * 三個並排時每個都很窄，所以數值用 "92/100 g" 這種緊湊寫法。
 * 單位跟著數值同一行 —— 單獨一行的 "g" 沒有對齊基準，看起來像掉出來的。
 */
@Composable
fun MacroStat(
    label: String,
    value: Double,
    target: Int,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val fraction = if (target > 0) (value / target).toFloat() else 0f
    val over = fraction > 1f
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value.fmtInt() + "/" + target + " " + unit,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (over) NutrientColors.Over else MaterialTheme.colorScheme.onSurface,
        )
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            color = if (over) NutrientColors.Over else color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp),
        )
    }
}

/** 「蛋白 12 · 脂肪 5 · 碳水 30」這種一行摘要，紀錄列與確認畫面共用。 */
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

val CardShape = RoundedCornerShape(20.dp)
val SmallCardShape = RoundedCornerShape(14.dp)
