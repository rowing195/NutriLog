package com.watson.nutrilog.data

import com.watson.nutrilog.data.db.DailyHealthMetric
import com.watson.nutrilog.data.db.NutriDao
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * 負責聚合整月份（如 2026-09，全月 1 號至最後一天）的 NutriLog 飲食紀錄與
 * Samsung Health 手錶運動數據，並進行跨月指標對比與組裝 AI 宏觀月報 Prompt。
 */
class MonthlyAggregator {

    suspend fun aggregateMonth(
        yearMonth: YearMonth,
        dao: NutriDao,
        healthConnectSync: HealthConnectSync,
        settings: NutriSettings,
        includeComparison: Boolean = true,
    ): MonthlyStats {
        val startDate = yearMonth.atDay(1)
        val endDate = yearMonth.atEndOfMonth()
        val daysInMonth = yearMonth.lengthOfMonth()

        val fromStr = startDate.toString()
        val toStr = endDate.toString()

        val entries = dao.getEntriesInRange(fromStr, toStr)
        val entriesByDate = entries.groupBy { it.date }

        val bmrPerDay = settings.estimatedBmrPerDay()

        var totalCaloriesConsumed = 0.0
        var totalActiveBurned = 0.0
        var totalProteinG = 0.0
        var totalFatG = 0.0
        var totalCarbsG = 0.0
        var loggedDaysCount = 0

        val today = LocalDate.now()

        for (day in 1..daysInMonth) {
            val date = yearMonth.atDay(day)
            val dateStr = date.toString()
            val dayEntries = entriesByDate[dateStr].orEmpty()

            if (dayEntries.isNotEmpty()) {
                loggedDaysCount++
                totalCaloriesConsumed += dayEntries.sumOf { it.calories }
                totalProteinG += dayEntries.sumOf { it.proteinG }
                totalFatG += dayEntries.sumOf { it.fatG }
                totalCarbsG += dayEntries.sumOf { it.carbsG }
            }

            // 快取優先讀取手錶活動熱量，若無且支援則從 Health Connect 抓取並存庫
            val cachedMetric = dao.getHealthMetric(dateStr)
            val activeBurn = if (healthConnectSync.isSupported() && settings.readExerciseCalories && !date.isAfter(today)) {
                val activity = runCatching { healthConnectSync.readDailyActivity(date, settings) }.getOrNull()
                if (activity != null) {
                    dao.upsertHealthMetric(
                        (cachedMetric ?: DailyHealthMetric(date = dateStr)).copy(
                            activeCalories = activity.calories,
                            steps = activity.steps,
                            workoutCalories = activity.workoutCalories,
                            lastSyncedAt = System.currentTimeMillis(),
                        )
                    )
                    activity.calories
                } else {
                    cachedMetric?.activeCalories ?: 0.0
                }
            } else {
                cachedMetric?.activeCalories ?: 0.0
            }

            totalActiveBurned += activeBurn
        }

        val effectiveDays = loggedDaysCount.coerceAtLeast(1)
        val avgDailyCaloriesConsumed = totalCaloriesConsumed / effectiveDays
        val avgDailyActiveBurned = totalActiveBurned / daysInMonth
        val totalBurnedForLoggedDays = (bmrPerDay * effectiveDays) + totalActiveBurned
        val netBalance = totalCaloriesConsumed - totalBurnedForLoggedDays
        val fatChangeKg = netBalance / 7700.0

        val avgDailyTdee = bmrPerDay + avgDailyActiveBurned
        val avgProteinG = totalProteinG / effectiveDays
        val avgFatG = totalFatG / effectiveDays
        val avgCarbsG = totalCarbsG / effectiveDays

        // 計算跨月對比（與上個月）
        val comparison = if (includeComparison) {
            val prevMonth = yearMonth.minusMonths(1)
            val prevStats = aggregateMonth(prevMonth, dao, healthConnectSync, settings, includeComparison = false)
            if (prevStats.loggedDaysCount > 0) {
                MonthlyComparison(
                    deltaCaloriesInPerDay = avgDailyCaloriesConsumed - prevStats.avgDailyCaloriesConsumed,
                    deltaActiveBurnPerDay = avgDailyActiveBurned - prevStats.avgDailyActiveBurned,
                    deltaDailyTdee = avgDailyTdee - prevStats.avgDailyTdee,
                    deltaFatChangeKg = fatChangeKg - prevStats.estimatedFatChangeKg,
                    deltaProteinG = avgProteinG - prevStats.avgProteinG,
                )
            } else null
        } else null

        return MonthlyStats(
            yearMonth = yearMonth.toString(),
            totalCaloriesConsumed = totalCaloriesConsumed,
            avgDailyCaloriesConsumed = avgDailyCaloriesConsumed,
            totalActiveCaloriesBurned = totalActiveBurned,
            avgDailyActiveBurned = avgDailyActiveBurned,
            estimatedBmrPerDay = bmrPerDay,
            avgDailyTdee = avgDailyTdee,
            netEnergyBalance = netBalance,
            estimatedFatChangeKg = fatChangeKg,
            avgProteinG = avgProteinG,
            avgFatG = avgFatG,
            avgCarbsG = avgCarbsG,
            loggedDaysCount = loggedDaysCount,
            daysInMonth = daysInMonth,
            comparison = comparison,
        )
    }

