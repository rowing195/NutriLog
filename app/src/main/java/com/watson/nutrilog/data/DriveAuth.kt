package com.watson.nutrilog.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 取得能寫 Drive 的 access token。
 *
 * 走 Identity 的 **AuthorizationClient**，不是 `GoogleSignIn`（已淘汰）。兩者的差別
 * 不只是新舊：這個 app 沒有帳號系統，需要的從來不是「這個人是誰」，而是「能不能寫
 * 進你的 Drive」。AuthorizationClient 要的正好就是後者，所以連使用者的姓名、頭像都
 * 不會拿到（帳號 email 是之後跟 Drive 問的，見 [DriveClient.accountEmail]）。
 *
 * **app 裡不需要放任何 client id。** Android 的 OAuth client 是靠「套件名 + 簽章
 * SHA-1」認的，所以要設定的東西全在 Google Cloud Console 那一側 ——
 * 見 `tools/setup-google-drive.sh`。這也表示：**debug 與 release 兩組 SHA-1 都要
 * 註冊**，只註冊一組的話另一種簽章的 APK 會在授權時失敗。
 *
 * 第一次要使用者同意（[Outcome.NeedsConsent]，得由 Activity 把 PendingIntent 送出去），
 * 之後再呼叫就會直接拿到權杖 —— 包含背景排程那次，所以每天的自動備份不需要使用者在場。
 */
class DriveAuth(context: Context) {

    private val client = Identity.getAuthorizationClient(context.applicationContext)

    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SCOPE_DRIVE_FILE)))
        .build()

    sealed interface Outcome {
        data class Token(val accessToken: String) : Outcome
        /** 還沒授權過（或使用者收回了）。要在 Activity 裡把這個 PendingIntent 送出去。 */
        data class NeedsConsent(val pendingIntent: PendingIntent) : Outcome
    }

    suspend fun authorize(): Result<Outcome> = suspendCancellableCoroutine { cont ->
        client.authorize(request)
            .addOnSuccessListener { result ->
                val pending = result.pendingIntent
                val outcome = when {
                    result.hasResolution() && pending != null -> Outcome.NeedsConsent(pending)
                    // 有 resolution 但拿不到 PendingIntent 是不該發生的組合，
                    // 與其回一個看起來成功的空權杖，不如當成失敗講清楚
                    result.hasResolution() -> null
                    else -> result.accessToken?.let(Outcome::Token)
                }
                cont.resume(
                    if (outcome != null) Result.success(outcome)
                    else Result.failure(IllegalStateException("授權沒有回傳權杖"))
                )
            }
            .addOnFailureListener { cont.resume(Result.failure(it)) }
    }

    /** 把同意畫面回來的 Intent 換成權杖。 */
    fun tokenFromConsent(data: Intent?): Result<String> = runCatching {
        client.getAuthorizationResultFromIntent(data).accessToken
            ?: error("同意畫面沒有回傳權杖")
    }

    companion object {
        /**
         * 只要 drive.file：只碰得到這個 app 自己建立的檔案。
         *
         * 不是為了保守而保守 —— drive / drive.readonly 是 Google 定義的**受限範圍**，
         * 上架要通過付費的安全評估、跑好幾週。drive.file 不用，而備份需要的就只有它。
         */
        const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    }
}
