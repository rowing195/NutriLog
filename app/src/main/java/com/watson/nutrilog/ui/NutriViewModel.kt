package com.watson.nutrilog.ui

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.watson.nutrilog.R
import com.watson.nutrilog.data.CsvExport
import com.watson.nutrilog.data.CsvImport
import com.watson.nutrilog.data.DriveBackup
import com.watson.nutrilog.data.AiProvider
import com.watson.nutrilog.data.AppIcon
import com.watson.nutrilog.data.AppIconSwitcher
import com.watson.nutrilog.data.ApiService
import com.watson.nutrilog.data.SearchMode
import com.watson.nutrilog.data.NutriSettings
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.watson.nutrilog.data.DriveAuth
import com.watson.nutrilog.data.SettingsStore
import com.watson.nutrilog.work.BackupWorker
import com.watson.nutrilog.data.db.CachedProduct
import com.watson.nutrilog.data.db.DayTotal
import com.watson.nutrilog.data.db.EntrySource
import com.watson.nutrilog.data.db.FoodEntry
import com.watson.nutrilog.data.db.FoodSuggestion
import com.watson.nutrilog.data.db.Meal
import com.watson.nutrilog.data.db.NutriDatabase
import com.watson.nutrilog.data.net.DetectedFood
import com.watson.nutrilog.data.net.GeminiClient
import com.watson.nutrilog.data.net.OpenRouterClient
import com.watson.nutrilog.data.net.TavilyClient
import com.watson.nutrilog.data.net.ImageCompressor
import com.watson.nutrilog.data.net.OpenFoodFactsClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlin.math.roundToInt
import androidx.compose.runtime.mutableStateMapOf
import com.watson.nutrilog.data.ActivitySource
import com.watson.nutrilog.data.DailyActivity
import com.watson.nutrilog.data.HealthConnectSync
import com.watson.nutrilog.data.MonthlyAggregator
import com.watson.nutrilog.data.MonthlyReport
import com.watson.nutrilog.data.MonthlyReportStore
import com.watson.nutrilog.data.MonthlyStats
import com.watson.nutrilog.data.NO_ACTIVITY_DATA_REASON
import com.watson.nutrilog.data.TargetRecommendation
import com.watson.nutrilog.data.WeeklyAggregator
import com.watson.nutrilog.data.WeeklyReport
import com.watson.nutrilog.data.WeeklyReportStore
import com.watson.nutrilog.data.WeeklyStats
import com.watson.nutrilog.data.db.DailyHealthMetric
import com.watson.nutrilog.data.BackedUpProfile

/**
 * 畫面。沿用 LocalReader 的做法：sealed interface + when 分派，不引入導航函式庫。
 *
 * 只有 [Today] 是根，其他一律按返回鍵就回 Today，所以不需要真正的返回堆疊。
 * [EditEntry] 沒有參數 —— 正在編輯的內容放在 ViewModel 的 draft 上，
 * 這樣三種輸入來源（手動／條碼／拍照）都能先塞好草稿再切過去。
 */
/**
 * 設定頁的分頁。設定原本是一整條長捲軸，六段疊在一起要捲很久才找得到東西，
 * 所以拆成「選單 -> 子頁」兩層，這個 enum 就是子頁的身分。
 *
 * 「顯示進階營養素」那個開關沒有自己的一頁 —— 它講的是編輯表單要不要攤開糖、鈉、
 * 膳食纖維、飽和脂肪，跟每日目標同樣是在講營養素，為了一個開關多開一頁不划算。
 */
enum class SettingsPage { APPEARANCE, TARGETS, HEALTH, AI, DRIVE, DATA }

sealed interface Screen {
    data object Today : Screen
    data object History : Screen

    /** 設定的選單那一層。 */
    data object Settings : Screen

    /**
     * 設定的子頁。這是整個 app 唯一有兩層的地方，所以返回鍵在這裡是回選單、
     * 不是回今日頁（見 App.kt 的 BackHandler）。
     */
    data class SettingsDetail(val page: SettingsPage) : Screen

    /**
     * 某一個服務的 key。設定裡唯一的第三層 —— 每家的欄位不一樣，
     * 全部攤在同一頁的話「AI 影像辨識」又會變回一條長捲軸。
     */
    data class ApiKeyDetail(val service: ApiService) : Screen
    data object EditEntry : Screen
    data object Barcode : Screen
    data object TextLookup : Screen
    data object Review : Screen
    data object Search : Screen

    /** AI 週報／月報。從月曆開進來，所以是月曆的下一層。 */
    data object Reports : Screen
}

/** AI 辨識的三個階段。照片與文字描述共用。 */
sealed interface AnalysisState {
    data object Analyzing : AnalysisState
    data class Ready(val items: List<AnalysisItem>) : AnalysisState
    data class Failed(val reason: String) : AnalysisState
}

/** 確認畫面裡的一列：模型認出來的東西，加上「要不要記錄」與「份數倍率」。 */
data class AnalysisItem(
    val baseFood: DetectedFood,
    val multiplier: Double = 1.0,
    val selected: Boolean = true,
) {
    /** 依照目前設定的倍率等比縮放後的營養素 */
    val food: DetectedFood get() = baseFood.scale(multiplier)
}

/**
 * 這次辨識是從哪裡來的。
 *
 * 「重試」必須知道要重跑哪一件事 —— 之前是讓畫面自己記著照片 URI，
 * 但文字辨識加進來之後那個做法就不成立了。
 */
sealed interface AnalysisSource {
    data class Photo(val uri: Uri) : AnalysisSource
    /**
     * [useSearch] 由使用者按哪一顆章決定，不是設定、也不是模型判斷。
     *
     * 它跟著來源走而不是另外存一個欄位，因為 `retryAnalysis()` 會原樣重跑
     * `lastSource` —— 分開存的話「重試」會靜悄悄地換一種模式。
     */
    data class Text(val query: String, val useSearch: Boolean) : AnalysisSource
}

/** 條碼查詢的四種結局。分開來 UI 才講得出「查無此商品」和「連線失敗」的差別。 */
sealed interface BarcodeState {
    data object Idle : BarcodeState
    data object Loading : BarcodeState
    data class Found(val product: CachedProduct, val fromCache: Boolean) : BarcodeState
    data object NotFound : BarcodeState

    /**
     * [message] 已經是可以直接顯示的完整句子。
     *
     * 「連不上 OFF」和「這台裝置叫不出掃描器」是兩件無關的事，
     * 讓失敗方自己把話講完，畫面就不會在掃描器出問題時叫使用者去檢查網路。
     */
    data class Failed(val message: String) : BarcodeState
}

/**
 * 匯入前的預覽。
 *
 * CSV 是外部資料，而且匯入是一次寫進去一整批 —— 和 AI 辨識結果一樣，
 * 要先讓使用者看過再入庫。差別在於這裡不逐筆勾選：CSV 裡的數字是使用者
 * 自己記過的，不是模型猜的，需要確認的只有「這一批是什麼、會加幾筆」。
 */
data class ImportPreview(
    /** 扣掉重複之後真正要寫進去的紀錄。 */
    val newEntries: List<FoodEntry>,
    /** 資料庫裡已經有、或檔案裡自己重複的筆數。 */
    val duplicates: Int,
    /** 認不得而跳過的資料列數。 */
    val skipped: Int,
    /**
     * 連結 Drive 時雲端那份目標與身型，跟本機不同才會有值。本地匯入 CSV 永遠是 null。
     * 和紀錄放在同一個確認面板：外部來的資料一律先停下來問，設定也不例外。
     */
    val profile: BackedUpProfile? = null,
) {
    val firstDate: String? get() = newEntries.minOfOrNull { it.date }
    val lastDate: String? get() = newEntries.maxOfOrNull { it.date }
}

/**
 * 編輯中的表單內容。
 *
 * 數字欄位刻意存 String 而不是 Double：使用者打到一半可能是 ""、"12." 或 "-"，
 * 這些都不是合法的 Double。存字串就不必在每次按鍵時和解析失敗搏鬥，
 * 只在儲存那一刻轉一次。
 */
