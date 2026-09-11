package com.watson.nutrilog.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 跨月對比數據指標（與上個月之差值）。
 */
@Serializable
data class MonthlyComparison(
    val deltaCaloriesInPerDay: Double = 0.0,
    val deltaActiveBurnPerDay: Double = 0.0,
    val deltaDailyTdee: Double = 0.0,
    val deltaFatChangeKg: Double = 0.0,
    val deltaProteinG: Double = 0.0,
)

/**
 * 整月飲食與運動客觀數據統計。
 */
@Serializable
data class MonthlyStats(
    val yearMonth: String, // "yyyy-MM"
    val totalCaloriesConsumed: Double,
    val avgDailyCaloriesConsumed: Double,
    val totalActiveCaloriesBurned: Double,
    val avgDailyActiveBurned: Double,
    val estimatedBmrPerDay: Double,
    val avgDailyTdee: Double,
    val netEnergyBalance: Double,
    val estimatedFatChangeKg: Double,
    val avgProteinG: Double,
    val avgFatG: Double,
    val avgCarbsG: Double,
    val loggedDaysCount: Int,
    val daysInMonth: Int,
    val comparison: MonthlyComparison? = null,
)

/**
 * 已生成之每月份健康覆盤月報物件。
 */
@Serializable
data class MonthlyReport(
    val yearMonth: String,     // "yyyy-MM"
    val generatedAt: Long,     // epoch millis
    val model: String,
    val markdownContent: String,
    val comparison: MonthlyComparison? = null,
)

/**
 * 月報本地快取與儲存機制。
 *
 * 將每月月報存於 app 私有目錄 `filesDir/monthly_reports/${yearMonth}.json`。
 */
class MonthlyReportStore(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val dir: File by lazy {
        File(context.filesDir, "monthly_reports").apply { if (!exists()) mkdirs() }
    }

    suspend fun getReport(yearMonth: String): MonthlyReport? = withContext(Dispatchers.IO) {
        val file = File(dir, "$yearMonth.json")
        if (!file.exists()) return@withContext null
        try {
            json.decodeFromString(MonthlyReport.serializer(), file.readText())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load monthly report for $yearMonth", e)
            null
        }
    }

    suspend fun saveReport(report: MonthlyReport): Unit = withContext(Dispatchers.IO) {
        try {
            val file = File(dir, "${report.yearMonth}.json")
            file.writeText(json.encodeToString(MonthlyReport.serializer(), report))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save monthly report for ${report.yearMonth}", e)
        }
    }

    companion object {
        private const val TAG = "MonthlyReportStore"
    }
}
