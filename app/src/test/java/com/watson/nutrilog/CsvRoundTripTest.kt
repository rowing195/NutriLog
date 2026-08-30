package com.watson.nutrilog

import com.watson.nutrilog.data.CsvExport
import com.watson.nutrilog.data.CsvImport
import com.watson.nutrilog.data.db.EntrySource
import com.watson.nutrilog.data.db.FoodEntry
import com.watson.nutrilog.data.db.Meal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** 匯出與匯入是一對，所以放在同一支測試裡：任何一邊改了格式，來回就對不上。 */
class CsvRoundTripTest {

    private val zone: ZoneId = ZoneId.of("Asia/Taipei")

    // 2026-08-30 07:13:23 +08:00
    private val loggedAt = 1787008403_000L

    private fun entry(
        name: String = "雞肉飯",
        servingText: String = "1 碗 (250 g)",
        sugarG: Double? = 3.5,
        barcode: String? = null,
        portionMultiplier: Double = 1.0,
        loggedAt: Long = this.loggedAt,
    ) = FoodEntry(
        date = "2026-08-30",
        loggedAt = loggedAt,
        meal = Meal.LUNCH.name,
        name = name,
        servingText = servingText,
        calories = 630.0,
        proteinG = 30.8,
        fatG = 11.0,
        carbsG = 27.5,
        sugarG = sugarG,
        sodiumMg = null,
        fiberG = null,
        satFatG = null,
        source = EntrySource.BARCODE.name,
        barcode = barcode,
        portionMultiplier = portionMultiplier,
    )

    @Test
    fun `export then import round-trips every field`() {
        val original = entry(barcode = "4711234567890", portionMultiplier = 1.5)
        val parsed = CsvImport.parse(CsvExport.build(listOf(original), zone), zone)

        assertEquals(0, parsed.skipped)
        assertEquals(1, parsed.entries.size)
        // id 是資料庫給的，不在 CSV 裡，所以比對時對齊掉
        assertEquals(original, parsed.entries.single().copy(id = original.id))
    }

    @Test
    fun `missing extended nutrients stay null instead of becoming zero`() {
        val parsed = CsvImport.parse(CsvExport.build(listOf(entry(sugarG = null)), zone), zone)
        val back = parsed.entries.single()

        assertNull(back.sugarG)
        assertNull(back.sodiumMg)
        assertNull(back.fiberG)
        assertNull(back.satFatG)
    }

    @Test
    fun `fields with commas quotes and newlines survive the round trip`() {
        val original = entry(name = "手搖杯：\"大杯\", 半糖", servingText = "700ml\n（外帶）")
        val back = CsvImport.parse(CsvExport.build(listOf(original), zone), zone).entries.single()

        assertEquals(original.name, back.name)
        assertEquals(original.servingText, back.servingText)
    }

    @Test
    fun `same record exported twice collides on the dedupe key`() {
        val original = entry()
        val back = CsvImport.parse(CsvExport.build(listOf(original), zone), zone).entries.single()

        assertEquals(CsvImport.dedupeKey(original, zone), CsvImport.dedupeKey(back, zone))
    }

    @Test
    fun `two identical foods logged at different times are not duplicates`() {
        val first = entry()
        val second = entry(loggedAt = loggedAt + 60_000)

        assertTrue(CsvImport.dedupeKey(first, zone) != CsvImport.dedupeKey(second, zone))
    }

    /** 舊版匯出沒有「記錄時間」與「份數倍率」兩欄，還是要讀得回來。 */
    @Test
    fun `old export without the trailing columns still imports`() {
        val csv = """
            日期,餐別,食物名稱,份量,熱量(kcal),蛋白質(g),脂肪(g),碳水(g),糖(g),鈉(mg),膳食纖維(g),飽和脂肪(g),來源,條碼
            2026-08-30,午餐,雞肉飯,1 碗,630,30.8,11,27.5,,,,,手動,
            2026-08-30,晚餐,滷肉飯,1 碗,700,25,20,80,,,,,手動,
        """.trimIndent()

        val parsed = CsvImport.parse(csv, zone)

        assertEquals(0, parsed.skipped)
        assertEquals(2, parsed.entries.size)
        assertEquals(1.0, parsed.entries.first().portionMultiplier, 0.0)
        // 沒有時間欄時用當天午夜往後排，同一天的先後順序才不會亂掉
        assertTrue(parsed.entries[0].loggedAt < parsed.entries[1].loggedAt)
    }

    @Test
    fun `rows without a usable date or name are skipped instead of failing the file`() {
        val csv = """
            日期,餐別,食物名稱,熱量(kcal)
            2026-08-30,午餐,雞肉飯,630
            not-a-date,午餐,滷肉飯,700
            2026-08-31,午餐,,700
            2026-08-31,午餐,燙青菜,50
        """.trimIndent()

        val parsed = CsvImport.parse(csv, zone)

        assertEquals(2, parsed.skipped)
        assertEquals(listOf("雞肉飯", "燙青菜"), parsed.entries.map { it.name })
    }

    @Test
    fun `a file that is not a NutriLog export is rejected outright`() {
        assertThrows(CsvImport.NotNutriLogCsv::class.java) {
            CsvImport.parse("name,calories\nchicken,630", zone)
        }
    }
}
