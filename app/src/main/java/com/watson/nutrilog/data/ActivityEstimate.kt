package com.watson.nutrilog.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 一天的活動量是從哪裡來的。
 *
 * 「運動消耗 0」有好幾種完全不同的原因（沒授權、三星沒寫這個型別、真的沒動），
 * 而它們在畫面上長得一模一樣 —— 使用者只會看到 0，沒辦法知道要去修哪裡。
 * 把來源分開記下來，才有辦法在同步時講清楚那個 0 是什麼意思。
 */
enum class ActivitySource {
    /** 三星／手錶直接記的活動大卡，最準，優先用 */
    ACTIVE_CALORIES,

    /** 體能訓練專項運動（重訓、跑步、健走等場次紀錄） */
    WORKOUT_SESSIONS,

    /** 只拿得到總消耗時，扣掉基礎代謝推回活動量 */
    TOTAL_MINUS_BMR,

    /** 連總消耗都沒有，只好用步數換算 */
    STEPS,

    /** 三種都沒有 */
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
    val nonWorkoutSteps: Long = 0L,
    val nonWorkoutCalories: Double = 0.0,
)

/**
 * 每走一步、每公斤體重約消耗的大卡。
 *
 * 業界常用的粗估值：68 kg 的人走一萬步約 340 kcal，和三星自己的估算相當接近。
 * 這是換算不是實測，所以只在拿不到任何熱量紀錄時才會用到。
 */
const val KCAL_PER_STEP_PER_KG = 0.0005

fun stepsToCalories(steps: Long, weightKg: Double): Double =
    if (steps <= 0L || weightKg <= 0.0) 0.0 else steps * weightKg * KCAL_PER_STEP_PER_KG

/**
 * 這一天到「現在」為止該扣掉多少基礎代謝。
 *
 * 總消耗是一路累加上去的，當天還沒過完時它只累積到現在，
 * 所以基礎代謝也只能扣到現在 —— 直接扣整天的話，早上看會是負的。
 */
fun bmrForElapsedPortion(
    bmrPerDay: Double,
    date: LocalDate,
    now: Instant,
    zone: ZoneId,
): Double {
    val today = now.atZone(zone).toLocalDate()
    return when {
        date.isAfter(today) -> 0.0
        date.isBefore(today) -> bmrPerDay
        else -> {
            val elapsedSeconds = now.atZone(zone).toLocalTime().toSecondOfDay().toDouble()
            bmrPerDay * (elapsedSeconds / SECONDS_PER_DAY)
        }
    }
}

private const val SECONDS_PER_DAY = 86_400.0

/** 三種來源都沒有東西時給使用者看的說明。 */
const val NO_ACTIVITY_DATA_REASON = "健康連線裡沒有這一天的活動或步數資料"

/**
 * 依優先序從各種來源挑出這一天的活動消耗。
 *
 * 純函式、不碰 Android API，所以退路對不對用單元測試就看得出來，
 * 不必真的接一支有資料的手錶。
 */