data class EntryDraft(
    val id: Long? = null,
    val meal: Meal = Meal.BREAKFAST,
    val name: String = "",
    val servingText: String = "",
    val calories: String = "",
    val protein: String = "",
    val fat: String = "",
    val carbs: String = "",
    val sugar: String = "",
    val sodium: String = "",
    val fiber: String = "",
    val satFat: String = "",
    val source: EntrySource = EntrySource.MANUAL,
    val barcode: String? = null,
    val portionMultiplier: Double = 1.0,
) {
    val isValid: Boolean get() = name.isNotBlank()

    fun toEntry(date: LocalDate, loggedAt: Long = System.currentTimeMillis()) = FoodEntry(
        id = id ?: 0,
        date = date.toString(),
        loggedAt = loggedAt,
        meal = meal.name,
        name = name.trim(),
        servingText = servingText.trim(),
        calories = calories.toNumberOrZero(),
        proteinG = protein.toNumberOrZero(),
        fatG = fat.toNumberOrZero(),
        carbsG = carbs.toNumberOrZero(),
        sugarG = sugar.toNumberOrNull(),
        sodiumMg = sodium.toNumberOrNull(),
        fiberG = fiber.toNumberOrNull(),
        satFatG = satFat.toNumberOrNull(),
        source = source.name,
        barcode = barcode,
        portionMultiplier = portionMultiplier,
    )

    /** 從已縮放的草稿中精確反推回 1.0x 基準草稿 */
    fun deriveBase(currentMultiplier: Double): EntryDraft {
        if (currentMultiplier <= 0.0 || currentMultiplier == 1.0) return copy(portionMultiplier = 1.0)
        fun unscale(raw: String, isInt: Boolean = false): String {
            val num = raw.toDoubleOrNull() ?: return ""
            val baseVal = num / currentMultiplier
            return if (isInt) Math.round(baseVal).toString() else baseVal.roundTo1().asInputValue()
        }
        return copy(
            servingText = scaleServingText(servingText, 1.0 / currentMultiplier),
            calories = unscale(calories, isInt = true),
            protein = unscale(protein),
            fat = unscale(fat),
            carbs = unscale(carbs),
            sugar = unscale(sugar),
            sodium = unscale(sodium, isInt = true),
            fiber = unscale(fiber),
            satFat = unscale(satFat),
            portionMultiplier = 1.0,
        )
    }

    fun scaleFromBase(base: EntryDraft, multiplier: Double): EntryDraft {
        val mult = (multiplier * 10.0).roundToInt() / 10.0
        if (mult == 1.0) {
            return copy(
                servingText = base.servingText,
                calories = base.calories,
                protein = base.protein,
                fat = base.fat,
                carbs = base.carbs,
                sugar = base.sugar,
                sodium = base.sodium,
                fiber = base.fiber,
                satFat = base.satFat,
                portionMultiplier = 1.0,
            )
        }
        fun scaleVal(raw: String, isInt: Boolean = false): String {
            val num = raw.toDoubleOrNull() ?: return ""
            val scaled = num * mult
            return if (isInt) Math.round(scaled).toString() else scaled.roundTo1().asInputValue()
        }
        return copy(
            servingText = scaleServingText(base.servingText, mult),
            calories = scaleVal(base.calories, isInt = true),
            protein = scaleVal(base.protein),
            fat = scaleVal(base.fat),
            carbs = scaleVal(base.carbs),
            sugar = scaleVal(base.sugar),
            sodium = scaleVal(base.sodium, isInt = true),
            fiber = scaleVal(base.fiber),
            satFat = scaleVal(base.satFat),
            portionMultiplier = mult,
        )
    }

    companion object {
        fun of(entry: FoodEntry) = EntryDraft(
            id = entry.id,
            meal = entry.mealType,
            name = entry.name,
            servingText = entry.servingText,
            calories = entry.calories.asInput(),
            protein = entry.proteinG.asInput(),
            fat = entry.fatG.asInput(),
            carbs = entry.carbsG.asInput(),
            sugar = entry.sugarG.asInput(),
            sodium = entry.sodiumMg.asInput(),
            fiber = entry.fiberG.asInput(),
            satFat = entry.satFatG.asInput(),
            source = runCatching { EntrySource.valueOf(entry.source) }.getOrDefault(EntrySource.MANUAL),
            barcode = entry.barcode,
            portionMultiplier = entry.portionMultiplier,
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class NutriViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = NutriDatabase.get(application).dao()
    private val settingsStore = SettingsStore(application)
    private val openFoodFacts = OpenFoodFactsClient()
    private val gemini = GeminiClient()
    private val openRouter = OpenRouterClient()
    private val tavily = TavilyClient()

    var screen by mutableStateOf<Screen>(Screen.Today)
        private set
    var settings by mutableStateOf(NutriSettings())
        private set
    var selectedDate by mutableStateOf(LocalDate.now())
        private set
    /** 月曆目前顯示的月份 */
    var visibleMonth by mutableStateOf(YearMonth.now())
        private set
    /** 該月每一天的合計，key 是 "yyyy-MM-dd"。沒紀錄的日子不會出現在 map 裡。 */
    var monthTotals by mutableStateOf<Map<String, DayTotal>>(emptyMap())
        private set

    /** 一週的第一天。星期日起算，和月曆的排法一致（台灣的日曆慣例）。 */
    val weekStart: LocalDate get() = selectedDate.minusDays((selectedDate.dayOfWeek.value % 7).toLong())
    var draft by mutableStateOf(EntryDraft())
        private set
    var barcodeState by mutableStateOf<BarcodeState>(BarcodeState.Idle)
        private set
    var analysisState by mutableStateOf<AnalysisState?>(null)
        private set
    private var lastSource: AnalysisSource? = null

    /**
     * AI 確認畫面要記進哪一餐。
     *
     * 原本是存檔當下才 guessMeal()，等於「照時間猜了就算」——
     * 補登昨天的晚餐時會全部掉進點心。改成開確認畫面時先猜一個當預設，
     * 使用者可以改。
     */
    var analysisMeal by mutableStateOf(Meal.BREAKFAST)
        private set

    /** 見 [setPendingMeal]。null 代表使用者沒指定，照時間猜。 */
    private var pendingMeal: Meal? = null

    /** 搜尋輸入。空字串時畫面顯示食物庫（常吃／最近兩頁），有字才換成逐筆結果。 */
    var searchQuery by mutableStateOf("")
        private set
    var searchResults by mutableStateOf<List<FoodEntry>>(emptyList())
        private set
    var frequentFoods by mutableStateOf<List<FoodSuggestion>>(emptyList())
        private set
    var recentFoods by mutableStateOf<List<FoodSuggestion>>(emptyList())
        private set

    /** 匯出結果訊息。顯示完就該清掉，離開設定頁時一併清。 */
    /** 匯出與匯入共用一行結果訊息：它們在畫面上是同一區，兩行訊息會分不清是誰的。 */
    var dataMessage by mutableStateOf<String?>(null)
        private set

    /** 有值就代表確認面板開著。null 是「沒有正在進行的匯入」。 */
    var importPreview by mutableStateOf<ImportPreview?>(null)
        private set

    /** 剛刪掉、還在復原視窗裡的那一筆。null＝左下角那條不顯示。 */
    var pendingUndo by mutableStateOf<FoodEntry?>(null)
        private set

    private var undoJob: Job? = null

    /** Drive 那一區自己的訊息。和匯出／匯入分開，因為它們是兩個獨立的區塊。 */
    var driveMessage by mutableStateOf<String?>(null)
        private set

    /** 正在跟 Drive 講話。按鍵要能擋住重複點擊，不然會同時跑兩次備份。 */
    var driveBusy by mutableStateOf(false)
        private set

    /**
     * 需要使用者同意時，要由 Activity 送出去的同意畫面。
     *
     * ViewModel 拿不到 Activity，而 PendingIntent 一定要從 Activity 發 ——
     * 所以這裡只是把它交出去，發射與清空由 App.kt 負責。
     */
    var pendingConsent by mutableStateOf<PendingIntent?>(null)
        private set

    /**
     * 今日頁的日／週分頁各自訂閱自己那天／那週的資料，不跟著 [selectedDate] 走 ——
     * 分頁滑動時左右兩頁都要能各自顯示正確內容，不能只有目前選到的那頁有資料。
     * 本機 SQLite 查詢近乎零成本，每頁各開一條 Flow 沒有問題。
     */
    fun entriesFlow(date: LocalDate) = dao.observeDay(date.toString())

    fun weekTotalsFlow(weekStart: LocalDate) =
        dao.observeRange(weekStart.toString(), weekStart.plusDays(6).toString())

    init {
        viewModelScope.launch {
            settingsStore.settingsFlow.collect {
                settings = it
                // 每次啟動都對一次：元件狀態存在系統那一側，重裝或使用者清資料之後
                // 會跟設定裡記的那一款對不上，那時候桌面上的圖示就不是他選的那個。
                AppIconSwitcher.apply(getApplication(), it.appIcon)
            }
        }
        // 每次啟動補排一次每日備份。
        //
        // 排程原本只在 connectDrive() 那一刻建立，而 WorkManager 的佇列是會被清掉的
        // ——「強制停止」會清，某些廠商的省電管理也會。清掉之後就**再也沒有人重排**，
        // 備份從此靜悄悄地停：設定頁仍然顯示「已連結」（driveBackupEnabled 這個旗標
        // 留在 DataStore 裡），底下卻沒有任何東西在跑，使用者要到需要還原時才發現。
        //
        // schedule() 用的是 ExistingPeriodicWorkPolicy.KEEP，已經有排程時這次呼叫會
        // 被丟掉，**不會把週期從頭算**，所以每次開 app 都補一次是安全的。
        viewModelScope.launch {
            if (settingsStore.current().driveBackupEnabled) {
                BackupWorker.schedule(getApplication())
            }
        }
        // 換月份就換一條 Flow，和換日期同樣的道理。
        //
        // **前後各多讀一個月**：月曆可以左右滑，拖到一半時鄰月已經畫在畫面上了，
        // 那時候才去查資料，滑進來的就是一格空白的月曆、等查完才跳出數字。
        // 多讀兩個月的成本是幾十列，換掉的是每次換月都閃一下。
        viewModelScope.launch {
            snapshotFlow { visibleMonth }
                .flatMapLatest { month ->
                    dao.observeRange(
                        month.minusMonths(1).atDay(1).toString(),
                        month.plusMonths(1).atEndOfMonth().toString(),
                    )
                }
                .collect { totals -> monthTotals = totals.associateBy { it.date } }
        }
        viewModelScope.launch {
            dao.observeFrequentFoods(
                since = LocalDate.now().minusDays(FREQUENT_WINDOW_DAYS).toString(),
                limit = LIBRARY_LIMIT,
            ).collect { frequentFoods = it }
        }
        viewModelScope.launch {
            dao.observeRecentFoods(LIBRARY_LIMIT).collect { recentFoods = it }
        }
        // 邊打邊搜。debounce 是為了不要每按一個鍵就查一次 ——
        // 查詢本身很快，但每次都重建 Flow、重繪整份清單並不值得。
        viewModelScope.launch {
            snapshotFlow { searchQuery }
                .debounce(SEARCH_DEBOUNCE_MS)
                .flatMapLatest { raw ->
                    val tokens = raw.trim().split(WHITESPACE).filter { it.isNotBlank() }
                    if (tokens.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        // SQL 只用第一個關鍵字粗篩，其餘在記憶體裡 AND ——
                        // 這樣「珍奶 大杯」才找得到（兩個字分別落在名稱與份量欄位）
                        dao.searchEntries(tokens.first()).map { rows ->
                            rows.filter { row -> tokens.all(row::matches) }
                        }
                    }
                }
                .collect { searchResults = it }
        }
    }

    // --- 導航 ---

    fun goTo(target: Screen) { screen = target }

    fun openSettingsPage(page: SettingsPage) { screen = Screen.SettingsDetail(page) }

    fun openApiKey(service: ApiService) { screen = Screen.ApiKeyDetail(service) }

    fun backToToday() {
        dataMessage = null
        driveMessage = null
        importPreview = null
        pendingMeal = null
        screen = Screen.Today
    }

    /**
     * 使用者從某一餐的「還沒記」點進新增選單時，記住那一餐。
     *
     * 五個入口（常吃／手動／拍照／相簿／條碼）最後都會走到 [guessMeal]，
     * 所以只要在那三個地方讓它讓位給這個值，不管使用者選哪一條路，
     * 記錄都會落在他點的那一餐 —— 而不是只有「輸入營養素」那一條有效。
     *
     * 從角落那顆章進來時傳 null，回到今日頁時清掉：不清的話下次從角落進來
     * 還會沿用上次那一餐，而使用者根本沒指定過。
     */
    fun setPendingMeal(meal: Meal?) { pendingMeal = meal }

    fun showDate(date: LocalDate) {
        selectedDate = date
        screen = Screen.Today
    }

    fun shiftDay(days: Long) { selectedDate = selectedDate.plusDays(days) }

    /** 開月曆時對齊到目前看的那一天所屬的月份，而不是永遠跳回本月。 */
    fun openHistory() {
        visibleMonth = YearMonth.from(selectedDate)
        screen = Screen.History
    }

    fun shiftMonth(months: Long) { visibleMonth = visibleMonth.plusMonths(months) }

    // --- 搜尋與食物庫 ---

    /** 每次重新打開都從乾淨的狀態開始，不要接著上次的關鍵字。 */
    fun openSearch() {
        searchQuery = ""
        screen = Screen.Search
    }

    fun updateSearchQuery(query: String) { searchQuery = query }

    /** 從食物庫或搜尋結果再記一筆：填好草稿丟進編輯表單，份量與餐別都還能改。 */
    fun reuse(suggestion: FoodSuggestion) = startPrefilled(suggestion.toDraft())

    fun reuse(entry: FoodEntry) = startPrefilled(EntryDraft.of(entry).copy(id = null))

    // --- 編輯 ---

    /**
     * 開一張空白表單。
     *
     * [meal] 給「從某一餐的區塊點進來」用；沒指定就依現在時間猜，
     * 猜錯使用者改一下就好，總比每次都要選。
     */
    fun startNewEntry(meal: Meal? = null) {
        draft = EntryDraft(meal = meal ?: pendingMeal ?: guessMeal())
        screen = Screen.EditEntry
    }

    fun startEdit(entry: FoodEntry) {
        draft = EntryDraft.of(entry)
        screen = Screen.EditEntry
    }

    /** 條碼／拍照流程用：先把草稿填好再切到表單，讓使用者確認後才入庫。 */
    fun startPrefilled(prefilled: EntryDraft) {
        draft = prefilled.copy(meal = pendingMeal ?: guessMeal())
        screen = Screen.EditEntry
    }

    fun updateDraft(newDraft: EntryDraft) { draft = newDraft }

    fun saveDraft() {
        if (!draft.isValid) return
        val entry = draft.toEntry(selectedDate)
        viewModelScope.launch {
            val id = dao.upsert(entry)
            // 新增時 upsert 回傳新的 id；編輯時沿用原本那個（回傳值沒有意義）
            pushToHealthConnect(listOf(if (entry.id != 0L) entry else entry.copy(id = id)))
            pendingMeal = null
            screen = Screen.Today
        }
    }

    /**
     * 今日頁左滑刪除的那一筆。刪完把它留在手上，[UNDO_WINDOW_MS] 之內可以復原。
     *
     * 資料庫是**立刻**刪的，不是等倒數結束才刪 —— 這樣列會馬上消失、熱量馬上更新，
     * 使用者看到的就是刪掉了。復原是把同一個物件原封不動塞回去（`upsert` 會沿用它
     * 原本的 id），所以連 CSV 的去重鍵都不會變，備份不會多出一筆。
     */
    fun deleteEntry(entry: FoodEntry) {
        // 前一筆的復原機會就此作廢（它已經刪掉了，只是不再提供復原）
        undoJob?.cancel()
        viewModelScope.launch {
            dao.delete(entry)
            removeFromHealthConnect(entry.id)
            pendingUndo = entry
            undoJob = launch {
                delay(UNDO_WINDOW_MS)
                pendingUndo = null
            }
        }
    }

    fun undoDelete() {
        val entry = pendingUndo ?: return
        undoJob?.cancel()
        pendingUndo = null
        viewModelScope.launch {
            dao.upsert(entry)
            pushToHealthConnect(listOf(entry))
        }
    }

    /** 刪掉正在編輯的那一筆。新增中的草稿還沒進資料庫，沒得刪。 */
    fun deleteCurrentDraft() {
        val id = draft.id ?: return
        viewModelScope.launch {
            dao.findEntry(id)?.let {
                dao.delete(it)
                removeFromHealthConnect(it.id)
            }
            // 刪完要退出去，不然會停在一張已經不存在的紀錄上
            screen = Screen.Today
        }
    }

    // --- 條碼 ---

    fun openBarcode() {
        barcodeState = BarcodeState.Idle
        screen = Screen.Barcode
    }

    /**
     * 先問本機快取，沒有才連 OFF；查到就順手存起來。
     * 這樣常吃的東西第二次之後完全不用網路，也避開 OFF 每分鐘 15 次的限制。
     */
    fun lookupBarcode(rawCode: String) {
        val code = rawCode.filter { it.isDigit() }
        if (code.isEmpty()) return
        barcodeState = BarcodeState.Loading
        viewModelScope.launch {
            dao.findProduct(code)?.let {
                barcodeState = BarcodeState.Found(it, fromCache = true)
                return@launch
            }
            barcodeState = openFoodFacts.lookup(code).fold(
                onSuccess = { product ->
                    if (product == null) {
                        BarcodeState.NotFound
                    } else {
                        dao.cacheProduct(product)
                        BarcodeState.Found(product, fromCache = false)
                    }
                },
                onFailure = { cause ->
                    val app = getApplication<Application>()
                    BarcodeState.Failed(
                        app.getString(R.string.network_error) + "（" + (cause.message ?: "unknown") + "）"
                    )
                },
            )
        }
    }

    /** 掃描器叫不出來時（Play 服務缺模組）走這裡，讓畫面明說並導向手動輸入。 */
    fun failBarcodeScanner(message: String) {
        barcodeState = BarcodeState.Failed(message)
    }

    /** 條碼查到的是每 100 g，這裡按實際吃的公克數換算後丟進表單讓使用者確認。 */
    fun useProduct(product: CachedProduct, grams: Double) {
        startPrefilled(product.toDraft(grams))
    }

    // --- AI 辨識（照片與文字共用）---

    /** 有沒有 key。給 UI 在開相機**之前**問，別讓使用者拍完才發現不能用。 */
    fun hasApiKey(): Boolean = settings.geminiApiKey.isNotBlank()

    /**
     * [provider] 一定要帶：兩家各有一把 key，訊息只寫「還沒設定 API key」的話，
     * 使用者很可能去填錯的那一把（尤其文字選了 OpenRouter、拍照卻缺 Gemini 那把時）。
     */
    fun reportMissingApiKey(provider: AiProvider = AiProvider.GEMINI) {
        analysisState = AnalysisState.Failed(NO_API_KEY + ":" + provider.label)
        screen = Screen.Review
    }

    fun openTextLookup() { screen = Screen.TextLookup }

    fun analyzePhoto(uri: Uri) = startAnalysis(AnalysisSource.Photo(uri))

    fun analyzeText(query: String, useSearch: Boolean) {
        if (query.isBlank()) return
        startAnalysis(AnalysisSource.Text(query.trim(), useSearch))
    }

    /** 重跑上一次的辨識。要重跑哪一件事由 [lastSource] 決定，畫面不必記。 */
    fun retryAnalysis() { lastSource?.let(::startAnalysis) }

    /**
     * 送去 Gemini -> 進確認畫面。
     *
     * 這裡**不會**直接寫進資料庫。模型估的數字一定要讓使用者看過、
     * 可以取消勾選，否則等於在使用者的飲食紀錄裡塞它自己編的數字。
     */
    private fun startAnalysis(source: AnalysisSource) {
        // 拍照永遠走 Gemini（要吃得下圖片的模型），文字才看使用者選了哪一家。
        // 所以要擋的是「這一條路要用的那把 key」，不是固定擋 Gemini 那把。
        val useOpenRouter =
            source is AnalysisSource.Text && settings.textProvider == AiProvider.OPENROUTER
        val key = if (useOpenRouter) settings.openRouterApiKey else settings.geminiApiKey
        // 這裡仍然要擋一次：從相機回來的期間設定可能被改掉，
        // 而這裡才是真正會把 key 送出去的地方。
        if (key.isBlank()) {
            reportMissingApiKey(if (useOpenRouter) AiProvider.OPENROUTER else AiProvider.GEMINI)
            return
        }
        lastSource = source
        analysisMeal = pendingMeal ?: guessMeal()
        analysisState = AnalysisState.Analyzing
        screen = Screen.Review
        viewModelScope.launch {
            val model = if (useOpenRouter) settings.openRouterModel else settings.geminiModel
            // **要不要查是使用者按哪一顆章決定的**，設定只決定「按下去用誰查」。
            //
            // 試過讓模型自己決定（tool-calling-search 分支），兩版都輸：不強制
            // tool_choice 它永遠不收斂，強制之後它自己下的查詢又比固定字尾差
            // （撈到美規數字而不是台灣官方頁）。而使用者打字的當下就已經知道要不要
            // 查了 —— 他在食物前面加店名，就是想要官方資料。
            //
            // Tavily 是自己先查、把結果當背景文字帶進去，所以兩家都適用；
            // OpenRouter 內建那個是它自己在伺服器端查，只有走 OpenRouter 時才有作用。
            // 查不到就是 null，讓模型照原本的方式估 —— 搜尋壞掉不該讓整條辨識失敗。
            val wantsSearch = source is AnalysisSource.Text && source.useSearch
            val searchContext = if (
                wantsSearch && settings.searchMode == SearchMode.TAVILY
            ) {
                tavily.contextFor((source as AnalysisSource.Text).query, settings.tavilyApiKey)
            } else {
                null
            }
            val result = when (source) {
                is AnalysisSource.Text ->
                    if (useOpenRouter)
                        openRouter.analyzeDescription(
                            source.query,
                            key,
                            model,
                            webSearch = wantsSearch &&
                                settings.searchMode == SearchMode.OPENROUTER,
                            searchContext = searchContext,
                        )
                    else gemini.analyzeDescription(source.query, key, model, searchContext)
                is AnalysisSource.Photo ->
                    // 壓縮失敗（檔案壞了、格式不支援）也要走同一條錯誤路徑，
                    // 不然使用者只會看到轉圈停住
                    ImageCompressor.toBase64Jpeg(getApplication(), source.uri)
                        .mapCatching { base64 ->
                            gemini.analyzeFood(base64, key, model).getOrThrow()
                        }
            }
            analysisState = result.fold(
                onSuccess = { AnalysisState.Ready(it.map { food -> AnalysisItem(baseFood = food) }) },
                onFailure = { AnalysisState.Failed(it.message ?: "unknown") },
            )
        }
    }

    fun updateAnalysisMeal(meal: Meal) { analysisMeal = meal }

    fun toggleAnalysisItem(index: Int) {
        val current = analysisState as? AnalysisState.Ready ?: return
        analysisState = AnalysisState.Ready(
            current.items.mapIndexed { i, item ->
                if (i == index) item.copy(selected = !item.selected) else item
            }
        )
    }

    fun updateAnalysisMultiplier(index: Int, multiplier: Double) {
        val current = analysisState as? AnalysisState.Ready ?: return
        val clamped = (multiplier.coerceIn(0.1, 5.0) * 10.0).roundToInt() / 10.0
        analysisState = AnalysisState.Ready(
            current.items.mapIndexed { i, item ->
                if (i == index) item.copy(multiplier = clamped) else item
            }
        )
    }

    /** 一次寫入所有勾選的項目。存完就回今天，使用者要細調再點進去改。 */
    fun saveAnalysisSelection() {
        val current = analysisState as? AnalysisState.Ready ?: return
        val chosen = current.items.filter { it.selected }
        if (chosen.isEmpty()) return
        val meal = analysisMeal
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            val fresh = chosen.map { it.food.toEntry(selectedDate, meal, now, portionMultiplier = it.multiplier) }
            val ids = dao.insertAll(fresh)
            pushToHealthConnect(fresh.zip(ids) { entry, id -> entry.copy(id = id) })
            analysisState = null
            screen = Screen.Today
        }
    }

    // --- 設定 ---

    /** 建議的檔名。交給 SAF 當預設值，使用者仍可自己改。 */
    fun suggestedCsvName(): String = CsvExport.fileName()

    /**
     * 寫進使用者用系統選擇器挑的位置。
     *
     * 走 SAF 而不是自己找路徑：不需要任何儲存權限，而且檔案落在
     * 使用者自己看得到的地方（下載資料夾、雲端硬碟…），
     * 不是藏在 app 沙箱裡等著被解除安裝一起刪掉。
     */
    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            dataMessage = runCatching {
                val entries = dao.allEntries()
                val csv = CsvExport.build(entries)
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csv.toByteArray(Charsets.UTF_8))
                } ?: error("無法寫入檔案")
                entries.size
            }.fold(
                onSuccess = { count ->
                    getApplication<Application>().getString(R.string.export_done, count)
                },
                onFailure = { cause ->
                    getApplication<Application>().getString(
                        R.string.export_failed,
                        cause.message ?: "unknown",
                    )
                },
            )
        }
    }

    /**
     * 讀檔、解析、和現有紀錄比對，然後停在確認面板 —— **這一步不寫資料庫**。
     *
     * 去重的鍵見 [CsvImport.dedupeKey]。用同一個 seen 集合連檔案內部的重複也一起
     * 擋掉：使用者可能把兩次匯出貼成同一個檔，那時候「已經有的」判斷不到，
     * 但同一筆確實會出現兩次。
     */
    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val text = getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: error("無法讀取檔案")
                previewOf(text)
            }.fold(
                onSuccess = { preview ->
                    dataMessage = null
                    // 一筆都不會新增就不必開確認面板，直接把結論講完
                    if (preview.newEntries.isEmpty()) {
                        dataMessage = getApplication<Application>().getString(
                            R.string.import_nothing_new,
                            preview.duplicates,
                        )
                    } else {
                        importPreview = preview
                    }
                },
                onFailure = { cause ->
                    importPreview = null
                    dataMessage = if (cause is CsvImport.NotNutriLogCsv) {
                        getApplication<Application>().getString(R.string.import_bad_format)
                    } else {
                        getApplication<Application>().getString(
                            R.string.import_failed,
                            cause.message ?: "unknown",
                        )
                    }
                },
            )
        }
    }

    /**
     * 把一份 CSV 和目前資料庫比對出「會新增什麼」。
     *
     * 本地匯入與雲端還原共用這一段 —— 兩邊的去重規則本來就該一樣，
     * 分成兩份遲早會走樣。同一個 seen 集合連檔案內部的重複也一起擋掉。
     */
    private suspend fun previewOf(csv: String): ImportPreview {
        val parsed = CsvImport.parse(csv)
        val seen = dao.allEntries().mapTo(mutableSetOf()) { CsvImport.dedupeKey(it) }
        val fresh = mutableListOf<FoodEntry>()
        var duplicates = 0
        parsed.entries.forEach { entry ->
            if (seen.add(CsvImport.dedupeKey(entry))) fresh += entry else duplicates++
        }
        return ImportPreview(newEntries = fresh, duplicates = duplicates, skipped = parsed.skipped)
    }

    fun confirmImport() {
        val preview = importPreview ?: return
        importPreview = null
        preview.profile?.let { updateSettings(it.applyTo(settings)) }
        viewModelScope.launch {
            dataMessage = runCatching {
                val ids = dao.insertAll(preview.newEntries)
                pushToHealthConnect(preview.newEntries.zip(ids) { entry, id -> entry.copy(id = id) })
                preview.newEntries.size
            }.fold(
                onSuccess = { count ->
                    // 只接回身型、沒有新紀錄時，講「匯入 0 筆」會讓人以為什麼都沒發生
                    if (count == 0 && preview.profile != null) {
                        getApplication<Application>().getString(R.string.import_profile_restored)
                    } else {
                        getApplication<Application>().getString(R.string.import_done, count)
                    }
                },
                onFailure = { cause ->
                    getApplication<Application>().getString(
                        R.string.import_failed,
                        cause.message ?: "unknown",
                    )
                },
            )
        }
    }

    fun cancelImport() { importPreview = null }

    // --- 健康連線（Health Connect／Samsung Health）---

    private val healthConnectSync = HealthConnectSync(application)
    val isHealthConnectSupported: Boolean get() = healthConnectSync.isSupported()

    /** 讀運動資料的權限（四種讀取型別至少拿到一種就算，它們彼此是退路）。 */
    var healthReadAuthorized by mutableStateOf(false)
        private set

    /** 把飲食寫進健康連線的權限。 */
    var healthWriteAuthorized by mutableStateOf(false)
        private set

    /**
     * 每一天從健康連線讀到的活動消耗。**今日頁、週長條、月曆格子、月摘要讀的是同一份** ——
     * 各算各的話，同一天在今日頁沒超標、到了月曆上卻是紅的。
     */
    val activeCaloriesMap = mutableStateMapOf<LocalDate, Double>()

    /** 那一天這次讀到的完整結果（來源、步數、運動場次、讀不到的原因），同步明細要講數字從哪來。 */
    val dailyActivityMap = mutableStateMapOf<LocalDate, DailyActivity>()

    var healthSyncBusy by mutableStateOf(false)
        private set

    /** 最近一次整批寫入或權限要求的結果，設定頁顯示用。 */
    var healthSyncResult by mutableStateOf<HealthSyncResult?>(null)
        private set

    /**
     * 要叫出健康連線權限畫面時設成這次要的那一組，App.kt 看到就 launch。
     * 權限畫面是 Activity 層的事，ViewModel 叫不出來，只能留一個請求讓畫面層接走。
     */
    var pendingHealthPermissions by mutableStateOf<Set<String>?>(null)
        private set

    /** 送出去的是哪一組 —— 回來時只打開這次要的那一邊，見 [onHealthPermissionsResult]。 */
    private var requestedHealthPermissions: Set<String> = emptySet()

    fun checkHealthPermissions() {
        if (!healthConnectSync.isSupported()) return
        viewModelScope.launch {
            healthReadAuthorized = healthConnectSync.hasReadExercisePermission()
            healthWriteAuthorized = healthConnectSync.hasWritePermission()
        }
    }

    /** 回到 app：權限可能在系統設定裡被改過，運動消耗也可能剛同步進來。只重讀今天，不整批重寫飲食。 */
    fun onAppResume() {
        checkHealthPermissions()
        fetchActiveCalories(selectedDate)
    }

    /** 讀某一天的活動消耗。沒有權限或裝置不支援就只看本機快取，不主動打擾使用者。 */
    fun fetchActiveCalories(date: LocalDate = selectedDate) {
        if (!settings.readExerciseCalories) return
        viewModelScope.launch { readActivity(date) }
    }

    /**
     * 使用者在同步明細裡按「重新讀取」。和 [fetchActiveCalories] 不同，這是明確要求，
     * 所以缺權限時可以直接去要。
     */
    fun refreshActiveCalories(date: LocalDate = selectedDate) {
        if (!settings.readExerciseCalories) return
        viewModelScope.launch {
            if (!healthConnectSync.isSupported()) {
                healthSyncResult = HealthSyncResult.NotSupported
                return@launch
            }
            if (!healthConnectSync.hasReadExercisePermission()) {
                pendingHealthPermissions = HealthConnectSync.READ_EXERCISE_PERMISSIONS
                return@launch
            }
            val activity = syncDailyActivity(date)
            // 讀不到、而且真的還缺某些讀取型別時補問一次：只授權過活動大卡的人，
            // 光看 hasReadExercisePermission() 會以為一切正常，永遠不會被問到步數。
            val missingRead = HealthConnectSync.READ_EXERCISE_PERMISSIONS intersect
                healthConnectSync.missingPermissions()
            if (activity.source == ActivitySource.NONE && missingRead.isNotEmpty()) {
                pendingHealthPermissions = HealthConnectSync.READ_EXERCISE_PERMISSIONS
            }
        }
    }

    private suspend fun readActivity(date: LocalDate) {
        if (healthConnectSync.isSupported() && healthConnectSync.hasReadExercisePermission()) {
            healthReadAuthorized = true
            syncDailyActivity(date)
        } else {
            dao.getHealthMetric(date.toString())?.let { activeCaloriesMap[date] = it.activeCalories }
        }
    }

    /**
     * 讀一天的活動量，更新記憶體與本機快取，回傳這次讀到的結果。
     *
     * 讀不到（[ActivitySource.NONE]）時要分兩種：**真的出錯或缺權限**就保留舊快取，
     * 不要把之前讀到的 400 大卡蓋成 0；**讀得到但那天就是沒動**則照實寫 0 ——
     * 沿用舊值的話，一天沒戴錶也會一直掛著前一次的數字。
     */
    private suspend fun syncDailyActivity(date: LocalDate): DailyActivity {
        val activity = healthConnectSync.readDailyActivity(date, settings)
        dailyActivityMap[date] = activity
        val dateKey = date.toString()
        val cached = dao.getHealthMetric(dateKey)
        if (activity.source == ActivitySource.NONE &&
            activity.unavailableReason != null &&
            activity.unavailableReason != NO_ACTIVITY_DATA_REASON
        ) {
            activeCaloriesMap[date] = cached?.activeCalories ?: 0.0
            return activity
        }
        activeCaloriesMap[date] = activity.calories
        dao.upsertHealthMetric(
            (cached ?: DailyHealthMetric(date = dateKey)).copy(
                activeCalories = activity.calories,
                steps = activity.steps,
                workoutCalories = activity.workoutCalories,
                lastSyncedAt = System.currentTimeMillis(),
            )
        )
        return activity
    }

    /**
     * 月曆換到某個月：先把本機快取貼上去，再去讀「有記錄的那幾天」與今天。
     *
     * **一次一天、依序讀**，不要整個月一起丟出去 —— 每一天背後是好幾個查詢，
     * 同時送出去容易撞到健康連線的讀取頻率限制。過去的日子讀過一次就不太會變，
     * 已經有快取的就不重讀；今天永遠重讀。
     */
    fun refreshMonthHealthMetrics(month: YearMonth) {
        viewModelScope.launch {
            dao.getHealthMetricsInRange(month.atDay(1).toString(), month.atEndOfMonth().toString())
                .forEach { metric ->
                    runCatching { LocalDate.parse(metric.date) }.getOrNull()
                        ?.let { activeCaloriesMap[it] = metric.activeCalories }
                }
            if (!settings.readExerciseCalories || !healthConnectSync.isSupported() ||
                !healthConnectSync.hasReadExercisePermission()
            ) return@launch
            val today = LocalDate.now()
            val prefix = "%04d-%02d".format(month.year, month.monthValue)
            val loggedDays = monthTotals.keys.filter { it.startsWith(prefix) }
                .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            (loggedDays + today).distinct().sorted()
                .filter { it.year == month.year && it.month == month.month && !it.isAfter(today) }
                .filter { it == today || it !in activeCaloriesMap }
                .forEach { syncDailyActivity(it) }
        }
    }

    /** 「讀取運動消耗」開關。打開時缺讀取權限就去要，拿到權限才真的打開。 */
    fun setReadExerciseCalories(enabled: Boolean) {
        if (!enabled) {
            updateSettings(settings.copy(readExerciseCalories = false))
            return
        }
        viewModelScope.launch {
            if (!healthConnectSync.isSupported()) {
                healthSyncResult = HealthSyncResult.NotSupported
                return@launch
            }
            if (healthConnectSync.hasReadExercisePermission()) {
                healthReadAuthorized = true
                updateSettings(settings.copy(readExerciseCalories = true))
                readActivity(selectedDate)
            } else {
                pendingHealthPermissions = HealthConnectSync.READ_EXERCISE_PERMISSIONS
            }
        }
    }

    /** 「寫入飲食」開關。**預設關**；打開時缺寫入權限就去要，拿到之後把既有紀錄補寫一次。 */
    fun setHealthConnectSync(enabled: Boolean) {
        if (!enabled) {
            updateSettings(settings.copy(healthConnectSyncEnabled = false))
            return
        }
        viewModelScope.launch {
            if (!healthConnectSync.isSupported()) {
                healthSyncResult = HealthSyncResult.NotSupported
                return@launch
            }
            if (healthConnectSync.hasWritePermission()) {
                healthWriteAuthorized = true
                updateSettings(settings.copy(healthConnectSyncEnabled = true))
                syncAllToHealthConnect()
            } else {
                pendingHealthPermissions = HealthConnectSync.WRITE_PERMISSIONS
            }
        }
    }

    fun onHealthPermissionRequestLaunched() {
        requestedHealthPermissions = pendingHealthPermissions.orEmpty()
        pendingHealthPermissions = null
    }

    /**
     * 權限畫面回來。**只打開這次要的那一邊** —— 使用者為了讀運動資料而授權時順手連寫入也給了，
     * 不代表他想把飲食寫出去。授權結果重新向系統查，不信任回傳的集合（使用者可能中途跳出）。
     */
    fun onHealthPermissionsResult() {
        val requested = requestedHealthPermissions
        requestedHealthPermissions = emptySet()
        viewModelScope.launch {
            healthReadAuthorized = healthConnectSync.hasReadExercisePermission()
            healthWriteAuthorized = healthConnectSync.hasWritePermission()
            val askedRead = requested.any { it in HealthConnectSync.READ_EXERCISE_PERMISSIONS }
            val askedWrite = requested.any { it in HealthConnectSync.WRITE_PERMISSIONS }
            when {
                askedWrite && healthWriteAuthorized -> {
                    updateSettings(settings.copy(healthConnectSyncEnabled = true))
                    syncAllToHealthConnect()
                }
                askedRead && healthReadAuthorized -> {
                    updateSettings(settings.copy(readExerciseCalories = true))
                    readActivity(selectedDate)
                }
                askedRead || askedWrite -> healthSyncResult = HealthSyncResult.PermissionDenied
            }
        }
    }

    /** 設定頁的「立即同步」：把全部紀錄重寫一次。靠 clientRecordId 更新，不會產生重複。 */
    fun syncAllToHealthConnect() {
        if (healthSyncBusy) return
        if (!healthConnectSync.isSupported()) {
            healthSyncResult = HealthSyncResult.NotSupported
            return
        }
        viewModelScope.launch {
            healthSyncBusy = true
            healthSyncResult = healthConnectSync.syncEntries(dao.allEntries()).fold(
                onSuccess = { count ->
                    updateSettings(settings.copy(lastHealthSyncAt = System.currentTimeMillis()))
                    HealthSyncResult.Written(count)
                },
                onFailure = { HealthSyncResult.Failed(it.message ?: "unknown") },
            )
            healthSyncBusy = false
        }
    }

    /**
     * 開了寫入才把這幾筆變動同步出去。**失敗只記 log，不擋存檔** —— 本機才是資料的主體，
     * 健康連線寫不進去不該讓使用者以為這一筆沒記到；設定頁的「立即同步」可以補。
     */
    private fun pushToHealthConnect(entries: List<FoodEntry>) {
        if (!settings.healthConnectSyncEnabled || entries.isEmpty()) return
        viewModelScope.launch {
            healthConnectSync.syncEntries(entries)
                .onFailure { android.util.Log.w("NutriViewModel", "health connect write failed", it) }
        }
    }

    private fun removeFromHealthConnect(entryId: Long) {
        if (!settings.healthConnectSyncEnabled) return
        viewModelScope.launch {
            healthConnectSync.deleteEntry(entryId)
                .onFailure { android.util.Log.w("NutriViewModel", "health connect delete failed", it) }
        }
    }

    // --- 依身型計算目標 ---

    var showBmrCalculator by mutableStateOf(false)
        private set

    fun openBmrCalculator() { showBmrCalculator = true }

    fun closeBmrCalculator() { showBmrCalculator = false }

    /** 身型計算按「套用」：身型與算出來的目標一起寫進設定。 */
    fun applyBmrPlan(updated: NutriSettings) {
        showBmrCalculator = false
        updateSettings(updated.copy(profileConfigured = true))
    }

    // --- AI 週報／月報 ---

    private val weeklyReportStore = WeeklyReportStore(application)
    private val monthlyReportStore = MonthlyReportStore(application)
    private val weeklyAggregator = WeeklyAggregator()
    private val monthlyAggregator = MonthlyAggregator()

    var reportTab by mutableStateOf(ReportTab.WEEKLY)
        private set
    var reportWeekStart by mutableStateOf(sundayOf(LocalDate.now()))
        private set
    var reportMonth by mutableStateOf(YearMonth.now())
        private set

    var weeklyReportState by mutableStateOf<ReportUiState<WeeklyReport>>(ReportUiState.Loading)
        private set
    var monthlyReportState by mutableStateOf<ReportUiState<MonthlyReport>>(ReportUiState.Loading)
        private set

    /**
     * 統計和報告分開放：統計是本機算的、隨時都有，報告要花一次 AI 呼叫才有。
     * 還沒產生報告的那一週，畫面照樣要能看數字。
     */
    var weeklyStats by mutableStateOf<WeeklyStats?>(null)
        private set
    var monthlyStats by mutableStateOf<MonthlyStats?>(null)
        private set

    /** 正在產生的是哪一週／哪個月。產生到一半切去看別週，回來時要看得出還在跑。 */
    private var generatingWeek: LocalDate? = null
    private var generatingMonth: YearMonth? = null

    fun selectReportTab(tab: ReportTab) { reportTab = tab }

    fun loadReports() {
        loadWeeklyReport()
        loadMonthlyReport()
    }

    /**
     * 從月曆開報表。月報看月曆正在看的那個月；週報看那個月的最後一週（本月就是本週）——
     * 翻到上個月再點進來，卻看到這一週的週報，會以為點錯了。
     */
    fun openReports(month: YearMonth) {
        val now = YearMonth.now()
        reportMonth = if (month.isAfter(now)) now else month
        val today = LocalDate.now()
        val lastDay = reportMonth.atEndOfMonth().let { if (it.isAfter(today)) today else it }
        reportWeekStart = sundayOf(lastDay)
        screen = Screen.Reports
        loadReports()
    }

    fun shiftReportWeek(weeks: Long) {
        val target = reportWeekStart.plusWeeks(weeks)
        if (target.isAfter(sundayOf(LocalDate.now()))) return
        reportWeekStart = target
        loadWeeklyReport()
    }

    fun shiftReportMonth(months: Long) {
        val target = reportMonth.plusMonths(months)
        if (target.isAfter(YearMonth.now())) return
        reportMonth = target
        loadMonthlyReport()
    }

    private fun loadWeeklyReport() {
        val start = reportWeekStart
        viewModelScope.launch {
            val stats = runCatching {
                weeklyAggregator.aggregateWeek(start, dao, healthConnectSync, settings)
            }.getOrNull()
            if (start != reportWeekStart) return@launch
            weeklyStats = stats
            weeklyReportState = when {
                generatingWeek == start -> ReportUiState.Generating
                else -> weeklyReportStore.getReport(start.toString())
                    ?.let { ReportUiState.Ready(it) }
                    ?: ReportUiState.Empty
            }
        }
    }

    private fun loadMonthlyReport() {
        val month = reportMonth
        viewModelScope.launch {
            val stats = runCatching {
                monthlyAggregator.aggregateMonth(month, dao, healthConnectSync, settings)
            }.getOrNull()
            if (month != reportMonth) return@launch
            monthlyStats = stats
            monthlyReportState = when {
                generatingMonth == month -> ReportUiState.Generating
                else -> monthlyReportStore.getReport(month.toString())
                    ?.let { ReportUiState.Ready(it) }
                    ?: ReportUiState.Empty
            }
        }
    }

    fun generateWeeklyReport() {
        if (generatingWeek != null) return
        val start = reportWeekStart
        val provider = settings.reportProvider
        val key = reportKeyFor(provider)
        if (key.isBlank()) {
            weeklyReportState = ReportUiState.MissingKey(provider)
            return
        }
        val snapshot = settings
        generatingWeek = start
        weeklyReportState = ReportUiState.Generating
        viewModelScope.launch {
            val outcome = runCatching {
                val stats = weeklyAggregator.aggregateWeek(start, dao, healthConnectSync, snapshot)
                if (stats.loggedDaysCount == 0 && stats.totalActiveCaloriesBurned == 0.0) {
                    return@runCatching null
                }
                val (system, user) = weeklyAggregator.buildPrompts(stats, snapshot)
                val raw = generateReportText(provider, system, user, key, snapshot).getOrThrow()
                val (markdown, recommendation) = weeklyAggregator.parseReportResponse(raw)
                WeeklyReport(
                    weekStartDate = stats.weekStart.toString(),
                    weekEndDate = stats.weekEnd.toString(),
                    generatedAt = System.currentTimeMillis(),
                    model = reportModelFor(provider, snapshot),
                    markdownContent = markdown,
                    recommendation = recommendation,
                    isApplied = false,
                    comparison = stats.comparison,
                ).also { weeklyReportStore.saveReport(it) }
            }
            generatingWeek = null
            if (start != reportWeekStart) return@launch
            weeklyReportState = outcome.fold(
                onSuccess = { report -> report?.let { ReportUiState.Ready(it) } ?: ReportUiState.Empty },
                onFailure = { ReportUiState.Failed(it.message ?: "unknown") },
            )
        }
    }

    fun generateMonthlyReport() {
        if (generatingMonth != null) return
        val month = reportMonth
        val provider = settings.reportProvider
        val key = reportKeyFor(provider)
        if (key.isBlank()) {
            monthlyReportState = ReportUiState.MissingKey(provider)
            return
        }
        val snapshot = settings
        generatingMonth = month
        monthlyReportState = ReportUiState.Generating
        viewModelScope.launch {
            val outcome = runCatching {
                val stats = monthlyAggregator.aggregateMonth(month, dao, healthConnectSync, snapshot)
                if (stats.loggedDaysCount == 0 && stats.totalActiveCaloriesBurned == 0.0) {
                    return@runCatching null
                }
                val (system, user) = monthlyAggregator.buildPrompts(stats, snapshot)
                val raw = generateReportText(provider, system, user, key, snapshot).getOrThrow()
                MonthlyReport(
                    yearMonth = stats.yearMonth,
                    generatedAt = System.currentTimeMillis(),
                    model = reportModelFor(provider, snapshot),
                    markdownContent = stripThinking(raw),
                    comparison = stats.comparison,
                ).also { monthlyReportStore.saveReport(it) }
            }
            generatingMonth = null
            if (month != reportMonth) return@launch
            monthlyReportState = outcome.fold(
                onSuccess = { report -> report?.let { ReportUiState.Ready(it) } ?: ReportUiState.Empty },
                onFailure = { ReportUiState.Failed(it.message ?: "unknown") },
            )
        }
    }

    /** 換桌面圖示。設定與系統的元件狀態要一起改，只改一邊下次啟動就會被對回去。 */
    fun setAppIcon(icon: AppIcon) {
        if (!icon.ready || icon == settings.appIcon) return
        AppIconSwitcher.apply(getApplication(), icon)
        updateSettings(settings.copy(appIcon = icon))
    }

    /** 週報建議的目標，按了「套用」才寫進設定 —— 模型算出來的數字一律要經過使用者確認。 */
    fun applyRecommendedTargets(recommendation: TargetRecommendation) {
        updateSettings(
            settings.copy(
                calorieTarget = recommendation.calorieTarget,
                proteinTargetG = recommendation.proteinTargetG,
                fatTargetG = recommendation.fatTargetG,
                carbsTargetG = recommendation.carbsTargetG,
            )
        )
        val state = weeklyReportState
        if (state is ReportUiState.Ready) {
            val updated = state.report.copy(isApplied = true)
            weeklyReportState = ReportUiState.Ready(updated)
            viewModelScope.launch { weeklyReportStore.saveReport(updated) }
        }
    }

    /** 報告送去哪一家，就用那一家已經填好的金鑰與模型 —— 報告不另外要一把金鑰。 */
    private fun reportKeyFor(provider: AiProvider): String = when (provider) {
        AiProvider.GEMINI -> settings.geminiApiKey
        AiProvider.OPENROUTER -> settings.openRouterApiKey
    }

    private fun reportModelFor(provider: AiProvider, s: NutriSettings): String = when (provider) {
        AiProvider.GEMINI -> s.geminiModel
        AiProvider.OPENROUTER -> s.openRouterModel
    }

    private suspend fun generateReportText(
        provider: AiProvider,
        system: String,
        user: String,
        key: String,
        s: NutriSettings,
    ): Result<String> = when (provider) {
        AiProvider.GEMINI -> gemini.generateText(system, user, key, s.geminiModel)
        AiProvider.OPENROUTER -> openRouter.generateText(system, user, key, s.openRouterModel)
    }

    /** 有些推理模型會把思考過程包在 <think> 裡一起回來，那不是報告的一部分。 */
    private fun stripThinking(raw: String): String =
        raw.replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "").trim()

    /** 報表的一週從星期日開始，和今日頁的週長條一致（日 一 二 … 六）。 */
    private fun sundayOf(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value % 7).toLong())

    // 放在所有健康連線與報表的欄位之後：init 區塊和屬性初始化是照書寫順序跑的，
    // 寫在前面的話這裡用到的 healthConnectSync 還是 null。
    init {
        checkHealthPermissions()
        // 先把本機快取的每日運動消耗貼上：週長條與月曆一打開就要用加上運動後的目標判斷，
        // 等健康連線回應才更新的話，每次開 app 顏色都會跳一次。
        viewModelScope.launch {
            dao.getAllHealthMetrics().forEach { metric ->
                runCatching { LocalDate.parse(metric.date) }.getOrNull()
                    ?.let { activeCaloriesMap[it] = metric.activeCalories }
            }
        }
        viewModelScope.launch { snapshotFlow { selectedDate }.collect { fetchActiveCalories(it) } }
        viewModelScope.launch { snapshotFlow { visibleMonth }.collect { refreshMonthHealthMetrics(it) } }
    }

    // --- Google Drive 備份 ---

    private val driveBackup by lazy { DriveBackup(application) }
    private val driveAuth by lazy { DriveAuth(application) }

    /** 同意畫面回來之後要繼續做的事。授權本身不是目的，使用者按的是「備份」或「還原」。 */
    private var afterConsent: (suspend (String) -> Unit)? = null

    /**
     * 連結 Drive：授權 → 打開每日排程 → **先看雲端有沒有東西可以接回來**。
     *
     * 換手機的情境裡，使用者按這顆鈕想要的是「把我的紀錄接回來」，不是「開始備份」。
     * 所以有找到雲端備份就停在確認面板（和本地匯入同一條路、同一套去重），
     * 沒有才立刻備份一次 —— 只把開關打開的話，使用者要等到明天才知道這件事有沒有
     * 成功，而那時候他已經不在設定頁了。
     *
     * **有東西可以還原時絕對不能先備份。** 備份檔名是當天日期，會把雲端那份完整的
     * 蓋成這支新手機上還空空如也的狀態 —— 正好毀掉使用者要救的東西。
     */
    fun connectDrive() = runDrive(R.string.drive_connecting) { token ->
        settingsStore.save(settingsStore.current().copy(driveBackupEnabled = true))
        BackupWorker.schedule(getApplication())

        val preview = driveBackup.latestBackupCsv(token).getOrNull()?.let { previewOf(it) }
        // 跟本機一模一樣就不必問（例如同一支手機斷開再連回來）。
        val profile = driveBackup.latestBackupProfile(token).getOrNull()
            ?.takeIf { it != BackedUpProfile.from(settings) }
        if ((preview != null && preview.newEntries.isNotEmpty()) || profile != null) {
            // 有東西可以接回來時**絕對不能先備份**：紀錄那邊會被當天日期的檔蓋掉，
            // 身型這邊則會多出一份日期最新的預設值，下次還原反而拿到它。
            importPreview = (preview ?: ImportPreview(emptyList(), 0, 0)).copy(profile = profile)
            null
        } else {
            val name = driveBackup.backupNow(token).getOrThrow()
            getApplication<Application>().getString(R.string.drive_backup_done, name)
        }
    }

    fun backupNow() = runDrive(R.string.drive_backing_up) { token ->
        val name = driveBackup.backupNow(token).getOrThrow()
        getApplication<Application>().getString(R.string.drive_backup_done, name)
    }

    /** 只停掉自動備份，不動雲端已經備份好的東西 —— 那是使用者的檔案，不是我們的。 */
    fun disconnectDrive() {
        viewModelScope.launch {
            BackupWorker.cancel(getApplication())
            settingsStore.save(settingsStore.current().copy(driveBackupEnabled = false))
            driveMessage = getApplication<Application>().getString(R.string.drive_disconnected)
        }
    }

    /** App.kt 發射完同意畫面就呼叫這個，避免同一個 PendingIntent 被送兩次。 */
    fun consentLaunched() { pendingConsent = null }

    fun onConsentResult(data: Intent?) {
        val next = afterConsent
        afterConsent = null
        val token = driveAuth.tokenFromConsent(data).getOrElse { cause ->
            driveBusy = false
            // 使用者自己按返回不是錯誤，不要留一行紅字說「Drive 出錯：User cancelled flow」——
            // 他知道自己取消了，那行只會看起來像壞掉。
            driveMessage = if (isCancellation(cause)) null else failureText(cause)
            return
        }
        viewModelScope.launch {
            runCatching { next?.invoke(token) }
                .onFailure { driveMessage = failureText(it) }
            driveBusy = false
        }
    }

    /**
     * Drive 的動作長得都一樣：先拿權杖（可能要先問使用者），成功就跑事情、
     * 失敗就把原因講出來。把這段收在一起，三個入口才不會各寫一次而慢慢走樣。
     *
     * [block] 回傳要顯示的訊息；回 null 代表「這次不是用訊息收尾」
     * （還原會開確認面板，那時候再蓋一行訊息只是噪音）。
     */
    private fun runDrive(busyLabel: Int, block: suspend (String) -> String?) {
        if (driveBusy) return
        driveBusy = true
        driveMessage = getApplication<Application>().getString(busyLabel)
        viewModelScope.launch {
            val outcome = driveAuth.authorize()
            outcome.fold(
                onSuccess = { result ->
                    when (result) {
                        is DriveAuth.Outcome.Token -> {
                            runCatching { block(result.accessToken) }
                                .onSuccess { message -> message?.let { driveMessage = it } }
                                .onFailure { driveMessage = failureText(it) }
                            driveBusy = false
                        }
                        is DriveAuth.Outcome.NeedsConsent -> {
                            // 同意畫面是非同步的，driveBusy 要等 onConsentResult 才放掉
                            afterConsent = { token ->
                                block(token)?.let { driveMessage = it }
                            }
                            pendingConsent = result.pendingIntent
                        }
                    }
                },
                onFailure = {
                    driveMessage = failureText(it)
                    driveBusy = false
                },
            )
        }
    }

    /** GMS 的取消是一個狀態碼，不是一種例外型別，所以只能認碼。 */
    private fun isCancellation(cause: Throwable): Boolean =
        cause is ApiException && cause.statusCode == CommonStatusCodes.CANCELED

    private fun failureText(cause: Throwable): String =
        getApplication<Application>().getString(
            R.string.drive_failed,
            cause.message ?: cause.javaClass.simpleName,
        )

    fun updateSettings(newSettings: NutriSettings) {
        // 先更新 UI 再落地，避免打字或拉 slider 時卡頓
        settings = newSettings
        viewModelScope.launch { settingsStore.save(newSettings) }
    }

    private fun guessMeal(): Meal {
        val now = LocalTime.now()
        return when {
            now.isBefore(LocalTime.of(10, 30)) -> Meal.BREAKFAST
            now.isBefore(LocalTime.of(15, 0)) -> Meal.LUNCH
            now.isBefore(LocalTime.of(21, 0)) -> Meal.DINNER
            else -> Meal.SNACK
        }
    }

    companion object {
        /** 用哨兵字串而不是寫死訊息，UI 才能把它換成有「去設定」按鈕的畫面。 */
        const val NO_API_KEY = "NO_API_KEY"

        /** 「常吃」只算最近這麼多天 —— 它該反映現在的習慣，不是三個月前戒掉的東西。 */
        private const val FREQUENT_WINDOW_DAYS = 90L

        private const val LIBRARY_LIMIT = 60
        private const val SEARCH_DEBOUNCE_MS = 250L
    }
}

