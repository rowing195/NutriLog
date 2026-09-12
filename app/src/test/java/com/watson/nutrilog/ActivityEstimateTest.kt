package com.watson.nutrilog

import com.watson.nutrilog.data.ActivitySource
import com.watson.nutrilog.data.WatchWearMode
import com.watson.nutrilog.data.WorkoutSessionItem
import com.watson.nutrilog.data.chooseActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 活動消耗要拿哪一種資料的守門測試。
 *
 * 這裡守的是兩件事：**只有運動時戴的人不能把全日活動大卡當一整天用**
 * （那個數字只涵蓋戴著的那幾小時），以及**讀不到就是讀不到**，
 * 不准再用「總消耗扣基礎代謝」或步數換算生一個猜的數字出來。
 */
class ActivityEstimateTest {

    private val run30min = WorkoutSessionItem(
        title = "跑步機（Treadmill）",
        exerciseType = 56,
        durationMinutes = 30,
        calories = 250.0,
        timeRangeText = "19:30 ~ 20:00",
        steps = 4000L,
    )

    // --- 整天配戴：活動大卡就是一整天的活動量 ---

    @Test
    fun `整天配戴時直接採用全日活動大卡，步數照樣帶回來`() {
        val result = chooseActivity(
            activeKcal = 420.0,
            steps = 9000L,
            wearMode = WatchWearMode.ALL_DAY,
        )
        assertEquals(ActivitySource.ACTIVE_CALORIES, result.source)
        assertEquals(420.0, result.calories, 0.001)
        assertEquals(9000L, result.steps)
        assertNull(result.unavailableReason)
    }

    @Test
    fun `整天配戴時運動場次比全日活動大卡多，就以場次為準`() {
        // 有些手錶只在運動時段寫活動大卡，全日那筆反而偏小
        val result = chooseActivity(
            activeKcal = 120.0,
            steps = 6000L,
            wearMode = WatchWearMode.ALL_DAY,
            workoutKcal = 250.0,
            workoutCount = 1,
            workoutSessions = listOf(run30min),
        )
        assertEquals(ActivitySource.WORKOUT_SESSIONS, result.source)
        assertEquals(250.0, result.calories, 0.001)
        assertEquals(250.0, result.workoutCalories, 0.001)
    }

    @Test
    fun `整天配戴但兩種都沒有時是 0，而且要說得出原因`() {
        val result = chooseActivity(
            activeKcal = null,
            steps = 3000L,
            wearMode = WatchWearMode.ALL_DAY,
        )
        assertEquals(ActivitySource.NONE, result.source)
        assertEquals(0.0, result.calories, 0.001)
        assertEquals(3000L, result.steps)
        assertNotNull(result.unavailableReason)
    }

    // --- 只有運動時戴：全日活動大卡不算數 ---

    /**
     * 實際遇到的情境：手錶只戴了兩小時，健康連線裡的全日活動大卡是 153。
     * 那 153 只涵蓋戴著的那幾小時，日常走動的部分本來就歸活動係數管，
     * 拿它當一整天的活動量會讓目標憑空多出一段。
     */
    @Test
    fun `只有運動時戴時，沒有運動場次就是沒有活動消耗`() {
        val result = chooseActivity(
            activeKcal = 153.0,
            steps = 4000L,
            wearMode = WatchWearMode.WORKOUT_ONLY,
        )
        assertEquals(ActivitySource.NONE, result.source)
        assertEquals(0.0, result.calories, 0.001)
        assertNotNull(result.unavailableReason)
    }

    @Test
    fun `只有運動時戴時只算場次，全日活動大卡比它大也不採用`() {
        val result = chooseActivity(
            activeKcal = 400.0,
            steps = 12000L,
            wearMode = WatchWearMode.WORKOUT_ONLY,
            workoutKcal = 250.0,
            workoutCount = 1,
            workoutSummary = "跑步機（Treadmill） 30分鐘 (250 kcal)",
            workoutSessions = listOf(run30min),
        )
        assertEquals(ActivitySource.WORKOUT_SESSIONS, result.source)
        assertEquals(250.0, result.calories, 0.001)
        assertEquals(1, result.workoutCount)
        assertEquals("跑步機（Treadmill） 30分鐘 (250 kcal)", result.workoutSummary)
        assertEquals(1, result.workoutSessions.size)
    }

    @Test
    fun `活動大卡是 0 不算有資料`() {
        val result = chooseActivity(
            activeKcal = 0.0,
            steps = 10_000L,
            wearMode = WatchWearMode.ALL_DAY,
        )
        assertEquals(ActivitySource.NONE, result.source)
        assertEquals(0.0, result.calories, 0.001)
    }

    /** 步數不再換算成大卡，但還是要一路帶回去存進每日健康快取。 */
    @Test
    fun `步數不換算成大卡，只是照原樣帶回來`() {
        val result = chooseActivity(
            activeKcal = null,
            steps = 10_000L,
            wearMode = WatchWearMode.ALL_DAY,
        )
        assertEquals(0.0, result.calories, 0.001)
        assertEquals(10_000L, result.steps)
    }
}
