package com.watson.nutrilog.data

import android.util.Log
import com.watson.nutrilog.data.db.DailyHealthMetric
import com.watson.nutrilog.data.db.FoodEntry
import com.watson.nutrilog.data.db.NutriDao
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

data class DailyBreakdown(
    val date: LocalDate,
    val dayOfWeek: String,
    val caloriesIn: Double,
    val activeCaloriesOut: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
    val sugarG: Double,
    val sodiumMg: Double,
    val itemCount: Int,
)

data class WeeklyStats(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val totalCaloriesConsumed: Double,
    val avgDailyCaloriesConsumed: Double,
    val totalActiveCaloriesBurned: Double,
    val estimatedBmrPerDay: Double,
    val avgDailyTdee: Double,
    val netEnergyBalance: Double, // 攝取 - 消耗 (負數為赤字，正數為盈餘)
    val estimatedFatChangeKg: Double,
    val avgProteinG: Double,
    val avgFatG: Double,
    val avgCarbsG: Double,
    val loggedDaysCount: Int,
    val dailyBreakdowns: List<DailyBreakdown>,
    val workoutsList: List<String>,
    val comparison: WeeklyComparison? = null,
)

/**
 * 負責聚合一週 7 天的 NutriLog 飲食紀錄與 Samsung Health 手錶運動數據，
 * 並在本地計算出客觀、100% 精準的熱量收支與營養素統計指標，支援跨週指標對比。
 */
