package com.watson.nutrilog

import com.watson.nutrilog.data.net.AiPrompts
import com.watson.nutrilog.ui.detailLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 鈉是唯一「查不到也要估」的營養素：原本四個進階營養素共用「沒把握就填 null」，
 * 結果搜尋結果沒列鈉時模型一律留空。這裡守住 prompt 的規則，以及估出來的鈉在確認畫面上看得到。
 */
class SodiumPromptTest {

    @Test fun bothPromptsAskToEstimateSodiumInsteadOfLeavingItNull() {
        for (prompt in listOf(AiPrompts.TEXT_PROMPT, AiPrompts.PHOTO_PROMPT)) {
            assertTrue(prompt.contains("鈉查不到官方數字時，依同類食物的常見值估一個數字"))
            assertTrue(prompt.contains("填完之後逐項再看一次：鈉是不是 null"))
            // 舊的那條把鈉也包進「沒把握就 null」，不能回來
            assertFalse(prompt.contains("沒把握的營養素就填 null"))
        }
    }

    @Test fun searchResultsComeWithTheMissingSodiumHint() {
        val withSearch = AiPrompts.textRequest("大麥克", "台灣麥當勞 大麥克 熱量 503 kcal")
        val withoutSearch = AiPrompts.textRequest("大麥克", null)

        assertTrue(withSearch.contains("那是資料沒寫，不是這個食物沒有鈉"))
        assertFalse(withoutSearch.contains("那是資料沒寫，不是這個食物沒有鈉"))
    }

    @Test fun reviewLineShowsEstimatedSodiumBeforeWater() {
        assertEquals(
            "大杯 700ml 全糖 · 蛋白 3 · 脂肪 8.5 · 碳水 90 · 鈉 820 mg · 水 700 ml",
            detailLine("大杯 700ml 全糖", 3.0, 8.5, 90.0, waterMl = 700.0, sodiumMg = 820.0),
        )
    }

    /** 今日頁的紀錄列不傳鈉，長相不變。 */
    @Test fun sodiumIsOptionalInTheLine() {
        assertEquals("1 碗 · 蛋白 10 · 脂肪 5 · 碳水 60", detailLine("1 碗", 10.0, 5.0, 60.0))
    }
}
