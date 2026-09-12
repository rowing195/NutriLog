package com.watson.nutrilog.data

import java.time.LocalDate
import java.time.LocalTime

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

    /**
     * 步數換算出來的、**超過久坐基準已經含的那一段**。
     *
     * 和 coworker 那版的 `STEPS` 同名但不同意思：那邊是把整天步數直接換成大卡，
     * 這裡只算超出 [dailyMovementAllowance] 的那一段 —— 沒有這個扣除就是把同一批
     * 熱量算兩次（活動係數本來就含日常走動）。
     */
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
    /** 扣掉運動場次後的日常步數。明細面板要講得出「走了多少」。 */
    val dailySteps: Long = 0L,
    /** 步數換算**超出久坐額度**的那一段，已經加進 [calories] 裡。 */
    val stepCalories: Double = 0.0,
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
    val grantedBasal: Boolean = false,
    val activeKcal: Double?,
    val totalKcal: Double?,
    /**
     * 同一個「總消耗」，但只查到**讀取的那一刻**為止。
     *
     * 和整天那個值一起看才問得出「它到底會不會跟著時間累加」：兩個一樣，代表它是
     * 一個不管你問哪一段都回同一個數的整日估計值，**那就沒辦法拿來當「今天到現在
     * 動了多少」**；到現在的比較小，才表示它真的在累加。
     */
    val totalKcalSoFar: Double?,
    /**
     * 健康連線裡的基礎代謝（`BasalMetabolicRateRecord` 當天合計）。
     *
     * 存在的理由是「總消耗 − 基礎代謝」那條路**唯一的修法**：減數要和被減數同源。
     * 我們自己用 Mifflin-St Jeor 算的那份和三星算的差幾個百分點，而答案只有一百多
     * 大卡，誤差和答案同一個量級。這裡讀的是三星自己寫進來的那份。
     */
    val basalKcal: Double?,
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
    val basalOrigins: RecordOrigins? = null,
    /** 這份診斷是幾點讀的。隔幾小時讀兩次要比較數字有沒有動，沒有它就只能靠記憶。 */
    val readAt: LocalTime = LocalTime.now(),
)

/** 兩種來源都沒有東西時給使用者看的說明。 */
const val NO_ACTIVITY_DATA_REASON = "健康連線裡沒有這一天的活動資料"

/**
 * 讀得到步數、但還沒超出久坐基準已經含的那一段。
 *
 * 和 [NO_ACTIVITY_DATA_REASON] 是兩件事：那個是「讀不到」，這個是「讀到了，
 * 而且答案就是 0」。混在一起使用者永遠分不出來自己該去修權限還是該多走兩步。
 */
const val NO_SURPLUS_REASON = "今天的走動還在久坐基準的額度內"

/**
 * 每走一步、每公斤體重大約消耗的大卡。
 *
 * 這是業界常用的粗估值，而且**和三星自己算的一致** —— 實測 1,998 步、
 * 64 kg 換算出 63.9，Samsung Health 自己顯示的活動消耗也是 63。換句話說，
 * 三星主頁那個「活動消耗熱量」本身就是步數換算出來的。
 */
const val KCAL_PER_STEP_PER_KG = 0.0005

fun stepsToCalories(steps: Long, weightKg: Double): Double =
    if (steps <= 0L || weightKg <= 0.0) 0.0 else steps * weightKg * KCAL_PER_STEP_PER_KG

/** 食物熱效應大約佔攝取熱量的比例。 */
private const val THERMIC_EFFECT_SHARE = 0.10

/**
 * 久坐係數**已經給了**的日常走動額度（大卡）。
 *
 * 熱量目標的底是基礎代謝 × [ActivityLevel.SEDENTARY]，而「久坐 1.2」的定義不是
 * 躺著不動，是「沒有規律運動的正常人」—— 走去廁所、走去買午餐都在裡面。
 * 所以**步數換算出來的熱量不能整筆加上去**，只能算超出這個額度的那一段，
 * 否則就是把同一批熱量算兩次 —— 和「活動係數目標＋手錶運動」那個錯誤同一類。
 *
 * 係數多給的那一段裡面還含食物熱效應，而那跟走路無關，要扣掉。
 *
 * **這個值有誤差。** 食物熱效應抽 10% 是標準值，但活動係數到底怎麼拆成
 * 「熱效應 vs 走動」文獻沒有統一講法，換算成步數大約是四千步、正負一千。
 * 落到目標上是 ±16 大卡（還要再乘回補比例），可以接受，但**不要把它當成精確值**。
 */
