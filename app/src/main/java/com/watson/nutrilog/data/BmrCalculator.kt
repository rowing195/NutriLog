package com.watson.nutrilog.data

import kotlin.math.roundToInt

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
    /** 蛋白質是照每公斤幾克算的，畫面要講出來 —— 只給一個總克數看不出高不高。 */
    val proteinPerKg: Float,
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
        /**
         * 運動消耗是不是改由手錶量（＝設定裡的「讀取運動消耗」開著）。
         * 開著時熱量的底退到久坐基準，運動另外回補。
         */
        watchSuppliesActivity: Boolean = false,
    ): DietPlanResult {
        val safeAge = age.coerceIn(10, 120)
        val safeHeight = heightCm.coerceIn(50f, 250f)
        val safeWeight = weightKg.coerceIn(20f, 300f)

        // Mifflin-St Jeor
        val base = 10f * safeWeight + 6.25f * safeHeight - 5f * safeAge
        val bmr = (if (gender == Gender.MALE) base + 5f else base - 161f).toInt().coerceAtLeast(500)

        // **交給手錶量的時候，熱量的底退到久坐係數。**
        //
        // 活動係數的定義本身就含運動 —— UI 上「輕度」寫的是「每週運動 1–3 天」——
        // 所以「係數目標 ＋ 當天的運動消耗」是把同一批熱量算兩次。實測：64 kg、159 cm
        // 的人輕度係數是 2087，再加一趟 45 分鐘的跑步（約 408）就變成 2495，
        // 而合理的估算是久坐 1821 ＋ 運動，回補一半約 2025。
        //
        // 沒在讀手錶的人相反：係數是他唯一的活動量來源，要照他填的算。
        //
        // **蛋白質兩種情況都照 [ActivityLevel.proteinPerKg] 走**：它看的是這個人活動量
        // 多大，不是熱量從哪裡來。
        val calorieMultiplier =
            if (watchSuppliesActivity) ActivityLevel.SEDENTARY.multiplier else activityLevel.multiplier
        val tdee = (bmr * calorieMultiplier).toInt()

        // 目標熱量：減脂時採用安全赤字（-300 kcal），並防呆不低於 BMR
        val rawTarget = tdee + goal.calorieDelta
        val targetCalories = when (goal) {
            DietGoal.LOSE_FAT -> rawTarget.coerceAtLeast(bmr)
            else -> rawTarget
        }.coerceIn(800, 6000)

        // 蛋白質**跟著活動量走**（見 [ActivityLevel.proteinPerKg]），目標再加一點。
        //
        // 原本是只看目標的固定倍率（維持 1.7 g/kg）—— 那是 ISSN 給「有在訓練的人」
        // 1.4–2.0 那一區的上緣，套在久坐、只想維持體重的人身上等於逼他每天喝高蛋白。
        // 而且久坐與非常高活動量會算出一模一樣的數字，活動量那一欄形同白填。
        val proteinPerKg = (activityLevel.proteinPerKg + goal.proteinBonusPerKg)
            .coerceAtMost(PROTEIN_MAX_PER_KG)
        val proteinG = (safeWeight * proteinPerKg).roundToInt().coerceIn(30, 400)
        val proteinKcal = proteinG * 4

        // **碳水固定佔 [CARBS_SHARE]，脂肪吃剩下的差額。**
        //
        // 反過來（脂肪固定 25%、碳水吃差額）的問題是：蛋白質一降，省下來的熱量
        // 一克不剩全部跑到碳水，比例被推到建議範圍 50–65% 的上緣。碳水是這三者裡
        // 份量最大的一項，讓它當定值、由脂肪吸收變動，三者的比例才穩得住。
        val idealCarbsKcal = (targetCalories * CARBS_SHARE).toInt()
        // 但蛋白質高的時候（減脂＋高活動量）固定比例會把脂肪擠到過低，
        // 而脂肪太少會影響荷爾蒙與脂溶性維生素的吸收 —— 那時候換碳水讓位。
        val maxCarbsKcal = (targetCalories - proteinKcal - (targetCalories * FAT_MIN_SHARE).toInt())
            .coerceAtLeast(0)
        val carbsG = (idealCarbsKcal.coerceAtMost(maxCarbsKcal) / 4).coerceIn(20, 800)

        val fatKcal = (targetCalories - proteinKcal - (carbsG * 4)).coerceAtLeast(0)
        val fatG = (fatKcal / 9).coerceIn(20, 200)

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
            proteinPerKg = proteinPerKg,
            fatG = fatG,
            carbsG = carbsG,
            meals = meals,
        )
    }

    /**
     * 蛋白質的上限。運動營養的建議區間到 2.0 g/kg 為止，再往上沒有證據支持更好，
     * 對一般人也只是更難吃到。
     */
    private const val PROTEIN_MAX_PER_KG = 2.0f

    /** 碳水佔總熱量的比例，落在一般建議的 50–65% 中間。 */
    private const val CARBS_SHARE = 0.55f

    /** 脂肪的下限，低於這裡就讓碳水退一步。 */
    private const val FAT_MIN_SHARE = 0.20f

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
