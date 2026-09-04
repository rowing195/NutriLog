package com.watson.nutrilog.data.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException

/** 模型從照片裡認出來的一項食物。數字全是估算值，一律要讓使用者確認過才入庫。 */
@Serializable
data class DetectedFood(
    val name: String = "",
    val servingText: String = "",
    val calories: Double = 0.0,
    val proteinG: Double = 0.0,
    val fatG: Double = 0.0,
    val carbsG: Double = 0.0,
    val sugarG: Double? = null,
    val sodiumMg: Double? = null,
    val fiberG: Double? = null,
    val satFatG: Double? = null,
    /** 0..1。低把握度的項目在確認畫面要標出來，不要讓使用者以為都一樣可靠。 */
    val confidence: Double = 0.0,
)

/**
 * Gemini 營養素估算：吃照片或吃文字描述，兩者共用同一組 schema 與確認流程。
 *
 * 直接打 REST，不用官方 SDK —— 只有這一支端點，
 * 手寫的量比引進整套 SDK 還少，而且不必跟著 SDK 改版走。
 */
class GeminiClient(private val client: OkHttpClient = SharedHttp.client) {

    private val json = Json { ignoreUnknownKeys = true }

    /** 從照片認食物。 */
    suspend fun analyzeFood(
        base64Jpeg: String,
        apiKey: String,
        model: String,
    ): Result<List<DetectedFood>> = analyze(apiKey, model) {
        addJsonObject { put("text", PHOTO_PROMPT) }
        addJsonObject {
            putJsonObject("inline_data") {
                put("mime_type", "image/jpeg")
                put("data", base64Jpeg)
            }
        }
    }

    /**
     * 從文字描述估營養素，例如「coco 珍珠奶茶」。
     *
     * 和照片走同一組 schema 與同一個確認畫面 —— 對使用者來說這只是
     * 「換一種告訴 app 我吃了什麼的方式」，後面的流程沒有理由不一樣。
     */
    suspend fun analyzeDescription(
        description: String,
        apiKey: String,
        model: String,
    ): Result<List<DetectedFood>> = analyze(apiKey, model) {
        addJsonObject { put("text", TEXT_PROMPT + "\n\n使用者輸入：" + description) }
    }

