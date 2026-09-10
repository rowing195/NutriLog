package com.watson.nutrilog.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterFoodClientTest {

    private val client = OpenRouterFoodClient()

    @Test
    fun `parseResponseContent parses standard items object successfully`() {
        val json = """
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
              "confidence": 0.95
            }
          ]
        }
        """.trimIndent()

        val result = client.parseResponseContent(json)
        assertTrue(result.isSuccess)
        val foods = result.getOrThrow()
        assertEquals(1, foods.size)
        val food = foods[0]
        assertEquals("CoCo 珍珠奶茶", food.name)
        assertEquals("大杯 700ml 半糖", food.servingText)
        assertEquals(540.0, food.calories, 0.01)
        assertEquals(3.0, food.proteinG, 0.01)
        assertEquals(18.0, food.fatG, 0.01)
        assertEquals(91.0, food.carbsG, 0.01)
        assertEquals(48.0, food.sugarG!!, 0.01)
        assertEquals(110.0, food.sodiumMg!!, 0.01)
        assertEquals(0.95, food.confidence, 0.01)
    }

    @Test
    fun `parseResponseContent parses markdown fenced json with multiple options`() {
        val raw = """
        根據台灣手搖飲官方標示與資料檢索：
        ```json
        {
          "items": [
            {
              "name": "50嵐 珍珠奶茶",
              "servingText": "大杯 700ml 全糖",
              "calories": 650.0,
              "proteinG": 4.0,
              "fatG": 22.0,
              "carbsG": 105.0,
              "confidence": 0.92
            },
            {
              "name": "50嵐 珍珠奶茶",
              "servingText": "大杯 700ml 半糖",
              "calories": 520.0,
              "proteinG": 4.0,
              "fatG": 22.0,
              "carbsG": 75.0,
              "confidence": 0.92
            }
          ]
        }
        ```
        以上營養標示僅供參考。
        """.trimIndent()

        val result = client.parseResponseContent(raw)
        assertTrue(result.isSuccess)
        val foods = result.getOrThrow()
        assertEquals(2, foods.size)
        assertEquals("大杯 700ml 全糖", foods[0].servingText)
        assertEquals(650.0, foods[0].calories, 0.01)
        assertEquals("大杯 700ml 半糖", foods[1].servingText)
        assertEquals(520.0, foods[1].calories, 0.01)
        assertNull(foods[0].sugarG)
    }

    @Test
    fun `parseResponseContent parses raw array format`() {
        val json = """
        [
          {
            "name": "麥當勞 大麥克",
            "servingText": "1個 (約218g)",
            "calories": 544.0,
            "proteinG": 26.0,
            "fatG": 28.0,
            "carbsG": 44.0,
            "sodiumMg": 860.0,
            "confidence": 0.98
          }
        ]
        """.trimIndent()

        val result = client.parseResponseContent(json)
        assertTrue(result.isSuccess)
        val foods = result.getOrThrow()
        assertEquals(1, foods.size)
        assertEquals("麥當勞 大麥克", foods[0].name)
        assertEquals(544.0, foods[0].calories, 0.01)
        assertEquals(26.0, foods[0].proteinG, 0.01)
        assertEquals(860.0, foods[0].sodiumMg!!, 0.01)
    }

    @Test
    fun `parseResponseContent handles empty items gracefully`() {
        val json = """{"items": []}"""
        val result = client.parseResponseContent(json)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `extractJsonBlock correctly extracts nested json between braces`() {
        val text = "這裡是用戶查詢的結果：{\"items\": [{\"name\": \"茶葉蛋\", \"calories\": 75.0}]} 請查收。"
        val extracted = client.extractJsonBlock(text)
        assertTrue(extracted.startsWith("{"))
        assertTrue(extracted.endsWith("}"))
        val result = client.parseResponseContent(extracted)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals("茶葉蛋", result.getOrThrow()[0].name)
    }

    // ── 欄位命名不照 prompt 走的那幾種回應 ──────────────────────────
    //
    // 這幾個 case 全是真實症狀「熱量對、三大營養素卻是 0」的來源：
    // calories 在任何命名慣例下都長一樣，所以它永遠對；蛋白／脂肪／碳水
    // 一旦換個寫法就對不上，舊版的解析會安靜地填 0，看起來像模型沒估。

    @Test
    fun `parseResponseContent reads snake_case macro keys`() {
        val json = """
        {
          "items": [
            {
              "name": "滷肉飯",
              "serving_text": "1 碗 (約 250g)",
              "calories": 560.0,
              "protein_g": 15.2,
              "fat_g": 20.5,
              "carbs_g": 78.0,
              "sugar_g": 3.0,
              "sodium_mg": 980.0,
              "fiber_g": 1.5,
              "sat_fat_g": 7.2,
              "confidence": 0.85
            }
          ]
        }
        """.trimIndent()

        val food = client.parseResponseContent(json).getOrThrow().single()
        assertEquals("1 碗 (約 250g)", food.servingText)
        assertEquals(560.0, food.calories, 0.01)
        assertEquals(15.2, food.proteinG, 0.01)
        assertEquals(20.5, food.fatG, 0.01)
        assertEquals(78.0, food.carbsG, 0.01)
        assertEquals(3.0, food.sugarG!!, 0.01)
        assertEquals(980.0, food.sodiumMg!!, 0.01)
        assertEquals(1.5, food.fiberG!!, 0.01)
        assertEquals(7.2, food.satFatG!!, 0.01)
    }

    @Test
    fun `parseResponseContent reads macros nested under a nutrition object`() {
        val json = """
        {
          "items": [
            {
              "name": "茶葉蛋",
              "servingText": "1 顆",
              "calories": 75.0,
              "nutrition": {
                "protein": 6.3,
                "fat": 5.0,
                "carbohydrates": 0.6,
                "sodium": 210.0
              },
              "confidence": 0.9
            }
          ]
        }
        """.trimIndent()

        val food = client.parseResponseContent(json).getOrThrow().single()
        assertEquals(75.0, food.calories, 0.01)
        assertEquals(6.3, food.proteinG, 0.01)
        assertEquals(5.0, food.fatG, 0.01)
        assertEquals(0.6, food.carbsG, 0.01)
        assertEquals(210.0, food.sodiumMg!!, 0.01)
    }

    @Test
    fun `parseResponseContent reads numbers written with units`() {
        val json = """
        {
          "items": [
            {
              "name": "美式咖啡",
              "servingText": "中杯",
              "calories": "15 kcal",
              "proteinG": "0.5 g",
              "fatG": "0 g",
              "carbsG": "2.5g",
              "sodiumMg": "5 mg",
              "confidence": "0.8"
            }
          ]
        }
        """.trimIndent()

        val food = client.parseResponseContent(json).getOrThrow().single()
        assertEquals(15.0, food.calories, 0.01)
        assertEquals(0.5, food.proteinG, 0.01)
        assertEquals(0.0, food.fatG, 0.01)
        assertEquals(2.5, food.carbsG, 0.01)
        assertEquals(5.0, food.sodiumMg!!, 0.01)
        assertEquals(0.8, food.confidence, 0.01)
    }

    @Test
    fun `parseResponseContent keeps unknown micronutrients null instead of zero`() {
        val json = """
        {
          "items": [
            {
              "name": "水煮蛋",
              "servingText": "1 顆",
              "calories": 70.0,
              "proteinG": 6.0,
              "fatG": 5.0,
              "carbsG": 0.5,
              "sugarG": null,
              "confidence": 0.9
            }
          ]
        }
        """.trimIndent()

        val food = client.parseResponseContent(json).getOrThrow().single()
        assertNull(food.sugarG)
        assertNull(food.sodiumMg)
        assertNull(food.fiberG)
        assertNull(food.satFatG)
        assertNotNull(food.name)
    }

    // ── 回應被 max_tokens 截斷 ────────────────────────────────────
    //
    // 這支是 reasoning 模型，光「想」就吃掉一兩千個 token，撞到上限時
    // OpenRouter 會回 finish_reason=length，而 message.content 是 JSON 的 null。
    // 舊版把 JsonNull 當 primitive 讀，拿到的是字串 "null"，一路解析下去變成
    // 「成功，但 0 項食物」—— 畫面於是講「沒有辨識到食物」，把一次截斷說成
    // 模型不認得這個食物。這幾支測試就是釘住「不准再把截斷講成沒辨識到」。

    @Test
    fun `parseCompletion reads null content as blank instead of the string null`() {
        val body = """
        {
          "choices": [
            { "finish_reason": "length", "message": { "role": "assistant", "content": null } }
          ]
        }
        """.trimIndent()

        val chat = client.parseCompletion(body).getOrThrow()
        assertEquals("", chat.content)
        assertEquals("length", chat.finishReason)
    }

    @Test
    fun `interpretCompletion reports truncation instead of an empty food list`() {
        val truncated = OpenRouterFoodClient.ChatResult(content = "", finishReason = "length")
        val result = client.interpretCompletion(truncated)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is OpenRouterFoodClient.TruncatedCompletionException)
    }

    @Test
    fun `interpretCompletion treats half written json as truncation`() {
        // 實際抓到的樣子：JSON 才寫到一半就沒了
        val half = OpenRouterFoodClient.ChatResult(
            content = """
                ```json
                {
                  "items": [
                    {
                      "name": "奶酥雞蛋糕",
                      "calories": 140.0,
            """.trimIndent(),
            finishReason = "length",
        )
        val result = client.interpretCompletion(half)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is OpenRouterFoodClient.TruncatedCompletionException)
    }

    @Test
    fun `interpretCompletion keeps a genuine empty items answer as success`() {
        // 模型好好講完話、就是認定這不是食物 —— 那「沒有辨識到食物」才是對的
        val notFood = OpenRouterFoodClient.ChatResult(
            content = """{"items": []}""",
            finishReason = "stop",
        )
        val result = client.interpretCompletion(notFood)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `interpretCompletion returns foods when the model finished properly`() {
        val ok = OpenRouterFoodClient.ChatResult(
            content = """{"items":[{"name":"奶酥雞蛋糕","servingText":"1顆","calories":140.0,"proteinG":2.6,"fatG":7.6,"carbsG":15.0,"confidence":0.75}]}""",
            finishReason = "stop",
        )
        val food = client.interpretCompletion(ok).getOrThrow().single()

        assertEquals("奶酥雞蛋糕", food.name)
        assertEquals(140.0, food.calories, 0.01)
        assertEquals(2.6, food.proteinG, 0.01)
    }
}
