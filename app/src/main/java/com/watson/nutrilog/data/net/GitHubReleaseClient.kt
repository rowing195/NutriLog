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
    /** 那一版附的 APK。CI 每次都會附一顆，沒有的話只能開 [url] 讓使用者自己看。 */
    val apk: ReleaseApk? = null,
)

/** release 附的 APK：程式內下載要的三樣東西。 */
data class ReleaseApk(
    val url: String,
    /** bytes。PackageInstaller 開 session 要先知道長度，進度線的分母也是它。 */
    val size: Long,
    /** 小寫十六進位。GitHub 有給才有（2025 年中以後上傳的資產都有），沒有就不核對。 */
    val sha256: String?,
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

/** 回應裡只取 tag、頁面、發佈時間與 APK 那一個資產；其餘（作者、本文）用不到。 */
internal fun parseLatestRelease(body: String): LatestRelease? {
    val raw = json.decodeFromString(ReleaseResponse.serializer(), body)
    if (raw.tagName.isBlank()) return null
    val apk = raw.assets.firstOrNull { it.name.endsWith(".apk") && it.url.isNotBlank() && it.size > 0 }
    return LatestRelease(
        tag = raw.tagName,
        url = raw.htmlUrl,
        publishedAt = raw.publishedAt,
        apk = apk?.let {
            ReleaseApk(
                url = it.url,
                size = it.size,
                sha256 = it.digest?.takeIf { d -> d.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase(),
            )
        },
    )
}

@Serializable
private data class ReleaseResponse(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("published_at") val publishedAt: String = "",
    val assets: List<ReleaseAsset> = emptyList(),
)

@Serializable
private data class ReleaseAsset(
    val name: String = "",
    @SerialName("browser_download_url") val url: String = "",
    val size: Long = 0,
    /** "sha256:4ecd…"。 */
    val digest: String? = null,
)
