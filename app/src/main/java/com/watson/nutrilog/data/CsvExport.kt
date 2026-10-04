package com.watson.nutrilog.data

import com.watson.nutrilog.data.db.DailyWater
import com.watson.nutrilog.data.db.FoodEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 把飲食紀錄轉成 CSV。
 *
 * 這是這個 app **唯一**能把資料帶出手機的路徑 —— 紀錄全部只在本地、
 * 沒有雲端備份，換手機或誤刪 app 就全沒了。所以匯出的定位是備份，
 * 預設就是全部匯出，不做日期篩選。
 *
 * 純函式、不碰 Android API：這樣格式對不對用眼睛看就知道，
 * 不必為了驗證跑一次完整的 UI 流程。
 *
 * **欄位名稱本身就是格式**：[CsvImport] 靠這些名字對應欄位（而不是靠位置），
 * 改名等於讓舊檔匯不回來。要加欄位就往後加，不要動既有的名字。
 */
object CsvExport {

    const val MIME_TYPE = "text/csv"

    fun fileName(today: LocalDate = LocalDate.now()): String = "nutrilog-$today.csv"

    const val COL_DATE = "日期"
    const val COL_MEAL = "餐別"
    const val COL_NAME = "食物名稱"
    const val COL_SERVING = "份量"
    const val COL_CALORIES = "熱量(kcal)"
    const val COL_PROTEIN = "蛋白質(g)"
    const val COL_FAT = "脂肪(g)"
    const val COL_CARBS = "碳水(g)"
    const val COL_SUGAR = "糖(g)"
    const val COL_SODIUM = "鈉(mg)"
    const val COL_FIBER = "膳食纖維(g)"
    const val COL_SATFAT = "飽和脂肪(g)"
    const val COL_SOURCE = "來源"
    const val COL_BARCODE = "條碼"
    const val COL_LOGGED_AT = "記錄時間"
    const val COL_MULTIPLIER = "份數倍率"
    const val COL_WATER = "水量(ml)"

    /**
     * 手動加減的飲水。**它以天為單位，不屬於任何一筆食物**，所以走自己的列：
     * 那一列只填日期與這一欄，食物名稱是空的（見 [buildWaterRow]）。
     *
     * 為什麼不塞進每一列：同一天會有很多列，每列都寫一次就得回答「以哪一列為準」，
     * 而且那天要是一筆食物都沒記（只喝水的日子），就完全沒有地方可以寫。
     */
    const val COL_MANUAL_WATER = "手動飲水(ml)"

    private val HEADERS = listOf(
        COL_DATE, COL_MEAL, COL_NAME, COL_SERVING,
        COL_CALORIES, COL_PROTEIN, COL_FAT, COL_CARBS,
        COL_SUGAR, COL_SODIUM, COL_FIBER, COL_SATFAT,
        COL_SOURCE, COL_BARCODE, COL_LOGGED_AT, COL_MULTIPLIER,
        COL_WATER, COL_MANUAL_WATER,
    )

    /**
     * 記錄時間寫成本地時間字串，不寫 epoch 毫秒。
     *
     * 一來使用者在試算表裡看得懂，二來匯入的去重是拿「格式化後的字串」比對，
     * 秒以下的精度在來回之間丟掉也不影響判斷 —— 反正同一秒內同名同份量的
     * 兩筆紀錄本來就不存在。
     */
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun formatLoggedAt(loggedAt: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(loggedAt).atZone(zone).format(TIME_FORMAT)

    fun parseLoggedAt(text: String, zone: ZoneId = ZoneId.systemDefault()): Long? =
        runCatching {
            java.time.LocalDateTime.parse(text.trim(), TIME_FORMAT).atZone(zone).toInstant().toEpochMilli()
        }.getOrNull()

    fun build(
        entries: List<FoodEntry>,
        zone: ZoneId = ZoneId.systemDefault(),
        /** 一天一筆的手動飲水。0 的那幾天不寫，沒必要為了一個 0 多一列。 */
        manualWater: List<DailyWater> = emptyList(),
    ): String = buildString {
        // Excel 看到 UTF-8 而沒有 BOM 時會用系統 ANSI 解讀，中文全變亂碼。
        // 這一個字元決定了檔案在 Excel 裡打得開還是一團垃圾。
        append('﻿')
        appendLine(HEADERS.joinToString(",") { escape(it) })

        entries.sortedWith(compareBy({ it.date }, { it.loggedAt })).forEach { entry ->
            appendLine(
                listOf(
                    entry.date,
                    mealLabel(entry.meal),
                    entry.name,
                    entry.servingText,
                    num(entry.calories),
                    num(entry.proteinG),
                    num(entry.fatG),
                    num(entry.carbsG),
                    // 延伸四項缺資料就留空白欄，不要補 0 ——
                    // 匯出到試算表之後更沒機會分辨「沒標示」和「真的是 0」
                    num(entry.sugarG),
                    num(entry.sodiumMg),
                    num(entry.fiberG),
                    num(entry.satFatG),
                    sourceLabel(entry.source),
                    entry.barcode.orEmpty(),
                    formatLoggedAt(entry.loggedAt, zone),
                    num(entry.portionMultiplier),
                    num(entry.waterMl),
                    "",
                ).joinToString(",") { escape(it) }
            )
        }

        // 手動飲水接在最後面，一天一列。匯入時靠「有日期、沒有食物名稱」認出來。
        manualWater.filter { it.manualMl != 0 }.sortedBy { it.date }.forEach { water ->
            appendLine(
                List(HEADERS.size) { i ->
                    when (i) {
                        HEADERS.indexOf(COL_DATE) -> water.date
                        HEADERS.indexOf(COL_MANUAL_WATER) -> water.manualMl.toString()
                        else -> ""
                    }
                }.joinToString(",") { escape(it) }
            )
        }
    }

    /**
     * 存的是多少就寫多少，不四捨五入。營養素格可以打 0.25 g、份數可以是 0.25 份；
     * 曾經一律寫一位小數（`%.1f`），0.25 匯出變 0.3，還原回來就跟當初記的不一樣了 ——
     * 份數錯了的話，之後再改份數連營養素都會換算錯。
     *
     * 不會寫出浮點尾巴（30.800000000000004）：進資料庫的數字不是使用者打的字串，
     * 就是縮放時已經收斂過的值（roundTo1、clampPortion）。
     */
    private fun num(value: Double?): String = when {
        value == null -> ""
        // 手改過的 CSV 寫「NaN」也會被匯入（toDoubleOrNull 認得），BigDecimal 接不住它。
        // 照字面寫出去，不讓整份匯出因為一格失敗。
        !value.isFinite() -> value.toString()
        else -> java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    }

    private fun mealLabel(raw: String): String = when (raw) {
        "BREAKFAST" -> "早餐"
        "LUNCH" -> "午餐"
        "DINNER" -> "晚餐"
        "SNACK" -> "點心"
        else -> raw
    }

    private fun sourceLabel(raw: String): String = when (raw) {
        "MANUAL" -> "手動"
        "PHOTO" -> "AI 辨識"
        "BARCODE" -> "條碼"
        else -> raw
    }

    /**
     * RFC 4180：含逗號、引號或換行的欄位要用雙引號包起來，內部的引號寫成兩個。
     * 食物名稱是使用者自己打的，這三種字元都可能出現。
     */
    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
