package com.watson.nutrilog.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.watson.nutrilog.data.HealthConnectSync
import com.watson.nutrilog.data.SettingsStore
import com.watson.nutrilog.data.WeeklyAggregator
import com.watson.nutrilog.data.WeeklyReport
import com.watson.nutrilog.data.WeeklyReportStore
import com.watson.nutrilog.data.db.NutriDatabase
import com.watson.nutrilog.data.net.NvidiaClient
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * 每週日自動在背景為使用者結算當週健康週報與卡路里推薦。
 * 若當週先前曾手動生成過，週日也會再次自動重新跑一次覆蓋，納入完整 7 天最新數據。
 */
class SundayReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settingsStore = SettingsStore(applicationContext)
        val settings = settingsStore.current()
        if (settings.nvidiaApiKey.isBlank()) {
            return Result.success()
        }

        val today = LocalDate.now()
        // 週日起算（台灣日曆慣例：星期日開始）
        val weekStart = today.minusDays((today.dayOfWeek.value % 7).toLong())

        val dao = NutriDatabase.get(applicationContext).dao()
        val healthSync = HealthConnectSync(applicationContext)
        val aggregator = WeeklyAggregator()
        val client = NvidiaClient()
        val reportStore = WeeklyReportStore(applicationContext)

        try {
            val stats = aggregator.aggregateWeek(weekStart, dao, healthSync, settings)
            if (stats.loggedDaysCount == 0 && stats.totalActiveCaloriesBurned == 0.0) {
                return Result.success()
            }

            val (sysPrompt, userPrompt) = aggregator.buildPrompts(stats, settings)
            val targetModel = if (settings.nvidiaModel.isNotBlank() && settings.nvidiaModel != "deepseek-ai/deepseek-v4-pro-0813") {
                settings.nvidiaModel
            } else {
                NvidiaClient.DEFAULT_MODEL
            }
            val responseResult = client.generateWeeklyReport(
                apiKey = settings.nvidiaApiKey,
                model = targetModel,
                systemPrompt = sysPrompt,
                userPrompt = userPrompt,
            )

            responseResult.fold(
                onSuccess = { rawText ->
                    val (markdown, recommendation) = aggregator.parseReportResponse(rawText)
                    val report = WeeklyReport(
                        weekStartDate = stats.weekStart.toString(),
                        weekEndDate = stats.weekEnd.toString(),
                        generatedAt = System.currentTimeMillis(),
                        model = targetModel,
                        markdownContent = markdown,
                        recommendation = recommendation,
                        isApplied = false,
                        comparison = stats.comparison,
                    )
                    reportStore.saveReport(report)
                    Log.i(TAG, "週日自動結算週報成功：${report.weekStartDate}")
                },
                onFailure = { error ->
                    Log.w(TAG, "週日自動結算週報失敗，稍後重試", error)
                    return Result.retry()
                }
            )
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "週日結算異常", e)
            return Result.retry()
        }
    }

    companion object {
        private const val TAG = "SundayReportWorker"
        private const val WORK_NAME = "sunday-weekly-report"

        /**
         * 計算到下一個週日晚間 21:30 的延遲時間（分鐘）
         */
        internal fun minutesUntilNextSundayEvening(now: LocalDateTime = LocalDateTime.now()): Long {
            var target = now.withHour(21).withMinute(30).withSecond(0)
            while (target.dayOfWeek != DayOfWeek.SUNDAY || target.isBefore(now)) {
                target = target.plusDays(1)
            }
            return Duration.between(now, target).toMinutes().coerceAtLeast(0)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SundayReportWorker>(7, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setInitialDelay(minutesUntilNextSundayEvening(), TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
