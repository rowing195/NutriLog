package com.watson.nutrilog.data

import java.time.LocalDate

/**
 * 一天的活動量是從哪裡來的。
 *
 * 「運動消耗 0」有好幾種完全不同的原因（沒授權、三星沒寫這個型別、真的沒動），
 * 而它們在畫面上長得一模一樣 —— 使用者只會看到 0，沒辦法知道要去修哪裡。
 * 把來源分開記下來，才有辦法在同步時講清楚那個 0 是什麼意思。
 */
enum class ActivitySource {
    /** 三星／手錶直接記的活動大卡。整天配戴時這就是一整天的活動量 */
    ACTIVE_CALORIES,

    /** 體能訓練專項運動（重訓、跑步、健走等場次紀錄） */
    WORKOUT_SESSIONS,

    /** 兩種都沒有 */
    NONE,
}

/** 單筆體能訓練場次（例如：跑步機、重訓、戶外跑步）之詳細資料 */
data class WorkoutSessionItem(
    val title: String,
    val exerciseType: Int,
    val durationMinutes: Long,
    val calories: Double = 0.0,
    val timeRangeText: String = "",
    val steps: Long = 0L,
)

/** 一天的活動結果。`unavailableReason` 只有在 [ActivitySource.NONE] 時才有值。 */
data class DailyActivity(
    val calories: Double,
    val steps: Long,
    val source: ActivitySource,
    val unavailableReason: String? = null,
    val workoutCalories: Double = 0.0,
    val workoutCount: Int = 0,
    val workoutSummary: String = "",
    val workoutSessions: List<WorkoutSessionItem> = emptyList(),
)

/**
 * 某一個型別在健康連線裡**實際有幾筆紀錄、由哪些 app 寫的**。
 *
 * 合計值看不出這兩件事，而它們各自對應一種真的發生過的狀況：
 *
 * - **合計有數字、紀錄卻是 0 筆** —— 那個值不是任何 app 寫進來的。實測三星完全不寫
 *   熱量，健康連線的合計卻讀得到 1,426，光看合計只會以為是三星寫的。
 * - **同一個型別有兩個以上的來源** —— 合計是把兩份疊起來的。健康連線只對
 *   「應用程式優先順序」清單裡的來源去重，而手機自己那份步數預設不在清單裡，
 *   於是同一批步伐被算兩次（實測 1,998 ＋ 1,790 = 3,788）。
 */
data class RecordOrigins(val count: Int, val packages: List<String>)

/**
 * 健康連線裡原始有什麼，給設定頁的診斷區用。
 *
 * 熱量與步數是 `null` 代表**沒有這種資料**，0 代表**有資料但那天沒動** —— 這兩件事
 * 在畫面上長得一樣，卻要往完全不同的方向修，所以型別上就要分開。
 */
data class HealthDiagnostics(
    val date: LocalDate,
    val supported: Boolean,
    val grantedActiveCalories: Boolean,
    val grantedTotalCalories: Boolean,
    val grantedSteps: Boolean,
    val grantedExercise: Boolean,
    val activeKcal: Double?,
    val totalKcal: Double?,
    val steps: Long?,
    /** 正式路徑（[chooseActivity]）挑出來的結果，不是另外算的一份。 */
    val chosen: DailyActivity,
    /**
     * 三個型別各自的原始紀錄狀況，見 [RecordOrigins]。
     * `null` 是「沒授權或讀取失敗」，和「0 筆」不是同一件事。
     */
    val activeOrigins: RecordOrigins? = null,
    val totalOrigins: RecordOrigins? = null,
    val stepsOrigins: RecordOrigins? = null,
)

/** 兩種來源都沒有東西時給使用者看的說明。 */
const val NO_ACTIVITY_DATA_REASON = "健康連線裡沒有這一天的活動資料"

/**
 * 依配戴方式挑出這一天要加進目標的活動消耗。
 *
 * **只認「活動消耗」與「運動場次」這兩種資料，因為它們本身就已經是活動量了。**
 * 曾經還有兩條退路：總消耗扣掉基礎代謝、步數換算。兩條都拿掉了：
 *
 * - **總消耗扣基礎代謝**是拿兩個一千五百多的大數字相減去換一個一百多的小數字，
 *   兩邊的基礎代謝只要差幾個百分點，誤差就和答案同一個量級。實測手錶記了 153，
 *   這條路算出來是 39。而且它得引進「今天過了幾成」才扣得對，時間一進來，
 *   同一天在不同時刻讀會得到不同的歷史值。
 * - **步數換算**是固定係數乘出來的猜測值，和手錶實測混在同一個數字裡，
 *   使用者分不出哪天是量的、哪天是猜的。
 *
 * 讀不到就老實說讀不到（[ActivitySource.NONE]），不要給一個猜的數字。
 *
 * 純函式、不碰 Android API，所以挑選邏輯對不對用單元測試就看得出來，
 * 不必真的接一支有資料的手錶。
 */
fun chooseActivity(
    activeKcal: Double?,
    steps: Long,
    wearMode: WatchWearMode,
    workoutKcal: Double? = null,
    workoutCount: Int = 0,
    workoutSummary: String = "",
    workoutSessions: List<WorkoutSessionItem> = emptyList(),
): DailyActivity {
    val workout = (workoutKcal ?: 0.0).coerceAtLeast(0.0)
    val active = (activeKcal ?: 0.0).coerceAtLeast(0.0)

    // 整天配戴：活動消耗已經含日常走動與運動，直接用。運動場次比它還多的話以場次為準
    // —— 有些手錶只在運動時段寫活動大卡，全日那筆反而偏小。
    val calories = when (wearMode) {
        WatchWearMode.ALL_DAY -> maxOf(active, workout)
        // 只有運動時戴：日常走動歸活動係數管，手錶只負責運動場次。
        // 這時候的全日活動消耗只涵蓋戴著的那幾小時，拿來當一整天用會低估。
        WatchWearMode.WORKOUT_ONLY -> workout
    }

    if (calories <= 0.0) {
        return DailyActivity(
            calories = 0.0,
            steps = steps,
            source = ActivitySource.NONE,
            unavailableReason = NO_ACTIVITY_DATA_REASON,
            workoutCount = workoutCount,
            workoutSummary = workoutSummary,
            workoutSessions = workoutSessions,
        )
    }

    return DailyActivity(
        calories = calories,
        steps = steps,
        // 只有運動時戴的日子，數字一定是場次來的；整天配戴時看誰比較大就是誰
        source = when {
            wearMode == WatchWearMode.WORKOUT_ONLY -> ActivitySource.WORKOUT_SESSIONS
            calories > active -> ActivitySource.WORKOUT_SESSIONS
            else -> ActivitySource.ACTIVE_CALORIES
        },
        workoutCalories = workout,
        workoutCount = workoutCount,
        workoutSummary = workoutSummary,
        workoutSessions = workoutSessions,
    )
}
