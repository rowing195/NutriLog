package com.watson.nutrilog.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 每日健康與運動指標快取。
 *
 * 儲存從 Samsung Health / Health Connect 讀取的每日手錶實測活動大卡、步數與運動專項大卡。
 * 存入本地資料庫能讓週報、月報與跨期對比秒開，且在離線時依然能完整呈現歷史數據。
 */
@Entity(tableName = "daily_health_metrics")
data class DailyHealthMetric(
    @PrimaryKey val date: String,          // "yyyy-MM-dd"
    val activeCalories: Double = 0.0,      // 每日手錶活動消耗大卡
    val steps: Long = 0L,                  // 每日去重總步數
    val workoutCalories: Double = 0.0,     // 跑步/健身等運動專項紀錄大卡
    val lastSyncedAt: Long = System.currentTimeMillis(), // 最後同步時間戳
)
