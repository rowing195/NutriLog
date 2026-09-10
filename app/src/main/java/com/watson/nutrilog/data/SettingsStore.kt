package com.watson.nutrilog.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    /** 每天自動備份到 Drive。關著的時候完全不碰網路，也不會排任何背景工作。 */
    val driveBackupEnabled: Boolean = false,
    /** 備份到哪個 Google 帳號。空字串代表還沒授權過。只拿來顯示，授權本身不靠它。 */
    val driveAccount: String = "",
    /** 上次備份成功的時間（epoch millis）。0 代表還沒備份過。 */
    val lastBackupAt: Long = 0,
    /** 個人體態數值（計算 BMR 與 TDEE 用） */
    val profileGender: Gender = Gender.MALE,
    val profileAge: Int = 28,
    val profileHeightCm: Float = 172f,
    val profileWeightKg: Float = 68f,
    val profileActivity: ActivityLevel = ActivityLevel.LIGHT,
    val profileGoal: DietGoal = DietGoal.MAINTAIN,
    /** 是否已完成個人身型與目標設定。尚未設定時首次進入或登入會主動跳出引導。 */
    val profileConfigured: Boolean = false,
    /** 是否開啟 Health Connect（健康連線）同步至 Samsung Health */
    val healthConnectSyncEnabled: Boolean = false,
    /** 上次同步至健康連線的時間（epoch millis） */
    val lastHealthSyncAt: Long = 0,
    /** 是否從 Health Connect 讀取運動消耗熱量 */
    val readExerciseCalories: Boolean = true,
    /** NVIDIA NIM / OpenRouter API Key (用於生成每週健康週報與卡路里推薦、文字食物辨識) */
    val nvidiaApiKey: String = "",
    val nvidiaModel: String = DEFAULT_NVIDIA_MODEL,
    /** Tavily Search API Key (用於文字食物辨識時聯網檢索最新熱量與營養標示) */
    val tavilyApiKey: String = "",
) {
    companion object {
        // 模型會改朝換代，所以設定頁可以改。注意 gemini-2.0-flash 已經下架，別填。
        //
        // 改預設**只影響全新安裝**：舊資料裡已經存了一個 geminiModel 值
        // （序列化時 encodeDefaults = true，第一次存設定就把它寫進去了），
        // 所以既有使用者要自己到設定頁改，不會被這行帶著走。
        const val DEFAULT_MODEL = "gemini-3.7-flash"
        const val DEFAULT_NVIDIA_MODEL = "nvidia/nemotron-3.5-lightning:free"
        const val MIN_TARGET = 0
        const val MAX_CALORIE_TARGET = 6000
        const val MAX_MACRO_TARGET = 800
    }
}

/**
 * Mifflin-St Jeor 臨床公式估算的每日基礎代謝。
 *
 * 週報、月報與「總消耗扣掉基礎代謝推回活動量」讀的必須是同一個值 ——
 * 各寫一份的話，首頁顯示的運動消耗會和報表對不起來。
 * 身高體重沒填時退回一個保守的常數，不要讓公式吐出負值。
 */
fun NutriSettings.estimatedBmrPerDay(): Double =
    if (profileWeightKg > 0 && profileHeightCm > 0) {
        if (profileGender == Gender.MALE) {
            10.0 * profileWeightKg + 6.25 * profileHeightCm - 5.0 * profileAge + 5.0
        } else {
            10.0 * profileWeightKg + 6.25 * profileHeightCm - 5.0 * profileAge - 161.0
        }
    } else {
        1600.0
    }

/** 深色模式要不要跟系統走。獨立成 enum 而不是單一 boolean，因為「跟系統」本身是第三種狀態。 */
@Serializable
enum class DarkModePreference { SYSTEM, LIGHT, DARK }

/** 生理性別（影響 BMR 計算常數） */
@Serializable
enum class Gender { MALE, FEMALE }

/** 日常活動量與運動頻率（對應 TDEE 活動係數） */
@Serializable
enum class ActivityLevel(val multiplier: Float) {
    SEDENTARY(1.2f),
    LIGHT(1.375f),
    MODERATE(1.55f),
    HEAVY(1.725f),
    VERY_HEAVY(1.9f)
}

/** 體態目標（熱量赤字或盈餘） */
@Serializable
enum class DietGoal(val calorieDelta: Int) {
    LOSE_FAT(-300),
    MAINTAIN(0),
    GAIN_MUSCLE(300)
}

// 必須是「每個檔名只有一個」的頂層委派，重複建立會在執行期直接拋例外
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "nutri_prefs")

class SettingsStore(context: Context) {

    private val store = context.applicationContext.settingsDataStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val settingsFlow: Flow<NutriSettings> = store.data.map { decode(it[KEY_SETTINGS]) }

    /** 背景工作沒有 UI 可以訂閱 Flow，就地讀一次目前的設定。 */
    suspend fun current(): NutriSettings = settingsFlow.first()

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
