package com.watson.nutrilog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.watson.nutrilog.R
import com.watson.nutrilog.data.WorkoutSessionItem
import com.watson.nutrilog.data.db.FoodEntry
import com.watson.nutrilog.data.db.Meal
import com.watson.nutrilog.ui.theme.NutrientColors
import com.watson.nutrilog.ui.theme.numeric
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

private val GreenAccent = Color(0xFF4EBA6F)

/**
 * 點擊首頁卡路里標籤時彈出的 Samsung Health 連動與熱量收支對帳對話框。
 */
@Composable
fun SamsungHealthDetailDialog(
    date: LocalDate,
    activeCalories: Double,
    calorieTarget: Int,
    consumedCalories: Double,
    entries: List<FoodEntry>,
    workoutSessions: List<WorkoutSessionItem> = emptyList(),
    onSyncNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val isToday = date == LocalDate.now()
    val netCalories = (consumedCalories - activeCalories).coerceAtLeast(0.0)
    val effectiveTarget = if (calorieTarget > 0 && activeCalories > 0) calorieTarget + activeCalories.roundToInt() else calorieTarget
    val remaining = if (calorieTarget > 0) effectiveTarget - consumedCalories else 0.0
    val isOver = remaining < 0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.background)
                    .border(1.dp, NutrientColors.FieldBorder, RoundedCornerShape(8.dp))
            ) {
                Rule()

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 頂部標題與狀態
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("⌚", fontSize = 16.sp)
                                Text(
                                    "Samsung Health 同步明細",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = scheme.onSurface,
                                )
                            }
                            Text(
                                if (isToday) "日期：$date（今天）" else "日期：$date",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(scheme.surfaceContainerHigh)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(GreenAccent)
                            )
                            Text(
                                "已連動",
                                style = MaterialTheme.typography.labelSmall,
                                color = GreenAccent,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    Hairline()

                    // 區塊 1：從手錶讀取的運動消耗
                    SectionTitle("📥 手錶運動消耗（讀取自三星健康）")
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(scheme.surfaceContainerLow)
                            .border(1.dp, NutrientColors.FieldBorder, RoundedCornerShape(6.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    "+${activeCalories.toInt()}",
                                    style = MaterialTheme.typography.displayMedium.numeric(),
                                    color = GreenAccent,
                                    modifier = Modifier.alignByBaseline(),
                                )
                                Text(
                                    "kcal",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic).numeric(),
                                    color = scheme.onSurfaceVariant,
                                    modifier = Modifier.alignByBaseline(),
                                )
                            }
                            Text(
                                "手錶全天活動與運動燃燒卡路里，已直接計入今日可攝取額度，協助您精準控制熱量赤字。",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                lineHeight = 18.sp,
                            )
                        }
                    }

                    // 區塊 1.5：手錶體能訓練專項紀錄
                    SectionTitle(
                        if (workoutSessions.isNotEmpty()) "🏃‍♂️ 手錶體能訓練專項紀錄（${workoutSessions.size} 場）"
                        else "🏃‍♂️ 手錶體能訓練專項紀錄"
                    )
                    if (workoutSessions.isEmpty()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(scheme.surfaceContainerLow)
                                .border(1.dp, NutrientColors.FieldBorder, RoundedCornerShape(6.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                "本日無特定專項體能訓練紀錄（如跑步機、重訓等），熱量消耗主要來自手錶日常走動與生活步數折抵。",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                lineHeight = 18.sp,
                            )
                        }
                    } else {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(scheme.surfaceContainerLow)
                                .border(1.dp, NutrientColors.FieldBorder, RoundedCornerShape(6.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            workoutSessions.forEachIndexed { index, session ->
                                if (index > 0) Hairline()
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Text(
                                            exerciseIcon(session.exerciseType),
                                            fontSize = 20.sp,
                                        )
                                        Column {
                                            Text(
                                                session.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = scheme.onSurface,
                                            )
                                            val subtitle = buildString {
                                                if (session.timeRangeText.isNotBlank()) {
                                                    append(session.timeRangeText)
                                                    append(" · ")
                                                }
                                                append("${session.durationMinutes} 分鐘")
                                                if (session.steps > 0) {
                                                    append(" · ${session.steps} 步")
                                                }
                                            }
                                            Text(
                                                subtitle,
                                                style = MaterialTheme.typography.labelSmall.numeric(),
                                                color = scheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    if (session.calories > 0.0) {
                                        Text(
                                            "+${session.calories.toInt()} kcal",
                                            style = MaterialTheme.typography.bodyMedium.numeric(),
                                            fontWeight = FontWeight.Bold,
                                            color = GreenAccent,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 區塊 2：今日熱量折抵收支對帳
                    SectionTitle("⚖️ 今日熱量收支平衡表")
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(scheme.surfaceContainerLow)
                            .border(1.dp, NutrientColors.FieldBorder, RoundedCornerShape(6.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        BalanceRow("飲食總攝取", "${consumedCalories.toInt()} kcal", scheme.onSurface)
                        BalanceRow("手錶活動消耗折抵", "-${activeCalories.toInt()} kcal", GreenAccent)
                        if (workoutSessions.isNotEmpty()) {
                            val workoutKcal = workoutSessions.sumOf { it.calories }
                            val nonWorkoutKcal = (activeCalories - workoutKcal).coerceAtLeast(0.0)
                            BalanceRow("  ↳ 專項體能訓練（${workoutSessions.size} 場）", "-${workoutKcal.toInt()} kcal", GreenAccent.copy(alpha = 0.85f))
                            if (nonWorkoutKcal > 0) {
                                BalanceRow("  ↳ 日常非運動步數折抵", "-${nonWorkoutKcal.toInt()} kcal", GreenAccent.copy(alpha = 0.85f))
                            }
                        }
                        Hairline()
                        BalanceRow("本日實際淨熱量", "${netCalories.toInt()} kcal", scheme.onSurface, bold = true)
                        BalanceRow("設定每日基準目標", "$calorieTarget kcal", scheme.onSurfaceVariant)
                        BalanceRow(
                            label = if (isOver) "目前淨額度狀態" else "目前剩餘可吃額度",
                            value = if (isOver) "超出 ${abs(remaining).toInt()} kcal" else "剩餘 ${remaining.toInt()} kcal",
                            color = if (isOver) NutrientColors.Over else GreenAccent,
                            bold = true,
                        )
                    }

                    // 區塊 3：同步回 Samsung Health 的飲食紀錄
                    SectionTitle("📤 已同步回 Samsung Health 之飲食紀錄")
                    if (entries.isEmpty()) {
                        Text(
                            "今天尚未記錄任何飲食。",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    } else {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(scheme.surfaceContainerLow)
                                .border(1.dp, NutrientColors.FieldBorder, RoundedCornerShape(6.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "已成功回傳 ${entries.size} 筆餐點資料至三星健康中樞：",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                            Hairline()
                            entries.forEach { entry ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            entry.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = scheme.onSurface,
                                        )
                                        Text(
                                            "${entry.mealType.shortLabel()} · P: ${entry.proteinG.roundToInt()}g / F: ${entry.fatG.roundToInt()}g / C: ${entry.carbsG.roundToInt()}g",
                                            style = MaterialTheme.typography.labelSmall.numeric(),
                                            color = scheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        "${entry.calories.roundToInt()} kcal",
                                        style = MaterialTheme.typography.bodySmall.numeric(),
                                        fontWeight = FontWeight.SemiBold,
                                        color = scheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }

                Rule()

                // 底部按鈕操作列
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    StampButton(
                        label = "↻ 立即從手錶重新同步",
                        onClick = onSyncNow,
                    )
                    TextAction(
                        stringResource(R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BalanceRow(
    label: String,
    value: String,
    color: Color,
    bold: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.numeric(),
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = color,
        )
    }
}

private fun exerciseIcon(exerciseType: Int): String = when (exerciseType) {
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "🏃‍♂️"
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> "🏋️‍♂️"
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "🚴‍♂️"
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "🚶‍♂️"
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "🏊‍♂️"
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "🧘"
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "⚡"
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "🚣"
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING,
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> "🪜"
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "⛷️"
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> "🤸"
    else -> "🏅"
}

