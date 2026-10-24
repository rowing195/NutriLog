package com.watson.nutrilog

import com.watson.nutrilog.data.NutriSettings
import com.watson.nutrilog.data.net.parseVisionToolModels
import com.watson.nutrilog.data.withMergedOpenRouterModel
import com.watson.nutrilog.ui.outputPriceLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * OpenRouter 模型選單的三件事：清單只留用得了的並照智力排、價格怎麼標、舊設定的兩個欄位怎麼併成一個。
 */
class OpenRouterModelsTest {

    private fun model(
        id: String,
        input: String,
        tools: Boolean,
        completion: String,
        intelligence: Double? = null,
    ) = """
        {
          "id": "$id",
          "architecture": { "input_modalities": [$input] },
          "pricing": { "prompt": "0", "completion": "$completion" },
          "supported_parameters": [${if (tools) "\"tools\", \"tool_choice\"" else "\"max_tokens\""}],
          "benchmarks": { "design_arena": [], "artificial_analysis": { "intelligence_index": $intelligence, "coding_index": null } }
        }
    """

    @Test fun keepsOnlyVisionModelsWithTools() {
        val body = """{ "data": [
            ${model("a/vision-tools", "\"text\", \"image\"", tools = true, completion = "0.0000002")},
            ${model("b/text-only", "\"text\"", tools = true, completion = "0.0000001")},
            ${model("c/vision-no-tools", "\"text\", \"image\"", tools = false, completion = "0.0000001")}
        ] }"""

        assertEquals(listOf("a/vision-tools"), parseVisionToolModels(body).map { it.id })
    }

    /** openrouter/auto 那類路由器的價格是 -1，沒有一個數字可以標。 */
    @Test fun dropsRoutersWithVariablePrice() {
        val body = """{ "data": [
            ${model("openrouter/auto", "\"text\", \"image\"", tools = true, completion = "-1")},
            ${model("a/real", "\"text\", \"image\"", tools = true, completion = "0")}
        ] }"""

        assertEquals(listOf("a/real"), parseVisionToolModels(body).map { it.id })
    }

    /** 照智力指數由高到低，不是照價格：便宜的排前面等於把最弱的排前面。 */
    @Test fun sortsSmartestFirstAndConvertsPricePerMillion() {
        val body = """{ "data": [
            ${model("cheap/weak", "\"image\"", tools = true, completion = "0", intelligence = 15.0)},
            ${model("pricey/smart", "\"image\"", tools = true, completion = "0.00001", intelligence = 57.6)},
            ${model("mid/mid", "\"image\"", tools = true, completion = "0.0000002", intelligence = 40.0)}
        ] }"""

        val models = parseVisionToolModels(body)

        assertEquals(listOf("pricey/smart", "mid/mid", "cheap/weak"), models.map { it.id })
        assertEquals(57.6, models.first().intelligence!!, 1e-9)
        assertEquals(10.0, models.first().outputPerMillion, 1e-9)
    }

    /** 沒測過的模型沒有分數，不能當成 0 分混進中間，排在最後、彼此依名稱。 */
    @Test fun unscoredModelsGoLastByName() {
        val body = """{ "data": [
            ${model("z/unscored", "\"image\"", tools = true, completion = "0")},
            ${model("a/unscored", "\"image\"", tools = true, completion = "0")},
            ${model("m/scored", "\"image\"", tools = true, completion = "0", intelligence = 10.0)}
        ] }"""

        assertEquals(listOf("m/scored", "a/unscored", "z/unscored"), parseVisionToolModels(body).map { it.id })
    }

    /** :batch 是同一個模型的批次版，留著的話清單裡幾乎每個模型都出現兩次。 */
    @Test fun dropsBatchVariants() {
        val body = """{ "data": [
            ${model("anthropic/claude-opus-5.5", "\"image\"", tools = true, completion = "0.00002", intelligence = 57.6)},
            ${model("anthropic/claude-opus-5.5:batch", "\"image\"", tools = true, completion = "0.00001", intelligence = 57.6)}
        ] }"""

        assertEquals(listOf("anthropic/claude-opus-5.5"), parseVisionToolModels(body).map { it.id })
    }

    @Test fun priceLabels() {
        assertEquals("免費", outputPriceLabel(0.0, "免費"))
        assertEquals("$0.18", outputPriceLabel(0.18000000000000002, "免費"))
        assertEquals("$180.00", outputPriceLabel(180.0, "免費"))
        // 不到一分錢不能四捨五入成 $0.00，那會讀成免費
        assertEquals("<$0.01", outputPriceLabel(0.002, "免費"))
    }

    @Test fun photoModelWinsWhenMerging() {
        val merged = NutriSettings(openRouterModel = "text/model", openRouterPhotoModel = "vision/model")
            .withMergedOpenRouterModel()

        assertEquals("vision/model", merged.openRouterModel)
        assertEquals("", merged.openRouterPhotoModel)
    }

    @Test fun textModelKeptWhenPhotoWasNeverSet() {
        val merged = NutriSettings(openRouterModel = "text/model").withMergedOpenRouterModel()

        assertEquals("text/model", merged.openRouterModel)
    }

    /** 舊預設已經下架，而 encodeDefaults 把它寫進了每個人的設定。 */
    @Test fun retiredDefaultBecomesNotChosen() {
        val merged = NutriSettings(openRouterModel = NutriSettings.RETIRED_OPENROUTER_MODEL)
            .withMergedOpenRouterModel()

        assertEquals("", merged.openRouterModel)
    }

    /** 每次讀設定都會跑一次，跑第二次不能再改動任何東西。 */
    @Test fun mergingIsIdempotent() {
        val once = NutriSettings(openRouterModel = "text/model", openRouterPhotoModel = "vision/model")
            .withMergedOpenRouterModel()

        assertEquals(once, once.withMergedOpenRouterModel())
    }
}
