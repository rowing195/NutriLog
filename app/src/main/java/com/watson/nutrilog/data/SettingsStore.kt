package com.watson.nutrilog.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 使用者設定與每日目標。
 *
 * 這裡用 DataStore 而不是 Room，是因為它就只有一份、不需要查詢。
 * 飲食紀錄則相反（逐日累積、要查區間），所以走 Room —— 兩種儲存方式共存是刻意的。
 */
@Serializable
data class NutriSettings(
    /** 只存在這支手機裡，不會外流。空字串代表還沒設定，拍照辨識會擋下來。 */
    val geminiApiKey: String = "",
    val geminiModel: String = DEFAULT_MODEL,
    val calorieTarget: Int = 2000,
    val proteinTargetG: Int = 100,
    val fatTargetG: Int = 60,
    val carbsTargetG: Int = 250,
    /** 關掉時，輸入表單的進階營養素區塊預設收合 */
    val showExtendedNutrients: Boolean = false,
    val darkMode: DarkModePreference = DarkModePreference.SYSTEM,
) {
    companion object {
        // 模型會改朝換代，所以設定頁可以改。注意 gemini-2.0-flash 已經下架，別填。
        //
        // 改預設**只影響全新安裝**：舊資料裡已經存了一個 geminiModel 值
        // （序列化時 encodeDefaults = true，第一次存設定就把它寫進去了），
        // 所以既有使用者要自己到設定頁改，不會被這行帶著走。
        const val DEFAULT_MODEL = "gemini-3.7-flash"
        const val MIN_TARGET = 0
        const val MAX_CALORIE_TARGET = 6000
        const val MAX_MACRO_TARGET = 800
    }
}

/** 深色模式要不要跟系統走。獨立成 enum 而不是單一 boolean，因為「跟系統」本身是第三種狀態。 */
@Serializable
enum class DarkModePreference { SYSTEM, LIGHT, DARK }

// 必須是「每個檔名只有一個」的頂層委派，重複建立會在執行期直接拋例外
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "nutri_prefs")

class SettingsStore(context: Context) {

    private val store = context.applicationContext.settingsDataStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val settingsFlow: Flow<NutriSettings> = store.data.map { decode(it[KEY_SETTINGS]) }

    suspend fun save(settings: NutriSettings) {
        store.edit { prefs ->
            prefs[KEY_SETTINGS] = json.encodeToString(NutriSettings.serializer(), settings)
        }
    }

    /**
     * 解析失敗一律退回預設值。設定檔壞掉是小事，因為設定檔壞掉而開不了 app 是大事。
     * 新增欄位時給預設值就能相容舊資料，所以這個 app 不做 DataStore 遷移。
     */
    private fun decode(raw: String?): NutriSettings =
        if (raw.isNullOrBlank()) NutriSettings()
        else runCatching { json.decodeFromString(NutriSettings.serializer(), raw) }
            .onFailure { Log.w(TAG, "設定解析失敗，改用預設值", it) }
            .getOrDefault(NutriSettings())

    private companion object {
        const val TAG = "SettingsStore"
        val KEY_SETTINGS = stringPreferencesKey("settings_json")
    }
}
