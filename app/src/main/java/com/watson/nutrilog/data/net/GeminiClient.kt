package com.watson.nutrilog.data.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
                }
            }

            val request = Request.Builder()
                .url(BASE_URL + model + ":generateContent")
                // key 走 header 而不是 ?key=，query string 會被各層代理與日誌記下來
                .header("x-goog-api-key", apiKey)
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    // 原始回應留在 logcat 就好。把一整包 JSON 印在畫面上，
                    // 使用者看不懂，還會把真正有用的那一句話擠掉。
                    Log.w(TAG, "Gemini " + response.code + ": " + body.take(500))
                    error(friendlyError(response.code))
                }
                val parsed = json.decodeFromString(GeminiResponse.serializer(), body)
                val text = parsed.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: error("模型沒有回傳內容")
                json.decodeFromString(AnalysisResult.serializer(), text).items
            }
        }
    }

    /** 把 HTTP status 翻成使用者看得懂的話。原始回應寫進 logcat，不進畫面。 */
    private fun friendlyError(code: Int): String = when (code) {
        400 -> "請求被拒絕，API key 可能不正確"
        401, 403 -> "API key 無效或沒有權限"
        404 -> "找不到這個模型，請到設定頁確認模型名稱"
        429 -> "已達 API 用量上限，稍後再試"
        in 500..599 -> "Gemini 服務暫時無法回應"
        else -> "Gemini 回應 HTTP " + code
    }

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
