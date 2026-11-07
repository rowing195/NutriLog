package com.watson.nutrilog

import com.watson.nutrilog.data.ActivitySource
import com.watson.nutrilog.data.WatchWearMode
import com.watson.nutrilog.data.WorkoutSessionItem
import com.watson.nutrilog.data.NO_SURPLUS_REASON
import com.watson.nutrilog.data.chooseActivity
import com.watson.nutrilog.data.dailyMovementAllowance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 活動消耗要拿哪一種資料的守門測試。
 *
 * 四件事：**只有運動時戴的人不能把全日活動大卡當一整天用**（那個數字
 * 只涵蓋戴著的那幾小時）；**總消耗扣基礎代謝那條退路不准回來**；
 * **步數只能算超出久坐額度的那一段**；以及**整天配戴時活動大卡和「場次＋步數」
 * 二選一取大的** —— 不是「有活動大卡就不算步數」，那會讓只記一部分活動大卡的
 * 手錶（三星）把場次以外的走路整段吃掉。
 *
 * 最後那件是最容易被「簡化」掉的：把整天步數直接換成大卡加進目標看起來很合理，
 * 但熱量目標的底是基礎代謝 × 久坐 1.2，而「久坐」本來就含日常走動 ——
 * 不扣就是把同一批熱量算兩次。
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

    /**
     * 只有運動時戴的人，全日活動大卡是不採用的 —— 那它也不能擋掉步數。
     *
     * 實際遇到：今天沒運動、走了 12,000 步，健康連線裡有一點活動大卡，結果步數被當成
     * 「已經有活動大卡了」整段丟掉，場次又是 0，兩邊都不算，目標只加 +0。
     */
    @Test
    fun `只有運動時戴時，全日活動大卡不能擋掉步數`() {
        val result = chooseActivity(
            activeKcal = 30.0,
            steps = 12_000L,
            wearMode = WatchWearMode.WORKOUT_ONLY,
            weightKg = 64.0,
            movementAllowance = 129.6,
        )
        // 12,000 × 64 × 0.0005 = 384，扣掉額度剩 254.4
        assertEquals(ActivitySource.STEPS, result.source)
        assertEquals(254.4, result.calories, 0.001)
        assertEquals(254.4, result.stepCalories, 0.001)
    }

    @Test
    fun `活動大卡是 0、又沒填體重，就什麼都算不出來`() {
        val result = chooseActivity(
            activeKcal = 0.0,
            steps = 10_000L,
            wearMode = WatchWearMode.ALL_DAY,
        )
        assertEquals(ActivitySource.NONE, result.source)
        assertEquals(0.0, result.calories, 0.001)
    }

    // --- 步數：只算超出久坐額度的那一段 ---

    /**
     * 額度是「久坐係數多給的那一段」再扣掉食物熱效應。
     * 扣熱效應不能省 —— 那一段跟走路無關，不扣會把門檻抬得太高、走再多也都是 0。
     */
    @Test
    fun `久坐額度要扣掉食物熱效應`() {
        // 基礎代謝 1620、目標 1944：1620 × 0.2 − 1944 × 0.1 = 324 − 194.4
        assertEquals(129.6, dailyMovementAllowance(1620.0, 1944), 0.1)
    }

    @Test
    fun `步數只算超出久坐額度的那一段`() {
        val result = chooseActivity(
            activeKcal = null,
            steps = 10_000L,
            wearMode = WatchWearMode.ALL_DAY,
            weightKg = 64.0,
            movementAllowance = 129.6,
        )
        // 10,000 × 64 × 0.0005 = 320，扣掉額度剩 190.4
        assertEquals(ActivitySource.STEPS, result.source)
        assertEquals(190.4, result.calories, 0.001)
        assertEquals(190.4, result.stepCalories, 0.001)
        assertEquals(10_000L, result.dailySteps)
    }

    /**
     * 走得不多的日子就是 0，**而且原因要說是「沒超出」不是「讀不到」**。
     * 兩者往完全不同的方向修：一個是去調權限，一個是多走兩步。
     */
    @Test
    fun `步數沒超出額度時是 0，原因要說沒超出`() {
        val result = chooseActivity(
            activeKcal = null,
            steps = 3_000L,
            wearMode = WatchWearMode.ALL_DAY,
            weightKg = 64.0,
            movementAllowance = 129.6,
        )
        assertEquals(ActivitySource.NONE, result.source)
        assertEquals(0.0, result.calories, 0.001)
        assertEquals(NO_SURPLUS_REASON, result.unavailableReason)
        assertEquals(3_000L, result.dailySteps)
    }

    /** 跑步那三十分鐘的步已經算在場次熱量裡了，不扣掉就是重複算。 */
    @Test
    fun `運動場次裡的步數要扣掉`() {
        val result = chooseActivity(
            activeKcal = null,
            steps = 10_000L,
            wearMode = WatchWearMode.ALL_DAY,
            workoutKcal = 250.0,
            workoutSessions = listOf(run30min),
            weightKg = 64.0,
            movementAllowance = 129.6,
        )
        // 10,000 − 4,000 = 6,000 步 -> 192，扣掉額度剩 62.4，再加上場次的 250
        assertEquals(6_000L, result.dailySteps)
        assertEquals(62.4, result.stepCalories, 0.001)
        assertEquals(312.4, result.calories, 0.001)
        assertEquals(ActivitySource.WORKOUT_SESSIONS, result.source)
    }

    // --- 整天配戴：活動大卡和「場次＋步數」二選一，取大的 ---

    /**
     * 真的整天記活動大卡的手錶，那個數字涵蓋走路、通常比較大 —— 那時步數不加上去。
     * 兩者估的是同一件事（一整天的走動），相加就是重複。
     */
    @Test
    fun `整天配戴時活動大卡比較大就用它，步數不加上去`() {
        val result = chooseActivity(
            activeKcal = 420.0,
            steps = 10_000L,
            wearMode = WatchWearMode.ALL_DAY,
            weightKg = 64.0,
            movementAllowance = 129.6,
        )
        // 步數那條是 190.4，比 420 小
        assertEquals(ActivitySource.ACTIVE_CALORIES, result.source)
        assertEquals(420.0, result.calories, 0.001)
        assertEquals(0.0, result.stepCalories, 0.001)
        assertEquals(10_000L, result.dailySteps)
    }

    @Test
    fun `整天配戴時步數那條比較大就用步數，不和活動大卡相加`() {
        val result = chooseActivity(
            activeKcal = 420.0,
            steps = 20_000L,
            wearMode = WatchWearMode.ALL_DAY,
            weightKg = 64.0,
            movementAllowance = 129.6,
        )
        // 20,000 × 64 × 0.0005 = 640，扣掉額度剩 510.4，比 420 大；相加的話會是 930.4
        assertEquals(ActivitySource.STEPS, result.source)
        assertEquals(510.4, result.calories, 0.001)
        assertEquals(510.4, result.stepCalories, 0.001)
    }

    /**
     * 實際遇到的情境（三星）：健康連線裡的活動大卡只涵蓋運動場次那幾段，而走超過十分鐘
     * 三星看起來會自動記一筆健走場次，所以幾乎每天都「有」活動大卡。舊版有活動大卡就把步數
     * 整段擋掉：這一天活動大卡 319、四段健走 122，場次以外的 13,239 步是 +0。
     */
    @Test
    fun `活動大卡只記了一部分時，場次以外的步數不能被擋掉`() {
        val walks = listOf(40.0, 18.0, 28.0, 36.0).map { kcal ->
            WorkoutSessionItem(
                title = "健走",
                exerciseType = 79,
                durationMinutes = 12,
                calories = kcal,
                steps = 1_000L,
            )
        }
        val result = chooseActivity(
            activeKcal = 319.0,
            steps = 17_239L,
            wearMode = WatchWearMode.ALL_DAY,
            workoutKcal = 122.0,
            workoutCount = 4,
            workoutSessions = walks,
            weightKg = 60.0,
            movementAllowance = 123.0,
        )
        // 17,239 − 4,000 = 13,239 步 -> 397.17，扣掉額度剩 274.17，加上場次 122 = 396.17 > 319
        assertEquals(13_239L, result.dailySteps)
        assertEquals(274.17, result.stepCalories, 0.001)
        assertEquals(396.17, result.calories, 0.001)
        assertEquals(122.0, result.workoutCalories, 0.001)
        assertEquals(ActivitySource.WORKOUT_SESSIONS, result.source)
    }
}
