package com.watson.nutrilog.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
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
     * 縮到 300 筆之後在記憶體裡過濾是微秒等級的事。
     *
     * 沒有為 name 建索引：全表掃 LIKE 在這個量級只要毫秒，
     * 為它動 schema 就得升 version 加 migration，不划算。
     */
    @Query(
        """
        SELECT * FROM food_entries
        WHERE name LIKE '%' || :token || '%' OR servingText LIKE '%' || :token || '%'
        ORDER BY date DESC, loggedAt DESC
        LIMIT 300
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
               calories, proteinG, fatG, carbsG, sugarG, sodiumMg, fiberG, satFatG,
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

    /** 最近吃過的品項。同樣的分組方式，但改依最後一次的時間排序，且不限期間。 */
    @Query(
        """
        SELECT name, servingText,
               calories, proteinG, fatG, carbsG, sugarG, sodiumMg, fiberG, satFatG,
               COUNT(*)        AS times,
               date            AS lastDate,
               MAX(loggedAt)   AS lastLoggedAt
        FROM food_entries
        GROUP BY name, servingText
        ORDER BY lastLoggedAt DESC
        LIMIT :limit
        """
    )
    fun observeRecentFoods(limit: Int): Flow<List<FoodSuggestion>>

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

    /** 新增回傳 rowId、更新回傳原 id，呼叫端不必分辨是哪一種 */
    @Upsert
    suspend fun upsert(entry: FoodEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<FoodEntry>)

    @Delete
    suspend fun delete(entry: FoodEntry)

    @Query("SELECT * FROM cached_products WHERE barcode = :barcode")
    suspend fun findProduct(barcode: String): CachedProduct?

    @Upsert
    suspend fun cacheProduct(product: CachedProduct)
}
