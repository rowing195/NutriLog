package com.watson.nutrilog.data.net

/**
 * 送給模型的兩份 prompt。
 *
 * 放在 client 外面，是因為**它們是共用的領域知識，client 只是傳輸層** ——
 * 同一段話 Gemini 與 OpenRouter 都要用，各自抄一份遲早會漂，而漂掉的症狀是
 * 「換一家之後回來的東西長得不一樣」，很難聯想到是 prompt 不同步。
 *
 * 改這裡等於同時改兩條路，回歸時兩家都要測。
 */
internal object AiPrompts {

    /**
     * 組出文字辨識要送出去的那一段。兩家 client 共用這一份，不要各組各的 ——
     * 「換一家之後回來的東西長得不一樣」最常見的成因就是這裡少接了一段。
     *
     * 搜尋結果放在**使用者輸入前面**，並且明講它是參考資料：它是外部來的、可能過期
     * 或根本在講別的品項（實測搜尋結果裡混著部落格整理的表格，數字和官方頁差了將近
     * 100 大卡），所以要讓模型知道優先順序，而不是把它當事實照抄。
     */
    fun textRequest(description: String, searchContext: String?): String = buildString {
        append(TEXT_PROMPT)
        if (!searchContext.isNullOrBlank()) {
            append("\n\n以下是剛剛查到的網路資料，供你參考。")
            append("**官方或品牌自己公布的營養標示優先**，")
            append("部落格或新聞整理的表格可能過期或算法不同；")
            append("和使用者問的不是同一個品項的就直接忽略。\n\n")
            append(searchContext)
        }
        append("\n\n使用者輸入：")
        append(description)
    }


    val PHOTO_PROMPT = """
        你是營養師。看這張食物照片，列出裡面每一種可辨識的食物。

        規則：
        - 依照片中看得到的份量估算，不要用「每 100 公克」的通用值。
        - servingText 要寫成人看得懂的份量，例如「1 碗（約 250 公克）」。
        - name 用繁體中文。
        - calories 單位 kcal；proteinG / fatG / carbsG / sugarG / fiberG / satFatG 單位公克；sodiumMg 單位毫克。
        - 沒把握的營養素就填 null，不要猜 0。
        - **數字只填純數值**：去掉千分位逗號與單位。來源寫「1,092.5 mg」就填
          1092.5 —— 不要因為它有逗號、格式不合就改填 null。
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
        - **數字只填純數值**：去掉千分位逗號與單位。來源寫「1,092.5 mg」就填
          1092.5 —— 不要因為它有逗號、格式不合就改填 null。
        - confidence 是 0 到 1 之間的數字。連鎖店有公開營養標示的給高一點，純估算的給低一點。
        - 完全看不懂在講什麼食物就回傳空的 items 陣列。
    """.trimIndent()
}
