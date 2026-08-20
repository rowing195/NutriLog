package com.watson.nutrilog.data

import com.watson.nutrilog.data.db.FoodEntry
import java.time.LocalDate

/**
 * 把飲食紀錄轉成 CSV。
 *
 * 這是這個 app **唯一**能把資料帶出手機的路徑 —— 紀錄全部只在本地、
 * 沒有雲端備份，換手機或誤刪 app 就全沒了。所以匯出的定位是備份，
 * 預設就是全部匯出，不做日期篩選。
 *
 * 純函式、不碰 Android API：這樣格式對不對用眼睛看就知道，
 * 不必為了驗證跑一次完整的 UI 流程。
 */
object CsvExport {

    const val MIME_TYPE = "text/csv"

    fun fileName(today: LocalDate = LocalDate.now()): String = "nutrilog-$today.csv"

    private val HEADERS = listOf(
        "日期", "餐別", "食物名稱", "份量",
        "熱量(kcal)", "蛋白質(g)", "脂肪(g)", "碳水(g)",
        "糖(g)", "鈉(mg)", "膳食纖維(g)", "飽和脂肪(g)",
        "來源", "條碼",
    )

    fun build(entries: List<FoodEntry>): String = buildString {
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
