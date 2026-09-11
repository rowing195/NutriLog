package com.watson.nutrilog

import com.watson.nutrilog.data.ActivitySource
import com.watson.nutrilog.data.bmrForElapsedPortion
import com.watson.nutrilog.data.chooseActivity
import com.watson.nutrilog.data.stepsToCalories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 活動熱量三段退路的守門測試。
 *
 * 這裡守的是「運動消耗一直是 0」那個 bug：三星只寫步數、沒寫活動大卡時，
 * 不能整條路悄悄回傳 0。
 */
class ActivityEstimateTest {

    private val zone = ZoneId.of("Asia/Taipei")

    // --- 優先序 ---

    @Test
    fun `有活動大卡時直接採用，步數照樣帶回來`() {
        val result = chooseActivity(
            activeKcal = 420.0,
            totalKcal = 2200.0,
            steps = 9000L,
            bmrForElapsed = 1500.0,
            weightKg = 68.0,
        )
        assertEquals(ActivitySource.ACTIVE_CALORIES, result.source)
        assertEquals(420.0, result.calories, 0.001)
        assertEquals(9000L, result.steps)
        assertNull(result.unavailableReason)
    }

    @Test
    fun `沒有活動大卡時用總消耗扣掉基礎代謝`() {
        val result = chooseActivity(
            activeKcal = null,
            totalKcal = 2200.0,
            steps = 9000L,
            bmrForElapsed = 1500.0,
            weightKg = 68.0,
        )
        assertEquals(ActivitySource.TOTAL_MINUS_BMR, result.source)
        assertEquals(700.0, result.calories, 0.001)
    }

    @Test
    fun `活動大卡是 0 不算有資料，要往下退到步數`() {
        val result = chooseActivity(
            activeKcal = 0.0,
            totalKcal = null,
            steps = 10_000L,
            bmrForElapsed = 1500.0,
            weightKg = 68.0,
        )
        assertEquals(ActivitySource.STEPS, result.source)
        assertEquals(340.0, result.calories, 0.001)
    }

    @Test
    fun `總消耗扣完基礎代謝不是正數時退到步數`() {
        val result = chooseActivity(
            activeKcal = null,
            totalKcal = 1200.0,
            steps = 4000L,
            bmrForElapsed = 1500.0,
            weightKg = 68.0,
        )
        assertEquals(ActivitySource.STEPS, result.source)
        assertEquals(136.0, result.calories, 0.001)
    }

    @Test
    fun `有專項體能訓練時，智能疊加專項消耗與日常非運動步數`() {
        val result = chooseActivity(
            activeKcal = null,
            totalKcal = 1000.0,
            steps = 2000L,
            bmrForElapsed = 800.0,
            weightKg = 68.0,
            workoutKcal = 350.0,
            hasWorkoutSessions = true,
            workoutCount = 1,
            workoutSummary = "重量訓練 45分鐘 (350 kcal)",
            workoutSteps = 0L,
        )
        assertEquals(ActivitySource.WORKOUT_SESSIONS, result.source)
        // 專項 350 kcal + 日常步數 2000 * 68 * 0.0005 = 68 kcal -> 合計 418 kcal
        assertEquals(418.0, result.calories, 0.001)
        assertEquals(350.0, result.workoutCalories, 0.001)
        assertEquals(2000L, result.nonWorkoutSteps)
        assertEquals(68.0, result.nonWorkoutCalories, 0.001)
        assertEquals(1, result.workoutCount)
        assertEquals("重量訓練 45分鐘 (350 kcal)", result.workoutSummary)
    }

    @Test
    fun `專項運動時段步數精準去重，不雙重重複計算`() {
        val sessionList = listOf(
            com.watson.nutrilog.data.WorkoutSessionItem(
                title = "跑步機（Treadmill）",
                exerciseType = 56, // EXERCISE_TYPE_RUNNING_TREADMILL
                durationMinutes = 30,
                calories = 250.0,
                timeRangeText = "19:30 ~ 20:00",
                steps = 4000L,
            )
        )
        val result = chooseActivity(
            activeKcal = null,
            totalKcal = 2000.0,
            steps = 15000L,
            bmrForElapsed = 1600.0,
            weightKg = 75.0,
            workoutKcal = 250.0,
            hasWorkoutSessions = true,
            workoutCount = 1,
            workoutSummary = "跑步機（Treadmill） 30分鐘 (250 kcal)",
            workoutSessions = sessionList,
            workoutSteps = 4000L,
        )
        assertEquals(ActivitySource.WORKOUT_SESSIONS, result.source)
        // 扣除跑步機步數後，日常步數 = 15000 - 4000 = 11000 步
        assertEquals(11000L, result.nonWorkoutSteps)
        // 日常步數消耗 = 11000 * 75 * 0.0005 = 412.5 kcal
        assertEquals(412.5, result.nonWorkoutCalories, 0.001)
        // 智能疊加總消耗 = 專項 250 + 日常 412.5 = 662.5 kcal
        assertEquals(662.5, result.calories, 0.001)
        assertEquals(250.0, result.workoutCalories, 0.001)
        assertEquals(1, result.workoutSessions.size)
        assertEquals("跑步機（Treadmill）", result.workoutSessions[0].title)
        assertEquals(250.0, result.workoutSessions[0].calories, 0.001)
        assertEquals(4000L, result.workoutSessions[0].steps)
    }

