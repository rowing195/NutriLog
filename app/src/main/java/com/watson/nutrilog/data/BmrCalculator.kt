package com.watson.nutrilog.data

/**
 * 各餐建議攝取目標。
 */
data class MealTarget(
    val mealName: String,
    val calories: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
)

/**
 * 體態與飲食計算結果。
 */
data class DietPlanResult(
    val bmr: Int,
    val tdee: Int,
    val targetCalories: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
    val meals: List<MealTarget>,
)

/**
 * 基礎代謝率 (BMR) 與飲食目標計算器。
 *
 * 採用臨床廣泛驗證的 Mifflin-St Jeor 公式：
 * - 男：10 × 體重(kg) + 6.25 × 身高(cm) - 5 × 年齡 + 5
 * - 女：10 × 體重(kg) + 6.25 × 身高(cm) - 5 × 年齡 - 161
 *
 * 依活動量 (TDEE) 與體態目標（減脂、維持、增肌）推導每日熱量與三大營養素配置。
 */
object BmrCalculator {

    fun calculate(
        gender: Gender,
        age: Int,
        heightCm: Float,
        weightKg: Float,
        activityLevel: ActivityLevel,
        goal: DietGoal,
    ): DietPlanResult {
        val safeAge = age.coerceIn(10, 120)
        val safeHeight = heightCm.coerceIn(50f, 250f)
        val safeWeight = weightKg.coerceIn(20f, 300f)

        // Mifflin-St Jeor
        val base = 10f * safeWeight + 6.25f * safeHeight - 5f * safeAge
        val bmr = (if (gender == Gender.MALE) base + 5f else base - 161f).toInt().coerceAtLeast(500)
        val tdee = (bmr * activityLevel.multiplier).toInt()

        // 目標熱量：減脂時採用安全赤字（-300 kcal），並防呆不低於 BMR
        val rawTarget = tdee + goal.calorieDelta
        val targetCalories = when (goal) {
            DietGoal.LOSE_FAT -> rawTarget.coerceAtLeast(bmr)
            else -> rawTarget
        }.coerceIn(800, 6000)

        // 蛋白質配置：減脂期給 2.0g/kg 保留瘦肉，維持 1.7g/kg，增肌 1.9g/kg
        val proteinMultiplier = when (goal) {
            DietGoal.LOSE_FAT -> 2.0f
            DietGoal.MAINTAIN -> 1.7f
            DietGoal.GAIN_MUSCLE -> 1.9f
        }
        val proteinG = (safeWeight * proteinMultiplier).toInt().coerceIn(30, 400)
        val proteinKcal = proteinG * 4

        // 脂肪配置：佔總熱量 25%（每克 9 kcal）
        val fatKcal = (targetCalories * 0.25f).toInt()
        val fatG = (fatKcal / 9).coerceIn(20, 200)

        // 碳水化合物配置：剩餘熱量全部補足（每克 4 kcal）
        val carbsKcal = (targetCalories - proteinKcal - (fatG * 9)).coerceAtLeast(0)
        val carbsG = (carbsKcal / 4).coerceIn(20, 800)

        // 四餐建議比例：早餐 25%、午餐 35%、晚餐 30%、點心 10%
        val meals = listOf(
            createMealTarget("早餐", 0.25f, targetCalories, proteinG, fatG, carbsG),
            createMealTarget("午餐", 0.35f, targetCalories, proteinG, fatG, carbsG),
            createMealTarget("晚餐", 0.30f, targetCalories, proteinG, fatG, carbsG),
            createMealTarget("點心", 0.10f, targetCalories, proteinG, fatG, carbsG),
        )

        return DietPlanResult(
            bmr = bmr,
            tdee = tdee,
            targetCalories = targetCalories,
            proteinG = proteinG,
            fatG = fatG,
            carbsG = carbsG,
            meals = meals,
        )
    }

    private fun createMealTarget(
        name: String,
        ratio: Float,
        totalKcal: Int,
        totalProtein: Int,
        totalFat: Int,
        totalCarbs: Int,
    ): MealTarget = MealTarget(
        mealName = name,
        calories = (totalKcal * ratio).toInt(),
        proteinG = (totalProtein * ratio).toInt(),
        fatG = (totalFat * ratio).toInt(),
        carbsG = (totalCarbs * ratio).toInt(),
    )
}
