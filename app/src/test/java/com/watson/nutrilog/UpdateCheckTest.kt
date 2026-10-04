package com.watson.nutrilog

import com.watson.nutrilog.data.isNewerRelease
import com.watson.nutrilog.data.net.LatestRelease
import com.watson.nutrilog.data.net.parseLatestRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 檢查更新的兩個純函式：版號比較與 GitHub 回應解析。
 *
 * 版號比較守的是兩件事：**比數字不比字串**（2.2.10 比 2.2.9 新），以及**比不了的時候
 * 不能說有新版** —— 本機建置的版號是「1.0-debug」，拿它去比會永遠是舊的，報頭的紅點就關不掉了。
 */
class UpdateCheckTest {

    @Test fun newerPatchIsNewer() {
        assertEquals(true, isNewerRelease("v2.2.8", "2.2.7"))
    }

    @Test fun sameVersionIsNotNewer() {
        assertEquals(false, isNewerRelease("v2.2.7", "2.2.7"))
    }

    @Test fun olderIsNotNewer() {
        assertEquals(false, isNewerRelease("v2.2.6", "2.2.7"))
    }

    /** 字串比的話 "2.2.10" < "2.2.9"。 */
    @Test fun comparesNumbersNotStrings() {
        assertEquals(true, isNewerRelease("v2.2.10", "2.2.9"))
        assertEquals(true, isNewerRelease("v2.10.0", "2.9.9"))
    }

    @Test fun missingTrailingPartCountsAsZero() {
        assertEquals(false, isNewerRelease("v2.3", "2.3.0"))
        assertEquals(true, isNewerRelease("v2.3.1", "2.3"))
    }

    @Test fun localDebugBuildCannotBeCompared() {
        assertNull(isNewerRelease("v2.2.8", "1.0-debug"))
    }

    @Test fun tagThatIsNotAVersionCannotBeCompared() {
        assertNull(isNewerRelease("nightly", "2.2.7"))
        assertNull(isNewerRelease("", "2.2.7"))
    }

    @Test fun parsesTheThreeFieldsItNeeds() {
        val body = """
            {
              "url": "https://api.github.com/repos/rowing195/NutriLog/releases/1",
              "html_url": "https://github.com/rowing195/NutriLog/releases/tag/v2.2.7",
              "tag_name": "v2.2.7",
              "draft": false,
              "prerelease": false,
              "published_at": "2026-10-04T03:18:14Z",
              "assets": [{ "browser_download_url": "https://example.invalid/NutriLog-v2.2.7.apk" }]
            }
        """.trimIndent()

        assertEquals(
            LatestRelease(
                tag = "v2.2.7",
                url = "https://github.com/rowing195/NutriLog/releases/tag/v2.2.7",
                publishedAt = "2026-10-04T03:18:14Z",
            ),
            parseLatestRelease(body),
        )
    }

    @Test fun responseWithoutATagIsNoRelease() {
        assertNull(parseLatestRelease("""{ "message": "Not Found" }"""))
    }
}
