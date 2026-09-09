package com.watson.nutrilog

import com.watson.nutrilog.data.db.FoodSuggestion
import com.watson.nutrilog.ui.filterByQuery
import com.watson.nutrilog.ui.matchScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 常吃頁那個搜尋框的比對規則。
 *
 * 守的是一件事：**同一個框同時要篩自己的清單、又要把描述交給 AI**，
 * 而使用者為了讓 AI 估得準會打得很細。整串比對的話「手沖藝妓黑咖啡」找不到
 * 庫裡的「手沖黑咖啡」，畫面就會在明明有相近品項時叫他去問 AI ——
 * 那是這個設計唯一真正會壞掉的地方，所以用測試釘住。
 */
class FoodLibraryMatchTest {

    private fun food(name: String, serving: String = "") = FoodSuggestion(
        name = name,
        servingText = serving,
        calories = 0.0,
        proteinG = 0.0,
        fatG = 0.0,
        carbsG = 0.0,
        sugarG = null,
        sodiumMg = null,
        fiberG = null,
        satFatG = null,
        times = 1,
        lastDate = "2026-09-09",
        lastLoggedAt = 0L,
    )

    private val blackCoffee = food("手沖黑咖啡")

    @Test
    fun `describing it in more detail than the library still finds it`() {
        assertTrue(blackCoffee.matchScore("手沖藝妓黑咖啡") >= 0.3)
    }

    @Test
    fun `a different name for the same kind of thing still finds it`() {
        assertTrue(blackCoffee.matchScore("美式黑咖啡") >= 0.3)
    }

    /**
     * 「手沖黑咖啡」對「黑咖啡」是整串命中（名稱本來就含有它），所以拿它比不出高下 ——
     * 要比的是**沒有整串包含、只是像**的那種，例如共用「咖啡」的美式咖啡。
     */
    @Test
    fun `an exact hit outranks a merely similar one`() {
        val query = "黑咖啡"
        assertTrue(food("黑咖啡").matchScore(query) > food("美式咖啡").matchScore(query))
    }

    /** 近似分數的上限是 1.0，整串命中一定要比它高，不然排序保證不成立。 */
    @Test
    fun `an exact hit outranks anything a near match can reach`() {
        assertTrue(food("黑咖啡").matchScore("黑咖啡") > 1.0)
    }

    /** 單字比對會讓「咖」把咖哩拉進來，相鄰兩字不會 —— 這正是用 bigram 的理由。 */
    @Test
    fun `sharing a single character is not a match`() {
        assertEquals(0.0, food("咖哩飯").matchScore("黑咖啡"), 0.0)
    }

    /** 使用者自己用空白拆好的關鍵字，分別落在名稱與份量欄位也要算完整命中。 */
    @Test
    fun `space separated keywords may span name and serving text`() {
        assertTrue(food("珍珠奶茶", "大杯 半糖").matchScore("珍珠 大杯") > 1.0)
    }

    @Test
    fun `blank query leaves the list untouched`() {
        val list = listOf(blackCoffee, food("咖哩飯"))
        assertEquals(list, list.filterByQuery("   "))
    }

    @Test
    fun `filtering drops the unrelated and puts the closest first`() {
        val list = listOf(food("美式咖啡"), food("咖哩飯"), food("黑咖啡"))
        assertEquals(listOf(food("黑咖啡"), food("美式咖啡")), list.filterByQuery("黑咖啡"))
    }
}
