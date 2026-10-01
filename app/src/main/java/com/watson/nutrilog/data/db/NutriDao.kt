package com.watson.nutrilog.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** 歷史清單用：一天一列的合計，不必把整年的明細撈進記憶體再自己加。 */
data class DayTotal(
    val date: String,
    val kcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
    val itemCount: Int,
)

@Dao
interface NutriDao {

    @Query("SELECT * FROM food_entries WHERE date = :date ORDER BY loggedAt")
    fun observeDay(date: String): Flow<List<FoodEntry>>

    @Query("SELECT * FROM food_entries WHERE id = :id")
    suspend fun findEntry(id: Long): FoodEntry?

    /** 匯出用。一次全撈是刻意的：匯出的定位是備份，篩一半的備份沒有意義。 */
    @Query("SELECT * FROM food_entries ORDER BY date, loggedAt")
    suspend fun allEntries(): List<FoodEntry>

    /**
     * 搜尋用的粗篩：先用第一個關鍵字把範圍縮小，其餘關鍵字交給 Kotlin 過濾。
     *
     * 多關鍵字 AND 用靜態 SQL 寫不出來（關鍵字數量不固定），而動態拼 SQL
     * 要自己處理跳脫，風險不成比例 —— 這裡的資料量是幾千筆，
     * 在記憶體裡過濾是微秒等級的事。
     *
     * **不設筆數上限。** 曾經是 LIMIT 300，結果第一個關鍵字一常見（「飯」、「珍奶 大杯」
     * 的「珍奶」），幾年前的紀錄就被最近那 300 筆擠出去 —— 而且是在 AND 過濾**之前**
     * 截斷的，第二個關鍵字明明對得上也救不回來。
     *
     * 沒有為 name 建索引：全表掃 LIKE 在這個量級只要毫秒，
     * 為它動 schema 就得升 version 加 migration，不划算。
     */
    @Query(
        """
        SELECT * FROM food_entries
        WHERE name LIKE '%' || :token || '%' OR servingText LIKE '%' || :token || '%'
        ORDER BY date DESC, loggedAt DESC
        """
    )
    fun searchEntries(token: String): Flow<List<FoodEntry>>

    /**
     * 常吃的品項：最近 [since] 之後，依出現次數排序。
     *
     * 以「名稱 + 份量文字」分組，不是只看名稱 —— 份量文字正是規格所在
     * （大杯／中杯／半糖），而它是文字、不會像 AI 每次估的數字那樣抖動。
     *
     * **營養素那幾個裸欄位取的是最後一次吃的那一筆。** 這靠的是 SQLite 的
     * 一個明文保證：當聚合查詢裡剛好有一個 max() 或 min() 時，
     * 同一列的裸欄位會取自那個極值所在的資料列。少了 MAX(loggedAt)，
     * 裸欄位就變成隨便挑一筆，數值會不可預測。
     */
    @Query(
        """
        SELECT name, servingText,
               calories, proteinG, fatG, carbsG, sugarG, sodiumMg, fiberG, satFatG, waterMl,
               COUNT(*)        AS times,
               date            AS lastDate,
               MAX(loggedAt)   AS lastLoggedAt
        FROM food_entries
        WHERE date >= :since
        GROUP BY name, servingText
        ORDER BY times DESC, lastLoggedAt DESC
        LIMIT :limit
        """
    )
    fun observeFrequentFoods(since: String, limit: Int): Flow<List<FoodSuggestion>>

    /**
     * 吃過的**所有**品項，依最後一次的時間排序。同樣的分組方式，不限期間也不限筆數。
     *
     * 「最近」分頁取它的前幾筆；常吃頁打字搜尋時則篩整份 —— 只篩前幾筆的話，
     * 吃過的品項一多，舊的就再也搜不到（見 [com.watson.nutrilog.ui.libraryLists]）。
     */
    @Query(
        """
        SELECT name, servingText,
               calories, proteinG, fatG, carbsG, sugarG, sodiumMg, fiberG, satFatG, waterMl,
               COUNT(*)        AS times,
               date            AS lastDate,
               MAX(loggedAt)   AS lastLoggedAt
        FROM food_entries
        GROUP BY name, servingText
        ORDER BY lastLoggedAt DESC
        """
    )
    fun observeAllFoods(): Flow<List<FoodSuggestion>>

