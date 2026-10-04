package com.watson.nutrilog.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** GitHub 上最新的那一個正式版。 */
data class LatestRelease(
    /** "v2.2.8"，和推上去的 tag 一字不差。 */
    val tag: String,
    /** 那一版的 release 頁面，看得到更新內容與 APK。 */
    val url: String,
    /** ISO 8601（"2026-10-04T03:18:14Z"），GitHub 原樣。 */
    val publishedAt: String,
)

/**
 * 查 GitHub 上最新的正式版。
 *
 * repo 是公開的，**不帶 token**：不用 token 每小時有 60 次，一天查一次加上偶爾手動按，
 * 遠遠用不完；帶 token 反而得把一把金鑰編進 APK。`releases/latest` 本身就會略過
 * draft 與 prerelease，所以回來的一定是推 `v*` tag 發出去的那種正式版。
 */
class GitHubReleaseClient(private val client: OkHttpClient = SharedHttp.client) {

    /** 查不到任何正式版回 null；連不上是 IOException、被限流是 [RateLimited]。 */
    suspend fun latest(): Result<LatestRelease?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_URL)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when (response.code) {
                    in 200..299 -> parseLatestRelease(body)
                    // 一個正式版都還沒有時 GitHub 回 404，那不是錯誤
                    404 -> null
                    403, 429 -> throw RateLimited()
                    else -> error("GitHub " + response.code)
                }
            }
        }
    }

    /** 每小時 60 次的上限用完了。重試沒有意義，要等它自己重置。 */
    class RateLimited : Exception("GitHub rate limited")

    private companion object {
        const val LATEST_URL = "https://api.github.com/repos/rowing195/NutriLog/releases/latest"
    }
}

private val json = Json { ignoreUnknownKeys = true }

/** 回應裡只取這三欄；其餘（作者、資產清單、本文）用不到。 */
internal fun parseLatestRelease(body: String): LatestRelease? {
    val raw = json.decodeFromString(ReleaseResponse.serializer(), body)
    if (raw.tagName.isBlank()) return null
    return LatestRelease(tag = raw.tagName, url = raw.htmlUrl, publishedAt = raw.publishedAt)
}

@Serializable
private data class ReleaseResponse(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("published_at") val publishedAt: String = "",
)
