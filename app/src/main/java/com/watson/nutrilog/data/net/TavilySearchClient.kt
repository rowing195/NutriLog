package com.watson.nutrilog.data.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Tavily Search API 單筆搜尋結果。
 */
data class TavilySearchResult(
    val title: String,
    val content: String,
    val url: String,
    val score: Double = 0.0,
)

/**
 * Tavily Search API 回應資料封裝。
 */
data class TavilySearchResponse(
    val query: String,
    val answer: String? = null,
    val results: List<TavilySearchResult> = emptyList(),
)

/**
 * Tavily Search API 客戶端。
 *
 * 專為 LLM 與 AI Agent 設計的即時搜尋引擎 (https://www.tavily.com/)。
 * 用於在文字飲食估算時，針對台灣在地食物（手搖飲、便利商店、連鎖外食）進行即時聯網檢索，
 * 取得最新官方熱量與三大營養素標示摘要。
 *
 * Tavily 提供免費方案（每月 1,000 次搜尋），且原生支援 `include_answer` 生成精煉的營養素摘要。
 */
class TavilySearchClient(private val client: OkHttpClient = SharedHttp.client) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val searchClient by lazy {
        client.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 檢索食物相關的熱量與營養標示。
     *
     * @param query 使用者輸入的食物描述（例如「CoCo 珍珠奶茶」）
     * @param apiKey Tavily Search API Token (tvly-...)
     * @param count 抓取前幾筆結果（預設 3 筆，精準且不膨脹 Prompt）
     */
    suspend fun searchFood(
        query: String,
        apiKey: String,
        count: Int = 3,
    ): Result<TavilySearchResponse> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext Result.success(TavilySearchResponse(query = query))
        }

        val searchQuery = "$query 熱量 營養成分"
        val requestPayload = buildJsonObject {
            put("api_key", trimmedKey)
            put("query", searchQuery)
            put("search_depth", "basic")
            put("include_answer", true)
            put("max_results", count.coerceIn(1, 10))
        }.toString()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(BASE_URL)
            .addHeader("Authorization", "Bearer $trimmedKey")
            .addHeader("Accept", "application/json")
            .post(requestPayload.toRequestBody(mediaType))
            .build()

        try {
            searchClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val msg = "Tavily Search API 請求失敗 (${response.code}): ${bodyString.take(200)}"
                    logW(msg)
                    return@withContext Result.failure(IOException(msg))
                }

                val parsed = json.parseToJsonElement(bodyString).jsonObject
                val responseQuery = parsed["query"]?.jsonPrimitive?.content ?: query
                val answer = parsed["answer"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                val resultsArray = parsed["results"]?.jsonArray ?: emptyList()

                val results = resultsArray.mapNotNull { element ->
                    val obj = element.jsonObject
                    val title = obj["title"]?.jsonPrimitive?.content?.trim().orEmpty()
                    val content = obj["content"]?.jsonPrimitive?.content?.trim().orEmpty()
                    val resultUrl = obj["url"]?.jsonPrimitive?.content?.trim().orEmpty()
                    val score = obj["score"]?.jsonPrimitive?.doubleOrNull ?: 0.0

                    if (title.isBlank() && content.isBlank()) null
                    else TavilySearchResult(
                        title = title,
                        content = content,
                        url = resultUrl,
                        score = score,
                    )
                }

                logD("Tavily Search retrieved ${results.size} snippets (has answer: ${answer != null}) for: $query")
                Result.success(
                    TavilySearchResponse(
                        query = responseQuery,
                        answer = answer,
                        results = results,
                    )
                )
            }
        } catch (e: Exception) {
            logW("Tavily Search error for query: $query", e)
            Result.failure(e)
        }
    }

    /**
     * 將 Tavily Search 結果格式化為可直接注入 Prompt 的文字段落。
     */
    fun formatForPrompt(response: TavilySearchResponse): String {
        if (response.answer.isNullOrBlank() && response.results.isEmpty()) return ""
        return buildString {
            appendLine("【Tavily 聯網檢索即時資料】")
            if (!response.answer.isNullOrBlank()) {
                appendLine("AI 總結摘要：${response.answer.trim()}")
            }
            if (response.results.isNotEmpty()) {
                appendLine("相關來源：")
                response.results.forEachIndexed { index, item ->
                    appendLine("${index + 1}. 來源：${item.title}")
                    if (item.content.isNotBlank()) {
                        appendLine("   內容摘要：${item.content}")
                    }
                }
            }
        }.trim()
    }

    private fun logD(msg: String) {
        try { Log.d(TAG, msg) } catch (_: Throwable) {}
    }

    private fun logW(msg: String, tr: Throwable? = null) {
        try {
            if (tr != null) Log.w(TAG, msg, tr) else Log.w(TAG, msg)
        } catch (_: Throwable) {}
    }

    companion object {
        private const val TAG = "TavilySearchClient"
        const val BASE_URL = "https://api.tavily.com/search"
    }
}