fun chooseActivity(
    activeKcal: Double?,
    totalKcal: Double?,
    steps: Long,
    bmrForElapsed: Double,
    weightKg: Double,
    workoutKcal: Double? = null,
    hasWorkoutSessions: Boolean = false,
    workoutCount: Int = 0,
    workoutSummary: String = "",
    workoutSessions: List<WorkoutSessionItem> = emptyList(),
    workoutSteps: Long = 0L,
): DailyActivity {
    val totalWorkoutKcal = (workoutKcal ?: 0.0).coerceAtLeast(0.0)
    // 智能去重疊加：扣除運動時段產生的步數，得到純日常非運動步數
    val nonWorkoutSteps = (steps - workoutSteps).coerceAtLeast(0L)
    val nonWorkoutStepKcal = stepsToCalories(nonWorkoutSteps, weightKg)

    // 1. 若手錶/系統實測並寫入了全天活動大卡 ActiveCaloriesBurnedRecord
    if (activeKcal != null && activeKcal > 0.0) {
        val stacked = totalWorkoutKcal + nonWorkoutStepKcal
        val effective = if (totalWorkoutKcal > 0.0) maxOf(activeKcal, stacked) else activeKcal
        return DailyActivity(
            calories = effective,
            steps = steps,
            source = if (totalWorkoutKcal > 0.0) ActivitySource.WORKOUT_SESSIONS else ActivitySource.ACTIVE_CALORIES,
            workoutCalories = totalWorkoutKcal,
            workoutCount = workoutCount,
            workoutSummary = workoutSummary,
            workoutSessions = workoutSessions,
            nonWorkoutSteps = nonWorkoutSteps,
            nonWorkoutCalories = nonWorkoutStepKcal,
        )
    }

    // 2. 智能去重疊加：有專項體能訓練時，專項手錶實測消耗 ＋ 扣除運動時段後的日常非運動步數消耗
    if (totalWorkoutKcal > 0.0) {
        val stacked = totalWorkoutKcal + nonWorkoutStepKcal
        val derived = if (totalKcal != null && (steps > 0L || hasWorkoutSessions)) {
            (totalKcal - bmrForElapsed).coerceAtLeast(0.0)
        } else 0.0
        val effective = maxOf(stacked, derived)
        return DailyActivity(
            calories = effective,
            steps = steps,
            source = ActivitySource.WORKOUT_SESSIONS,
            workoutCalories = totalWorkoutKcal,
            workoutCount = workoutCount,
            workoutSummary = workoutSummary,
            workoutSessions = workoutSessions,
            nonWorkoutSteps = nonWorkoutSteps,
            nonWorkoutCalories = nonWorkoutStepKcal,
        )
    }

    // 3. 無專項運動時，依總消耗扣除基礎代謝（退路）
    // 關鍵防呆保護：剛過午夜時，三星常預先寫入整天 24 小時的基礎代謝（~1600 kcal）。
    // 若此時步數為 0 且沒有任何專項運動紀錄，使用者根本沒活動，絕不可能憑空產生上千大卡的活動熱量！
    // 只有在真的有走動（steps > 0）或有運動場次時，才允許依總消耗推算。
    if (totalKcal != null && (steps > 0L || hasWorkoutSessions)) {
        val derived = totalKcal - bmrForElapsed
        if (derived > 0.0) {
            // 防呆限額：若步數非常少（< 200 步）且無運動專項，推算值不應因 BMR 公式誤差而大幅溢出
            val plausibleKcal = if (steps < 200L && !hasWorkoutSessions) {
                derived.coerceAtMost(stepsToCalories(steps, weightKg) * 2.5).coerceAtLeast(0.0)
            } else {
                derived
            }
            if (plausibleKcal > 0.0) {
                return DailyActivity(
                    calories = plausibleKcal,
                    steps = steps,
                    source = ActivitySource.TOTAL_MINUS_BMR,
                    workoutCalories = 0.0,
                    workoutCount = workoutCount,
                    workoutSummary = workoutSummary,
                    workoutSessions = workoutSessions,
                    nonWorkoutSteps = steps,
                    nonWorkoutCalories = stepsToCalories(steps, weightKg),
                )
            }
        }
    }

    // 4. 步數換算（最後退路）
    val fromSteps = stepsToCalories(steps, weightKg)
    if (fromSteps > 0.0) {
        return DailyActivity(
            calories = fromSteps,
            steps = steps,
            source = ActivitySource.STEPS,
            workoutCalories = 0.0,
            workoutCount = workoutCount,
            workoutSummary = workoutSummary,
            workoutSessions = workoutSessions,
            nonWorkoutSteps = steps,
            nonWorkoutCalories = fromSteps,
        )
    }

    return DailyActivity(
        calories = 0.0,
        steps = steps,
        source = ActivitySource.NONE,
        unavailableReason = NO_ACTIVITY_DATA_REASON,
        workoutCalories = 0.0,
        workoutCount = workoutCount,
        workoutSummary = workoutSummary,
        workoutSessions = workoutSessions,
        nonWorkoutSteps = 0L,
        nonWorkoutCalories = 0.0,
    )
}
