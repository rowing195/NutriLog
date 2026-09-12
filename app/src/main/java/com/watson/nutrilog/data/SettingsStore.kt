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
    /** 同上，只是換一家。空字串代表沒設定。 */
    val openRouterApiKey: String = "",
    val openRouterModel: String = DEFAULT_OPENROUTER_MODEL,
    /** 同上。Tavily 是搜尋服務不是模型，見 [SearchMode]。 */
    val tavilyApiKey: String = "",
    /** 文字辨識前要不要先查網路、用誰查。 */
    val searchMode: SearchMode = SearchMode.OFF,
    /**
     * 文字描述要送去哪一家。**只管文字**，拍照永遠走 Gemini ——
     * 理由見 [AiProvider]。
     */
    val textProvider: AiProvider = AiProvider.GEMINI,
    val calorieTarget: Int = 2000,
    val proteinTargetG: Int = 100,
    val fatTargetG: Int = 60,
    val carbsTargetG: Int = 250,
    /** 關掉時，輸入表單的進階營養素區塊預設收合 */
    val showExtendedNutrients: Boolean = false,
    val darkMode: DarkModePreference = DarkModePreference.SYSTEM,
    /** 桌面圖示用哪一款。實際切換的是 manifest 裡的 activity-alias，見 [AppIconSwitcher]。 */
    val appIcon: AppIcon = AppIcon.DEFAULT,
    /** 每天自動備份到 Drive。關著的時候完全不碰網路，也不會排任何背景工作。 */
    val driveBackupEnabled: Boolean = false,
    /** 備份到哪個 Google 帳號。空字串代表還沒授權過。只拿來顯示，授權本身不靠它。 */
    val driveAccount: String = "",
    /** 上次備份成功的時間（epoch millis）。0 代表還沒備份過。 */
    val lastBackupAt: Long = 0,
    /**
     * AI 週報／月報送去哪一家。和文字辨識分開選，但**沿用各家已經填好的金鑰與模型** ——
     * 報告不需要另一把金鑰，多一把只是多一個會填錯的地方。
     */
    val reportProvider: AiProvider = AiProvider.GEMINI,
    // --- 身型（算 BMR／TDEE 用）---
    // 預設值只是讓公式有東西可以算，不代表使用者真的是這個身型；
    // 有沒有真的填過看 [profileConfigured]。
    val profileGender: Gender = Gender.MALE,
    val profileAge: Int = 28,
    val profileHeightCm: Float = 172f,
    val profileWeightKg: Float = 68f,
    val profileActivity: ActivityLevel = ActivityLevel.LIGHT,
    val profileGoal: DietGoal = DietGoal.MAINTAIN,
    val profileConfigured: Boolean = false,
    // --- 健康連線（Health Connect／Samsung Health）---
    /** 把飲食紀錄寫進健康連線。**預設關**：寫出去的東西會出現在別的 app 裡，要使用者自己決定。 */
    val healthConnectSyncEnabled: Boolean = false,
    /** 上次整批寫入健康連線的時間（epoch 毫秒），設定頁顯示用。0＝從來沒有。 */
    val lastHealthSyncAt: Long = 0,
    /** 讀運動消耗並加進當天的熱量總量。實際能不能讀還要看權限，這裡只是使用者的意願。 */
    val readExerciseCalories: Boolean = true,
    /**
     * 手錶是整天戴著，還是只有運動時才戴。見 [WatchWearMode]。
     */
    val watchWearMode: WatchWearMode = WatchWearMode.WORKOUT_ONLY,
    /**
     * 量到的運動消耗**回補幾成**進當天的目標。
     *
     * 預設 50：穿戴裝置估熱量本來就不準（系統性回顧給的誤差從 9% 到 40% 以上都有，
     * 有些裝置的 MAPE 甚至破百），全額回補等於把高估的部分一起吃回去。
     * 營養師的普遍建議是活動量設低一點、運動熱量只回補 25–50%。
     */
    val exerciseEatBackPercent: Int = 50,
) {
    companion object {
        // 模型會改朝換代，所以設定頁可以改。注意 gemini-2.0-flash 已經下架，別填。
        //
        // 改預設**只影響全新安裝**：舊資料裡已經存了一個 geminiModel 值
        // （序列化時 encodeDefaults = true，第一次存設定就把它寫進去了），
        // 所以既有使用者要自己到設定頁改，不會被這行帶著走。
        const val DEFAULT_MODEL = "gemini-3.7-flash"

        /**
         * OpenRouter 的預設。挑健康領域的模型而不是通用大模型，因為這條路只做
         * 一件事：把「吃了什麼」換算成營養素。`:free` 是它自己的免費層級標記。
         */
        const val DEFAULT_OPENROUTER_MODEL = "inclusionai/ling-3.0-flash-sante:free"
        const val MIN_TARGET = 0
        const val MAX_CALORIE_TARGET = 6000
        const val MAX_MACRO_TARGET = 800
    }
}

/**
 * 文字辨識要走哪一家。
 *
 * **這個選擇只管文字，不管拍照。** 拍照需要吃得下圖片的模型，而這條路上想用的
 * OpenRouter 免費模型（見 [NutriSettings.DEFAULT_OPENROUTER_MODEL]）是純文字的 ——
 * 做成一個總開關的話，使用者選了 OpenRouter 之後拍照會神祕地失敗或偷偷跑去別家，
 * 兩種都比在設定頁講清楚差。所以設定頁那一欄叫「文字辨識用哪一家」。
 *
 * 以後要加 OpenAI 之類的就在這裡多一個 entry，設定頁的清單是照 entries 長出來的。
 */
