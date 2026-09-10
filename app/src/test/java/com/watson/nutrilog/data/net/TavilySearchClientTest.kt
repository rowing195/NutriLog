package com.watson.nutrilog.data.net

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TavilySearchClientTest {

    private val client = TavilySearchClient()

    @Test
    fun `searchFood with blank apiKey returns empty response immediately`() = runBlocking {
        val result = client.searchFood("珍珠奶茶", "")
        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertEquals("珍珠奶茶", response.query)
        assertEquals(null, response.answer)
        assertTrue(response.results.isEmpty())
    }

    @Test
    fun `formatForPrompt with answer and results formats correctly`() {
        val response = TavilySearchResponse(
            query = "CoCo 珍珠奶茶 熱量",
            answer = "CoCo都可珍珠奶茶大杯（全糖）約含有 540 大卡熱量，半糖約 430 大卡。",
            results = listOf(
                TavilySearchResult(
                    title = "CoCo都可 官方營養成分標示",
                    content = "大杯珍珠奶茶：熱量 540 kcal，糖 60g，蛋白質 3.2g，脂肪 18g",
                    url = "https://example.com/coco",
                    score = 0.98,
                ),
                TavilySearchResult(
                    title = "台灣手搖杯熱量圖鑑",
                    content = "珍奶熱量大解密，波霸與小珍珠差異分析...",
                    url = "https://example.com/tea",
                    score = 0.85,
                ),
            ),
        )

        val formatted = client.formatForPrompt(response)
        assertTrue(formatted.contains("【Tavily 聯網檢索即時資料】"))
        assertTrue(formatted.contains("AI 總結摘要：CoCo都可珍珠奶茶大杯"))
        assertTrue(formatted.contains("1. 來源：CoCo都可 官方營養成分標示"))
        assertTrue(formatted.contains("540 kcal"))
        assertTrue(formatted.contains("2. 來源：台灣手搖杯熱量圖鑑"))
    }

    @Test
    fun `formatForPrompt with only results without answer formats correctly`() {
        val response = TavilySearchResponse(
            query = "麥當勞大麥克",
            answer = null,
            results = listOf(
                TavilySearchResult(
                    title = "麥當勞 營養成分計算機",
                    content = "大麥克熱量為 548 大卡，蛋白質 26g，脂肪 28g",
                    url = "https://example.com/mcd",
                    score = 0.95,
                )
            ),
        )

        val formatted = client.formatForPrompt(response)
        assertTrue(formatted.contains("【Tavily 聯網檢索即時資料】"))
        assertTrue(!formatted.contains("AI 總結摘要"))
        assertTrue(formatted.contains("1. 來源：麥當勞 營養成分計算機"))
        assertTrue(formatted.contains("548 大卡"))
    }

    @Test
    fun `formatForPrompt with empty response returns empty string`() {
        val response = TavilySearchResponse(query = "test", answer = null, results = emptyList())
        val formatted = client.formatForPrompt(response)
        assertEquals("", formatted)
    }
}