    /**
     * 某段日期區間的每日合計，月曆用。
     *
     * date 存的是 ISO "yyyy-MM-dd"，字串比大小的結果和日期先後一致，
     * 所以 BETWEEN 直接比字串就對了，不必為了範圍查詢另存 timestamp。
     *
     * 欄位別名必須和 DayTotal 的建構子參數同名，Room 靠名字對應。
     */
    @Query(
        """
        SELECT date,
               SUM(calories) AS kcal,
               SUM(proteinG) AS proteinG,
               SUM(fatG)     AS fatG,
               SUM(carbsG)   AS carbsG,
               COUNT(*)      AS itemCount
        FROM food_entries
        WHERE date BETWEEN :from AND :to
        GROUP BY date
        """
    )
    fun observeRange(from: String, to: String): Flow<List<DayTotal>>

    /** 週報用：查詢某區間所有飲食明細，以利統計餐別分佈與微量營養素 */
    @Query("SELECT * FROM food_entries WHERE date BETWEEN :from AND :to ORDER BY date, loggedAt")
    suspend fun getEntriesInRange(from: String, to: String): List<FoodEntry>

    /** 新增回傳 rowId、更新回傳原 id，呼叫端不必分辨是哪一種 */
    @Upsert
    suspend fun upsert(entry: FoodEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    // 回傳新列的 id：寫進健康連線要用它當 clientRecordId。拿插入前的物件（id 還是 0）去同步的話，
    // 一整批會全部叫 nutrilog_0、在健康連線裡互相覆蓋成只剩一筆。
    suspend fun insertAll(entries: List<FoodEntry>): List<Long>

    @Delete
    suspend fun delete(entry: FoodEntry)

    @Query("SELECT * FROM cached_products WHERE barcode = :barcode")
    suspend fun findProduct(barcode: String): CachedProduct?

    @Upsert
    suspend fun cacheProduct(product: CachedProduct)

    // --- 每日運動與健康指標快取 ---

    @Upsert
    suspend fun upsertHealthMetric(metric: DailyHealthMetric)

    @Upsert
    suspend fun upsertHealthMetrics(metrics: List<DailyHealthMetric>)

    @Query("SELECT * FROM daily_health_metrics WHERE date = :date")
    suspend fun getHealthMetric(date: String): DailyHealthMetric?

    @Query("SELECT * FROM daily_health_metrics WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getHealthMetricsInRange(from: String, to: String): List<DailyHealthMetric>

    @Query("SELECT * FROM daily_health_metrics")
    suspend fun getAllHealthMetrics(): List<DailyHealthMetric>

    // --- 每日目標快照 ---

    @Upsert
    suspend fun upsertDailyTarget(target: DailyTarget)

    /**
     * 一次訂全部。一天一列、五個欄位，十年也才三千多列 ——
     * 為了它再養一條跟著可見範圍跑的 Flow 不劃算。
     */
    @Query("SELECT * FROM daily_targets")
    fun observeDailyTargets(): Flow<List<DailyTarget>>

    // --- 飲水 ---

    @Upsert
    suspend fun upsertDailyWater(water: DailyWater)

    /** 一天一列、五個欄位，一次訂全部比跟著可見範圍查便宜，同 [observeDailyTargets]。 */
    @Query("SELECT * FROM daily_water")
    fun observeDailyWater(): Flow<List<DailyWater>>

    /** 匯出用。 */
    @Query("SELECT * FROM daily_water ORDER BY date")
    suspend fun allDailyWater(): List<DailyWater>

    /** 那一天食物帶進來的水。手動值要減到剛好讓總量歸零就停，所以需要這個。 */
    @Query("SELECT SUM(waterMl) FROM food_entries WHERE date = :date")
    suspend fun waterFromFoodOn(date: String): Double?

    @Query("SELECT * FROM daily_water WHERE date = :date")
    suspend fun dailyWaterOn(date: String): DailyWater?

    @Transaction
    suspend fun adjustWater(date: String, deltaMl: Int) {
        val floor = -Math.round(waterFromFoodOn(date) ?: 0.0).toInt()
        val next = ((dailyWaterOn(date)?.manualMl ?: 0) + deltaMl).coerceAtLeast(floor)
        upsertDailyWater(DailyWater(date, next))
    }

    // 保留歸零的日期，刪掉最後一筆飲料後仍能在下次同步清除健康連線的舊水量。
    @Query("INSERT OR IGNORE INTO daily_water (date, manualMl) VALUES (:date, 0)")
    suspend fun retainWaterDate(date: String)

    @Query("SELECT date FROM daily_water UNION SELECT date FROM food_entries WHERE waterMl > 0")
    suspend fun allWaterDates(): List<String>

    @Query("SELECT COALESCE((SELECT SUM(waterMl) FROM food_entries WHERE date = :date), 0.0) + COALESCE((SELECT manualMl FROM daily_water WHERE date = :date), 0)")
    suspend fun totalWaterOn(date: String): Double
}
