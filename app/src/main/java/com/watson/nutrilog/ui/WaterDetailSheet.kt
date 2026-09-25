package com.watson.nutrilog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.db.FoodEntry
import java.time.LocalDate
import kotlin.math.abs

/**
 * 今日頁點「飲水 800 ml」打開的明細：這個數字是哪幾杯、再加上手動按了多少。
 *
 * 手動加減一天只存一個淨值（見 `DailyWater`），所以它只有一行 —— 使用者想知道的是
 * 「我自己按了多少」，不是按了哪幾下，而那個淨值剛好就是答案。
 *
 * 做成面板而不是在飲水那一排底下就地展開：展開會把底下的紀錄整批往下推，
 * 而且展開狀態跟著日分頁走，換一天就收起來。面板浮在上面、開的時候就綁定那一天，
 * 兩件事都沒有。版面照運動明細：粗規線框頭尾、數字靠右、不上色。
 */
@Composable
fun WaterDetailSheet(
    date: LocalDate,
    entries: List<FoodEntry>,
    manualMl: Int,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val isToday = date == LocalDate.now()
    val drinks = entries.filter { (it.waterMl ?: 0.0) > 0.0 }
    // 和今日頁那一排同一條算式，兩邊的數字才對得上
    val total = drinks.sumOf { it.waterMl ?: 0.0 } + manualMl
    val dateLabel = stringResource(R.string.exercise_detail_date, date.monthValue, date.dayOfMonth)

    Column(
        Modifier
            .fillMaxWidth()
            // 浮在遮罩上的面板用 surfaceContainerLow：深色模式下 background 會和壓暗後的背景同色
            .background(scheme.surfaceContainerLow)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.water_detail_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                withNumerals(
                    if (isToday) stringResource(R.string.exercise_detail_today, dateLabel) else dateLabel
                ),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Rule(Modifier.padding(top = 10.dp, bottom = 6.dp))

        drinks.forEach { WaterDetailRow(it.name, (it.waterMl ?: 0.0).fmtInt()) }
        if (manualMl != 0) {
            WaterDetailRow(
                stringResource(R.string.water_manual),
                (if (manualMl > 0) "+" else "−") + abs(manualMl),
            )
        }
        // 一行都沒有時只剩合計 0，不必再隔一條線
        if (drinks.isNotEmpty() || manualMl != 0) Hairline(Modifier.padding(vertical = 6.dp))
        WaterDetailRow(stringResource(R.string.water_total), total.fmtInt())

        Rule(Modifier.padding(top = 10.dp, bottom = 4.dp))
        TextAction(
            label = stringResource(R.string.close),
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
private fun WaterDetailRow(label: String, ml: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 飲料名稱長短不一（「手沖藝妓黑咖啡」），不用運動明細那種固定寬度的標籤欄
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Text(
            withNumerals(ml + " " + stringResource(R.string.unit_ml)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
