package com.watson.nutrilog.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** OpenRouter 上一個這個 app 用得了的模型。 */
data class OpenRouterModel(
    /** "google/gemma-4-31b-it:free"，存進設定、送給 API 的就是這一串。 */
    val id: String,
    /** 每一百萬個 output token 的美元價格。0＝免費。 */
    val outputPerMillion: Double,
    /** Artificial Analysis 的智力指數（OpenRouter 轉載的）。沒測過的模型是 null。 */
    val intelligence: Double? = null,
)

/**
 * 查 OpenRouter 的模型清單，只留**看得懂圖片、又支援 tools** 的。
 *
 * 文字與拍照共用同一個 OpenRouter 模型，所以兩邊都要看得懂圖片；tools 則是
 * [OpenRouterClient] 鎖住 JSON 格式的唯一手段（見它的說明），不支援的選了也用不了。
 * 這支 API 是公開的，不用金鑰 —— 還沒填 key 的人也挑得了模型。
 */
class OpenRouterModelCatalog(private val client: OkHttpClient = SharedHttp.client) {

    suspend fun fetch(): Result<List<OpenRouterModel>> = withContext(Dispatchers.IO) {
        runCatching {
            // 在伺服器端先篩掉不支援 tools 的，回應小一成多；看不看得懂圖片 API 沒有參數能篩，在下面自己篩
            val request = Request.Builder().url("$MODELS_URL?supported_parameters=tools").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("OpenRouter models " + response.code)
                parseVisionToolModels(response.body?.string().orEmpty())
            }
        }
    }

    private companion object {
        const val MODELS_URL = "https://openrouter.ai/api/v1/models"
    }
}

// 外部資料：某一筆的欄位哪天變成 null，退回該欄預設值就好，不要讓整份清單解析失敗
private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/**
 * 依智力指數由高到低排，沒有分數的排最後，同分依名稱。照價格排試過：便宜的排前面
 * 等於把最弱的排前面，挑模型時真正在比的是「夠不夠聰明」，價格是看一眼的事。
 *
 * 不列的兩種：
 * - `openrouter/auto` 那類路由器：價格是 -1（看轉給誰而定），沒有一個數字可以標。
 * - `:batch` 版本：同一個模型的批次處理版（價格打對折），清單裡幾乎每個模型都會重複一次。
 */
internal fun parseVisionToolModels(body: String): List<OpenRouterModel> =
    json.decodeFromString(ModelsResponse.serializer(), body).data
        .filter { "image" in it.architecture.inputModalities && "tools" in it.supportedParameters }
        .filterNot { it.id.endsWith(":batch") }
        .mapNotNull { raw ->
            val perToken = raw.pricing.completion?.toDoubleOrNull() ?: return@mapNotNull null
            if (perToken < 0) return@mapNotNull null
            OpenRouterModel(raw.id, perToken * 1_000_000, raw.benchmarks?.artificialAnalysis?.intelligenceIndex)
        }
        .sortedWith(
            compareBy<OpenRouterModel> { it.intelligence == null }
                .thenByDescending { it.intelligence ?: 0.0 }
                .thenBy { it.id }
        )

@Serializable
private data class ModelsResponse(val data: List<RawModel> = emptyList())

@Serializable
private data class RawModel(
    val id: String,
    val architecture: Architecture = Architecture(),
    val pricing: Pricing = Pricing(),
    @SerialName("supported_parameters") val supportedParameters: List<String> = emptyList(),
    val benchmarks: Benchmarks? = null,
)

@Serializable
private data class Benchmarks(
    @SerialName("artificial_analysis") val artificialAnalysis: ArtificialAnalysis? = null,
)

@Serializable
private data class ArtificialAnalysis(
    @SerialName("intelligence_index") val intelligenceIndex: Double? = null,
)

@Serializable
private data class Architecture(
    @SerialName("input_modalities") val inputModalities: List<String> = emptyList(),
)

/** 價格是**每個 token**的美元，而且是字串（"0.00000018"）。 */
@Serializable
private data class Pricing(val completion: String? = null)
