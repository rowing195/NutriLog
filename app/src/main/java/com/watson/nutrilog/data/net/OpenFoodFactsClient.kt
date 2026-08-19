package com.watson.nutrilog.data.net

import com.watson.nutrilog.data.db.CachedProduct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Open Food Facts 條碼查詢。
 *
 * 讀取不需要 API key，但**一定要帶自訂 User-Agent**，這是 OFF 明文要求的；
 * 用預設的 UA 會被擋掉。另外讀取端點限制每個 IP 每分鐘 15 次 ——
 * 這就是查到的東西要存進 [CachedProduct] 的原因。
 */
class OpenFoodFactsClient(private val client: OkHttpClient = SharedHttp.client) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @return 成功但沒這個商品 -> `success(null)`；連線或解析失敗 -> `failure`。
     *   兩者要分得開，UI 才能一邊說「查無此商品」一邊說「連線失敗」。
     */
    suspend fun lookup(barcode: String): Result<CachedProduct?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(BASE_URL + barcode + ".json?fields=" + FIELDS)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                // 404 是「沒這個商品」，不是壞掉，所以不當成錯誤丟出去
                if (response.code == 404) return@use null
                if (!response.isSuccessful) error("HTTP " + response.code)
                val body = response.body?.string().orEmpty()
                val parsed = json.decodeFromString(OffResponse.serializer(), body)
                val product = parsed.product
                if (parsed.status != 1 || product == null) return@use null
                product.toCached(barcode)
            }
        }
    }

    @Serializable
    private data class OffResponse(
        val status: Int = 0,
        val product: OffProduct? = null,
    )

    @Serializable
    private data class OffProduct(
        @SerialName("product_name") val productName: String? = null,
        val brands: String? = null,
        @SerialName("serving_size") val servingSize: String? = null,
        // 營養素刻意收成原始 JSON：OFF 是群眾貢獻的資料，同一個欄位
        // 有時是數字有時是字串，宣告成 Double? 會讓整筆商品解析失敗。
        val nutriments: Map<String, JsonElement> = emptyMap(),
    ) {
        fun toCached(barcode: String) = CachedProduct(
            barcode = barcode,
            // 沒有品名就退回品牌，再不行就用條碼本身，至少不會出現空白的一列
            name = productName?.takeIf { it.isNotBlank() }
                ?: brands?.takeIf { it.isNotBlank() }
                ?: barcode,
            brand = brands?.takeIf { it.isNotBlank() },
            servingSizeText = servingSize?.takeIf { it.isNotBlank() },
            caloriesPer100g = nutriments.num("energy-kcal_100g"),
            proteinPer100g = nutriments.num("proteins_100g"),
            fatPer100g = nutriments.num("fat_100g"),
            carbsPer100g = nutriments.num("carbohydrates_100g"),
            sugarPer100g = nutriments.num("sugars_100g"),
            // OFF 的 sodium 單位是公克，但營養標示與使用者的直覺都是毫克
            sodiumMgPer100g = nutriments.num("sodium_100g")?.times(1000),
            fiberPer100g = nutriments.num("fiber_100g"),
            satFatPer100g = nutriments.num("saturated-fat_100g"),
            fetchedAt = System.currentTimeMillis(),
        )
    }

    private companion object {
        const val BASE_URL = "https://world.openfoodfacts.org/api/v2/product/"
        const val FIELDS = "code,product_name,brands,serving_size,nutriments"

        // OFF 要求格式為 AppName/Version (contact)。這裡放專案網址而不是個人信箱，
        // 因為原始碼是公開的，信箱寫進去等於直接被爬蟲收走。
        const val USER_AGENT = "NutriLog/1.0 (https://github.com/rowing195/NutriLog)"
    }
}

/** 數字有時被記成字串（"12.5"），兩種都要吃得下來。 */
private fun Map<String, JsonElement>.num(key: String): Double? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return primitive.doubleOrNull ?: primitive.contentOrNull?.trim()?.toDoubleOrNull()
}
