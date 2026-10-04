package com.watson.nutrilog.data

import android.content.Context
import com.watson.nutrilog.data.net.GitHubReleaseClient
import kotlinx.serialization.Serializable

/**
 * 上一次到 GitHub 查新版的結果。存在 DataStore 裡、和設定分開（見 [SettingsStore.updateStatusFlow]）。
 */
@Serializable
data class UpdateStatus(
    /** 最新正式版的 tag（"v2.2.8"）。空字串＝還沒查到過，或 GitHub 上一個正式版都沒有。 */
    val latestTag: String = "",
    /** 那一版的 release 頁面。 */
    val url: String = "",
    /** 發佈時間，ISO 8601，GitHub 原樣。 */
    val publishedAt: String = "",
    /** 上次查成功的時間（epoch 毫秒）。0＝從來沒有。 */
    val checkedAt: Long = 0,
    /**
     * 使用者已經在關於頁看過的那一版。報頭設定圖示上的紅點只在它和 [latestTag] 不一樣時出現 ——
     * 看過了還一直亮著，紅點就只是噪音；下一個新版出來它才會再亮。
     */
    val seenTag: String = "",
)

/**
 * [latest] 是不是比 [current] 新。比的是**每一段的數字**，不是字串：字串比的話 "2.2.10" < "2.2.9"。
 *
 * 回 null 表示**比不了**，呼叫端不要說「有新版」：
 * - 目前裝的不是 CI 發的正式版。本機建置的版號是「1.0-debug」（版號只有推 tag 時才會被
 *   覆寫，見 app/build.gradle.kts），拿它去比的話永遠是舊的，紅點會一直亮著。
 * - tag 不是版號的形狀。
 */
fun isNewerRelease(latest: String, current: String): Boolean? {
    val a = versionParts(latest) ?: return null
    val b = versionParts(current) ?: return null
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

private fun versionParts(raw: String): List<Int>? {
    val text = raw.trim().removePrefix("v")
    if (!VERSION_SHAPE.matches(text)) return null
    return text.split('.').map { it.toIntOrNull() ?: return null }
}

private val VERSION_SHAPE = Regex("""\d+(\.\d+)*""")

/**
 * 查一次並把結果存起來。關於頁那顆按鈕與每天的背景檢查（`UpdateCheckWorker`）共用這一份 ——
 * 各寫一份的話，兩邊存的欄位遲早會對不上。
 */
class UpdateChecker(context: Context) {

    private val store = SettingsStore(context)
    private val github = GitHubReleaseClient()

    /** 「看過沒」不在這裡改：那由關於頁在真的把新版顯示出來時決定（見 NutriViewModel.markUpdateSeen）。 */
    suspend fun check(): Result<UpdateStatus> = github.latest().mapCatching { release ->
        store.editUpdateStatus { old ->
            old.copy(
                latestTag = release?.tag.orEmpty(),
                url = release?.url.orEmpty(),
                publishedAt = release?.publishedAt.orEmpty(),
                checkedAt = System.currentTimeMillis(),
            )
        }
    }
}
