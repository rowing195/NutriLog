package com.watson.nutrilog

import com.watson.nutrilog.data.CsvExport
import com.watson.nutrilog.data.CsvImport
import com.watson.nutrilog.data.db.DailyWater
import com.watson.nutrilog.data.db.EntrySource
import com.watson.nutrilog.data.db.FoodEntry
import com.watson.nutrilog.data.db.Meal
import com.watson.nutrilog.data.db.totals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * 飲水有兩個來源：飲料自己帶的水（每一筆紀錄的欄位）與手動加減的（一天一列）。
 *
 * 這支測試釘的是**它們在 CSV 裡來回一趟還是分得開** —— 混在一起的症狀是還原之後
 * 紀錄清單長出一堆沒有名字的空紀錄，或者手動那段整個消失。
 */
class WaterCsvTest {

    private val zone: ZoneId = ZoneId.of("Asia/Taipei")
    private val loggedAt = 1787008403_000L

    private fun drink(waterMl: Double?) = FoodEntry(
        date = "2026-09-20",
        loggedAt = loggedAt,
        meal = Meal.SNACK.name,
        name = "珍珠奶茶",
        servingText = "大杯 700ml",
        calories = 520.0,
        proteinG = 8.0,
        fatG = 12.0,
        carbsG = 95.0,
        waterMl = waterMl,
        source = EntrySource.MANUAL.name,
    )

    @Test
    fun `飲料的水量來回一趟不會變`() {
        val csv = CsvExport.build(listOf(drink(700.0)), zone)
        val parsed = CsvImport.parse(csv, zone)
        assertEquals(1, parsed.entries.size)
        assertEquals(700.0, parsed.entries.single().waterMl!!, 0.01)
    }

    /** 固體食物的空白欄要維持 null。補 0 的話「沒標示」和「真的沒有水」就分不出來。 */
    @Test
    fun `沒有水量的食物匯回來還是 null`() {
        val parsed = CsvImport.parse(CsvExport.build(listOf(drink(null)), zone), zone)
        assertNull(parsed.entries.single().waterMl)
    }

    @Test
    fun `手動飲水走自己的列，不會變成一筆食物`() {
        val csv = CsvExport.build(
            listOf(drink(700.0)),
            zone,
            manualWater = listOf(DailyWater("2026-09-20", 500), DailyWater("2026-09-19", -50)),
        )
        val parsed = CsvImport.parse(csv, zone)

        // 只有那杯珍奶是紀錄，手動飲水不混進來
        assertEquals(1, parsed.entries.size)
        assertEquals(mapOf("2026-09-20" to 500, "2026-09-19" to -50), parsed.manualWater)
        // 沒有食物名稱的那兩列是刻意的，不能被算成「壞掉的列」
        assertEquals(0, parsed.skipped)
    }

    /** 那一天一筆食物都沒記、只按了加減鍵，也要留得下來。 */
    @Test
    fun `只喝水沒吃東西的日子也保得住`() {
        val csv = CsvExport.build(emptyList(), zone, manualWater = listOf(DailyWater("2026-09-18", 1200)))
        val parsed = CsvImport.parse(csv, zone)
        assertTrue(parsed.entries.isEmpty())
        assertEquals(mapOf("2026-09-18" to 1200), parsed.manualWater)
    }

    /** 0 的那幾天不寫進檔案：為了一個 0 多一列沒有意義。 */
    @Test
    fun `手動飲水是 0 的日子不寫進 CSV`() {
        val csv = CsvExport.build(emptyList(), zone, manualWater = listOf(DailyWater("2026-09-17", 0)))
        assertEquals(emptyMap<String, Int>(), CsvImport.parse(csv, zone).manualWater)
    }

    /** 合計要把缺資料當 0 加，但那是合計自己的事，欄位本身仍然是 null。 */
    @Test
    fun `合計只加有水量的那幾筆`() {
        val totals = listOf(drink(700.0), drink(null), drink(250.0)).totals()
        assertEquals(950.0, totals.waterMl, 0.01)
    }
}
