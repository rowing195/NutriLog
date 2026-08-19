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

    // 欄位別名必須和 DayTotal 的建構子參數同名，Room 靠名字對應
    @Query(
        """
        SELECT date,
               SUM(calories) AS kcal,
               SUM(proteinG) AS proteinG,
               SUM(fatG)     AS fatG,
               SUM(carbsG)   AS carbsG,
               COUNT(*)      AS itemCount
        FROM food_entries
        GROUP BY date
        ORDER BY date DESC
        LIMIT :limit
        """
    )
    fun observeDailyTotals(limit: Int): Flow<List<DayTotal>>

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
