package com.watson.nutrilog.data

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

    private val HEADERS = listOf(
        COL_DATE, COL_MEAL, COL_NAME, COL_SERVING,
        COL_CALORIES, COL_PROTEIN, COL_FAT, COL_CARBS,
        COL_SUGAR, COL_SODIUM, COL_FIBER, COL_SATFAT,
        COL_SOURCE, COL_BARCODE, COL_LOGGED_AT, COL_MULTIPLIER,
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

    fun build(entries: List<FoodEntry>, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
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
                ).joinToString(",") { escape(it) }
            )
        }
    }

    private fun num(value: Double?): String = when {
        value == null -> ""
        value % 1.0 == 0.0 -> value.toLong().toString()
        else -> String.format(java.util.Locale.US, "%.1f", value)
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
