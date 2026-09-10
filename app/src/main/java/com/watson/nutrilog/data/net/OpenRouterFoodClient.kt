package com.watson.nutrilog.data.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * OpenRouter 專用 AI 文字飲食估算客戶端。
 *
 * 採用 OpenRouter 上的醫療健康專用大模型 `inclusionai/ling-3.0-flash-sante:free`（124B MoE），
 * 完全使用純模型推理（100% 免費、0 額外收費），不掛載任何 OpenRouter 收費工具（如 openrouter:web_search）。
 * 聯網檢索完全由客戶端免費之 Tavily Search API 負責，並將檢索結果透過 Prompt 上下文注入。
 */
class OpenRouterFoodClient(private val client: OkHttpClient = SharedHttp.client) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val timeoutClient by lazy {
        client.newBuilder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 從文字描述估算營養素。
     *
     * @param description 使用者輸入的食物描述（例如「CoCo 珍珠奶茶大杯半糖」、「麥當勞大麥克」）
     * @param apiKey OpenRouter API key (sk-or-v1-...)
     * @param model 預設 DEFAULT_MODEL ("inclusionai/ling-3.0-flash-sante:free")
     * @param webSearchContext 外部檢索結果（來自 Tavily Search 的即時營養標示摘要）
     */
    suspend fun analyzeDescription(
        description: String,
        apiKey: String,
        model: String = DEFAULT_MODEL,
        webSearchContext: String? = null,
    ): Result<List<DetectedFood>> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("尚未設定 OpenRouter API Key，請先至設定頁填入"))
        }

        val targetModel = model.ifBlank { DEFAULT_MODEL }
        val userPrompt = buildString {
            appendLine("使用者想記錄的食物或飲食描述：")
            appendLine(description)
            if (!webSearchContext.isNullOrBlank()) {
                appendLine()
                appendLine(webSearchContext)
                appendLine("請優先參考上述檢索資料之官方營養標示與熱量，精準輸出營養素數值。")
            }
        }.trim()

        logD("Executing OpenRouter pure model inference (model: $targetModel, hasSearchContext: ${!webSearchContext.isNullOrBlank()})...")

        // 這支是 reasoning 模型，「想」的部分也算進 max_tokens，而且想多久很不穩定
        // （實測同一句話連打五次，reasoning token 從 950 到 6300 都有）。想太久就會
        // 撞到上限，答案還沒寫完就被切斷。重試一次是因為它每次想的長度不一樣，
        // 換一次通常就過了 —— 不是那種「重試幾次結果都一樣」的錯誤。
        var lastFailure: Throwable? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            val outcome = sendRequest(trimmedKey, targetModel, SYSTEM_PROMPT, userPrompt)
                .mapCatching { chat -> interpretCompletion(chat).getOrThrow() }
            outcome.onSuccess { return@withContext Result.success(it) }
            lastFailure = outcome.exceptionOrNull()
            // 只有截斷值得重試；key 錯、額度不足重試幾次都一樣，只是讓使用者多等
            if (lastFailure !is TruncatedCompletionException) break
            logW("Attempt $attempt truncated by max_tokens, retrying...")
        }
        Result.failure(lastFailure ?: IOException("OpenRouter 沒有回應"))
    }

    /**
     * 把一次完成的對話判讀成食物清單，或判讀成「這次不算數」。
     *
     * 分辨兩件長得很像、意義完全相反的事：
     *   - 模型好好講完話，就是認定這不是食物 -> 空清單，畫面講「沒有辨識到食物」是對的。
     *   - 模型被 max_tokens 切斷，話沒講完 -> **失敗**。這時候講「沒有辨識到食物」是在
     *     說謊，使用者會以為 app 不認得這個食物而放棄，實際上再按一次重試通常就出來了。
     */
    internal fun interpretCompletion(chat: ChatResult): Result<List<DetectedFood>> {
        val truncated = chat.finishReason == FINISH_LENGTH
        if (chat.content.isBlank()) {
            return Result.failure(
                if (truncated) TruncatedCompletionException(TRUNCATED_MESSAGE)
                else IOException("模型沒有回傳任何內容，請再試一次")
            )
        }

        val parsed = parseResponseContent(chat.content)
        val foods = parsed.getOrNull()
        // 半截的 JSON 解不出來，或解出來是空的，只要這次是被切斷的就一律當截斷處理
        if (truncated && foods.isNullOrEmpty()) {
            return Result.failure(TruncatedCompletionException(TRUNCATED_MESSAGE))
        }
        return parsed
    }

    /**
     * 發送 OpenRouter Chat Completions 請求（純模型推理，不掛載任何外部工具，0 費用）。
     */
    private fun sendRequest(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
    ): Result<ChatResult> {
        val requestBody = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", userPrompt)
                }
            }
            put("temperature", 0.2)
            put("max_tokens", MAX_COMPLETION_TOKENS)
        }.toString().toRequestBody(JSON_MEDIA)

        val request = Request.Builder()
            .url(OPENROUTER_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://github.com/rowing195/NutriLog")
            .addHeader("X-Title", "NutriLog")
            .post(requestBody)
            .build()

        val startTime = System.currentTimeMillis()
        return try {
            timeoutClient.newCall(request).execute().use { response ->
                val elapsedMs = System.currentTimeMillis() - startTime
                val bodyString = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    val errorDetail = extractErrorMessage(bodyString)
                    val msg = when (response.code) {
                        401 -> "OpenRouter API Key 無效，請確認設定頁填入的 Key 是否正確"
                        402 -> "OpenRouter 帳號額度不足: $errorDetail"
                        403 -> "權限不足，請檢查 OpenRouter API 帳號權限"
                        429 -> "OpenRouter 請求頻率達到上限，請稍候 20 秒再試"
                        else -> "OpenRouter API 請求失敗 (${response.code}) [耗時 ${elapsedMs}ms]: $errorDetail"
                    }
                    logW("Request failed (code: ${response.code}): $msg")
                    return Result.failure(OpenRouterException(response.code, msg))
                }

                parseCompletion(bodyString).onSuccess { chat ->
                    logD(
                        "Received response in ${elapsedMs}ms " +
                            "(content length: ${chat.content.length}, finish: ${chat.finishReason})"
                    )
                }
            }
        } catch (e: SocketTimeoutException) {
            val elapsedMs = System.currentTimeMillis() - startTime
            logE("OpenRouter 連線逾時 (耗時 ${elapsedMs}ms)", e)
            Result.failure(IOException("連線逾時（已等待 ${elapsedMs / 1000} 秒），請檢查網路連線後重試"))
        } catch (e: Exception) {
            logE("OpenRouter 請求異常", e)
            Result.failure(e)
        }
    }

    /**
     * 從 chat completions 的回應 body 取出 content 與 finish_reason。
     *
     * `message.content` 在被截斷時是 JSON 的 `null`。kotlinx 的 `JsonNull` **也是**
     * 一個 primitive，`.jsonPrimitive.content` 會回字串 `"null"` 而不是 Kotlin 的 null
     * —— 舊版就是這樣把「沒有內容」變成四個字元的內容，`?:` 那條退路永遠不會走到，
     * 然後 `"null"` 一路解析成空的食物清單。用 `contentOrNull` 才分得出來。
     */
    internal fun parseCompletion(bodyString: String): Result<ChatResult> = runCatching {
        val parsed = json.parseToJsonElement(bodyString).jsonObject
        val choices = parsed["choices"]?.jsonArray
        if (choices.isNullOrEmpty()) throw IOException("OpenRouter 回傳內容為空")

        val choice = choices[0].jsonObject
        val message = choice["message"]?.jsonObject
        ChatResult(
            content = (message?.get("content") as? JsonPrimitive)?.contentOrNull.orEmpty(),
            finishReason = (choice["finish_reason"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
        )
    }

    /** 一次對話的結果。finish_reason 要一路帶到判讀那層，不然分不出「沒東西」和「被切斷」。 */
    internal data class ChatResult(val content: String, val finishReason: String)

    /** 被 max_tokens 切斷。這是唯一值得自動重試的失敗。 */
    internal class TruncatedCompletionException(message: String) : IOException(message)

    /**
     * 從錯誤 JSON 響應中提取錯誤訊息。
     */
    private fun extractErrorMessage(bodyString: String): String {
        return try {
            val obj = json.parseToJsonElement(bodyString).jsonObject
            obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                ?: obj["message"]?.jsonPrimitive?.content
                ?: bodyString.take(300)
        } catch (e: Exception) {
            bodyString.take(300)
        }
    }

    /**
     * 從模型回傳的字串中健壯地提取 JSON 並解析為 `List<DetectedFood>`。
     *
     * 支援多種常見輸出格式：
     * 1. ```json {"items": [...]} ```
     * 2. 純物件 {"items": [...]}
     * 3. 純陣列 [...]
     * 4. 夾雜前後文字之 JSON 區塊
     */
    internal fun parseResponseContent(content: String): Result<List<DetectedFood>> {
        return runCatching {
            val jsonText = extractJsonBlock(content)
            val rootElement = json.parseToJsonElement(jsonText)

            val itemsArray = when (rootElement) {
                is JsonObject -> {
                    rootElement["items"]?.jsonArray
                        ?: rootElement["foods"]?.jsonArray
                        ?: rootElement["results"]?.jsonArray
                        ?: if (rootElement.containsKey("name") && rootElement.containsKey("calories")) {
                            JsonArray(listOf(rootElement))
                        } else {
                            JsonArray(emptyList())
                        }
                }
                is JsonArray -> rootElement
                else -> JsonArray(emptyList())
            }

            val result = itemsArray.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                parseSingleFood(obj)
            }

            if (result.isEmpty()) {
                logW("No food items parsed from response: $content")
            }
            result
        }
    }

    private fun parseSingleFood(obj: JsonObject): DetectedFood? {
        val fields = obj.flattenedFields()

        val name = fields.text("name", "foodname", "food", "item") ?: return null
        val servingText = fields.text("servingtext", "serving", "servingsize", "portion") ?: "1 份"

        val calories = fields.number("calories", "calorie", "kcal", "energykcal", "energy")
        val proteinG = fields.number("proteing", "protein")
        val fatG = fields.number("fatg", "fat", "totalfat", "totalfatg")
        val carbsG = fields.number("carbsg", "carbs", "carbohydrate", "carbohydrates", "totalcarbohydrate")

        val sugarG = fields.number("sugarg", "sugar", "sugars", "totalsugars")
        val sodiumMg = fields.number("sodiummg", "sodium")
        val fiberG = fields.number("fiberg", "fiber", "fibre", "dietaryfiber")
        val satFatG = fields.number("satfatg", "satfat", "saturatedfat", "saturatedfatg")

        // 有熱量卻一個營養素都對不上，幾乎一定是欄位名又換了一種寫法。
        // 這時候使用者看到的是「AI 只抓到熱量」而不是錯誤畫面，沒有這行 log
        // 就只能靠猜；把當下實際收到的 key 印出來，下次照著補進上面的別名。
        if (calories != null && proteinG == null && fatG == null && carbsG == null) {
            logW("Item '$name' has calories but no macros. keys=" + fields.keys)
        }

        val confidence = (fields.number("confidence") ?: 0.85).coerceIn(0.0, 1.0)

        return DetectedFood(
            name = name,
            servingText = servingText,
            calories = round1(calories ?: 0.0),
            proteinG = round1(proteinG ?: 0.0),
            fatG = round1(fatG ?: 0.0),
            carbsG = round1(carbsG ?: 0.0),
            sugarG = sugarG?.let(::round1),
            sodiumMg = sodiumMg?.let(::round1),
            fiberG = fiberG?.let(::round1),
            satFatG = satFatG?.let(::round1),
            confidence = (confidence * 100).toInt() / 100.0,
        )
    }

    private fun round1(value: Double): Double = (value * 10).toInt() / 10.0

    /**
     * 把一個項目物件攤平成「正規化欄位名 -> 值」。
     *
     * 正規化 = 轉小寫後拿掉所有非英數字元，所以 `proteinG`、`protein_g`、
     * `"Protein (g)"` 會收斂成同一個 key。
     *
     * 這是為了對付「熱量是對的、三大營養素卻全是 0」那個症狀：`calories`
     * 在任何命名慣例下都長一樣，所以它永遠抓得到，蛋白／脂肪／碳水卻會因為
     * 模型改用底線命名而整組對不上，然後被填成 0。使用者看到的不是解析錯誤，
     * 而是「AI 只抓到熱量」—— 比報錯還難查，因為畫面上什麼都沒壞。
     *
     * 巢狀物件（`nutrition` / `nutrients` / `perServing`…）也一併攤上來，
     * 那是同一個症狀的另一種來源：熱量在最外層、營養素被包在裡面一層。
     * 同名時外層優先，而且只攤一層 —— 再深就分不清是誰的值了。
     */
    private fun JsonObject.flattenedFields(): Map<String, JsonElement> {
        val flat = LinkedHashMap<String, JsonElement>()
        forEach { (key, value) ->
            if (value !is JsonObject) flat.putIfAbsent(normalizeKey(key), value)
        }
        forEach { (_, value) ->
            (value as? JsonObject)?.forEach { (nested, nestedValue) ->
                if (nestedValue !is JsonObject) flat.putIfAbsent(normalizeKey(nested), nestedValue)
            }
        }
        return flat
    }

    private fun normalizeKey(key: String): String =
        key.lowercase().filter { it.isLetterOrDigit() }

    /** 依序試每個別名，回第一個有內容的字串；全都沒有就回 null 讓呼叫端決定退路。 */
    private fun Map<String, JsonElement>.text(vararg aliases: String): String? =
        aliases.firstNotNullOfOrNull { alias ->
            (this[alias] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
        }

    /** 同上，數值版。回 null 代表「這個欄位模型沒給」，和「它給了 0」是兩回事。 */
    private fun Map<String, JsonElement>.number(vararg aliases: String): Double? =
        aliases.firstNotNullOfOrNull { alias ->
            (this[alias] as? JsonPrimitive)?.asDoubleOrNull()
        }

    /**
     * 值可能是 18.5、"18.5"，也可能是 "18.5 g"、"約 540 kcal" —— 模型很愛把單位
     * 寫進值裡。純數字先走 doubleOrNull，不行才從字串裡撈第一段數字。
     */
    private fun JsonPrimitive.asDoubleOrNull(): Double? {
        doubleOrNull?.let { return it }
        val raw = contentOrNull ?: return null
        return NUMBER_IN_TEXT.find(raw)?.value?.toDoubleOrNull()
    }

    /**
     * 從字串中抽出有效的 JSON 區塊。
     */
    internal fun extractJsonBlock(raw: String): String {
        val trimmed = raw.trim()

        // 優先找 ```json ... ``` 或 ``` ... ```
        val fenceRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE)
        val match = fenceRegex.find(trimmed)
        if (match != null) {
            val fenced = match.groupValues[1].trim()
            if (fenced.startsWith("{") || fenced.startsWith("[")) {
                return fenced
            }
        }

        // 找第一個 { 到最後一個 }
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace > firstBrace) {
            // 也可能外層是陣列 [ ... ]
            val firstBracket = trimmed.indexOf('[')
            val lastBracket = trimmed.lastIndexOf(']')
            if (firstBracket != -1 && lastBracket > firstBracket && firstBracket < firstBrace && lastBracket > lastBrace) {
                return trimmed.substring(firstBracket, lastBracket + 1)
            }
            return trimmed.substring(firstBrace, lastBrace + 1)
        }

        // 找第一個 [ 到最後一個 ]
        val firstBracket = trimmed.indexOf('[')
        val lastBracket = trimmed.lastIndexOf(']')
        if (firstBracket != -1 && lastBracket > firstBracket) {
            return trimmed.substring(firstBracket, lastBracket + 1)
        }

        return trimmed
    }

    private fun logD(msg: String) {
        try { Log.d(TAG, msg) } catch (_: Throwable) {}
    }

    private fun logW(msg: String, tr: Throwable? = null) {
        try {
            if (tr != null) Log.w(TAG, msg, tr) else Log.w(TAG, msg)
        } catch (_: Throwable) {}
    }

    private fun logE(msg: String, tr: Throwable? = null) {
        try {
            if (tr != null) Log.e(TAG, msg, tr) else Log.e(TAG, msg)
        } catch (_: Throwable) {}
    }

    class OpenRouterException(val statusCode: Int, message: String) : IOException(message)

    companion object {
        private const val TAG = "OpenRouterFoodClient"
        const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
        const val DEFAULT_MODEL = "inclusionai/ling-3.0-flash-sante:free"
        private val JSON_MEDIA = "application/json".toMediaType()
        private const val MAX_ATTEMPTS = 2
        private const val FINISH_LENGTH = "length"

        /**
         * 思考與答案共用這個預算。實測「想」的部分要 950～6300 個 token，
         * 原本的 2048 連想都想不完（五次有四次被切斷），8192 才裝得下絕大多數情況。
         */
        private const val MAX_COMPLETION_TOKENS = 8192

        private const val TRUNCATED_MESSAGE =
            "AI 想太久，答案還沒寫完就被截斷了。這支免費模型偶爾會這樣，按重試通常就會出來"
        private val NUMBER_IN_TEXT = Regex("-?\\d+(?:\\.\\d+)?")

        val SYSTEM_PROMPT = """
            你是專業臨床營養師與飲食健康專家。
            使用者會以文字描述他們所吃或想查詢的食物、飲料或餐點（包含台灣手搖飲、連鎖速食、便利商店食品、各類外食小吃或家常菜）。

            你的任務是精準分析該飲食的熱量與營養成分。

            【核心原則】：
            1. 參考即時檢索資料：若 Prompt 中附有【Tavily 聯網檢索即時資料】（包含 AI 總結摘要與各品牌官方標示），請優先參考該數據，以獲取最準確的熱量與三大營養素。
            2. 規格選項：若使用者輸入未明確指出規格或甜度冰塊（例如僅輸入「珍珠奶茶」），請提供 2 到 4 種常見規格供挑選（例如「大杯 700ml 半糖微冰」、「大杯 700ml 全糖微冰」、「中杯 500ml 無糖」），各自成為一個獨立項目。若使用者描述已非常明確（例如「一顆茶葉蛋」或「大杯半糖四季春」），則僅需提供該特定項目即可，切勿硬湊。
            3. 份量說明 servingText：請具體描述份量與規格（例如「大杯 700ml 半糖」、「1份 (約220g)」、「1顆」、「1碗」）。
            4. 語言與數值單位：
               - name: 食物名稱，請務必使用台灣繁體中文。
               - calories: 單位 kcal（大卡）。
               - proteinG / fatG / carbsG / sugarG / fiberG / satFatG: 單位公克 (g)。
               - sodiumMg: 單位毫克 (mg)。
               - 沒把握或無可靠數據的微量營養素填 null，切勿隨意猜 0。
               - confidence: 介於 0.0 ~ 1.0 的信心指數（連鎖店官方標示給 0.90~0.98，大眾公認值給 0.80~0.90，粗估給 0.65~0.75）。
            5. 輸出格式要求：
               - 必須只回傳合法的標準 JSON，不得包含任何 Markdown 前言、結語或多餘對話文字。
               - 欄位名稱必須與下方範例**完全一致**（camelCase，如 proteinG），不可改寫成 protein_g 這類底線命名，
                 也不可把營養素包進 nutrition 之類的子物件裡，全部平放在同一層。
               - 結構如下：
               {
                 "items": [
                   {
                     "name": "CoCo 珍珠奶茶",
                     "servingText": "大杯 700ml 半糖",
                     "calories": 540.0,
                     "proteinG": 3.0,
                     "fatG": 18.0,
                     "carbsG": 91.0,
                     "sugarG": 48.0,
                     "sodiumMg": 110.0,
                     "fiberG": 0.0,
                     "satFatG": 12.0,
                     "confidence": 0.92
                   }
                 ]
               }
            6. 若使用者輸入完全不是食物或無法理解，回傳 {"items": []}。
        """.trimIndent()
    }
}
