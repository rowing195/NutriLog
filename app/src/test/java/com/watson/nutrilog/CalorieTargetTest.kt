package com.watson.nutrilog

import com.watson.nutrilog.ui.effectiveCalorieTarget
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「今天可以吃多少」那條式子。
 *
 * 守的是兩件事：**運動熱量不是全額回補**（手錶估的偏高，全額吃回去等於把高估的
 * 那一段也吃掉），以及**沒動的日子就是目標本身**，不會憑空多出一段。
 */
class CalorieTargetTest {

    /** 64 kg、159 cm、24 歲、輕度活動的人：久坐基準 1821、活動係數目標 2087。 */
    private val sedentaryBase = 1821

    @Test
    fun `沒有運動的日子就是目標本身`() {
        assertEquals(
            sedentaryBase,
            effectiveCalorieTarget(sedentaryBase, 0.0, readExercise = true, eatBackPercent = 50),
        )
    }

    @Test
    fun `回補一半`() {
        // 45 分鐘的跑步約 408 大卡 -> 只加 204
        assertEquals(
            2025,
            effectiveCalorieTarget(sedentaryBase, 408.0, readExercise = true, eatBackPercent = 50),
        )
    }

    @Test
    fun `回補比例可以調到全額`() {
        assertEquals(
            2229,
            effectiveCalorieTarget(sedentaryBase, 408.0, readExercise = true, eatBackPercent = 100),
        )
        assertEquals(
            1923,
            effectiveCalorieTarget(sedentaryBase, 408.0, readExercise = true, eatBackPercent = 25),
        )
    }

    /** 關掉讀取運動消耗時，目標是使用者自己填的那個（活動係數已含運動），不加任何東西。 */
    @Test
    fun `沒在讀運動消耗時原樣返回`() {
        assertEquals(
            2087,
            effectiveCalorieTarget(2087, 408.0, readExercise = false, eatBackPercent = 50),
        )
    }

    /** 目標 0 的意思是關掉額度，不能因為有運動就長出一個目標。 */
    @Test
    fun `目標 0 不會因為運動變成有目標`() {
        assertEquals(0, effectiveCalorieTarget(0, 408.0, readExercise = true, eatBackPercent = 50))
    }
}
