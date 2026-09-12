package com.watson.nutrilog.data

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import com.watson.nutrilog.data.db.FoodEntry
import com.watson.nutrilog.data.db.Meal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 與 Android Health Connect（健康連線）溝通的封裝類別。
 *
 * 1. 負責將 NutriLog 的飲食紀錄（[FoodEntry]）轉換並寫入 Health Connect 的 [NutritionRecord]，
 *    藉此與 Samsung Health 及系統健康中樞無縫同步。
 * 2. 負責從 Health Connect 讀取運動與日常活動燃燒的卡路里，讓使用者在 NutriLog
 *    能一目了然看見運動消耗與淨熱量。要讀哪一種看手錶的配戴方式，見 [readDailyActivity]。
 */
class HealthConnectSync(private val context: Context) {

    private val client: HealthConnectClient? by lazy {
        if (isSupported()) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    /** 檢查目前裝置是否支援 Health Connect */
    fun isSupported(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /** 檢查是否已取得寫入飲食權限 */
    suspend fun hasWritePermission(): Boolean {
        val c = client ?: return false
        val granted = c.permissionController.getGrantedPermissions()
        return granted.containsAll(WRITE_PERMISSIONS)
    }

    /**
     * 檢查是否已取得讀取運動的權限。
     *
     * 判斷條件是「至少一種」而不是「全部」：三種讀取權限是彼此的退路，
     * 只授權其中一種也還原得出活動量。要求全部的話，既有使用者
     * （只授權過活動大卡）升級後會突然變成未授權。
     */
    suspend fun hasReadExercisePermission(): Boolean {
        val c = client ?: return false
        val granted = c.permissionController.getGrantedPermissions()
        return READ_EXERCISE_PERMISSIONS.any { it in granted }
    }

    /** 檢查是否擁有所有權限（寫入飲食 + 至少一種讀取運動） */
    suspend fun hasPermissions(): Boolean =
        hasWritePermission() && hasReadExercisePermission()

    /**
     * 還沒授權的權限有哪些。
     *
     * 新增讀取型別（例如補上步數）之後，既有使用者的 [hasReadExercisePermission]
     * 仍然是 true（他授權過活動大卡），所以不會再被問一次 —— 少掉的那個型別
     * 就永遠拿不到，症狀是升級後運動消耗照樣是 0。要靠這個判斷主動補問。
     */
    suspend fun missingPermissions(): Set<String> {
        val c = client ?: return REQUIRED_PERMISSIONS
        val granted = runCatching { c.permissionController.getGrantedPermissions() }
            .getOrDefault(emptySet())
        return REQUIRED_PERMISSIONS - granted
    }

    /**
     * 單筆寫入或更新飲食紀錄至 Health Connect。
     *
     * 使用 clientRecordId = "nutrilog_${entry.id}" 進行等冪操作（Upsert），避免重複建立紀錄。
     * 0 卡項目（如水或黑咖啡）不寫入，若先前曾同步過則自動刪除。
     */
    suspend fun writeEntry(entry: FoodEntry): Result<Unit> = runCatching {
        if (entry.calories <= 0.0) {
            deleteEntry(entry.id)
            return@runCatching
        }

        val c = client ?: error("Health Connect is not available")
        if (!hasWritePermission()) error("Permission not granted")

        val record = toNutritionRecord(entry)
        c.insertRecords(listOf(record))
    }

    /**
     * 依據 NutriLog 的 entryId 刪除 Health Connect 中對應的紀錄。
     */
    suspend fun deleteEntry(entryId: Long): Result<Unit> = runCatching {
        val c = client ?: return@runCatching
        if (!hasWritePermission()) return@runCatching

        c.deleteRecords(
            recordType = NutritionRecord::class,
            recordIdsList = emptyList(),
            clientRecordIdsList = listOf(clientRecordId(entryId)),
        )
    }

    /**
     * 批次同步多筆飲食紀錄（例如歷史紀錄匯入或手動同步）。
     * 自動過濾掉 0 卡的項目，並清理健康連線中可能已存在的 0 卡紀錄。
     */
    suspend fun syncEntries(entries: List<FoodEntry>): Result<Int> = runCatching {
        val c = client ?: error("Health Connect is not available")
        if (!hasWritePermission()) error("Permission not granted")

        val (validEntries, zeroEntries) = entries.partition { it.calories > 0.0 }

        if (zeroEntries.isNotEmpty()) {
            c.deleteRecords(
                recordType = NutritionRecord::class,
                recordIdsList = emptyList(),
                clientRecordIdsList = zeroEntries.map { clientRecordId(it.id) },
            )
        }

        var count = 0
        validEntries.chunked(50).forEach { chunk ->
            val records = chunk.map { toNutritionRecord(it) }
            c.insertRecords(records)
            count += records.size
        }
        count
    }

    /**
     * 讀取指定日期（00:00 至隔日 00:00）的活動量。
     *
     * 只讀兩種資料：全日的**活動大卡**與**運動場次**。要用哪一種看
     * [NutriSettings.watchWearMode] —— 整天戴的話活動大卡就是一整天的活動量；
     * 只有運動時戴的話它只涵蓋戴著的那幾小時，拿來當一整天用會低估，所以只取場次。
     *
     * 步數照樣讀、照樣回傳（要存進 [com.watson.nutrilog.data.db.DailyHealthMetric]
     * 給週報／月報用），但**不再換算成大卡**。
     *
     * 挑選邏輯本身在 [chooseActivity]（純函式、有單元測試），這裡只負責取數字。
     */
    suspend fun readDailyActivity(date: LocalDate, settings: NutriSettings): DailyActivity =
        withContext(Dispatchers.IO) {
            val c = client
                ?: return@withContext DailyActivity(0.0, 0L, ActivitySource.NONE, "此裝置不支援健康連線")

            val granted = runCatching { c.permissionController.getGrantedPermissions() }
                .getOrDefault(emptySet())
            if (READ_EXERCISE_PERMISSIONS.none { it in granted }) {
                return@withContext DailyActivity(
                    0.0, 0L, ActivitySource.NONE, "尚未在健康連線授權 NutriLog 讀取運動與步數",
                )
            }

            try {
                val zoneId = ZoneId.systemDefault()
                val now = Instant.now()
                val startInstant = date.atStartOfDay(zoneId).toInstant()
                val endInstant = date.plusDays(1).atStartOfDay(zoneId).toInstant()

                // 只問拿得到權限的那幾種：問到沒授權的指標整個 aggregate 會丟例外，
                // 等於一種沒給就兩種都讀不到。
                //
                // **不再問全日的總消耗。** 它含靜態消耗，要還原活動量就得扣掉基礎代謝，
                // 而那是兩個一千五百多的大數字相減去換一個一百多的小數字，誤差和答案
                // 同一個量級（實測手錶記 153、這條路算出 39），見 [chooseActivity]。
                val metrics = buildSet {
                    if (READ_ACTIVE_CALORIES_PERMISSION in granted) {
                        add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
                    }
                    if (READ_STEPS_PERMISSION in granted) {
                        add(StepsRecord.COUNT_TOTAL)
                    }
                }

                val response = if (metrics.isNotEmpty()) {
                    c.aggregate(
                        AggregateRequest(
                            metrics = metrics,
                            timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant),
                        )
                    )
                } else null

                // 讀取專項體能訓練場次（重訓、跑步、跑步機、健走等）
                var workoutCaloriesTotal = 0.0
                var workoutCount = 0
                val sessionSummaries = mutableListOf<String>()
                val workoutSessionItems = mutableListOf<WorkoutSessionItem>()
                val timeFormat = java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(zoneId)

                if (READ_EXERCISE_PERMISSION in granted) {
                    val sessions = runCatching {
                        c.readRecords(
                            ReadRecordsRequest(
                                recordType = ExerciseSessionRecord::class,
                                timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant),
                            )
                        ).records
                    }.onFailure { e ->
                        Log.e("HealthConnectSync", "readRecords ExerciseSessionRecord error for $date: ${e.message}", e)
                    }.getOrDefault(emptyList())

                    Log.i("HealthConnectSync", "[$date] Read ${sessions.size} exercise sessions")
                    if (sessions.isEmpty()) {
                        // 診斷日誌：若當日未查得運動，列印近 14 天健康連線內的所有運動場次與時間
                        runCatching {
                            val recent = c.readRecords(
                                ReadRecordsRequest(
                                    recordType = ExerciseSessionRecord::class,
                                    timeRangeFilter = TimeRangeFilter.between(
                                        now.minus(14, java.time.temporal.ChronoUnit.DAYS),
                                        now,
                                    ),
                                )
                            ).records
                            Log.i("HealthConnectSync", "Diagnostic: total ${recent.size} sessions in past 14 days")
                            for (r in recent) {
                                Log.i("HealthConnectSync", "  -> Session: type=${r.exerciseType}, title=${r.title}, start=${r.startTime}, end=${r.endTime}, origin=${r.metadata.dataOrigin.packageName}")
                            }
                        }
                    }

                    workoutCount = sessions.size
                    for (session in sessions) {
                        val durationMin = java.time.Duration.between(session.startTime, session.endTime).toMinutes().coerceAtLeast(1)
                        var sessionKcal = if (READ_ACTIVE_CALORIES_PERMISSION in granted) {
                            val sessionAgg = runCatching {
                                c.aggregate(
                                    AggregateRequest(
                                        metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                                        timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime),
                                    )
                                )
                            }.getOrNull()
                            sessionAgg?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories ?: 0.0
                        } else 0.0

                        // 若活動大卡為 0，嘗試以該時段總消耗扣除基礎代謝推算該場運動熱量（容差前後 60 秒）
                        if (sessionKcal <= 0.0 && READ_TOTAL_CALORIES_PERMISSION in granted) {
                            val totalAgg = runCatching {
                                c.aggregate(
                                    AggregateRequest(
                                        metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                                        timeRangeFilter = TimeRangeFilter.between(
                                            session.startTime.minusSeconds(60),
                                            session.endTime.plusSeconds(60),
                                        ),
                                    )
                                )
                            }.getOrNull()
                            val sessionTotal = totalAgg?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories ?: 0.0
                            if (sessionTotal > 0.0) {
                                val durationSec = java.time.Duration.between(session.startTime, session.endTime).seconds.toDouble()
                                val bmrPortion = settings.estimatedBmrPerDay() * (durationSec / 86400.0)
                                sessionKcal = (sessionTotal - bmrPortion).coerceAtLeast(0.0)
                            }
                        }

                        // 退路：若手錶未單獨記錄該時段卡路里，以運動醫學 MET 公式依體重與時長推估熱量
                        if (sessionKcal <= 0.0) {
                            sessionKcal = estimateWorkoutCalories(
                                exerciseType = session.exerciseType,
                                durationMinutes = durationMin,
                                weightKg = settings.profileWeightKg.toDouble(),
                            )
                        }

                        val sessionSteps = if (READ_STEPS_PERMISSION in granted) {
                            val stepsAgg = runCatching {
                                c.aggregate(
                                    AggregateRequest(
                                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                                        timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime),
                                    )
                                )
                            }.getOrNull()
                            stepsAgg?.get(StepsRecord.COUNT_TOTAL) ?: 0L
                        } else 0L

                        workoutCaloriesTotal += sessionKcal
                        val typeName = formatExerciseName(session)
                        val kcalText = if (sessionKcal > 0.0) " (${sessionKcal.toInt()} kcal)" else ""
                        sessionSummaries.add("$typeName ${durationMin}分鐘$kcalText")

                        val timeRange = "${timeFormat.format(session.startTime)} ~ ${timeFormat.format(session.endTime)}"
                        Log.i("HealthConnectSync", "  WorkoutItem: $typeName $timeRange ($durationMin min) -> $sessionKcal kcal, $sessionSteps steps")
                        workoutSessionItems.add(
                            WorkoutSessionItem(
                                title = typeName,
                                exerciseType = session.exerciseType,
                                durationMinutes = durationMin,
                                calories = sessionKcal,
                                timeRangeText = timeRange,
                                steps = sessionSteps,
                            )
                        )
                    }
                }

