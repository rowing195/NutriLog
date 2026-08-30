package com.watson.nutrilog.data

import com.watson.nutrilog.data.db.EntrySource
import com.watson.nutrilog.data.db.FoodEntry
import com.watson.nutrilog.data.db.Meal
import java.time.LocalDate
import java.time.ZoneId

/**
 * 把 [CsvExport] 匯出的 CSV 讀回來。
 *
 * 這是這個 app 唯一能把資料帶回手機的路徑 —— 換手機或誤刪 app 之後，
 * 沒有它就只能對著試算表一筆一筆重打。
 *
 * 三個刻意的取捨：
 *
 * - **靠欄位名稱對應，不靠位置。** 使用者可能在試算表裡調過欄序、或者拿的是
 *   舊版匯出的檔（那時候還沒有「記錄時間」與「份數倍率」兩欄）。認名字就兩種
 *   都吃得下，缺的欄位用預設值補。
 * - **壞的那一列跳過，不是整份放棄。** 手改過的檔案很可能只有一兩列有問題，
 *   為了那一兩列讓整份匯不進來並不划算 —— 跳掉幾列會誠實回報。
 * - **缺資料一律 null，不補 0。** 和資料庫同一條規則：空白欄的意思是「沒標示」，
 *   補 0 會讓它變成「真的是 0」，而匯進去之後就再也分不出來了。
 *
 * 純函式、不碰 Android API，理由同 [CsvExport]。
 */
object CsvImport {

    /** 只有這兩欄是必要的：沒有它們就不是這個 app 的匯出檔，硬解也解不出東西。 */
    class NotNutriLogCsv : IllegalArgumentException("找不到「日期」與「食物名稱」欄位")

    data class Result(
        /** 解析成功的紀錄，維持檔案裡的順序。 */
        val entries: List<FoodEntry>,
        /** 認不得而跳過的資料列數（日期解析不出來、或沒有食物名稱）。 */
        val skipped: Int,
    )

    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): Result {
        val records = splitRecords(text.removePrefix("\uFEFF"))
        val header = records.firstOrNull() ?: throw NotNutriLogCsv()
        val index = header.withIndex().associate { (i, name) -> name.trim() to i }

        val dateAt = index[CsvExport.COL_DATE]
        val nameAt = index[CsvExport.COL_NAME]
        if (dateAt == null || nameAt == null) throw NotNutriLogCsv()

        val entries = mutableListOf<FoodEntry>()
        var skipped = 0

        records.drop(1).forEach { row ->
            // 試算表存檔常常在結尾留下整列空白，那不算「壞掉的列」，直接忽略
            if (row.all { it.isBlank() }) return@forEach

            fun cell(column: String): String = index[column]?.let { row.getOrNull(it) }?.trim().orEmpty()

            val date = runCatching { LocalDate.parse(cell(CsvExport.COL_DATE)) }.getOrNull()
            val name = cell(CsvExport.COL_NAME)
            if (date == null || name.isBlank()) {
                skipped++
                return@forEach
            }

            // 沒有時間欄的舊檔：用當天午夜加上流水號，同一天的先後順序才不會亂掉
            val loggedAt = CsvExport.parseLoggedAt(cell(CsvExport.COL_LOGGED_AT), zone)
                ?: (date.atStartOfDay(zone).toInstant().toEpochMilli() + entries.size)

            entries += FoodEntry(
                date = date.toString(),
                loggedAt = loggedAt,
                meal = mealOf(cell(CsvExport.COL_MEAL)).name,
                name = name,
                servingText = cell(CsvExport.COL_SERVING),
                calories = number(cell(CsvExport.COL_CALORIES)) ?: 0.0,
                proteinG = number(cell(CsvExport.COL_PROTEIN)) ?: 0.0,
                fatG = number(cell(CsvExport.COL_FAT)) ?: 0.0,
                carbsG = number(cell(CsvExport.COL_CARBS)) ?: 0.0,
                sugarG = number(cell(CsvExport.COL_SUGAR)),
                sodiumMg = number(cell(CsvExport.COL_SODIUM)),
                fiberG = number(cell(CsvExport.COL_FIBER)),
                satFatG = number(cell(CsvExport.COL_SATFAT)),
                source = sourceOf(cell(CsvExport.COL_SOURCE)).name,
                barcode = cell(CsvExport.COL_BARCODE).ifBlank { null },
                portionMultiplier = number(cell(CsvExport.COL_MULTIPLIER))?.takeIf { it > 0 } ?: 1.0,
            )
        }

        return Result(entries, skipped)
    }

    /**
     * 去重的鍵。
     *
     * 同一天吃兩份一模一樣的東西是完全合理的，所以不能只看「日期＋名稱＋份量」——
     * 那會把使用者真實記過兩次的東西吃掉一筆。加上記錄時間之後，只有「同一筆紀錄
     * 被匯出又匯回來」才會撞在一起。
     *
     * 比的是格式化後的時間字串而不是 epoch 毫秒：CSV 只寫到秒，直接比毫秒的話
     * 匯出再匯入永遠對不上，去重等於沒做。
     */
    fun dedupeKey(entry: FoodEntry, zone: ZoneId = ZoneId.systemDefault()): String =
        listOf(
            entry.date,
            entry.name,
            entry.servingText,
            CsvExport.formatLoggedAt(entry.loggedAt, zone),
        ).joinToString("|")

    /** 標籤對回 enum。認不得就退回預設值，和 [Meal.from] 同樣的理由：總比讓整筆消失好。 */
    private fun mealOf(label: String): Meal = when (label.trim()) {
        "早餐" -> Meal.BREAKFAST
        "午餐" -> Meal.LUNCH
        "晚餐" -> Meal.DINNER
        "點心" -> Meal.SNACK
        else -> Meal.entries.firstOrNull { it.name == label.trim() } ?: Meal.BREAKFAST
    }

    private fun sourceOf(label: String): EntrySource = when (label.trim()) {
        "手動" -> EntrySource.MANUAL
        "AI 辨識" -> EntrySource.PHOTO
        "條碼" -> EntrySource.BARCODE
        else -> EntrySource.entries.firstOrNull { it.name == label.trim() } ?: EntrySource.MANUAL
    }

    /** 空白 → null（沒標示），不是 0。試算表可能留下千分位逗號與空白。 */
    private fun number(raw: String): Double? =
        raw.trim().replace(",", "").ifBlank { null }?.toDoubleOrNull()

    /**
     * RFC 4180 的切法：欄位可以用雙引號包起來，裡面的引號寫成兩個，
     * 而且**被引號包住的欄位裡可以有換行** —— 所以不能先 split("\n") 再切逗號，
     * 食物名稱裡只要有一個換行就會把一筆紀錄拆成兩列。
     */
    private fun splitRecords(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0

        fun endField() {
            row.add(field.toString())
            field.setLength(0)
        }

        fun endRow() {
            endField()
            records.add(row)
            row = mutableListOf()
        }

        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> endField()
                !quoted && (c == '\n' || c == '\r') -> {
                    endRow()
                    // CRLF 是一個換行，不是兩個
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()

        return records
    }
}