/** 逐筆搜尋與食物庫篩選共用同一套拆字規則，所以放在檔案層級而不是 companion 裡。 */
private val WHITESPACE = Regex("\\s+")

/** 模型的估算值 -> 可以入庫的一筆紀錄。 */
private fun DetectedFood.toEntry(date: LocalDate, meal: Meal, loggedAt: Long, portionMultiplier: Double = 1.0) = FoodEntry(
    date = date.toString(),
    loggedAt = loggedAt,
    meal = meal.name,
    name = name.ifBlank { "未命名食物" },
    servingText = servingText,
    calories = calories,
    proteinG = proteinG,
    fatG = fatG,
    carbsG = carbsG,
    sugarG = sugarG,
    sodiumMg = sodiumMg,
    fiberG = fiberG,
    satFatG = satFatG,
    source = EntrySource.PHOTO.name,
    portionMultiplier = portionMultiplier,
)

/**
 * 條碼商品（每 100 g）換算成指定公克數的表單草稿。
 *
 * 缺的營養素維持 null 而不是補 0 —— OFF 沒有那筆資料，
 * 補 0 會讓使用者以為這個食物真的不含鈉。
 */
fun CachedProduct.toDraft(grams: Double): EntryDraft {
    val ratio = grams / 100.0
    fun scale(per100g: Double?): String = per100g?.times(ratio)?.roundTo1()?.asInputValue() ?: ""
    return EntryDraft(
        meal = Meal.BREAKFAST,
        name = name,
        servingText = grams.roundTo1().asInputValue() + " g" + (brand?.let { "・" + it } ?: ""),
        calories = scale(caloriesPer100g),
        protein = scale(proteinPer100g),
        fat = scale(fatPer100g),
        carbs = scale(carbsPer100g),
        sugar = scale(sugarPer100g),
        sodium = scale(sodiumMgPer100g),
        fiber = scale(fiberPer100g),
        satFat = scale(satFatPer100g),
        source = EntrySource.BARCODE,
        barcode = barcode,
    )
}

