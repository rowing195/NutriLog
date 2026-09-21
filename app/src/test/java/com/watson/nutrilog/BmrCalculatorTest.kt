package com.watson.nutrilog

import com.watson.nutrilog.data.ActivityLevel
import com.watson.nutrilog.data.BmrCalculator
import com.watson.nutrilog.data.DietGoal
import com.watson.nutrilog.data.Gender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BmrCalculatorTest {

    private fun plan(
        activity: ActivityLevel,
        goal: DietGoal,
        weightKg: Float = 64f,
        watchSuppliesActivity: Boolean = false,
    ) = BmrCalculator.calculate(
        gender = Gender.FEMALE,
        age = 24,
        heightCm = 159f,
        weightKg = weightKg,
        activityLevel = activity,
        goal = goal,
        watchSuppliesActivity = watchSuppliesActivity,
    )

    /**
     * 運動交給手錶量的時候，熱量的底退到久坐係數：活動係數的定義本身就含運動
     * （UI 上「輕度」寫的是「每週運動 1–3 天」），不退的話同一批熱量會算兩次。
     * **蛋白質不跟著退**。
     */
    @Test
    fun `運動交給手錶量時熱量用久坐係數，蛋白質不受影響`() {
        val workoutOnly = plan(ActivityLevel.LIGHT, DietGoal.MAINTAIN)
        val allDay = plan(ActivityLevel.LIGHT, DietGoal.MAINTAIN, watchSuppliesActivity = true)

        val bmr = workoutOnly.bmr
        assertEquals((bmr * ActivityLevel.LIGHT.multiplier).toInt(), workoutOnly.targetCalories)
        assertEquals((bmr * ActivityLevel.SEDENTARY.multiplier).toInt(), allDay.targetCalories)
        assertTrue(allDay.targetCalories < workoutOnly.targetCalories)
        assertEquals(workoutOnly.proteinG, allDay.proteinG)
        assertEquals(workoutOnly.proteinPerKg, allDay.proteinPerKg, 0.001f)
    }

    /** 久坐的人兩種算法一樣 —— 係數本來就已經是久坐了。 */
    @Test
    fun `久坐的人不管誰量運動都是同一個熱量`() {
        assertEquals(
            plan(ActivityLevel.SEDENTARY, DietGoal.MAINTAIN).targetCalories,
            plan(ActivityLevel.SEDENTARY, DietGoal.MAINTAIN, watchSuppliesActivity = true).targetCalories,
        )
    }

    @Test
    fun `蛋白質依目標固定倍率且不受活動量與手錶影響`() {
        for ((goal, ratio) in listOf(
            DietGoal.MAINTAIN to 1.4f,
            DietGoal.GAIN_MUSCLE to 1.8f,
            DietGoal.LOSE_FAT to 1.6f,
        )) {
            for (activity in ActivityLevel.entries) {
                for (watch in listOf(false, true)) {
                    for (weight in listOf(20f, 64f, 70f, 95f, 300f)) {
                        val result = plan(activity, goal, weightKg = weight, watchSuppliesActivity = watch)
                        assertEquals(ratio, result.proteinPerKg, 0.001f)
                        assertEquals(kotlin.math.round(weight * ratio).toInt(), result.proteinG)
                    }
                }
            }
        }
    }

    /** 碳水是定值（總熱量的 55%），脂肪吃差額 —— 蛋白質變動不該全部倒進碳水。 */
    @Test
    fun `碳水固定佔五成五`() {
        val p = plan(ActivityLevel.LIGHT, DietGoal.MAINTAIN)
        val carbsShare = p.carbsG * 4.0 / p.targetCalories
        assertTrue("碳水佔 $carbsShare", carbsShare in 0.53..0.57)
    }

    /**
     * 蛋白質高到固定比例塞不下時，讓位的是碳水、不是脂肪 ——
     * 脂肪太低會影響荷爾蒙與脂溶性維生素吸收。
     */
    @Test
    fun `蛋白質很高的時候脂肪仍然不會被擠掉`() {
        val p = plan(ActivityLevel.VERY_HEAVY, DietGoal.LOSE_FAT, weightKg = 95f)
        val fatShare = p.fatG * 9.0 / p.targetCalories
        assertTrue("脂肪佔 $fatShare", fatShare >= 0.18)
    }

    /** 三大營養素加起來要等於目標熱量，不能因為換了算法就對不上。 */
    @Test
    fun `三大營養素加起來就是目標熱量`() {
        val p = plan(ActivityLevel.MODERATE, DietGoal.MAINTAIN)
        val sum = p.proteinG * 4 + p.fatG * 9 + p.carbsG * 4
        assertTrue("$sum vs ${p.targetCalories}", Math.abs(sum - p.targetCalories) <= 10)
    }
}
