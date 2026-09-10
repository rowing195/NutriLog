package com.watson.nutrilog.data.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
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
 * AI 健檢報告客戶端（支援 OpenRouter 與 NVIDIA NIM 端點）。
 *
 * 預設使用 OpenRouter 免費高速推理模型 `nvidia/nemotron-3-ultra-550b-a55b:free`，
 * 預設使用 OpenRouter 免費高速推理模型 `nvidia/nemotron-3.5-lightning:free`，
 * 同時相容 NVIDIA NIM API 端點，將飲食紀錄與 Samsung Health 運動數據轉化為深度報告。
 */
class NvidiaClient(private val client: OkHttpClient = SharedHttp.client) {

    private val json = Json { ignoreUnknownKeys = true }

    // 週報生成長文本需要較長的等待時間，設定 90 秒逾時
    private val longTimeoutClient by lazy {
        client.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 呼叫 OpenRouter / NVIDIA NIM API 生成每週健康週報與卡路里推薦。
     *
     * @param apiKey 使用者填入的 API Key (OpenRouter 或 NVIDIA NIM)
     * @param model 預設 DEFAULT_MODEL ("nvidia/nemotron-3-ultra-550b-a55b:free")
     * @param model 預設 DEFAULT_MODEL ("nvidia/nemotron-3.5-lightning:free")
     * @param systemPrompt 角色與輸出格式規範（含 JSON targets 標籤）
     * @param userPrompt 本週 7 天飲食與手錶運動數據表格
     */
    suspend fun generateWeeklyReport(
        apiKey: String,
        model: String = DEFAULT_MODEL,
        systemPrompt: String,
        userPrompt: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("尚未填入 OpenRouter / NVIDIA API Key"))
        }

        val trimmedKey = apiKey.trim()
        val isNvidiaNim = trimmedKey.startsWith("nvapi-")
        val invokeUrl = if (isNvidiaNim) NVIDIA_NIM_URL else OPENROUTER_URL

        // 若使用 NVIDIA NIM 但帶入的是 OpenRouter 的 free 標籤模型，自動切換至相容模型
        val targetModel = if (isNvidiaNim && model.contains(":free")) {
            NVIDIA_FALLBACK_MODEL
        } else {
            model.ifBlank { DEFAULT_MODEL }
        }

        val requestBody = buildJsonObject {
            put("model", targetModel)
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
            put("temperature", 0.7)
            put("top_p", 0.95)
            put("max_tokens", 4096)
            put("stream", false)
            if (isNvidiaNim) {
                put("seed", 42)
            }
        }.toString().toRequestBody("application/json".toMediaType())

        val requestBuilder = Request.Builder()
            .url(invokeUrl)
            .addHeader("Authorization", "Bearer $trimmedKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)

        if (!isNvidiaNim) {
            requestBuilder.addHeader("HTTP-Referer", "https://github.com/rowing195/NutriLog")
            requestBuilder.addHeader("X-Title", "NutriLog")
        }

        val request = requestBuilder.build()
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting AI report generation request to $invokeUrl (model: $targetModel)...")

        try {
            longTimeoutClient.newCall(request).execute().use { response ->
                val elapsedMs = System.currentTimeMillis() - startTime
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errorDetail = try {
                        val obj = json.parseToJsonElement(bodyString).jsonObject
                        obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                            ?: obj["message"]?.jsonPrimitive?.content
                            ?: bodyString
                    } catch (e: Exception) {
                        bodyString
                    }
                    val providerName = if (isNvidiaNim) "NVIDIA NIM" else "OpenRouter"
                    val msg = when (response.code) {
                        401 -> "$providerName API Key 無效，請確認設定頁填入的 Key 是否正確"
                        402 -> "OpenRouter 額度不足，請檢查帳號額度或稍後重試"
                        403 -> "權限不足，請檢查 $providerName API 帳號權限"
                        429 -> "$providerName API 請求頻率已達上限（免費模型有每分鐘次數限制），請稍候 30 秒再試"
                        else -> "$providerName API 請求失敗 (${response.code}) [耗時 ${elapsedMs}ms]：$errorDetail"
                    }
                    Log.e(TAG, msg)
                    return@withContext Result.failure(IOException(msg))
                }

                val parsed = json.parseToJsonElement(bodyString).jsonObject
                val choices = parsed["choices"]?.jsonArray
                if (choices.isNullOrEmpty()) {
                    return@withContext Result.failure(IOException("API 回傳內容為空"))
                }
                val content = choices[0].jsonObject["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content
                    ?: return@withContext Result.failure(IOException("無法解析報告回傳內容"))

                Log.d(TAG, "Successfully generated report with $targetModel in ${elapsedMs}ms (${content.length} chars)")
                Result.success(content)
            }
        } catch (e: SocketTimeoutException) {
            val elapsedMs = System.currentTimeMillis() - startTime
            Log.e(TAG, "AI 報告 API 連線逾時 (耗時 ${elapsedMs}ms)", e)
            Result.failure(IOException("連線逾時（已等待 ${elapsedMs / 1000} 秒），AI 生成報告需要較多時間，請檢查網路後重試"))
        } catch (e: Exception) {
            val elapsedMs = System.currentTimeMillis() - startTime
            Log.e(TAG, "AI 報告 API 呼叫異常 (耗時 ${elapsedMs}ms)", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "NvidiaClient"
        const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
        const val NVIDIA_NIM_URL = "https://integrate.api.nvidia.com/v1/chat/completions"
        const val DEFAULT_MODEL = "nvidia/nemotron-3.5-lightning:free"
        const val NVIDIA_FALLBACK_MODEL = "deepseek-ai/deepseek-v4-pro-0813"
    }
}