/** OFF 上的 serving_size 長得像 "15 g"，取開頭的數字當預設份量；認不出來就用 100 g。 */
fun CachedProduct.defaultGrams(): Double =
    servingSizeText?.let { Regex("[0-9]+(\\.[0-9]+)?").find(it)?.value?.toDoubleOrNull() }
        ?.takeIf { it > 0 }
        ?: 100.0

/** 依照倍率等比縮放食物營養素。 */
fun DetectedFood.scale(multiplier: Double): DetectedFood {
    if (multiplier == 1.0) return this
    val mult = (multiplier * 10.0).roundToInt() / 10.0
    return copy(
        servingText = scaleServingText(servingText, mult),
        calories = Math.round(calories * mult).toDouble(),
        proteinG = (proteinG * mult).roundTo1(),
        fatG = (fatG * mult).roundTo1(),
        carbsG = (carbsG * mult).roundTo1(),
        sugarG = sugarG?.let { (it * mult).roundTo1() },
        sodiumMg = sodiumMg?.let { Math.round(it * mult).toDouble() },
        fiberG = fiberG?.let { (it * mult).roundTo1() },
        satFatG = satFatG?.let { (it * mult).roundTo1() },
    )
}

/** 智慧份量文字縮放：數值（如 200g, 700ml, 1 碗）等比縮放，無數值則附加倍數標記。 */
fun scaleServingText(text: String, multiplier: Double): String {
    val trimmed = text.trim()
    val mult = (multiplier * 10.0).roundToInt() / 10.0
    if (trimmed.isEmpty()) {
        return if (mult == 1.0) "" else "${mult.asInputValue()} 份"
    }
    if (mult == 1.0) return trimmed

    val numberRegex = Regex("[0-9]+(?:\\.[0-9]+)?")
    val matches = numberRegex.findAll(trimmed).toList()
    if (matches.isNotEmpty()) {
        val sb = StringBuilder()
        var lastIndex = 0
        for (match in matches) {
            sb.append(trimmed.substring(lastIndex, match.range.first))
            val origVal = match.value.toDoubleOrNull()
            if (origVal != null && origVal > 0) {
                val scaled = (origVal * mult).roundTo1()
                sb.append(scaled.asInputValue())
            } else {
                sb.append(match.value)
            }
            lastIndex = match.range.last + 1
        }
        sb.append(trimmed.substring(lastIndex))
        return sb.toString()
    } else {
        val clean = trimmed.replace(Regex("\\s*\\([0-9.]*x\\)"), "")
        return "$clean (${mult.asInputValue()}x)"
    }
}