class WeeklyAggregator {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun aggregateWeek(
        weekStart: LocalDate,
        dao: NutriDao,
        healthConnectSync: HealthConnectSync,
        settings: NutriSettings,
        includeComparison: Boolean = true,
    ): WeeklyStats {
        val weekEnd = weekStart.plusDays(6)
        val fromStr = weekStart.toString()
        val toStr = weekEnd.toString()

        val entries = dao.getEntriesInRange(fromStr, toStr)
        val entriesByDate = entries.groupBy { it.date }

        val bmrPerDay = settings.estimatedBmrPerDay()

        val dailyBreakdowns = mutableListOf<DailyBreakdown>()
        val workoutsList = mutableListOf<String>()
        var totalActiveBurned = 0.0
        val today = LocalDate.now()

        for (i in 0..6) {
            val date = weekStart.plusDays(i.toLong())
            val dateStr = date.toString()
            val dayEntries = entriesByDate[dateStr].orEmpty()

            val calsIn = dayEntries.sumOf { it.calories }
            val pG = dayEntries.sumOf { it.proteinG }
            val fG = dayEntries.sumOf { it.fatG }
            val cG = dayEntries.sumOf { it.carbsG }
            val sG = dayEntries.sumOf { it.sugarG ?: 0.0 }
            val naMg = dayEntries.sumOf { it.sodiumMg ?: 0.0 }

            val cachedMetric = dao.getHealthMetric(dateStr)
            val (activeBurn, workoutDesc) = if (healthConnectSync.isSupported() && settings.readExerciseCalories && !date.isAfter(today)) {
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
                    Pair(activity.calories, activity.workoutSummary)
                } else {
                    Pair(cachedMetric?.activeCalories ?: 0.0, "")
                }
            } else {
                Pair(cachedMetric?.activeCalories ?: 0.0, "")
            }
            totalActiveBurned += activeBurn

            val dayOfWeekName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.TAIWAN)
            if (workoutDesc.isNotBlank()) {
                workoutsList.add("${date}（${dayOfWeekName}）：$workoutDesc")
            }
            dailyBreakdowns.add(
                DailyBreakdown(
                    date = date,
                    dayOfWeek = dayOfWeekName,
                    caloriesIn = calsIn,
                    activeCaloriesOut = activeBurn,
                    proteinG = pG,
                    fatG = fG,
                    carbsG = cG,
                    sugarG = sG,
                    sodiumMg = naMg,
                    itemCount = dayEntries.size,
                )
            )
        }

        val loggedDays = dailyBreakdowns.count { it.itemCount > 0 }
        val effectiveLoggedDays = loggedDays.coerceAtLeast(1)

        val totalConsumed = dailyBreakdowns.sumOf { it.caloriesIn }
        val avgConsumed = totalConsumed / effectiveLoggedDays

        val totalTdeeForWeek = (bmrPerDay * 7) + totalActiveBurned
        val avgDailyTdee = totalTdeeForWeek / 7.0

        // 淨熱量平衡 = 總攝取 - (BMR * 有記錄天數 + 活動消耗)
        val totalBurnedForLoggedDays = (bmrPerDay * effectiveLoggedDays) + totalActiveBurned
        val netBalance = totalConsumed - totalBurnedForLoggedDays
        val fatChangeKg = netBalance / 7700.0

        val avgP = dailyBreakdowns.sumOf { it.proteinG } / effectiveLoggedDays
        val avgF = dailyBreakdowns.sumOf { it.fatG } / effectiveLoggedDays
        val avgC = dailyBreakdowns.sumOf { it.carbsG } / effectiveLoggedDays

        // 計算跨週對比（與上週同期）
        val comparison = if (includeComparison) {
            val prevWeekStart = weekStart.minusWeeks(1)
            val prevStats = aggregateWeek(prevWeekStart, dao, healthConnectSync, settings, includeComparison = false)
            if (prevStats.loggedDaysCount > 0) {
                WeeklyComparison(
                    deltaCaloriesInPerDay = avgConsumed - prevStats.avgDailyCaloriesConsumed,
                    deltaActiveBurnPerDay = (totalActiveBurned / 7.0) - (prevStats.totalActiveCaloriesBurned / 7.0),
                    deltaDailyTdee = avgDailyTdee - prevStats.avgDailyTdee,
                    deltaFatChangeKg = fatChangeKg - prevStats.estimatedFatChangeKg,
                    deltaProteinG = avgP - prevStats.avgProteinG,
                )
            } else null
        } else null

        return WeeklyStats(
            weekStart = weekStart,
            weekEnd = weekEnd,
            totalCaloriesConsumed = totalConsumed,
            avgDailyCaloriesConsumed = avgConsumed,
            totalActiveCaloriesBurned = totalActiveBurned,
            estimatedBmrPerDay = bmrPerDay,
            avgDailyTdee = avgDailyTdee,
            netEnergyBalance = netBalance,
            estimatedFatChangeKg = fatChangeKg,
            avgProteinG = avgP,
            avgFatG = avgF,
            avgCarbsG = avgC,
            loggedDaysCount = loggedDays,
            dailyBreakdowns = dailyBreakdowns,
            workoutsList = workoutsList,
            comparison = comparison,
        )
    }

    fun buildPrompts(stats: WeeklyStats, settings: NutriSettings): Pair<String, String> {
        val goalDesc = when (settings.profileGoal) {
            DietGoal.LOSE_FAT -> "溫和減脂（赤字約 300 kcal/日）"
            DietGoal.MAINTAIN -> "維持體重與雕塑（收支平衡）"
            DietGoal.GAIN_MUSCLE -> "增肌（熱量微盈餘約 300 kcal/日）"
        }

        val systemPrompt = """
你是一位擁有 ACSM 體能教練與 ISSN 運動營養專家的資深教練。
你的任務是根據使用者本週真實的【NutriLog 飲食明細與營養素】與【Samsung Health 手錶記錄的真實每日活動/運動消耗】，撰寫一份兼具專業科學依據、鼓勵性且具備高度實操性的每週健康覆盤週報，並精準推薦下週的最佳目標。

請嚴格遵循以下輸出結構規範（採用清晰優雅的 Markdown 排版）：
### 1. 🏆 本週總結與體態進度評級
- 深度分析能量天秤（總攝取 vs 實測總消耗 vs 淨熱量收支）。
- 根據使用者的個人目標（$goalDesc），評估本週達成率評級（如：S 級完美達標 / A 級良好 / B 級需微調）。
- 預估體脂變化趨勢（以熱量赤字/盈餘換算體重變化）。

### 2. 🥗 飲食與三大營養素體檢
- 檢視每日平均蛋白質（${stats.avgProteinG.roundToInt()}g），評估是否足夠支援肌肉保留與運動修復（參考體重 ${settings.profileWeightKg}kg）。
- 剖析碳水化合物與脂肪配置，並指出精緻糖與鈉攝取是否偏高。
- 點出本週哪一天飲食控制得最好、哪一天熱量偏高（爆卡日分析）。

### 3. 🏃 運動與生活活動量點評
- 點評 Samsung Health 手錶實測的動態活動熱量（平均每日實測活動消耗約 ${(stats.totalActiveCaloriesBurned / 7).roundToInt()} kcal）${if (stats.workoutsList.isNotEmpty()) "，並針對各項手錶專項體能訓練（如跑步機、健走等）給予具體修復與運動表現建議" else ""}。
- 肯定日常非運動步數與專項體能訓練的去重疊加成果，鼓勵持續保持良好的活動習慣。

${if (stats.comparison != null) """
### 4. 📈 跨週進展對比分析
- 深入點評相較於上週的具體進步（每日攝取控制、手錶活動消耗變化與體能習慣演進）。
""" else ""}

### 5. 📋 下週個人化行動清單（3 大具體微習慣）
- 提出 3 項下週馬上能執行的具體行動方針（例如運動日前後碳水補充、平日下午改無糖茶、增加蛋白質攝取時機等）。

【核心要求：下週目標推薦 JSON 區塊】：
在你的週報文本最結尾，請務必附帶一個專屬的 JSON 代碼塊（包含建議每日攝取大卡與蛋白質/脂肪/碳水克數，數值必須為整數）：
```json:targets
{
  "calorieTarget": 1850,
  "proteinTargetG": 140,
  "fatTargetG": 50,
  "carbsTargetG": 210,
  "reason": "根據上週手錶實測平均真實 TDEE 與目標，設定最佳每日熱量與巨量營養素配置。"
}
```
""".trimIndent()

        val userPromptBuilder = StringBuilder()
        userPromptBuilder.appendLine("## 使用者個人體態檔案：")
        userPromptBuilder.appendLine("- 性別：${if (settings.profileGender == Gender.MALE) "男" else "女"}，年齡：${settings.profileAge} 歲")
        userPromptBuilder.appendLine("- 身高：${settings.profileHeightCm} cm，體重：${settings.profileWeightKg} kg")
        userPromptBuilder.appendLine("- 目前體態目標：$goalDesc")
        userPromptBuilder.appendLine("- 預估基礎代謝 (BMR)：${stats.estimatedBmrPerDay.roundToInt()} kcal/日")
        userPromptBuilder.appendLine("- 原設定每日目標：熱量 ${settings.calorieTarget} kcal，蛋白質 ${settings.proteinTargetG}g，脂肪 ${settings.fatTargetG}g，碳水 ${settings.carbsTargetG}g")
        userPromptBuilder.appendLine()

        userPromptBuilder.appendLine("## 本週實測客觀數據（${stats.weekStart} 至 ${stats.weekEnd}，共記錄 ${stats.loggedDaysCount} 天）：")
        userPromptBuilder.appendLine("- 本週飲食總攝取：${stats.totalCaloriesConsumed.roundToInt()} kcal（記錄日平均：${stats.avgDailyCaloriesConsumed.roundToInt()} kcal/日）")
        userPromptBuilder.appendLine("- 手錶實測一週總活動消耗：${stats.totalActiveCaloriesBurned.roundToInt()} kcal（平均每日活動消耗：${(stats.totalActiveCaloriesBurned / 7).roundToInt()} kcal/日）")
        userPromptBuilder.appendLine("- 手錶實測平均每日真實 TDEE (BMR + 活動)：${stats.avgDailyTdee.roundToInt()} kcal/日")
        val deficitText = if (stats.netEnergyBalance < 0) {
            "熱量赤字 ${(-stats.netEnergyBalance).roundToInt()} kcal（預計減脂約 ${(-stats.estimatedFatChangeKg * 100).roundToInt() / 100.0} kg）"
        } else {
            "熱量盈餘 ${stats.netEnergyBalance.roundToInt()} kcal"
        }
        userPromptBuilder.appendLine("- 本週淨能量平衡：$deficitText")
        userPromptBuilder.appendLine("- 平均三大營養素：蛋白質 ${stats.avgProteinG.roundToInt()}g，脂肪 ${stats.avgFatG.roundToInt()}g，碳水 ${stats.avgCarbsG.roundToInt()}g")

        if (stats.workoutsList.isNotEmpty()) {
            userPromptBuilder.appendLine()
            userPromptBuilder.appendLine("## 本週手錶專項體能訓練紀錄（已採用去重疊加）：")
            stats.workoutsList.forEach { w ->
                userPromptBuilder.appendLine("- $w")
            }
        }

        if (stats.comparison != null) {
            userPromptBuilder.appendLine()
            userPromptBuilder.appendLine("## 與上週同期相比之跨週進展：")
            val c = stats.comparison
            userPromptBuilder.appendLine("- 每日平均攝取變化：${if (c.deltaCaloriesInPerDay >= 0) "+" else ""}${c.deltaCaloriesInPerDay.roundToInt()} kcal/日")
            userPromptBuilder.appendLine("- 每日活動消耗變化：${if (c.deltaActiveBurnPerDay >= 0) "+" else ""}${c.deltaActiveBurnPerDay.roundToInt()} kcal/日")
            userPromptBuilder.appendLine("- 實測每日 TDEE 變化：${if (c.deltaDailyTdee >= 0) "+" else ""}${c.deltaDailyTdee.roundToInt()} kcal/日")
            userPromptBuilder.appendLine("- 預估減脂速度變化：${if (c.deltaFatChangeKg >= 0) "+" else ""}${(c.deltaFatChangeKg * 10).roundToInt() / 10.0} kg")
            userPromptBuilder.appendLine("- 每日蛋白質變化：${if (c.deltaProteinG >= 0) "+" else ""}${c.deltaProteinG.roundToInt()}g/日")
        }

        userPromptBuilder.appendLine()
        userPromptBuilder.appendLine("### 本週逐日紀錄明細表：")
        userPromptBuilder.appendLine("| 日期 | 星期 | 飲食攝取 | 手錶活動消耗 | 蛋白質 | 脂肪 | 碳水 | 糖 | 鈉 | 筆數 |")
        userPromptBuilder.appendLine("| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |")
        stats.dailyBreakdowns.forEach { d ->
            userPromptBuilder.appendLine(
                "| ${d.date} | ${d.dayOfWeek} | ${d.caloriesIn.roundToInt()} kcal | ${d.activeCaloriesOut.roundToInt()} kcal | ${d.proteinG.roundToInt()}g | ${d.fatG.roundToInt()}g | ${d.carbsG.roundToInt()}g | ${d.sugarG.roundToInt()}g | ${d.sodiumMg.roundToInt()}mg | ${d.itemCount} |"
            )
        }
        userPromptBuilder.appendLine()
        userPromptBuilder.appendLine("請根據以上本週真實數據與上週對比，為我撰寫一份深度的每週健康覆盤週報，並給出下週最精確的每日卡路里與三大營養素推薦！")

        return Pair(systemPrompt, userPromptBuilder.toString())
    }

    /**
     * 從 LLM 回傳的完整文字中，提取 ```json:targets ... ``` 結構化推薦目標，
     * 並返回過濾後的 Markdown 週報本文與推薦目標物件。
     */
    fun parseReportResponse(rawResponse: String): Pair<String, TargetRecommendation?> {
        // 先去除可能包含的 <think>...</think> 思考標籤
        val noThink = rawResponse.replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "").trim()

        val targetsRegex = Regex("```(?:json:targets|json)\\s*([\\s\\S]*?\"calorieTarget\"[\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE)
        val match = targetsRegex.find(noThink)
            ?: Regex("```json:targets\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE).find(noThink)

        if (match == null) {
            val cleaned = noThink.replace(Regex("```(?:json:targets|json)[\\s\\S]*?```", RegexOption.IGNORE_CASE), "").trim()
            return Pair(cleaned, null)
        }

        val jsonString = match.groupValues[1].trim()
        val recommendation = try {
            val jsonElement = json.parseToJsonElement(jsonString).jsonObject
            val cal = jsonElement["calorieTarget"]?.jsonPrimitive?.content?.toDoubleOrNull()?.roundToInt() ?: 2000
            val p = jsonElement["proteinTargetG"]?.jsonPrimitive?.content?.toDoubleOrNull()?.roundToInt() ?: 120
            val f = jsonElement["fatTargetG"]?.jsonPrimitive?.content?.toDoubleOrNull()?.roundToInt() ?: 60
            val c = jsonElement["carbsTargetG"]?.jsonPrimitive?.content?.toDoubleOrNull()?.roundToInt() ?: 200
            val reason = jsonElement["reason"]?.jsonPrimitive?.content.orEmpty()
            TargetRecommendation(
                calorieTarget = cal,
                proteinTargetG = p,
                fatTargetG = f,
                carbsTargetG = c,
                reason = reason,
            )
        } catch (e: Exception) {
            Log.w("WeeklyAggregator", "Failed to parse targets JSON: $jsonString", e)
            null
        }

        val cleanedMarkdown = noThink.replace(match.value, "").trim()
        return Pair(cleanedMarkdown, recommendation)
    }
}
