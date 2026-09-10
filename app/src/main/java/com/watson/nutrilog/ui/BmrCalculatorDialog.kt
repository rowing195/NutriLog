package com.watson.nutrilog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.watson.nutrilog.R
import com.watson.nutrilog.data.ActivityLevel
import com.watson.nutrilog.data.BmrCalculator
import com.watson.nutrilog.data.DietGoal
import com.watson.nutrilog.data.DietPlanResult
import com.watson.nutrilog.data.Gender
import com.watson.nutrilog.data.NutriSettings
import com.watson.nutrilog.ui.theme.NutrientColors
import com.watson.nutrilog.ui.theme.numeric

/**
 * 基礎代謝率 (BMR) 與體態飲食目標計算面板。
 *
 * 依據使用者輸入之身體數據、活動量與目標，即時推算：
 * 1. BMR（基礎代謝）與 TDEE（每日總能耗）
 * 2. 建議每日熱量、蛋白質、脂肪、碳水目標
 * 3. 早/午/晚/點心 四餐配比建議
 *
 * 點選「套用為每日目標」直接回填並儲存至 [NutriSettings]。
 */
@Composable
fun BmrCalculatorDialog(
    initialSettings: NutriSettings,
    onApply: (NutriSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    var gender by remember { mutableStateOf(initialSettings.profileGender) }
    var ageStr by remember { mutableStateOf(initialSettings.profileAge.toString()) }
    var heightStr by remember { mutableStateOf(initialSettings.profileHeightCm.let { if (it % 1 == 0f) it.toInt().toString() else it.toString() }) }
    var weightStr by remember { mutableStateOf(initialSettings.profileWeightKg.let { if (it % 1 == 0f) it.toInt().toString() else it.toString() }) }
    var activity by remember { mutableStateOf(initialSettings.profileActivity) }
    var goal by remember { mutableStateOf(initialSettings.profileGoal) }

    val age = ageStr.toIntOrNull() ?: 28
    val height = heightStr.toFloatOrNull() ?: 172f
    val weight = weightStr.toFloatOrNull() ?: 68f

    val plan: DietPlanResult by remember(gender, age, height, weight, activity, goal) {
        derivedStateOf {
            BmrCalculator.calculate(
                gender = gender,
                age = age,
                heightCm = height,
                weightKg = weight,
                activityLevel = activity,
                goal = goal,
            )
        }
    }

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
                    .fillMaxHeight(0.92f)
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
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        stringResource(R.string.bmr_calc_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.bmr_calc_open_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )

                    Hairline()

                    // 性別
                    SectionLabel(stringResource(R.string.bmr_gender))
                    BallotRow(
                        labels = listOf(
                            stringResource(R.string.bmr_gender_male),
                            stringResource(R.string.bmr_gender_female),
                        ),
                        selectedIndex = if (gender == Gender.MALE) 0 else 1,
                        onSelect = { gender = if (it == 0) Gender.MALE else Gender.FEMALE },
                    )

                    // 年齡、身高、體重
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        NutriTextField(
                            value = ageStr,
                            onValueChange = { ageStr = it.filter { c -> c.isDigit() }.take(3) },
                            label = stringResource(R.string.bmr_age) + "（" + stringResource(R.string.bmr_age_unit) + "）",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            numeric = true,
                            modifier = Modifier.weight(1f),
                        )
                        NutriTextField(
                            value = heightStr,
                            onValueChange = { heightStr = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                            label = stringResource(R.string.bmr_height) + "（" + stringResource(R.string.bmr_height_unit) + "）",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            numeric = true,
                            modifier = Modifier.weight(1f),
                        )
                        NutriTextField(
                            value = weightStr,
                            onValueChange = { weightStr = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                            label = stringResource(R.string.bmr_weight) + "（" + stringResource(R.string.bmr_weight_unit) + "）",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            numeric = true,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // 日常活動量
                    SectionLabel(stringResource(R.string.bmr_activity))
                    val isWatchConnected = initialSettings.readExerciseCalories && initialSettings.healthConnectSyncEnabled
                    if (isWatchConnected) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .border(1.dp, NutrientColors.FieldBorder, RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "🏃 已連動 Samsung Health 手錶實測",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = NutrientColors.Accent,
                                )
                                Text(
                                    "您的活動消耗由手錶每日真實記錄，且每週由 AI 健檢週報根據實測真實 TDEE 自動推薦最佳目標，無需手動猜測活動量。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        ActivityField(
                            selected = activity,
                            onSelect = { activity = it },
                        )
                    }

                    // 體態目標
                    SectionLabel(stringResource(R.string.bmr_goal))
                    RadioGroup(
                        options = listOf(
                            DietGoal.LOSE_FAT to stringResource(R.string.bmr_goal_lose),
                            DietGoal.MAINTAIN to stringResource(R.string.bmr_goal_maintain),
                            DietGoal.GAIN_MUSCLE to stringResource(R.string.bmr_goal_gain),
                        ),
                        selected = goal,
                        onSelect = { goal = it },
                    )

                    Hairline()

                    // 計算結果看板
                    ResultCard(plan)
                }

                Hairline()

                // 底部動作列
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    StampButton(
                        label = stringResource(R.string.bmr_apply_targets),
                        onClick = {
                            val updated = initialSettings.copy(
                                profileGender = gender,
                                profileAge = age,
                                profileHeightCm = height,
                                profileWeightKg = weight,
                                profileActivity = activity,
                                profileGoal = goal,
                                calorieTarget = plan.targetCalories,
                                proteinTargetG = plan.proteinG,
                                fatTargetG = plan.fatG,
                                carbsTargetG = plan.carbsG,
                            )
                            onApply(updated)
                        },
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
private fun <T> RadioGroup(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (item, label) ->
            val active = item == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(item) }
                    .padding(vertical = 7.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (active) scheme.onSurface else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActivityField(
    selected: ActivityLevel,
    onSelect: (ActivityLevel) -> Unit,
) {
    val options = listOf(
        ActivityLevel.SEDENTARY to stringResource(R.string.bmr_act_sedentary),
        ActivityLevel.LIGHT to stringResource(R.string.bmr_act_light),
        ActivityLevel.MODERATE to stringResource(R.string.bmr_act_moderate),
        ActivityLevel.HEAVY to stringResource(R.string.bmr_act_heavy),
        ActivityLevel.VERY_HEAVY to stringResource(R.string.bmr_act_very_heavy),
    )
    RadioGroup(
        options = options,
        selected = selected,
        onSelect = onSelect,
    )
}

@Composable
private fun ResultCard(plan: DietPlanResult) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(scheme.surfaceContainerLow)
            .border(1.dp, NutrientColors.FieldBorder, RoundedCornerShape(6.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 代謝雙指標
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    stringResource(R.string.bmr_stat_bmr),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    "${plan.bmr} kcal",
                    style = MaterialTheme.typography.titleMedium.numeric(),
                    color = scheme.onSurface,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.bmr_stat_tdee),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    "${plan.tdee} kcal",
                    style = MaterialTheme.typography.titleMedium.numeric(),
                    color = scheme.onSurface,
                )
            }
        }

        Hairline()

        // 建議每日目標
        Text(
            stringResource(R.string.bmr_suggested_targets),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    plan.targetCalories.toString(),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp).numeric(),
                    color = NutrientColors.Calories,
                )
                Text(
                    " kcal / 日",
                    style = MaterialTheme.typography.bodyMedium.numeric(),
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp, start = 4.dp),
                )
            }
        }

        // 三大營養素克數
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MacroCol("蛋白質", "${plan.proteinG} g", NutrientColors.Protein)
            MacroCol("脂肪", "${plan.fatG} g", NutrientColors.Fat)
            MacroCol("碳水", "${plan.carbsG} g", NutrientColors.Carbs)
        }

        Hairline()

        // 四餐分配建議
        Text(
            stringResource(R.string.bmr_meal_breakdown),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            plan.meals.forEach { meal ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        meal.mealName,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurface,
                    )
                    Text(
                        "${meal.calories} kcal · 蛋 ${meal.proteinG}g · 油 ${meal.fatG}g · 碳 ${meal.carbsG}g",
                        style = MaterialTheme.typography.bodySmall.numeric(),
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MacroCol(name: String, amount: String, color: Color) {
    Column {
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            amount,
            style = MaterialTheme.typography.titleMedium.numeric(),
            color = color,
        )
    }
}