fun Double.roundTo1(): Double = Math.round(this * 10.0) / 10.0

fun Double.asInputValue(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

/**
 * 一筆紀錄有沒有命中某個關鍵字。名稱與份量文字都算 ——
 * 規格常常只寫在份量裡（「大杯 700ml 半糖」），只比對名稱會漏掉。
 */
fun FoodEntry.matches(token: String): Boolean =
    name.contains(token, ignoreCase = true) || servingText.contains(token, ignoreCase = true)

/**
 * 近似命中的門檻。這是整套模糊比對唯一的旋鈕。
 *
 * 調高會退回「打太細就找不到」，調低會開始推薦只共用一個常見字的東西。
 * 0.3 的實際意義最好記的說法是：**兩個字的關鍵字，兩個字都要出現**
 * （只中一個是 0.25，落在門檻外）。
 */
private const val NEAR_MATCH_THRESHOLD = 0.3

/** 整串命中。刻意大於近似分數的上限 1.0，理由見 [matchScore]。 */
private const val EXACT_MATCH_SCORE = 2.0

/**
 * 相鄰兩字比單字重幾倍。相鄰兩字帶著詞的邊界資訊（「黑咖」幾乎只會出現在黑咖啡裡），
 * 單字沒有（「咖」咖哩也有），所以兩者都要算但不能等重。
 */
private const val BIGRAM_WEIGHT = 2

/**
 * 一個品項對關鍵字的相符程度。0 是完全沒關係，[EXACT_MATCH_SCORE] 是整串命中，
 * 中間是 n-gram 的重疊比例。
 *
 * **不能只用 `contains`。** 這個框同時服務兩件事：篩自己的清單，以及把描述交給 AI。
 * 而使用者為了讓 AI 估得準，打的往往是「手沖藝妓黑咖啡」這種很細的描述 ——
 * 整串比對的話，庫裡明明有「手沖黑咖啡」也會被判成找不到，畫面就理直氣壯地叫他
 * 去問 AI。那正是這個畫面最該避免的事。
 *
 * 近似的部分**同時看相鄰兩字與單字**，相鄰兩字加權 [BIGRAM_WEIGHT] 倍。中文沒有
 * 空白可以拆詞，而這兩種 n-gram 各自補對方的洞：
 *
 * - **只看相鄰兩字會漏掉拆開的詞。** 「烤肉」在「煎烤豬肉排」裡是拆開的（烤…肉），
 *   相鄰兩字一個都對不上，但兩個字其實都在。
 * - **只看單字會把不相干的拉進來。** 「咖」對上咖哩，「肉」對上任何有肉的東西。
 *
 * 加權相加之後兩件事同時成立：「烤肉」對「煎烤豬肉排」是 0.5（單字全中），
 * 「咖啡」對「咖哩飯」是 0.25（只中一個單字，而且沒有相鄰兩字撐）。這也是
 * CJK 搜尋的標準做法 —— 單字與相鄰兩字各建一份索引再加權合分。
 *
 * 整串命中給的是**比 1 大**的分數，不是 1.0：近似的比例上限就是 1.0（所有 n-gram
 * 都湊得到，但整串不在裡面），兩者撞在一起的話「真的有這個」就不保證排在
 * 「長得有點像」前面了。
 *
 * **它仍然沒有語意。** 「拿鐵」和「牛奶咖啡」一個字都不共用，這裡就是配不起來；
 * 那種事只有 AI 做得到，而那正是底下那顆章存在的理由。
 */
fun FoodSuggestion.matchScore(query: String): Double {
    val hay = (name + " " + servingText).lowercase()
    val compact = query.filterNot(Char::isWhitespace).lowercase()
    if (compact.isEmpty()) return 0.0

    // 整串就在裡面，或使用者自己用空白拆好的關鍵字全部命中（「珍奶 大杯」——
    // 那兩個字分別落在名稱與份量欄位，合起來反而找不到）
    val tokens = query.trim().split(WHITESPACE).filter { it.isNotBlank() }
    if (hay.contains(compact)) return EXACT_MATCH_SCORE
    if (tokens.size > 1 && tokens.all { hay.contains(it.lowercase()) }) return EXACT_MATCH_SCORE

    val unigrams = compact.toSet().map(Char::toString)
    val bigrams = compact.windowed(2).toSet()
    val hits = BIGRAM_WEIGHT * bigrams.count(hay::contains) + unigrams.count(hay::contains)
    val total = BIGRAM_WEIGHT * bigrams.size + unigrams.size
    return hits.toDouble() / total
}

/**
 * 依關鍵字篩食物庫（常吃／最近），並把最像的排前面。空字串就原封不動回傳。
 *
 * 排序用 [sortedByDescending]，它是穩定的 —— 同分的維持原本的順序，
 * 所以整串命中的那幾筆仍然照「常吃」的次數／「最近」的日期排。
 *
 * **這是純記憶體篩選，不查資料庫**，所以不需要 debounce：常吃與最近整份都已經在
 * [NutriViewModel] 裡（各最多 LIBRARY_LIMIT 筆），打一個字就能立刻收斂。
 */
fun List<FoodSuggestion>.filterByQuery(query: String): List<FoodSuggestion> {
    if (query.isBlank()) return this
    return map { it to it.matchScore(query) }
        .filter { it.second >= NEAR_MATCH_THRESHOLD }
        .sortedByDescending { it.second }
        .map { it.first }
}

/**
 * 食物庫的一項 -> 可以直接存的草稿。
 *
 * 刻意**不帶** id 與 barcode：這是「照這個再記一筆」，不是編輯舊的那一筆。
 * 來源標成 MANUAL，因為使用者是自己從庫裡挑的，不是這次新跑了一次 AI 或條碼。
 */
fun FoodSuggestion.toDraft() = EntryDraft(
    name = name,
    servingText = servingText,
    calories = calories.asInput(),
    protein = proteinG.asInput(),
    fat = fatG.asInput(),
    carbs = carbsG.asInput(),
    sugar = sugarG.asInput(),
    sodium = sodiumMg.asInput(),
    fiber = fiberG.asInput(),
    satFat = satFatG.asInput(),
    source = EntrySource.MANUAL,
)

/** 空字串當 0。使用者留白通常是「不知道」而不是想輸入別的東西。 */
private fun String.toNumberOrZero(): Double = toNumberOrNull() ?: 0.0

/** 留白就是沒資料，維持 null —— 和「真的是 0」要分得開。 */
private fun String.toNumberOrNull(): Double? = trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

/** 回填表單時 12.0 要顯示成 "12" 而不是 "12.0"，不然每次編輯都會多一截小數點。 */
private fun Double?.asInput(): String = when {
    this == null -> ""
    this == 0.0 -> ""
    this % 1.0 == 0.0 -> toLong().toString()
    else -> toString()
}

/** 健康連線整批寫入或權限要求的結果。文字由畫面層決定，這裡只說發生了什麼。 */
sealed interface HealthSyncResult {
    data class Written(val count: Int) : HealthSyncResult
    data class Failed(val reason: String) : HealthSyncResult
    data object PermissionDenied : HealthSyncResult
    data object NotSupported : HealthSyncResult
}

enum class ReportTab { WEEKLY, MONTHLY }

/** 週報／月報頁的狀態。統計數字另外放（見 NutriViewModel.weeklyStats），不綁在報告上。 */
sealed interface ReportUiState<out R> {
    data object Loading : ReportUiState<Nothing>

    /** 這段期間還沒產生過報告（或根本沒有資料可寫）。 */
    data object Empty : ReportUiState<Nothing>

    data object Generating : ReportUiState<Nothing>

    data class Ready<out R>(val report: R) : ReportUiState<R>

    data class Failed(val reason: String) : ReportUiState<Nothing>

    /** 報告選的那一家還沒填金鑰；帶著 [provider] 讓畫面講清楚要去填哪一把。 */
    data class MissingKey(val provider: AiProvider) : ReportUiState<Nothing>
}
