package com.watson.nutrilog.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 查過的條碼商品，**每 100 g** 的營養值。
 *
 * 存在的理由有兩個：一是 Open Food Facts 限制每個 IP 每分鐘 15 次查詢，
 * 二是常吃的東西會一直重複掃到 —— 查過一次之後就算完全沒網路也帶得出來。
 *
 * 所有營養欄位都可為 null：OFF 的資料是群眾貢獻的，缺欄位是常態而不是例外。
 */
@Entity(tableName = "cached_products")
data class CachedProduct(
    @PrimaryKey val barcode: String,
    val name: String,
    val brand: String? = null,
    /** OFF 上的建議份量文字，例如 "15 g"。解析得出數字就拿來當預設份量。 */
    val servingSizeText: String? = null,
    val caloriesPer100g: Double? = null,
    val proteinPer100g: Double? = null,
    val fatPer100g: Double? = null,
    val carbsPer100g: Double? = null,
    val sugarPer100g: Double? = null,
    val sodiumMgPer100g: Double? = null,
    val fiberPer100g: Double? = null,
    val satFatPer100g: Double? = null,
    val fetchedAt: Long,
)
