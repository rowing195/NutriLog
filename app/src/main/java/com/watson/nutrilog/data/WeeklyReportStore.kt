package com.watson.nutrilog.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * AI 建議之每日目標熱量與三大營養素配置。
 */
@Serializable
data class TargetRecommendation(
    val calorieTarget: Int,
    val proteinTargetG: Int,
    val fatTargetG: Int,
    val carbsTargetG: Int,
    val reason: String = "",
)

/**
 * 跨週對比數據指標（與上週之差值）。
 */
@Serializable
data class WeeklyComparison(
    val deltaCaloriesInPerDay: Double = 0.0,
    val deltaActiveBurnPerDay: Double = 0.0,
    val deltaDailyTdee: Double = 0.0,
    val deltaFatChangeKg: Double = 0.0,
    val deltaProteinG: Double = 0.0,
)

/**
 * 已生成之每週健康週報物件。
 */
@Serializable
data class WeeklyReport(
    val weekStartDate: String, // "yyyy-MM-dd"
    val weekEndDate: String,   // "yyyy-MM-dd"
    val generatedAt: Long,     // epoch millis
    val model: String,
    val markdownContent: String,
    val recommendation: TargetRecommendation?,
    val isApplied: Boolean = false,
    val comparison: WeeklyComparison? = null,
)

/**
 * 週報本地快取與儲存機制。
 *
 * 將每週週報存於 app 私有目錄 `filesDir/weekly_reports/${weekStartDate}.json`，
 * 避免重複呼叫 API 消耗額度，並支援離線隨時回顧歷史週報。
 */
class WeeklyReportStore(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val dir: File by lazy {
        File(context.filesDir, "weekly_reports").apply { if (!exists()) mkdirs() }
    }

    suspend fun getReport(weekStartDate: String): WeeklyReport? = withContext(Dispatchers.IO) {
        val file = File(dir, "$weekStartDate.json")
        if (!file.exists()) return@withContext null
        try {
            json.decodeFromString(WeeklyReport.serializer(), file.readText())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load report for $weekStartDate", e)
            null
        }
    }

    suspend fun saveReport(report: WeeklyReport): Unit = withContext(Dispatchers.IO) {
        try {
            val file = File(dir, "${report.weekStartDate}.json")
            file.writeText(json.encodeToString(WeeklyReport.serializer(), report))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save report for ${report.weekStartDate}", e)
        }
    }

    companion object {
        private const val TAG = "WeeklyReportStore"
    }
}

