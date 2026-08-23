package com.watson.nutrilog

import com.watson.nutrilog.data.db.FoodEntry
import com.watson.nutrilog.data.net.DetectedFood
import com.watson.nutrilog.ui.EntryDraft
import com.watson.nutrilog.ui.scale
import com.watson.nutrilog.ui.scaleServingText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class NutrientScalingTest {

    @Test
    fun `scaleServingText scales grams and milliliters accurately`() {
        assertEquals("300g", scaleServingText("200g", 1.5))
        assertEquals("100g", scaleServingText("200g", 0.5))
        assertEquals("200g", scaleServingText("200g", 1.0))
        assertEquals("400g", scaleServingText("200g", 2.0))
        assertEquals("1050ml", scaleServingText("700ml", 1.5))
        assertEquals("1400ml", scaleServingText("700ml", 2.0))
        assertEquals("350ml", scaleServingText("700ml", 0.5))
    }

    @Test
    fun `scaleServingText scales bowls and complex quantities`() {
        assertEquals("1.5 碗", scaleServingText("1 碗", 1.5))
        assertEquals("0.5 碗", scaleServingText("1 碗", 0.5))
        assertEquals("2 碗", scaleServingText("1 碗", 2.0))
        assertEquals("1.5 碗 (375g)", scaleServingText("1 碗 (250g)", 1.5))
        assertEquals("2 碗 (500g)", scaleServingText("1 碗 (250g)", 2.0))
        assertEquals("半碗 (1.5x)", scaleServingText("半碗", 1.5))
        assertEquals("1.5 份", scaleServingText("", 1.5))
        assertEquals("", scaleServingText("", 1.0))
    }

    @Test
    fun `scale on DetectedFood scales calories to integer and macros to 1 decimal place`() {
        val base = DetectedFood(
            name = "雞胸肉便當",
            servingText = "1 份 (380g)",
            calories = 520.0,
            proteinG = 42.0,
            fatG = 12.0,
            carbsG = 58.0,
            sugarG = 4.5,
            sodiumMg = 650.0,
            fiberG = 3.2,
            satFatG = 2.1,
            confidence = 0.95,
        )

        val scaled = base.scale(1.5)

        assertEquals("1.5 份 (570g)", scaled.servingText)
        assertEquals(780.0, scaled.calories, 0.001)
        assertEquals(63.0, scaled.proteinG, 0.001)
        assertEquals(18.0, scaled.fatG, 0.001)
        assertEquals(87.0, scaled.carbsG, 0.001)
        assertEquals(6.8, scaled.sugarG!!, 0.001)
        assertEquals(975.0, scaled.sodiumMg!!, 0.001)
        assertEquals(4.8, scaled.fiberG!!, 0.001)
        assertEquals(3.2, scaled.satFatG!!, 0.001)
        assertEquals(0.95, scaled.confidence, 0.001)
    }

    @Test
    fun `scale on DetectedFood keeps null optional fields as null`() {
        val base = DetectedFood(
            name = "無糖豆漿",
            servingText = "700ml",
            calories = 180.0,
            proteinG = 15.2,
            fatG = 7.0,
            carbsG = 14.0,
            sugarG = null,
            sodiumMg = null,
        )

        val scaled = base.scale(2.0)

        assertEquals("1400ml", scaled.servingText)
        assertEquals(360.0, scaled.calories, 0.001)
        assertEquals(30.4, scaled.proteinG, 0.001)
        assertEquals(14.0, scaled.fatG, 0.001)
        assertEquals(28.0, scaled.carbsG, 0.001)
        assertNull(scaled.sugarG)
        assertNull(scaled.sodiumMg)
    }

    @Test
    fun `EntryDraft scaleFromBase scales and restores accurately without rounding drift`() {
        val baseDraft = EntryDraft(
            name = "牛排",
            servingText = "200克",
            calories = "360",
            protein = "48.0",
            fat = "18.0",
            carbs = "0",
            sodium = "350",
        )

        val scaled15 = baseDraft.scaleFromBase(baseDraft, 1.5)
        assertEquals("300克", scaled15.servingText)
        assertEquals("540", scaled15.calories)
        assertEquals("72", scaled15.protein)
        assertEquals("27", scaled15.fat)
        assertEquals("0", scaled15.carbs)
        assertEquals("525", scaled15.sodium)

        // Scaling back to 1.0 restores exact base values
        val restored = scaled15.scaleFromBase(baseDraft, 1.0)
        assertEquals("200克", restored.servingText)
        assertEquals("360", restored.calories)
        assertEquals("48.0", restored.protein)
        assertEquals("18.0", restored.fat)
        assertEquals("0", restored.carbs)
        assertEquals("350", restored.sodium)
    }

    @Test
    fun `Baseline Persistence Bug Fix - Reopening 2x entry derives original base and allows restoring to 1x by subtracting 1`() {
        // 1. Initial 1x Food Entry saved as 2x
        val savedEntry = FoodEntry(
            id = 42L,
            date = "2026-08-23",
            loggedAt = 1000L,
            meal = "LUNCH",
            name = "舒肥雞胸肉便當",
            servingText = "2 份 (760g)",
            calories = 1040.0,
            proteinG = 84.0,
            fatG = 24.0,
            carbsG = 116.0,
            portionMultiplier = 2.0,
        )

        // 2. User opens entry to edit
        val draft = EntryDraft.of(savedEntry)
        assertEquals(2.0, draft.portionMultiplier, 0.001)
        assertEquals("1040", draft.calories)

        // 3. System derives exact 1.0x baseline draft
        val baseDraft = draft.deriveBase(draft.portionMultiplier)
        assertEquals(1.0, baseDraft.portionMultiplier, 0.001)
        assertEquals("520", baseDraft.calories)
        assertEquals("42", baseDraft.protein)
        assertEquals("12", baseDraft.fat)
        assertEquals("58", baseDraft.carbs)
        assertEquals("1 份 (380g)", baseDraft.servingText)

        // 4. User steps down by 1.0 (from 2.0x to 1.0x) -> Restores exact original 1x base!
        val backTo1x = draft.scaleFromBase(baseDraft, 1.0)
        assertEquals(1.0, backTo1x.portionMultiplier, 0.001)
        assertEquals("520", backTo1x.calories)
        assertEquals("42", backTo1x.protein)
        assertEquals("12", backTo1x.fat)
        assertEquals("58", backTo1x.carbs)
        assertEquals("1 份 (380g)", backTo1x.servingText)

        // 5. User steps up by 1.0 (to 3.0x)
        val upTo3x = draft.scaleFromBase(baseDraft, 3.0)
        assertEquals(3.0, upTo3x.portionMultiplier, 0.001)
        assertEquals("1560", upTo3x.calories)
        assertEquals("126", upTo3x.protein)
        assertEquals("36", upTo3x.fat)
        assertEquals("174", upTo3x.carbs)
        assertEquals("3 份 (1140g)", upTo3x.servingText)

        // 6. Saving as toEntry preserves new portionMultiplier
        val updatedEntry = upTo3x.toEntry(LocalDate.of(2026, 8, 23))
        assertEquals(3.0, updatedEntry.portionMultiplier, 0.001)
        assertEquals(1560.0, updatedEntry.calories, 0.001)
    }
}
