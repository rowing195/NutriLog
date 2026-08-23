package com.watson.nutrilog.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一筆吃下去的東西。
 *
 * 幾個刻意的取捨：
 * - [date] 存 "2026-08-19" 這種本地日期字串而不是 timestamp。查詢一律是「某一天」，
 *   用字串當鍵就不必在每次查詢時做時區換算 —— 跨時區旅行時「今天」該是使用者當下的
 *   今天，而不是 UTC 的今天。
 * - [meal] / [source] 存 enum 的 name 而不是 enum 本身，這樣就不需要 TypeConverter，
 *   而且日後 enum 改名也不會讓舊資料變成無法解析的垃圾（解析不出來就退回預設值）。
 * - 延伸四項（糖／鈉／纖維／飽和脂肪）可為 null。資料來源常常就是沒有這些欄位，
 *   用 0.0 頂替會讓「沒資料」和「真的是 0」分不出來。
 */
@Entity(tableName = "food_entries", indices = [Index("date")])
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val loggedAt: Long,
    val meal: String,
    val name: String,
    val servingText: String = "",
    val calories: Double = 0.0,
    val proteinG: Double = 0.0,
    val fatG: Double = 0.0,
    val carbsG: Double = 0.0,
    val sugarG: Double? = null,
    val sodiumMg: Double? = null,
    val fiberG: Double? = null,
    val satFatG: Double? = null,
    val source: String = EntrySource.MANUAL.name,
    val barcode: String? = null,
    val portionMultiplier: Double = 1.0,
) {
    // 沒有 backing field 的 getter，Room 不會把它當成欄位
    val mealType: Meal get() = Meal.from(meal)
}

enum class Meal {
    BREAKFAST, LUNCH, DINNER, SNACK;

    companion object {
        /** 認不得就當早餐，總比讓整筆紀錄消失好 */
        fun from(raw: String): Meal = entries.firstOrNull { it.name == raw } ?: BREAKFAST
    }
}

/** 這筆是怎麼進來的。之後回頭看紀錄時，能分辨哪些數字是 AI 估的、哪些是標示上抄的。 */
enum class EntrySource { MANUAL, PHOTO, BARCODE }

/** 一天（或任一組紀錄）的營養素合計。 */
data class Totals(
    val calories: Double = 0.0,
    val proteinG: Double = 0.0,
    val fatG: Double = 0.0,
    val carbsG: Double = 0.0,
    val sugarG: Double = 0.0,
    val sodiumMg: Double = 0.0,
    val fiberG: Double = 0.0,
    val satFatG: Double = 0.0,
)

fun List<FoodEntry>.totals(): Totals = Totals(
    calories = sumOf { it.calories },
    proteinG = sumOf { it.proteinG },
    fatG = sumOf { it.fatG },
    carbsG = sumOf { it.carbsG },
    // 延伸項缺資料就當 0 加總。合計本來就只是參考值，
    // 為了少數幾筆沒標示就整個不顯示反而沒用。
    sugarG = sumOf { it.sugarG ?: 0.0 },
    sodiumMg = sumOf { it.sodiumMg ?: 0.0 },
    fiberG = sumOf { it.fiberG ?: 0.0 },
    satFatG = sumOf { it.satFatG ?: 0.0 },
)
