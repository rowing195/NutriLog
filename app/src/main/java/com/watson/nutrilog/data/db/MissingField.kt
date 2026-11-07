package com.watson.nutrilog.data.db

/**
 * 資料健檢的一列：同一個品項（名稱 + 份量文字，和 [FoodSuggestion] 同一個分法）底下
 * 有幾筆缺了某個欄位。查詢結果的容器，不是資料表。
 */
data class MissingField(
    val name: String,
    val servingText: String,
    /** 這個品項有幾筆是 null。 */
    val missing: Int,
    /** 同一個品項別筆記過的值（最後一次那筆）。有的話先帶進輸入框。 */
    val known: Double?,
    val lastLoggedAt: Long,
)

/**
 * 份量文字裡寫的容量（ml），例如「大杯 700ml」「1 瓶 (600 mL)」「350cc」。沒寫就是 null。
 *
 * 缺水量的清單只列看得出是飲料的品項，判斷依據就是這個 —— 份量沒寫容量的
 * 東西（「1 碗」）可能是湯也可能是飯，列進來只會讓使用者一筆一筆去想要不要填。
 * 不認「L」：飲料店的大杯就叫 L，「L 杯」會被誤讀成一公升。
 */
fun drinkVolumeMl(servingText: String): Double? =
    DrinkVolume.find(servingText)?.groupValues?.get(1)?.toDoubleOrNull()

private val DrinkVolume = Regex("""(\d+(?:\.\d+)?)\s*(?:ml|毫升|cc)""", RegexOption.IGNORE_CASE)