    @Test
    fun `專項運動佔滿全天步數時，僅計專項大卡不重疊`() {
        val sessionList = listOf(
            com.watson.nutrilog.data.WorkoutSessionItem(
                title = "跑步機（Treadmill）",
                exerciseType = 56,
                durationMinutes = 45,
                calories = 380.0,
                timeRangeText = "19:00 ~ 19:45",
                steps = 5000L,
            )
        )
        val result = chooseActivity(
            activeKcal = null,
            totalKcal = 1800.0,
            steps = 5000L,
            bmrForElapsed = 1600.0,
            weightKg = 75.0,
            workoutKcal = 380.0,
            hasWorkoutSessions = true,
            workoutCount = 1,
            workoutSummary = "跑步機（Treadmill） 45分鐘 (380 kcal)",
            workoutSessions = sessionList,
            workoutSteps = 5000L,
        )
        assertEquals(ActivitySource.WORKOUT_SESSIONS, result.source)
        assertEquals(0L, result.nonWorkoutSteps)
        assertEquals(0.0, result.nonWorkoutCalories, 0.001)
        assertEquals(380.0, result.calories, 0.001)
        assertEquals(380.0, result.workoutCalories, 0.001)
        assertEquals(1, result.workoutSessions.size)
    }

    @Test
    fun `剛過午夜0步且無專項運動時，不因全天總消耗預估而誤報1576大卡`() {
        // 剛過午夜15分鐘，三星健康預先填入全天1600大卡，已過時間BMR僅16大卡
        val result = chooseActivity(
            activeKcal = null,
            totalKcal = 1600.0,
            steps = 0L,
            bmrForElapsed = 16.0,
            weightKg = 75.0,
            workoutKcal = null,
            hasWorkoutSessions = false,
        )
        // 0步且0運動場次，活動大卡必須判定為 0
        assertEquals(ActivitySource.NONE, result.source)
        assertEquals(0.0, result.calories, 0.001)
        assertEquals(0L, result.steps)
    }

    @Test
    fun `三種都沒有時是 0，而且要說得出原因`() {
        val result = chooseActivity(
            activeKcal = null,
            totalKcal = null,
            steps = 0L,
            bmrForElapsed = 1500.0,
            weightKg = 68.0,
        )
        assertEquals(ActivitySource.NONE, result.source)
        assertEquals(0.0, result.calories, 0.001)
        assertNotNull(result.unavailableReason)
    }

    // --- 步數換算 ---

    @Test
    fun `一萬步乘上體重的換算值`() {
        assertEquals(340.0, stepsToCalories(10_000L, 68.0), 0.001)
        assertEquals(500.0, stepsToCalories(10_000L, 100.0), 0.001)
    }

    @Test
    fun `沒有體重時不硬掰一個數字出來`() {
        assertEquals(0.0, stepsToCalories(10_000L, 0.0), 0.001)
        assertEquals(0.0, stepsToCalories(0L, 68.0), 0.001)
    }

    // --- 基礎代謝按已過時間比例 ---

    @Test
    fun `過去的日子扣整天的基礎代謝`() {
        val now = ZonedDateTime.of(2026, 9, 9, 12, 0, 0, 0, zone).toInstant()
        assertEquals(1500.0, bmrForElapsedPortion(1500.0, LocalDate.of(2026, 9, 8), now, zone), 0.001)
    }

    @Test
    fun `今天只扣到現在為止的比例`() {
        val now = ZonedDateTime.of(2026, 9, 9, 12, 0, 0, 0, zone).toInstant()
        assertEquals(750.0, bmrForElapsedPortion(1500.0, LocalDate.of(2026, 9, 9), now, zone), 0.001)
    }

    @Test
    fun `未來的日子不扣`() {
        val now = ZonedDateTime.of(2026, 9, 9, 12, 0, 0, 0, zone).toInstant()
        assertEquals(0.0, bmrForElapsedPortion(1500.0, LocalDate.of(2026, 9, 10), now, zone), 0.001)
    }
}