    private suspend fun analyze(
        apiKey: String,
        model: String,
        parts: JsonArrayBuilder.() -> Unit,
    ): Result<List<DetectedFood>> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                putJsonArray("contents") {
                    addJsonObject {
                        putJsonArray("parts", parts)
                    }
                }
                putJsonObject("generationConfig") {
                    // 強制結構化輸出：沒有這兩行，模型會回夾著說明文字的
                    // markdown code fence，就得自己剝字串，而且隨時會變。
                    put("responseMimeType", "application/json")
                    put("responseSchema", json.parseToJsonElement(RESPONSE_SCHEMA))
                    thinkingConfigFor(model)?.let { put("thinkingConfig", it) }
                }
            }

            val request = Request.Builder()
                .url(BASE_URL + model + ":generateContent")
                // key 走 header 而不是 ?key=，query string 會被各層代理與日誌記下來
                .header("x-goog-api-key", apiKey)
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()

            val body = sendWithRetry(request)
            val parsed = json.decodeFromString(GeminiResponse.serializer(), body)
            val text = parsed.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: error("模型沒有回傳內容")
            json.decodeFromString(AnalysisResult.serializer(), text).items
        }
    }

    /**
     * 送出請求，暫時性失敗自動重試，回傳成功的 response body。
     *
     * 只重試**重試得有意義**的兩種情況：
     *   - 5xx：Google 那邊忙不過來（503 model overloaded 在新模型剛推出時很常見）
     *   - 連線層的 IOException／逾時
     * 4xx 一律不重試 —— key 錯、模型名稱錯、配額不足，重試幾次結果都一樣，
     * 只是讓使用者多等好幾秒才看到同一則錯誤。
     *
     * 退避時間刻意保守（1.5s、3s）：使用者正盯著「辨識中」的轉圈等結果，
     * 拖太久還不如早點告訴他失敗了。
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
            // body 只能讀一次，而且要在 use 區塊裡讀完
            val body = response.use { it.body?.string().orEmpty() }
            if (code in 200..299) return body

            Log.w(TAG, "attempt " + attempt + " Gemini " + code + ": " + body.take(800))
            val message = explain(code, body)
            if (code !in 500..599) error(message)
            lastFailure = message
        }
        error(lastFailure ?: "Gemini 無法回應")
    }

    /**
     * OkHttp 的逾時訊息是英文的 "timeout"，直接丟給使用者等於沒說。
     * 而逾時最常見的成因是模型思考太久，解法是換模型而不是一直重試。
     */
    private fun networkMessage(cause: IOException): String = when (cause) {
        is SocketTimeoutException ->
            "等太久了，連線逾時。這個模型可能思考時間較長 —— " +
                "到設定頁換成 gemini-3.5-flash-lite 之類比較快的模型再試一次"
        else -> "連線失敗，請檢查網路"
    }

    /**
     * 錯誤訊息 = 我們的一句話定性 + Google 自己的說明。
     *
     * 只給定性（例如「已達用量上限」）在第一次使用就撞 429 時會**誤導**：
     * 使用者會以為自己用太多，但實際原因可能是專案還在 Free tier、
     * 或那個模型的每日額度是 0。Google 的 error.message 與 quotaId
     * 才講得出是哪一項配額，所以照原文帶出來 —— 但只帶解析過的欄位，
     * 不是把整包 JSON 倒在畫面上。
     */
    private fun explain(code: Int, body: String): String {
        val headline = when (code) {
            400 -> "請求被拒絕，API key 可能不正確"
            401, 403 -> "API key 無效或沒有權限"
            404 -> "找不到這個模型，請到設定頁確認模型名稱"
            429 -> "配額不足。這不一定代表你用太多 —— 也可能是專案還在免費層，或這個模型的額度是 0"
            in 500..599 ->
                "Gemini 那邊暫時忙不過來（已自動重試）。剛推出的模型特別容易遇到，" +
                    "可到設定頁改用 gemini-3.5-flash-lite"
            else -> "Gemini 回應 HTTP " + code
        }
        val detail = runCatching {
            val error = json.decodeFromString(ErrorEnvelope.serializer(), body).error ?: return@runCatching ""
            // quotaId 長得像 GenerateRequestsPerDayPerProjectPerModel-FreeTier，
            // 直接點出是哪一條限制擋下來的，比什麼都有用
            val quotaId = error.details
                .mapNotNull { it as? JsonObject }
                .flatMap { it["violations"]?.jsonArray.orEmpty() }
                .mapNotNull { (it as? JsonObject)?.get("quotaId")?.jsonPrimitive?.contentOrNull }
                .firstOrNull()
            listOfNotNull(
                error.message.trim().takeIf { it.isNotEmpty() },
                quotaId?.let { "quotaId: " + it },
            ).joinToString("\n\n")
        }.getOrDefault("")

        return if (detail.isBlank()) headline else headline + "\n\n" + detail
    }

    /**
     * 把思考程度壓到最低。
     *
     * 「看照片認食物」不需要推理，但 Gemini 3.x 預設是 thinking medium，
     * 延遲會拉到讓請求逾時（實際踩到：3.7-flash 一直斷線）。
     *
     * 參數策略：
     *   Gemini 3.7 / 3.8  -> thinkingLevel = "low"（這兩代不支援 minimal，最低只能設 low）
     *   Gemini 3.x        -> thinkingLevel = "minimal"（最接近關閉；flash 系列無法完全關）
     *   Gemini 2.5 Flash  -> thinkingBudget = 0（這一系列才能真的完全關閉）
     *
     * 2.5 **Pro** 特別排除：它的 thinkingBudget 下限是 128，不接受 0，
     * 送 0 會直接被打回 400。認不得的模型也整個不送，讓它用自己的預設 ——
     * 亂送不支援的欄位一樣是 400。
     */
    private fun thinkingConfigFor(model: String): JsonObject? {
        val id = model.trim().lowercase()
        return when {
            id.startsWith("gemini-3.7") || id.startsWith("gemini-3.8") ->
                buildJsonObject { put("thinkingLevel", "low") }
            id.startsWith("gemini-3") ->
                buildJsonObject { put("thinkingLevel", "minimal") }
            id.startsWith("gemini-2.5-flash") ->
                buildJsonObject { put("thinkingBudget", 0) }
            else -> null
        }
    }

    @Serializable
    private data class ErrorEnvelope(val error: ErrorBody? = null)

    @Serializable
    private data class ErrorBody(
        val message: String = "",
        val status: String = "",
        val details: List<JsonElement> = emptyList(),
    )

    @Serializable
    private data class GeminiResponse(val candidates: List<Candidate> = emptyList())

    @Serializable
    private data class Candidate(val content: Content? = null)

    @Serializable
    private data class Content(val parts: List<Part> = emptyList())

    @Serializable
    private data class Part(val text: String? = null)

    @Serializable
    private data class AnalysisResult(val items: List<DetectedFood> = emptyList())

    private companion object {
        const val TAG = "GeminiClient"
        const val MAX_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 1500L
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
        val JSON_MEDIA = "application/json".toMediaType()

        val PHOTO_PROMPT = """
            你是營養師。看這張食物照片，列出裡面每一種可辨識的食物。

            規則：
            - 依照片中看得到的份量估算，不要用「每 100 公克」的通用值。
            - servingText 要寫成人看得懂的份量，例如「1 碗（約 250 公克）」。
            - name 用繁體中文。
            - calories 單位 kcal；proteinG / fatG / carbsG / sugarG / fiberG / satFatG 單位公克；sodiumMg 單位毫克。
            - 沒把握的營養素就填 null，不要猜 0。
            - confidence 是 0 到 1 之間的數字，代表你對這一項的把握程度。
            - 照片裡沒有食物就回傳空的 items 陣列。
        """.trimIndent()

        val TEXT_PROMPT = """
            你是營養師。使用者用文字描述他吃了什麼，請估算營養素。

            規則：
            - 台灣的連鎖店品項（例如 CoCo、50 嵐、麥當勞）就用該店的常見規格估。
            - 描述沒講清楚規格時，列出 2 到 4 個**常見選項**讓使用者挑，
              例如大杯／中杯、全糖／半糖、加料與否，各自算成一項。
              描述已經很明確（例如「一顆水煮蛋」）就只回一項，不要硬湊。
            - servingText 要寫清楚是哪一種規格，例如「大杯 700ml 全糖」。
            - name 用繁體中文。
            - calories 單位 kcal；proteinG / fatG / carbsG / sugarG / fiberG / satFatG 單位公克；sodiumMg 單位毫克。
            - 沒把握的營養素就填 null，不要猜 0。
            - confidence 是 0 到 1 之間的數字。連鎖店有公開營養標示的給高一點，純估算的給低一點。
            - 完全看不懂在講什麼食物就回傳空的 items 陣列。
        """.trimIndent()

        // 用 OpenAPI 子集描述回傳格式。屬性名稱要和 DetectedFood 完全一致。
        const val RESPONSE_SCHEMA = """
        {
          "type": "OBJECT",
          "properties": {
            "items": {
              "type": "ARRAY",
              "items": {
                "type": "OBJECT",
                "properties": {
                  "name":       { "type": "STRING" },
                  "servingText":{ "type": "STRING" },
                  "calories":   { "type": "NUMBER" },
                  "proteinG":   { "type": "NUMBER" },
                  "fatG":       { "type": "NUMBER" },
                  "carbsG":     { "type": "NUMBER" },
                  "sugarG":     { "type": "NUMBER", "nullable": true },
                  "sodiumMg":   { "type": "NUMBER", "nullable": true },
                  "fiberG":     { "type": "NUMBER", "nullable": true },
                  "satFatG":    { "type": "NUMBER", "nullable": true },
                  "confidence": { "type": "NUMBER" }
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
