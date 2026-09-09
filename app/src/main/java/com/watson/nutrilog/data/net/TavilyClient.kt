package com.watson.nutrilog.data.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 查網路，把結果整理成一段可以接在 prompt 前面的背景文字。
 *
 * **和 OpenRouter 那個內建外掛的差別，是它跟供應商無關** —— 回來的東西只是多一段
 * 文字，所以 Gemini 與 OpenRouter 兩條路都能用。Gemini 免費層的 grounding 額度是 0
 * （見 CLAUDE.md），這條是那邊唯一能查網路的方法。
 *
 * **為什麼是 Tavily 而不是 Brave：** Brave 免費層回的是 SERP 片段，實測搜「麥當勞
 * 大麥克 營養素」四筆裡三筆在講雞塊和薯條，唯一有數字的是 2018 年的新聞稿。Tavily
 * 回的是清洗過的頁面正文，實測第一筆就是台灣麥當勞官方產品頁，整張營養表原樣帶回來
 * （熱量 503.17、蛋白質 26、脂肪 25、飽和脂肪 11、碳水 43、糖 6.1、鈉 1092.5）。
 *
 * **不開 `include_answer`。** 那會回一段 AI 寫好的摘要，而摘要傾向給「約 500 至 550」
 * 這種跨地區的區間；我們自己的 prompt 讀官方表格會比讀別人的摘要準。
 */
class TavilyClient(private val client: OkHttpClient = SharedHttp.client) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 查一次，回傳要塞進 prompt 的背景文字；查不到或出錯就回 null。
     *
     * **失敗一律回 null 而不是拋例外**：搜尋只是輔助，查不到就讓模型照原本的方式估，
     * 不該因為搜尋壞掉就讓整條辨識失敗。錯誤留在 logcat 供事後追。
     */
    suspend fun contextFor(query: String, apiKey: String): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || query.isBlank()) return@withContext null
        runCatching {
            val payload = buildJsonObject {
                put("query", query.trim() + " " + QUERY_SUFFIX)
                // basic 一次 1 credit，免費層 1000/月。advanced 要 2 credit，
                // 而實測 basic 就已經把官方頁排在第一筆了。
                put("search_depth", "basic")
                put("max_results", MAX_RESULTS)
                put("include_answer", false)
                put("include_raw_content", false)
            }
            val request = Request.Builder()
                .url(BASE_URL)
                .header("Authorization", "Bearer " + apiKey)
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()

            val response = client.newCall(request).execute()
            val body = response.use { it.body?.string().orEmpty() }
            if (response.code !in 200..299) {
                Log.w(TAG, "Tavily " + response.code + ": " + body.take(400))
                return@runCatching null
            }
            json.decodeFromString(SearchResponse.serializer(), body).results
                .take(MAX_RESULTS)
                .joinToString("\n\n") { r ->
                    // 正文很長而且夾著一堆導覽選單的雜訊，整段塞進去會把 token 吃光。
                    // 官方頁的營養表都落在前段，所以截斷是安全的。
                    r.title + "（" + r.url + "）\n" + r.content.take(CONTENT_LIMIT)
                }
                .ifBlank { null }
        }.onFailure { Log.w(TAG, "查詢失敗", it) }.getOrNull()
    }

    @Serializable
    private data class SearchResponse(val results: List<SearchResult> = emptyList())

    @Serializable
    private data class SearchResult(
        val url: String = "",
        val title: String = "",
        val content: String = "",
    )

    private companion object {
        const val TAG = "TavilyClient"

        /**
         * 補在使用者輸入後面的固定字尾。
         *
         * 使用者打的是「大麥克」，不是「麥當勞 大麥克 營養素」—— 前者搜出來可能是
         * 新聞或食記，後者才會把官方營養頁排到第一。補字尾是最便宜的補救：
         * **這個功能的意圖是恆定的**（永遠在問營養標示），所以不需要叫模型改寫查詢。
         *
         * 為什麼不叫模型改寫：那是多一次模型請求。Gemini 免費層一天只有二十幾次，
         * 而且拍照也吃同一份額度 —— 為了修飾查詢就砍掉一半的可用次數不划算。
         * 真的要走那條之前，先確認補字尾在實際會打的簡短關鍵字上不夠用。
         */
        const val QUERY_SUFFIX = "營養成分 熱量"
        const val BASE_URL = "https://api.tavily.com/search"
        /** 官方頁排第一（實測 score 0.87），三筆足夠涵蓋它與兩個交叉比對的來源。 */
        const val MAX_RESULTS = 3
        const val CONTENT_LIMIT = 1500
        val JSON_MEDIA = "application/json".toMediaType()
    }
}
