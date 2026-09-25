package com.watson.nutrilog

import com.watson.nutrilog.data.net.ImageCompressor
import com.watson.nutrilog.data.net.ImageCompressor.Problem
import com.watson.nutrilog.ui.hundredMillionsLabel
import com.watson.nutrilog.ui.megabytesLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageLimitTest {

    @Test fun phoneSized200MpPhotoPasses() {
        assertNull(ImageCompressor.classify(bytes = 25_000_000, width = 16320, height = 12240))
    }

    @Test fun exactlyAtLimitsPasses() {
        assertNull(ImageCompressor.classify(bytes = 30_000_000, width = 25_000, height = 10_000))
    }

    @Test fun oneByteOverTheFileLimitIsBlocked() {
        assertEquals(Problem.TooManyBytes(30_000_001), ImageCompressor.classify(30_000_001, 4000, 3000))
    }

    @Test fun onePixelOverThePixelLimitIsBlocked() {
        assertEquals(Problem.TooManyPixels(250_000_001), ImageCompressor.classify(1_000_000, 250_000_001, 1))
    }

    /** 解壓縮炸彈：檔案很小、宣稱的畫素極大。只看檔案大小擋不住它。 */
    @Test fun decompressionBombIsBlockedByPixels() {
        assertEquals(
            Problem.TooManyPixels(100_000L * 100_000L),
            ImageCompressor.classify(bytes = 3_000_000, width = 100_000, height = 100_000),
        )
    }

    @Test fun unknownFileSizeFallsBackToPixels() {
        assertNull(ImageCompressor.classify(bytes = null, width = 4000, height = 3000))
        assertEquals(Problem.TooManyPixels(300_000_000), ImageCompressor.classify(null, 20_000, 15_000))
    }

    @Test fun headerThatCannotBeReadIsUnreadable() {
        assertEquals(Problem.Unreadable, ImageCompressor.classify(bytes = 50_000, width = -1, height = -1))
    }

    /** 無條件進位：剛好超過一點點時不能顯示成和上限一樣的數字。 */
    @Test fun labelsRoundUpSoOverLimitNeverLooksEqual() {
        assertEquals("2.5", hundredMillionsLabel(ImageCompressor.MAX_PIXELS))
        assertEquals("2.6", hundredMillionsLabel(250_000_001))
        assertEquals("3.2", hundredMillionsLabel(320_000_000))
        assertEquals(30L, megabytesLabel(ImageCompressor.MAX_BYTES))
        assertEquals(31L, megabytesLabel(30_000_001))
    }
}
