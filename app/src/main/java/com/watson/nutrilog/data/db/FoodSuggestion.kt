package com.watson.nutrilog.data.db

/**
 * 個人食物庫的一列 —— 從既有紀錄聚合出來的品項，不是資料表。
 *
 * 「名稱 + 份量文字」相同的紀錄算同一個品項，營養素取**最後一次**吃的數值。
 * 這是這個 app 唯一不用打字也不用花 API 額度就能記一筆的來源：
 * 你自己吃過的東西本來就是最準的資料。
 *
 * 沒有對應的 Entity，只是查詢結果的容器，所以不需要動 schema。
 */
data class FoodSuggestion(
    val name: String,
    val servingText: String,
    val calories: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
    val sugarG: Double?,
    val sodiumMg: Double?,
    val fiberG: Double?,
    val satFatG: Double?,
    /** 統計期間內出現幾次 */
    val times: Int,
    /** 最後一次吃的日期，"yyyy-MM-dd" */
    val lastDate: String,
    val lastLoggedAt: Long,
)
