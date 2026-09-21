package com.watson.nutrilog

import com.watson.nutrilog.data.net.AiPrompts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoPromptTest {
    @Test
    fun `不填備註仍可直接辨識`() {
        assertEquals(AiPrompts.PHOTO_PROMPT, AiPrompts.photoRequest(""))
        assertEquals(AiPrompts.PHOTO_PROMPT, AiPrompts.photoRequest("  \n  "))
    }

    @Test
    fun `烹調備註完整附在照片指示後`() {
        val note = "水煮餐\n蔬菜與肉片沒加油，旁邊是白飯"
        val prompt = AiPrompts.photoRequest("  $note  ")
        assertTrue(prompt.startsWith(AiPrompts.PHOTO_PROMPT))
        assertTrue(prompt.endsWith(note))
        assertTrue(prompt.contains("烹調方式、食材與份量以此為準"))
    }
}
