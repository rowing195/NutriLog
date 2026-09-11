package com.watson.nutrilog

import com.watson.nutrilog.data.ActivityLevel
import com.watson.nutrilog.data.BackedUpProfile
import com.watson.nutrilog.data.DietGoal
import com.watson.nutrilog.data.DriveBackup
import com.watson.nutrilog.data.Gender
import com.watson.nutrilog.data.NutriSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BackedUpProfileTest {

    private val withSecrets = NutriSettings(
        geminiApiKey = "gemini-secret-value",
        openRouterApiKey = "openrouter-secret-value",
        tavilyApiKey = "tavily-secret-value",
        calorieTarget = 1850,
        proteinTargetG = 140,
        profileGender = Gender.FEMALE,
        profileAge = 34,
        profileHeightCm = 163f,
        profileWeightKg = 55.5f,
        profileActivity = ActivityLevel.MODERATE,
        profileGoal = DietGoal.LOSE_FAT,
        profileConfigured = true,
    )

    @Test
    fun `backup never contains api keys`() {
        val json = BackedUpProfile.from(withSecrets).toJson()
        listOf("gemini-secret-value", "openrouter-secret-value", "tavily-secret-value").forEach {
            assertFalse("leaked $it", json.contains(it))
        }
        assertFalse(json.contains("ApiKey", ignoreCase = true))
    }

    @Test
    fun `round trip restores targets and profile but keeps local keys`() {
        val restored = BackedUpProfile.fromJson(BackedUpProfile.from(withSecrets).toJson())!!
        val onNewPhone = NutriSettings(geminiApiKey = "new-phone-key")
        val applied = restored.applyTo(onNewPhone)
        assertEquals("new-phone-key", applied.geminiApiKey)
        assertEquals(1850, applied.calorieTarget)
        assertEquals(140, applied.proteinTargetG)
        assertEquals(Gender.FEMALE, applied.profileGender)
        assertEquals(55.5f, applied.profileWeightKg)
        assertEquals(DietGoal.LOSE_FAT, applied.profileGoal)
        assertEquals(true, applied.profileConfigured)
    }

    @Test
    fun `broken json gives null instead of throwing`() {
        assertNull(BackedUpProfile.fromJson("{not json"))
    }

    @Test
    fun `untouched settings equal the default and are not worth uploading`() {
        assertEquals(BackedUpProfile.DEFAULT, BackedUpProfile.from(NutriSettings(geminiApiKey = "k")))
    }

    @Test
    fun `profile files are pruned separately from csv backups`() {
        val names = (1..35).map { "nutrilog-profile-2026-08-%02d.json".format(it % 31 + 1) }.distinct() +
            listOf("nutrilog-2026-08-01.csv", "notes.txt")
        val doomed = DriveBackup.namesToPrune(names, 30, DriveBackup.PROFILE_NAME)
        assertEquals(1, doomed.size)
        assertEquals("nutrilog-profile-2026-08-01.json", doomed.single())
        assertFalse(DriveBackup.namesToPrune(names, 30).contains("nutrilog-2026-08-01.csv"))
    }
}
