package com.watson.nutrilog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.watson.nutrilog.R
import com.watson.nutrilog.data.ActivityLevel
import com.watson.nutrilog.data.BmrCalculator
import com.watson.nutrilog.data.DietGoal
import com.watson.nutrilog.data.DietPlanResult
import com.watson.nutrilog.data.Gender
import com.watson.nutrilog.data.NutriSettings
import com.watson.nutrilog.data.db.Meal
import com.watson.nutrilog.ui.theme.numeric

/**
 * 依身型與目標算每日熱量與三大營養素（Mifflin-St Jeor ＋ 活動係數，算法在 [BmrCalculator]）。
 *
 * **算出來的只是建議，按「套用」才寫進目標** —— 和 AI 辨識、週報推薦同一條規則。
 * 身型跟著一起存下來：下次打開不必重填，週報也要用體重判斷蛋白質夠不夠。
 *
 * 從對方的版本改寫：拿掉圓角容器與 emoji 提示，營養素與餐別名稱改用字串資源；
 * 活動量的頻率說明與各餐配比照使用者的選擇保留。
 */
@Composable
fun BmrCalculatorDialog(
    initialSettings: NutriSettings,
    onApply: (NutriSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var gender by remember { mutableStateOf(initialSettings.profileGender) }
    var ageText by remember { mutableStateOf(initialSettings.profileAge.toString()) }
    var heightText by remember { mutableStateOf(initialSettings.profileHeightCm.plain()) }
    var weightText by remember { mutableStateOf(initialSettings.profileWeightKg.plain()) }
    var activity by remember { mutableStateOf(initialSettings.profileActivity) }
    var goal by remember { mutableStateOf(initialSettings.profileGoal) }

    // 欄位清空或打到一半時沿用原本的值：公式不能拿空字串算，但也不該因此讓結果跳成 0
    val age = ageText.toIntOrNull() ?: initialSettings.profileAge
    val height = heightText.toFloatOrNull() ?: initialSettings.profileHeightCm
    val weight = weightText.toFloatOrNull() ?: initialSettings.profileWeightKg
    val plan = remember(gender, age, height, weight, activity, goal) {
        BmrCalculator.calculate(
            gender = gender,
            age = age,
            heightCm = height,
            weightKg = weight,
            activityLevel = activity,
            goal = goal,
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                // 浮在遮罩上的面板用 surfaceContainerLow，方角 —— 這套版面沒有圓角容器
                .background(scheme.surfaceContainerLow),
        ) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.bmr_title), style = MaterialTheme.typography.titleMedium)
                Rule()

                SectionLabel(stringResource(R.string.bmr_gender))
                BallotRow(
                    labels = listOf(
                        stringResource(R.string.bmr_gender_male),
                        stringResource(R.string.bmr_gender_female),
                    ),
                    selectedIndex = if (gender == Gender.MALE) 0 else 1,
                    onSelect = { gender = if (it == 0) Gender.MALE else Gender.FEMALE },
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NutriTextField(
                        value = ageText,
                        onValueChange = { ageText = it.filter(Char::isDigit).take(3) },
                        label = stringResource(R.string.bmr_age) + "（" + stringResource(R.string.bmr_age_unit) + "）",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        numeric = true,
                        modifier = Modifier.weight(1f),
                    )
                    NutriTextField(
                        value = heightText,
                        onValueChange = { heightText = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                        label = stringResource(R.string.bmr_height) + "（cm）",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        numeric = true,
                        modifier = Modifier.weight(1f),
                    )
                    NutriTextField(
                        value = weightText,
                        onValueChange = { weightText = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                        label = stringResource(R.string.bmr_weight) + "（kg）",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        numeric = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                SectionLabel(stringResource(R.string.bmr_activity))
                // 五個選項各帶一句頻率說明，橫排放不下，所以直排；圓圈仍然是「單選」的形狀
                RadioList(
                    options = listOf(
                        ActivityLevel.SEDENTARY to stringResource(R.string.bmr_act_sedentary),
                        ActivityLevel.LIGHT to stringResource(R.string.bmr_act_light),
                        ActivityLevel.MODERATE to stringResource(R.string.bmr_act_moderate),
                        ActivityLevel.HEAVY to stringResource(R.string.bmr_act_heavy),
                        ActivityLevel.VERY_HEAVY to stringResource(R.string.bmr_act_very_heavy),
                    ),
                    selected = activity,
                    onSelect = { activity = it },
                )

                SectionLabel(stringResource(R.string.bmr_goal))
                BallotRow(
                    labels = listOf(
                        stringResource(R.string.bmr_goal_lose),
                        stringResource(R.string.bmr_goal_maintain),
                        stringResource(R.string.bmr_goal_gain),
                    ),
                    selectedIndex = DietGoal.entries.indexOf(goal),
                    onSelect = { goal = DietGoal.entries[it] },
                )

                Hairline()
                PlanSummary(plan)
            }

            Rule()
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StampButton(
                    label = stringResource(R.string.bmr_apply),
                    onClick = {
                        onApply(
                            initialSettings.copy(
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
                        )
                    },
                    // 章預設撐滿整行，不給 weight 的話「取消」會被擠到看不見
                    modifier = Modifier.weight(1f),
                )
                TextAction(
                    label = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.padding(start = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun PlanSummary(plan: DietPlanResult) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth()) {
        Text(
            withNumerals(stringResource(R.string.bmr_stat_bmr) + " " + "%,d".format(plan.bmr)),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            withNumerals(stringResource(R.string.bmr_stat_tdee) + " " + "%,d".format(plan.tdee)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    SectionLabel(stringResource(R.string.bmr_suggested))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(
            R.string.nutrient_calories to plan.targetCalories,
            R.string.nutrient_protein to plan.proteinG,
            R.string.nutrient_fat to plan.fatG,
            R.string.nutrient_carbs to plan.carbsG,
        ).forEach { (label, value) ->
            Column {
                Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                Text("%,d".format(value), style = MaterialTheme.typography.titleMedium.numeric())
            }
        }
    }

    // 標出每公斤幾克：這個數字高不高，看總克數看不出來，要對著體重才有意義
    Text(
        withNumerals(stringResource(R.string.bmr_protein_per_kg, "%.1f".format(plan.proteinPerKg))),
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurfaceVariant,
    )

    Hairline()
    SectionLabel(stringResource(R.string.bmr_meal_breakdown))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        // BmrCalculator 回傳的四餐順序就是早、午、晚、點心；名稱用 app 自己的餐別字串，
        // 不用它寫死在計算器裡的中文，兩邊才不會有一天對不上。
        plan.meals.zip(Meal.entries).forEach { (target, meal) ->
            Column {
                Text(meal.label(), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                Text("%,d".format(target.calories), style = MaterialTheme.typography.titleMedium.numeric())
            }
        }
    }
}

/** 直排的單選。圓圈是「只能選一個」的形狀，和 [BallotRow] 同一種語言，只是方向不同。 */
@Composable
private fun <T> RadioList(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column {
        options.forEach { (item, label) ->
            val active = item == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(item) }
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(16.dp)
                        .border(1.5.dp, if (active) scheme.onSurface else scheme.outline, CircleShape)
                        .padding(4.dp)
                        .background(if (active) scheme.onSurface else Color.Transparent, CircleShape)
                )
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (active) scheme.onSurface else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 172.0 顯示成 172，172.5 照原樣 —— 欄位裡多一個 .0 會讓人以為要填小數。 */
private fun Float.plain(): String = if (this % 1f == 0f) toInt().toString() else toString()
