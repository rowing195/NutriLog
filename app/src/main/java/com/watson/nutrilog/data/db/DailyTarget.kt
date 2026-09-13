package com.watson.nutrilog.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 那一天當時的四格目標。
 *
 * **過去的日子要用過去的標準判斷。** 不存這一列的話，改一次熱量目標會把
 * 以前每一天都重新判一次 —— 上個月明明都在目標內的日子會集體變紅（或反過來
 * 集體變綠），而使用者那幾天實際吃了多少一次都沒變。
 *
 * 寫入的規則只有兩條（見 `NutriViewModel.rememberTodayTarget`）：
 *
 * - **今天**：每次設定變動與每次開 app 都覆寫 —— 今天是活的，
 *   剛改完目標就要馬上看得到效果。
 * - **過去**：碰都不碰。
 *
 * 沒有那一列的日子（這個功能之前的舊紀錄、或 app 根本沒開過的那幾天）
 * 退回目前的設定，行為和以前一模一樣。
 */
@Entity(tableName = "daily_targets")
data class DailyTarget(
    @PrimaryKey val date: String,
    val calorieTarget: Int,
    val proteinTargetG: Int,
    val fatTargetG: Int,
    val carbsTargetG: Int,
)
