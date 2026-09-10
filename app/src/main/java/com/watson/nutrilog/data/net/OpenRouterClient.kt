package com.watson.nutrilog.data.net

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * OpenRouter 的文字辨識。**只做文字**，拍照那條仍然走 [GeminiClient]。
 *
 * 分成兩個 client 而不是抽一層介面：兩邊真正共用的只有 prompt 與 [DetectedFood]，
 * 傳輸格式、強制 JSON 的手法、錯誤訊息全都不一樣，硬包成一個介面只會得到一堆
 * `when (provider)`。呼叫端（`NutriViewModel.startAnalysis`）只有一處要分流。
 *
 * **為什麼不用 `response_format`：** 想用的那個健康模型
 * （`inclusionai/ling-3.0-flash-sante:free`）的 `supported_parameters` 裡沒有它 ——
 * OpenRouter 上很多模型都沒有。但它有 `tools`，所以改用「定義一個函式、參數就是
 * 那份 schema、再用 `tool_choice` 強制它呼叫」——這是結構化輸出出現之前的標準做法。
 * 換模型之前先到 openrouter.ai/api/v1/models 確認它有沒有 `tools`，沒有的話就得
 * 回去剝 markdown code fence，那正是 Gemini 那邊當初想避開的事。
 */
class OpenRouterClient(private val client: okhttp3.OkHttpClient = SharedHttp.client) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 從文字描述估營養素。prompt 與 Gemini 那條共用同一份（[AiPrompts.TEXT_PROMPT]）。
     *
     * [search] 非 null 時走**代理迴圈**：多給模型一個 `search_web` 工具，讓它自己決定
     * 要不要查、查什麼，查完把結果餵回去再問一次，直到它改叫 `record_foods` 為止。
     * 這是這個分支要驗的東西——main 上是「固定補字尾、查一次、結果塞進 prompt」。
     *
     * 代理迴圈的代價不只是多幾次請求：**多一個工具就不能再強制 `tool_choice`**，
     * 而那正是 main 上鎖住 JSON 的手段。所以這裡多了一條「它可能永遠不叫
     * record_foods」的失敗路徑，要靠 [MAX_ROUNDS] 收尾。
     */
    suspend fun analyzeDescription(
        description: String,
        apiKey: String,
        model: String,
        webSearch: Boolean,
        searchContext: String? = null,
        search: (suspend (String) -> String?)? = null,
    ): Result<List<DetectedFood>> = withContext(Dispatchers.IO) {
        runCatching {
            // 對話要一路累積：模型的回覆與工具結果都得原樣送回去，
            // 不然下一輪它不知道自己剛剛查過什麼。
            val messages = mutableListOf<JsonElement>(
                buildJsonObject {
                    put("role", "user")
                    put("content", AiPrompts.textRequest(description, searchContext))
                }
            )

            var searched = false
            repeat(MAX_ROUNDS) { round ->
                // **查過一次之後就強制它回答。** 實測不強制的話這個模型會一直查下去
                // （三輪查詢一次比一次精緻，卻從來沒叫過 record_foods，最後只能喊停
                // 丟給使用者一個「重試」）。讓它自己下第一次查詢是這條路的價值所在，
                // 讓它無限期地查下去則純粹是代價。
                val offerSearch = search != null && !searched
                val body = sendWithRetry(
                    request(apiKey, payload(model, messages, webSearch, offerSearch))
                )
                val message = json.parseToJsonElement(body)
                    .jsonObject["choices"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("message")?.jsonObject
                    ?: error("模型沒有回傳內容")

                val calls = message["tool_calls"]?.jsonArray.orEmpty().map {
                    val fn = it.jsonObject["function"]!!.jsonObject
                    Triple(
                        it.jsonObject["id"]?.jsonPrimitive?.content.orEmpty(),
                        fn["name"]?.jsonPrimitive?.content.orEmpty(),
                        fn["arguments"]?.jsonPrimitive?.content.orEmpty(),
                    )
                }

                calls.firstOrNull { it.second == TOOL_NAME }?.let { (_, _, args) ->
                    Log.w(TAG, "第 " + (round + 1) + " 輪收到 " + TOOL_NAME)
                    return@runCatching json.decodeFromString(
                        AnalysisResult.serializer(), args,
                    ).items
                }

                val searches = calls.filter { it.second == SEARCH_TOOL }
                if (searches.isEmpty() || search == null) {
                    error("模型沒有照要求回傳結構化結果")
                }
                searched = true

                // 模型自己的那一則要原樣接回去，工具結果才對得上 tool_call_id
                messages += message
                searches.forEach { (id, _, args) ->
                    val query = runCatching {
                        json.parseToJsonElement(args).jsonObject["query"]?.jsonPrimitive?.content
                    }.getOrNull().orEmpty()
                    Log.w(TAG, "第 " + (round + 1) + " 輪 search_web: " + query)
                    val result = search(query) ?: "（查不到相關資料）"
                    messages += buildJsonObject {
                        put("role", "tool")
                        put("tool_call_id", id)
                        put("content", result)
                    }
                }
            }
            error("查了 " + MAX_ROUNDS + " 輪還是沒有結果，換個說法再試一次")
        }
    }

    private fun payload(
        model: String,
        messages: List<JsonElement>,
        webSearch: Boolean,
        offerSearch: Boolean,
    ) = buildJsonObject {
        put("model", model)
        putJsonArray("messages") { messages.forEach { add(it) } }
        if (webSearch) {
            putJsonArray("plugins") { addJsonObject { put("id", "web") } }
        }
        putJsonArray("tools") {
            addJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", TOOL_NAME)
                    put("description", "回報這次描述裡的每一項食物與它的營養素")
                    put("parameters", json.parseToJsonElement(TOOL_SCHEMA))
                }
            }
            if (offerSearch) {
                addJsonObject {
                    put("type", "function")
                    putJsonObject("function") {
                        put("name", SEARCH_TOOL)
                        put(
                            "description",
                            "查網路上的營養標示。只在連鎖店品項、包裝食品這種" +
                                "「查得到官方數字」的情況才用；家常菜自己估就好。",
                        )
                        put("parameters", json.parseToJsonElement(SEARCH_SCHEMA))
                    }
                }
            }
        }
        // 只有一個工具時強制它呼叫，JSON 才鎖得住。這一輪如果同時給了搜尋工具就
        // 不能強制——那是代理迴圈的固有代價。所以搜尋只給第一輪，之後就鎖回來。
        if (!offerSearch) {
            putJsonObject("tool_choice") {
                put("type", "function")
                putJsonObject("function") { put("name", TOOL_NAME) }
            }
        }
    }

    private fun request(apiKey: String, payload: JsonObject) = Request.Builder()
        .url(BASE_URL)
        .header("Authorization", "Bearer " + apiKey)
        // OpenRouter 用這兩個做來源歸屬。不帶也能用，帶了它的儀表板才分得出
        // 是哪支 app 打的 —— 對只有一支 app 的人沒差，但漏掉會被歸到 unknown。
        .header("HTTP-Referer", "https://github.com/rowing195/NutriLog")
        .header("X-Title", "NutriLog")
        .post(payload.toString().toRequestBody(JSON_MEDIA))
        .build()

    /**
     * 和 [GeminiClient] 同一套重試規則：只重試 5xx 與連線層的失敗。
     * 4xx 重試幾次結果都一樣，只是讓使用者多等好幾秒才看到同一則錯誤。
     */
    private suspend fun sendWithRetry(request: Request): String {
        var lastFailure: String? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            if (attempt > 1) delay(RETRY_DELAY_MS shl (attempt - 2))

            val response = try {
                client.newCall(request).execute()
            } catch (cause: IOException) {
                lastFailure = networkMessage(cause)
                Log.w(TAG, "attempt " + attempt + " 連線失敗", cause)
                continue
            }

            val code = response.code
            val body = response.use { it.body?.string().orEmpty() }
            if (code in 200..299) return body

            Log.w(TAG, "attempt " + attempt + " OpenRouter " + code + ": " + body.take(800))
            val message = explain(code, body)
            if (code !in 500..599) error(message)
            lastFailure = message
        }
        error(lastFailure ?: "OpenRouter 無法回應")
    }

    private fun networkMessage(cause: IOException): String = when (cause) {
        is SocketTimeoutException ->
            "等太久了，連線逾時。這個模型可能思考時間較長 —— 到設定頁換一個比較快的模型再試一次"
        else -> "連線失敗，請檢查網路"
    }

    /**
     * OpenRouter 的錯誤碼跟 Gemini 不一樣，最常見的兩個要分開講：
     * 401 是 key 的問題，402 是**餘額**的問題（Gemini 那邊沒有這種狀態）。
     */
    private fun explain(code: Int, body: String): String {
        val detail = runCatching {
            json.decodeFromString(ErrorEnvelope.serializer(), body).error?.message
        }.getOrNull().orEmpty()
        val head = when (code) {
            401 -> "OpenRouter 拒絕了這把 key，到設定頁確認一下"
            402 -> "OpenRouter 餘額不足 —— 免費模型也需要帳號裡有額度才跑得動"
            404 -> "找不到這個模型，到設定頁確認模型名稱（要含 openrouter 上的完整路徑）"
            429 -> "太頻繁或額度用完了，等一下再試；免費模型的限制比較嚴"
            in 500..599 -> "OpenRouter 那邊忙不過來"
            else -> "OpenRouter 回報錯誤（" + code + "）"
        }
        return if (detail.isBlank()) head else head + "\n\n" + detail
    }

    @Serializable
    private data class ChatResponse(val choices: List<Choice> = emptyList())

    @Serializable
    private data class Choice(val message: Message? = null)

    @Serializable
    private data class Message(
        @kotlinx.serialization.SerialName("tool_calls")
        val toolCalls: List<ToolCall> = emptyList(),
    )

    @Serializable
    private data class ToolCall(val function: FunctionCall)

    /** `arguments` 是**字串包著的 JSON**，不是巢狀物件 —— 這是 OpenAI 格式的慣例。 */
    @Serializable
    private data class FunctionCall(val arguments: String)

    @Serializable
    private data class ErrorEnvelope(val error: ErrorBody? = null)

    @Serializable
    private data class ErrorBody(val message: String = "")

    @Serializable
    private data class AnalysisResult(val items: List<DetectedFood> = emptyList())

    private companion object {
        const val TAG = "OpenRouterClient"
        const val BASE_URL = "https://openrouter.ai/api/v1/chat/completions"
        const val MAX_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 1500L
        const val TOOL_NAME = "record_foods"
        const val SEARCH_TOOL = "search_web"
        /** 迴圈上限。模型可能永遠不叫 record_foods，總得有人喊停。 */
        const val MAX_ROUNDS = 3
        const val SEARCH_SCHEMA = """
        {
          "type": "object",
          "properties": { "query": { "type": "string" } },
          "required": ["query"]
        }
        """
        val JSON_MEDIA = "application/json".toMediaType()

        /**
         * 標準 JSON Schema，和 Gemini 那份 [GeminiClient] 的 OpenAPI 子集**不是同一種**：
         * 型別是小寫、可空欄位要寫成 `["number", "null"]` 而不是 `nullable: true`。
         * 屬性名稱一樣要和 [DetectedFood] 完全一致。
         */
        const val TOOL_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "items": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "name":       { "type": "string" },
                  "servingText":{ "type": "string" },
                  "calories":   { "type": "number" },
                  "proteinG":   { "type": "number" },
                  "fatG":       { "type": "number" },
                  "carbsG":     { "type": "number" },
                  "sugarG":     { "type": ["number", "null"] },
                  "sodiumMg":   { "type": ["number", "null"] },
                  "fiberG":     { "type": ["number", "null"] },
                  "satFatG":    { "type": ["number", "null"] },
                  "confidence": { "type": "number" }
                },
                "required": ["name", "servingText", "calories", "proteinG", "fatG", "carbsG", "confidence"]
              }
            }
          },
          "required": ["items"]
        }
        """
    }
}
