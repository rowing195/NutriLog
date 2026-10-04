package com.watson.nutrilog

import com.watson.nutrilog.ui.acceptsPortionText
import com.watson.nutrilog.ui.applyKey
import com.watson.nutrilog.ui.clampPortion
import com.watson.nutrilog.ui.typedPortion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortionInputTest {

    /** 模擬在份數格上依序按鍵：第一下覆蓋原值，超出格式的那一下無效。 */
    private fun type(start: String, vararg keys: String): String {
        var text = start
        var fresh = true
        for (key in keys) {
            val next = applyKey(text, key, fresh)
            if (acceptsPortionText(next)) text = next
            fresh = false
        }
        return text
    }

    @Test fun firstKeyReplacesTheOldValue() {
        assertEquals("2", type("1.5", "2"))
    }

    @Test fun decimalPointAfterFreshStartsWithZero() {
        assertEquals("0.5", type("1", ".", "5"))
    }

    @Test fun thirdIntegerDigitIsIgnored() {
        assertEquals("99", type("1", "9", "9", "9"))
    }

    @Test fun quarterPortionCanBeTyped() {
        assertEquals("0.25", type("1", ".", "2", "5"))
    }

    @Test fun thirdDecimalDigitIsIgnored() {
        assertEquals("1.25", type("1", "1", ".", "2", "5", "7"))
    }

    @Test fun backspaceCanEmptyTheField() {
        assertEquals("", type("1", "7", "⌫"))
    }

    @Test fun acceptedShapes() {
        listOf("", "0", "7", "12", "99", "0.", "1.", "1.5", "1.25", "99.99").forEach { assertTrue(it, acceptsPortionText(it)) }
        listOf("100", "1.255", "1..", "..", "-1").forEach { assertFalse(it, acceptsPortionText(it)) }
    }

    @Test fun leavingWithBlankKeepsTheOldMultiplier() {
        assertEquals(1.5, typedPortion("", 1.5), 0.0)
        assertEquals(1.5, typedPortion(".", 1.5), 0.0)
    }

    @Test fun leavingWithZeroGoesToTheLowerLimit() {
        assertEquals(0.01, typedPortion("0", 2.0), 0.0)
        assertEquals(0.01, typedPortion("0.", 2.0), 0.0)
    }

    @Test fun leavingWithAValueUsesIt() {
        assertEquals(2.5, typedPortion("2.5", 1.0), 0.0)
        assertEquals(0.25, typedPortion("0.25", 1.0), 0.0)
        assertEquals(0.05, typedPortion("0.05", 1.0), 0.0)
        assertEquals(99.0, typedPortion("99", 1.0), 0.0)
        assertEquals(12.0, typedPortion("12.", 1.0), 0.0)
    }

    @Test fun clampKeepsLimitsAndTwoDecimals() {
        assertEquals(0.01, clampPortion(0.0), 0.0)
        assertEquals(99.0, clampPortion(150.0), 0.0)
        // 浮點累加的尾巴要收掉
        assertEquals(1.7, clampPortion(1.6 + 0.1), 0.0)
        assertEquals(0.35, clampPortion(0.25 + 0.1), 0.0)
        // 從 0.25 往下按 −0.1 兩次：停在下限，不會變成負的
        assertEquals(0.05, clampPortion(0.15 - 0.1), 0.0)
        assertEquals(0.01, clampPortion(0.05 - 0.1), 0.0)
    }
}