fun dailyMovementAllowance(bmrPerDay: Double, calorieTarget: Int): Double =
    (bmrPerDay * (ActivityLevel.SEDENTARY.multiplier - 1f) - calorieTarget * THERMIC_EFFECT_SHARE)
        .coerceAtLeast(0.0)

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
    /** 體重，步數換算要用。沒填過就走不了步數那條。 */
    weightKg: Double = 0.0,
    /** 久坐係數已經給的日常走動額度，見 [dailyMovementAllowance]。 */
    movementAllowance: Double = 0.0,
): DailyActivity {
    val workout = (workoutKcal ?: 0.0).coerceAtLeast(0.0)
    val active = (activeKcal ?: 0.0).coerceAtLeast(0.0)

    // **步數只在沒有活動大卡時才算。** 兩者估的是同一件事（一整天的走動），
    // 永遠是二選一、不能相加。三星不寫活動大卡，所以實際上每天都走步數這條；
    // 別的手機真的寫了的話，那個數字本身已經含日常走動，再加就重複。
    //
    // 運動場次裡的步數要扣掉：跑步那三十分鐘的步已經算在場次熱量裡了。
    val workoutSteps = workoutSessions.sumOf { it.steps }.coerceAtLeast(0L)
    val dailySteps = (steps - workoutSteps).coerceAtLeast(0L)
    val stepKcal = if (active > 0.0) 0.0 else stepsToCalories(dailySteps, weightKg)
    // 只算超出久坐基準的那一段，理由見 [dailyMovementAllowance]。
    val stepSurplus = (stepKcal - movementAllowance).coerceAtLeast(0.0)

    // 整天配戴：活動消耗已經含日常走動與運動，直接用。運動場次比它還多的話以場次為準
    // —— 有些手錶只在運動時段寫活動大卡，全日那筆反而偏小。
    //
    // 步數那一段兩種配戴方式都加：它是手機計步器量的，和手錶戴不戴無關。
    val measured = when (wearMode) {
        WatchWearMode.ALL_DAY -> maxOf(active, workout)
        // 只有運動時戴：全日活動消耗只涵蓋戴著的那幾小時，拿來當一整天會低估。
        WatchWearMode.WORKOUT_ONLY -> workout
    }
    val calories = measured + stepSurplus

    if (calories <= 0.0) {
        return DailyActivity(
            calories = 0.0,
            steps = steps,
            source = ActivitySource.NONE,
            // 「讀不到」與「讀到了但沒超出」要分開講，兩件事往不同方向修。
            unavailableReason =
                if (dailySteps > 0L) NO_SURPLUS_REASON else NO_ACTIVITY_DATA_REASON,
            workoutCount = workoutCount,
            workoutSummary = workoutSummary,
            workoutSessions = workoutSessions,
            dailySteps = dailySteps,
        )
    }

    return DailyActivity(
        calories = calories,
        steps = steps,
        // 標的是「主要來源」：測到的那一段比步數重要，因為它是量的不是算的。
        // 兩段都有時，明細面板會把場次與走路各列一行，不靠這個標籤講完。
        source = when {
            measured <= 0.0 -> ActivitySource.STEPS
            wearMode == WatchWearMode.WORKOUT_ONLY -> ActivitySource.WORKOUT_SESSIONS
            measured > active -> ActivitySource.WORKOUT_SESSIONS
            else -> ActivitySource.ACTIVE_CALORIES
        },
        workoutCalories = workout,
        workoutCount = workoutCount,
        workoutSummary = workoutSummary,
        workoutSessions = workoutSessions,
        dailySteps = dailySteps,
        stepCalories = stepSurplus,
    )
}
