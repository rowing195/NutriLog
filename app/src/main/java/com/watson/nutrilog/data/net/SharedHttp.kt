package com.watson.nutrilog.data.net

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 全 app 共用一個 OkHttpClient。
 *
 * OkHttp 的連線池與執行緒池都掛在 client 實例上，每次呼叫都 new 一個
 * 等於每次都重新握手、還會漏執行緒。官方的建議就是共用一個。
 */
object SharedHttp {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // 影像辨識要把圖傳上去再等模型回答，比一般 API 久得多。
        // 60 秒不夠：Gemini 3.x 預設開 thinking，實測會逾時。
        // GeminiClient 已經把 thinking 壓到最低，這裡再留一倍餘裕。
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}