/**
 * 每一個需要 API key 的外部服務。設定頁那份金鑰清單就是照 entries 長出來的，
 * 以後要加 OpenAI 之類的在這裡多一個 entry 就好。
 *
 * 名稱是品牌名不是要翻譯的文案，所以直接寫在 enum 上而不是丟字串資源。
 */
@Serializable
enum class ApiService(val label: String) {
    GEMINI("Gemini"),
    OPENROUTER("OpenRouter"),
    TAVILY("Tavily"),
}

/**
 * 文字辨識要走哪一家**模型**。
 *
 * **這個選擇只管文字，不管拍照。** 拍照需要吃得下圖片的模型，而這條路上想用的
 * OpenRouter 免費模型（見 [NutriSettings.DEFAULT_OPENROUTER_MODEL]）是純文字的 ——
 * 做成一個總開關的話，使用者選了 OpenRouter 之後拍照會神祕地失敗或偷偷跑去別家，
 * 兩種都比在設定頁講清楚差。所以設定頁那一欄叫「文字辨識用哪一家」。
 *
 * 它是 [ApiService] 的子集：不是每個有 key 的服務都能回答問題（Tavily 只會搜尋）。
 */
@Serializable
enum class AiProvider(val service: ApiService) {
    GEMINI(ApiService.GEMINI),
    OPENROUTER(ApiService.OPENROUTER);

    val label: String get() = service.label
}

/**
 * 回答之前要不要先查網路、用誰查。
 *
 * 兩條路的差別不只是價錢：
 *
 * - [OPENROUTER] 是 OpenRouter 內建的外掛，**每次查詢另外收費**（預設抓 5 筆結果
 *   約 $0.02），而且**只有文字走 OpenRouter 時才有作用**。實測它回的數字其實來自
 *   部落格整理的表格，不是官方頁。
 * - [TAVILY] 是自己打 Tavily 的搜尋 API，把結果當背景文字接在 prompt 前面。
 *   免費層 1000 次/月，**兩家模型都適用**，而且實測第一筆就是台灣麥當勞的官方
 *   產品頁、整張營養表原樣帶回來（503.17 kcal 那一份）。
 */
@Serializable
enum class SearchMode { OFF, OPENROUTER, TAVILY }

/** 深色模式要不要跟系統走。獨立成 enum 而不是單一 boolean，因為「跟系統」本身是第三種狀態。 */
@Serializable
enum class DarkModePreference { SYSTEM, LIGHT, DARK }

/** 生理性別。只影響 BMR 公式裡的常數（Mifflin-St Jeor 男 +5、女 −161）。 */
enum class Gender { MALE, FEMALE }

/** 日常活動量，對應 TDEE 的活動係數。 */
/**
 * 可以切換的桌面圖示。**宣告順序就是外觀頁上的排列順序。**
 *
 * [aliasSuffix] 要和 `AndroidManifest.xml` 裡的 activity-alias 名稱完全一致 ——
 * 對不上的話那一款就是「按了沒反應」，而且不會有任何錯誤訊息。
 */
enum class AppIcon(val aliasSuffix: String) {
    DEFAULT(".IconDefault"),
    CAT(".IconCat"),
    HAT(".IconHat"),
    KEYBOARD(".IconKeyboard"),
    INK(".IconInk"),
    VERMILION(".IconVermilion"),
    BOWL(".IconBowl"),
}

/**
 * 手錶的配戴方式。這是「日常走動算誰的」那個決定，不是偏好。
 *
 * - [ALL_DAY]：手錶的活動消耗本來就含日常走動，所以熱量目標的底退到久坐係數，
 *   走動多少由手錶說了算。
 * - [WORKOUT_ONLY]：日常走動手錶記不到，只能靠活動係數，手錶只補運動場次那幾筆。
 *
 * **兩種都不影響蛋白質** —— 蛋白質看的是這個人活動量多大，不是手錶戴多久。
 */
enum class WatchWearMode { WORKOUT_ONLY, ALL_DAY }

enum class ActivityLevel(val multiplier: Float, val proteinPerKg: Float) {
    SEDENTARY(1.2f, 1.0f),
    LIGHT(1.375f, 1.2f),
    MODERATE(1.55f, 1.4f),
    HEAVY(1.725f, 1.6f),
    VERY_HEAVY(1.9f, 1.8f),
}

/** 體態目標：在 TDEE 上加減多少熱量，以及蛋白質要不要再往上加一點。 */
enum class DietGoal(val calorieDelta: Int, val proteinBonusPerKg: Float) {
    // 赤字期間多留一點蛋白質保住肌肉；增肌則是給合成用的材料
    LOSE_FAT(-300, 0.2f),
    MAINTAIN(0, 0f),
    GAIN_MUSCLE(300, 0.2f),
}

/**
 * Mifflin-St Jeor 估算的每日基礎代謝。
 *
 * **週報、月報與「總消耗扣掉基礎代謝推回活動量」讀的必須是同一個值** ——
 * 各寫一份的話，今日頁顯示的運動消耗會和報表對不起來。
 * 身高體重沒有值時退回一個保守的常數，不要讓公式吐出負值。
 */
fun NutriSettings.estimatedBmrPerDay(): Double =
    if (profileWeightKg > 0 && profileHeightCm > 0) {
        val base = 10.0 * profileWeightKg + 6.25 * profileHeightCm - 5.0 * profileAge
        if (profileGender == Gender.MALE) base + 5.0 else base - 161.0
    } else {
        1600.0
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
