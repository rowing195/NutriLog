package com.watson.nutrilog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.db.Meal
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

/**
 * 分隔線。這套版面用線分隔而不是卡片色塊，所以這是最常出現的元件，
 * 值得有個名字 —— 而不是每個畫面各自寫一次 1dp 的 Box。
 */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/** 拉開字距的小標（「常吃 · 一點就記」）。 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outline,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier,
    )
}

/**
 * 餐別選擇。編輯表單與 AI 確認畫面共用 —— 只要是「要把東西記進某一餐」的地方
 * 就該讓使用者自己挑，不能由 app 依時間猜了就算。
 *
 * 不用 M3 的 FilterChip：它自帶的容器色在這套低對比色票上幾乎看不出選中與否。
 */
@Composable
fun MealPicker(selected: Meal, modifier: Modifier = Modifier, onSelect: (Meal) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Meal.entries.forEach { meal ->
            val active = meal == selected
            Text(
                meal.label(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(PillShape)
                    .background(if (active) scheme.primary else Color.Transparent)
                    .border(1.dp, if (active) scheme.primary else scheme.outlineVariant, PillShape)
                    .clickable { onSelect(meal) }
                    .padding(vertical = 7.dp),
            )
        }
    }
}

val PillShape = RoundedCornerShape(16.dp)
val CardShape = RoundedCornerShape(16.dp)
