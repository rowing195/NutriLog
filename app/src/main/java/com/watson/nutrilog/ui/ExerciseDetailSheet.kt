package com.watson.nutrilog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.watson.nutrilog.R
import com.watson.nutrilog.data.ActivitySource
import com.watson.nutrilog.data.DailyActivity
import com.watson.nutrilog.data.NO_ACTIVITY_DATA_REASON
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 今日頁點「運動 +350」打開的明細：這個數字從哪來、今天因此多了多少額度。
 *
 * 版面照紙與墨：粗規線框住頭尾、細線分段、數字靠右。**不上顏色** —— 運動消耗不是
 * 「看這裡」的警示，拿綠色去畫它會變成畫面上最搶眼的東西（對方原本的版本就是這樣）。
 *
 * 「來源」要講清楚，因為健康連線有三段退路（活動大卡 → 總消耗扣基礎代謝 → 步數換算），
 * 同樣是「+350」，可信度差很多；讀不到時也要講原因，不然看起來像壞了。
 */
@Composable
fun ExerciseDetailSheet(
    date: LocalDate,
    consumed: Double,
    baseTarget: Int,
    activeCalories: Double,
    activity: DailyActivity?,
    /** 這一天有幾筆已寫入健康連線；null＝沒開寫入，那一行整個不出現。 */
    mealsWritten: Int?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val isToday = date == LocalDate.now()
    val goal = effectiveCalorieTarget(baseTarget, activeCalories, readExercise = true)
    val remaining = goal - consumed
    val burned = activeCalories.roundToInt()
    val dateLabel = stringResource(R.string.exercise_detail_date, date.monthValue, date.dayOfMonth)

    Dialog(onDismissRequest = onDismiss) {
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
                    stringResource(R.string.exercise_detail_title),
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

            if (activity != null) {
                DetailRow(stringResource(R.string.exercise_source), sourceLabel(activity), alignEnd = false)
                activity.workoutSessions.forEachIndexed { index, session ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (index == 0) stringResource(R.string.exercise_workouts) else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.width(LABEL_WIDTH),
                        )
                        Text(
                            withNumerals(
                                session.title + "  " +
                                    stringResource(R.string.exercise_minutes, session.durationMinutes.toInt())
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            withNumerals("+" + session.calories.roundToInt()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Hairline(Modifier.padding(vertical = 6.dp))
            }

            DetailRow(stringResource(R.string.exercise_eaten), consumed.roundToInt().toString())
            DetailRow(stringResource(R.string.exercise_burned), "−$burned")
            DetailRow(
                stringResource(R.string.exercise_net),
                // 淨攝取不顯示負數：「吃了 300、動了 500」講成淨攝取 −200 讀起來像吃了負的東西
                (consumed - activeCalories).coerceAtLeast(0.0).roundToInt().toString(),
            )
            Hairline(Modifier.padding(vertical = 6.dp))
            DetailRow(
                stringResource(if (isToday) R.string.exercise_target_today else R.string.budget_target),
                "$baseTarget + $burned = $goal",
            )
            DetailRow(
                stringResource(if (remaining < 0) R.string.budget_over else R.string.budget_left),
                abs(remaining).roundToInt().toString(),
            )

            if (mealsWritten != null) {
                Hairline(Modifier.padding(vertical = 6.dp))
                Text(
                    withNumerals(stringResource(R.string.exercise_written, mealsWritten)),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }

            Rule(Modifier.padding(top = 10.dp, bottom = 12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // 章預設撐滿整行，不給 weight 的話「關閉」會被擠到看不見。
                // 給了 weight 之後 Row 先量「關閉」自己的寬度，剩下的才給章。
                StampButton(
                    label = stringResource(R.string.exercise_refresh),
                    onClick = onRefresh,
                    color = Color.Transparent,
                    modifier = Modifier.weight(1f),
                )
                TextAction(
                    label = stringResource(R.string.close),
                    onClick = onDismiss,
                    modifier = Modifier.padding(start = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun sourceLabel(activity: DailyActivity): String = when (activity.source) {
    ActivitySource.ACTIVE_CALORIES -> stringResource(R.string.exercise_source_active)
    ActivitySource.WORKOUT_SESSIONS -> stringResource(R.string.exercise_source_workouts)
    ActivitySource.TOTAL_MINUS_BMR -> stringResource(R.string.exercise_source_total)
    ActivitySource.STEPS -> stringResource(R.string.exercise_source_steps, "%,d".format(activity.steps))
    ActivitySource.NONE -> stringResource(
        R.string.exercise_source_none,
        activity.unavailableReason ?: NO_ACTIVITY_DATA_REASON,
    )
}

@Composable
private fun DetailRow(label: String, value: String, alignEnd: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(LABEL_WIDTH),
        )
        Text(
            withNumerals(value),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 左欄標籤的固定寬度，讓「來源」「運動」「吃了」「淨攝取」的值對齊同一條線。 */
private val LABEL_WIDTH = 88.dp