    fun buildPrompts(stats: MonthlyStats, settings: NutriSettings): Pair<String, String> {
        val goalDesc = when (settings.profileGoal) {
            DietGoal.LOSE_FAT -> "溫和減脂（赤字約 300 kcal/日）"
            DietGoal.MAINTAIN -> "維持體重與雕塑（收支平衡）"
            DietGoal.GAIN_MUSCLE -> "增肌（熱量微盈餘約 300 kcal/日）"
        }

        val systemPrompt = """
你是一位兼具 ACSM 體能運動總監與 ISSN 運動營養總監認證的資深健康教練。
你的任務是根據使用者整月份（${stats.yearMonth}）的【NutriLog 飲食總攝取與三大營養素】與【Samsung Health 手錶實測運動大數據】，撰寫一份宏觀、具備長週期科學洞察的「每月份健康覆盤與體態重塑月報」。

請嚴格遵循以下 Markdown 結構輸出：
### 1. 🏛️ 全月週期體態重塑總結
- 深度剖析整月份能量守恆平衡（全月飲食總攝取 vs 手錶實測總消耗 vs 淨熱量收支）。
- 換算預估全月純體脂變化（以熱量赤字/盈餘換算體重變化）。
- 檢視全月目標（$goalDesc）達成率評級（S 級卓越 / A 級良好 / B 級需調整），評估長期代謝適應（Metabolic Adaptation）與是否有進入平台期現象。

### 2. 🥗 月度宏觀營養平衡體檢
- 檢視每日平均蛋白質（${stats.avgProteinG.roundToInt()}g，體重 ${settings.profileWeightKg}kg），評估全月肌肉保留防護網。
- 剖析三大營養素能量分佈比例（蛋白質、脂肪、碳水是否均衡健康）。
- 點評飲食規律度（全月 ${stats.daysInMonth} 天中記錄了 ${stats.loggedDaysCount} 天）。

### 3. 🏃 月度生活活動力與訓練適應
- 點評手錶實測全月總活動消耗（${stats.totalActiveCaloriesBurned.roundToInt()} kcal，平均每日實測活動消耗 ${stats.avgDailyActiveBurned.roundToInt()} kcal）。
- 評估日常非運動活動（NEAT）與高強度訓練的長線適應與體能提升。

${if (stats.comparison != null) """
### 4. 📈 跨月進展對比分析
- 深入點評相較於上個月的變化（攝取控制、運動消耗提升與體態轉變速度）。
""" else ""}

### 5. 🎯 下個月宏觀戰略方針（3 大宏觀調整策略）
- 給出下一個月份的總體執行方針（例如熱量循環策略、增強特定時段蛋白質、或調整運動強度）。
""".trimIndent()

        val userPromptBuilder = StringBuilder()
        userPromptBuilder.appendLine("## 使用者體態基本檔案：")
        userPromptBuilder.appendLine("- 性別：${if (settings.profileGender == Gender.MALE) "男" else "女"}，年齡：${settings.profileAge} 歲，身高：${settings.profileHeightCm} cm，體重：${settings.profileWeightKg} kg")
        userPromptBuilder.appendLine("- 目前體態目標：$goalDesc")
        userPromptBuilder.appendLine("- 預估基礎代謝 (BMR)：${stats.estimatedBmrPerDay.roundToInt()} kcal/日")
        userPromptBuilder.appendLine()

        userPromptBuilder.appendLine("## ${stats.yearMonth} 月份全月實測客觀數據（共 ${stats.daysInMonth} 天，記錄 ${stats.loggedDaysCount} 天）：")
        userPromptBuilder.appendLine("- 全月飲食總攝取：${stats.totalCaloriesConsumed.roundToInt()} kcal（記錄日平均：${stats.avgDailyCaloriesConsumed.roundToInt()} kcal/日）")
        userPromptBuilder.appendLine("- 手錶實測全月總活動消耗：${stats.totalActiveCaloriesBurned.roundToInt()} kcal（平均每日活動消耗：${stats.avgDailyActiveBurned.roundToInt()} kcal/日）")
        userPromptBuilder.appendLine("- 手錶實測平均每日真實 TDEE：${stats.avgDailyTdee.roundToInt()} kcal/日")
        val deficitText = if (stats.netEnergyBalance < 0) {
            "全月熱量赤字 ${(-stats.netEnergyBalance).roundToInt()} kcal（預計減脂約 ${(-stats.estimatedFatChangeKg * 100).roundToInt() / 100.0} kg）"
        } else {
            "全月熱量盈餘 ${stats.netEnergyBalance.roundToInt()} kcal"
        }
        userPromptBuilder.appendLine("- 全月淨能量平衡：$deficitText")
        userPromptBuilder.appendLine("- 每日平均三大營養素：蛋白質 ${stats.avgProteinG.roundToInt()}g，脂肪 ${stats.avgFatG.roundToInt()}g，碳水 ${stats.avgCarbsG.roundToInt()}g")

        if (stats.comparison != null) {
            userPromptBuilder.appendLine()
            userPromptBuilder.appendLine("## 與上個月（同期基準）之跨月變化：")
            val c = stats.comparison
            userPromptBuilder.appendLine("- 每日平均攝取變化：${if (c.deltaCaloriesInPerDay >= 0) "+" else ""}${c.deltaCaloriesInPerDay.roundToInt()} kcal/日")
            userPromptBuilder.appendLine("- 每日活動消耗變化：${if (c.deltaActiveBurnPerDay >= 0) "+" else ""}${c.deltaActiveBurnPerDay.roundToInt()} kcal/日")
            userPromptBuilder.appendLine("- 實測每日 TDEE 變化：${if (c.deltaDailyTdee >= 0) "+" else ""}${c.deltaDailyTdee.roundToInt()} kcal/日")
            userPromptBuilder.appendLine("- 累積體脂增減變化：${if (c.deltaFatChangeKg >= 0) "+" else ""}${(c.deltaFatChangeKg * 10).roundToInt() / 10.0} kg")
            userPromptBuilder.appendLine("- 平均蛋白質變化：${if (c.deltaProteinG >= 0) "+" else ""}${c.deltaProteinG.roundToInt()}g/日")
        }

        userPromptBuilder.appendLine()
        userPromptBuilder.appendLine("請根據以上全月份真實大數據，為我撰寫一份深度的每月份健康覆盤與體態重塑月報！")

        return Pair(systemPrompt, userPromptBuilder.toString())
    }
}