                val workoutSummaryText = sessionSummaries.joinToString("、")

                chooseActivity(
                    activeKcal = response?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories,
                    steps = response?.get(StepsRecord.COUNT_TOTAL) ?: 0L,
                    wearMode = settings.watchWearMode,
                    workoutKcal = workoutCaloriesTotal.takeIf { it > 0.0 },
                    workoutCount = workoutCount,
                    workoutSummary = workoutSummaryText,
                    workoutSessions = workoutSessionItems,
                )
            } catch (e: Exception) {
                Log.e("HealthConnectSync", "readDailyActivity error: ${e.message}", e)
                DailyActivity(0.0, 0L, ActivitySource.NONE, "讀取健康連線失敗：${e.message}")
            }
        }

    private fun toNutritionRecord(entry: FoodEntry): NutritionRecord {
        val zoneId = ZoneId.systemDefault()
        val instant = runCatching {
            val entryDate = LocalDate.parse(entry.date)
            val localTime = Instant.ofEpochMilli(entry.loggedAt).atZone(zoneId).toLocalTime()
            entryDate.atTime(localTime).atZone(zoneId).toInstant()
        }.getOrDefault(Instant.ofEpochMilli(entry.loggedAt))
        // IntervalRecord 要求 startTime 必須嚴格早於 endTime
        val endTime = instant.plusSeconds(60)
        val zoneOffset = zoneId.rules.getOffset(instant)
        val mealType = when (entry.mealType) {
            Meal.BREAKFAST -> MealType.MEAL_TYPE_BREAKFAST
            Meal.LUNCH -> MealType.MEAL_TYPE_LUNCH
            Meal.DINNER -> MealType.MEAL_TYPE_DINNER
            Meal.SNACK -> MealType.MEAL_TYPE_SNACK
        }

        return NutritionRecord(
            startTime = instant,
            endTime = endTime,
            startZoneOffset = zoneOffset,
            endZoneOffset = zoneOffset,
            // 1.1.0-beta01 起 Metadata 的建構子不再公開，要用工廠方法。飲食是使用者自己記的，
            // 屬於 manualEntry 而不是裝置自動記錄。clientRecordId 讓同一筆紀錄重寫時是更新而不是新增。
            metadata = Metadata.manualEntry(
                clientRecordId = clientRecordId(entry.id),
                clientRecordVersion = System.currentTimeMillis(),
            ),
            mealType = mealType,
            name = entry.name,
            energy = Energy.kilocalories(entry.calories.coerceAtLeast(0.0)),
            protein = Mass.grams(entry.proteinG.coerceAtLeast(0.0)),
            totalFat = Mass.grams(entry.fatG.coerceAtLeast(0.0)),
            totalCarbohydrate = Mass.grams(entry.carbsG.coerceAtLeast(0.0)),
            sugar = entry.sugarG?.let { Mass.grams(it.coerceAtLeast(0.0)) },
            dietaryFiber = entry.fiberG?.let { Mass.grams(it.coerceAtLeast(0.0)) },
            sodium = entry.sodiumMg?.let { Mass.milligrams(it.coerceAtLeast(0.0)) },
            saturatedFat = entry.satFatG?.let { Mass.grams(it.coerceAtLeast(0.0)) },
        )
    }

    private fun formatExerciseName(session: ExerciseSessionRecord): String {
        session.title?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return when (session.exerciseType) {
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "跑步機（Treadmill）"
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "跑步"
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "重量訓練"
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "健走"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "騎車"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "游泳"
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "瑜珈"
            ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "皮拉提斯"
            ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
            ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "橢圓機 / 滑步機"
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "划船機"
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING,
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> "爬梯機"
            ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> "徒手健身"
            ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> "伸展"
            else -> "體能訓練"
        }
    }

    private fun estimateWorkoutCalories(exerciseType: Int, durationMinutes: Long, weightKg: Double): Double {
        if (durationMinutes <= 0L) return 0.0
        val safeWeight = if (weightKg > 0.0) weightKg else 70.0
        val met = when (exerciseType) {
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> 8.5
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> 6.8
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> 7.0
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> 7.0
            ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> 8.0
            ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> 5.5
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
            ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> 5.0
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> 3.5
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
            ExerciseSessionRecord.EXERCISE_TYPE_PILATES,
            ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> 2.8
            else -> 5.0
        }
        return met * safeWeight * (durationMinutes / 60.0)
    }

    companion object {
        val WRITE_PERMISSIONS = setOf(
            HealthPermission.getWritePermission(NutritionRecord::class),
        )

        val READ_ACTIVE_CALORIES_PERMISSION =
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
        val READ_TOTAL_CALORIES_PERMISSION =
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
        val READ_STEPS_PERMISSION =
            HealthPermission.getReadPermission(StepsRecord::class)
        val READ_EXERCISE_PERMISSION =
            HealthPermission.getReadPermission(ExerciseSessionRecord::class)

        // 四種是彼此的退路與專項補充，判斷授權時一律用 any 不要用 containsAll。
        val READ_EXERCISE_PERMISSIONS = setOf(
            READ_ACTIVE_CALORIES_PERMISSION,
            READ_TOTAL_CALORIES_PERMISSION,
            READ_STEPS_PERMISSION,
            READ_EXERCISE_PERMISSION,
        )

        val REQUIRED_PERMISSIONS = WRITE_PERMISSIONS + READ_EXERCISE_PERMISSIONS

        fun clientRecordId(entryId: Long): String = "nutrilog_$entryId"
    }
}

