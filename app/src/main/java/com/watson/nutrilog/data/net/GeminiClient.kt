package com.watson.nutrilog.data.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
 * Gemini 影像辨識。直接打 REST，不用官方 SDK ——
 * 只有這一支端點，手寫的量比引進整套 SDK 還少，而且不必跟著 SDK 改版走。
 */
class GeminiClient(private val client: OkHttpClient = SharedHttp.client) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun analyzeFood(
        base64Jpeg: String,
        apiKey: String,
        model: String,
    ): Result<List<DetectedFood>> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                putJsonArray("contents") {
                    addJsonObject {
                        putJsonArray("parts") {
                            addJsonObject { put("text", PROMPT) }
                            addJsonObject {
                                putJsonObject("inline_data") {
                                    put("mime_type", "image/jpeg")
                                    put("data", base64Jpeg)
                                }
                            }
                        }
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

            val call = runCatching { client.newCall(request).execute() }.getOrElse { cause ->
                // OkHttp 的逾時訊息是英文的 "timeout"，直接丟給使用者等於沒說。
                // 而且逾時最常見的成因是模型太慢，解法是換模型而不是重試。
                error(
                    when (cause) {
                        is SocketTimeoutException ->
                            "等太久了，連線逾時。這個模型可能思考時間較長 —— " +
                                "到設定頁換成 gemini-3.5-flash-lite 之類比較快的模型再試一次"
                        is IOException -> "連線失敗，請檢查網路"
                        else -> throw cause
                    }
                )
            }

            call.use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Gemini " + response.code + ": " + body.take(800))
                    error(explain(response.code, body))
                }
                val parsed = json.decodeFromString(GeminiResponse.serializer(), body)
                val text = parsed.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: error("模型沒有回傳內容")
                json.decodeFromString(AnalysisResult.serializer(), text).items
            }
        }
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
            in 500..599 -> "Gemini 服務暫時無法回應"
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
     * 兩個系列的參數不一樣，**混用會回 400**：
     *   Gemini 3.x        -> thinkingLevel（minimal 最接近關閉；flash 系列無法完全關）
     *   Gemini 2.5 Flash  -> thinkingBudget = 0（這一系列才能真的完全關閉）
     *
     * 2.5 **Pro** 特別排除：它的 thinkingBudget 下限是 128，不接受 0，
     * 送 0 會直接被打回 400。認不得的模型也整個不送，讓它用自己的預設 ——
     * 亂送不支援的欄位一樣是 400。
     */
    private fun thinkingConfigFor(model: String): JsonObject? {
        val id = model.trim().lowercase()
        return when {
            id.startsWith("gemini-3") -> buildJsonObject { put("thinkingLevel", "minimal") }
            id.startsWith("gemini-2.5-flash") -> buildJsonObject { put("thinkingBudget", 0) }
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
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
        val JSON_MEDIA = "application/json".toMediaType()

        val PROMPT = """
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
