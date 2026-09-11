package com.watson.nutrilog.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * 跟著飲食紀錄一起備份到 Drive 的那一份設定：每日目標與身型。
 *
 * **這是白名單，不是把 [NutriSettings] 整個序列化再挖掉金鑰。** 整份序列化的話，
 * 以後在設定裡多加任何一個敏感欄位都會默默跟著上傳 —— 而設定頁寫的是
 * 「API key 只存在於該裝置內」。這裡只有明確列出來的欄位出得去，
 * [BackedUpProfileTest] 守著金鑰不會出現在輸出裡。
 *
 * 飲食紀錄的格式仍然是那份 CSV（見 [DriveBackup]）；這一份只是讓換手機時
 * 不必重填身高體重和目標，缺了它資料也不會被鎖住。
 */
@Serializable
data class BackedUpProfile(
    val calorieTarget: Int,
    val proteinTargetG: Int,
    val fatTargetG: Int,
    val carbsTargetG: Int,
    val gender: Gender,
    val age: Int,
    val heightCm: Float,
    val weightKg: Float,
    val activity: ActivityLevel,
    val goal: DietGoal,
    val configured: Boolean,
) {
    /** 還原到 [settings] 上。金鑰、外觀、備份狀態這些本機才有意義的東西一律不動。 */
    fun applyTo(settings: NutriSettings): NutriSettings = settings.copy(
        calorieTarget = calorieTarget,
        proteinTargetG = proteinTargetG,
        fatTargetG = fatTargetG,
        carbsTargetG = carbsTargetG,
        profileGender = gender,
        profileAge = age,
        profileHeightCm = heightCm,
        profileWeightKg = weightKg,
        profileActivity = activity,
        profileGoal = goal,
        profileConfigured = configured,
    )

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun from(settings: NutriSettings) = BackedUpProfile(
            calorieTarget = settings.calorieTarget,
            proteinTargetG = settings.proteinTargetG,
            fatTargetG = settings.fatTargetG,
            carbsTargetG = settings.carbsTargetG,
            gender = settings.profileGender,
            age = settings.profileAge,
            heightCm = settings.profileHeightCm,
            weightKg = settings.profileWeightKg,
            activity = settings.profileActivity,
            goal = settings.profileGoal,
            configured = settings.profileConfigured,
        )

        /**
         * 全新安裝時的樣子。**跟它一模一樣就不上傳**：新手機連結 Drive 之後、還沒按還原
         * 就先跑了一次備份的話，雲端會多出一份「日期最新」的預設值，下次還原反而拿到它。
         */
        val DEFAULT = from(NutriSettings())

        fun fileName(date: LocalDate) = "nutrilog-profile-$date.json"

        /** 解析失敗回 null：手改壞的檔或未來的新格式，不該讓連結 Drive 整個失敗。 */
        fun fromJson(raw: String): BackedUpProfile? =
            runCatching { json.decodeFromString(serializer(), raw) }.getOrNull()
    }
}
