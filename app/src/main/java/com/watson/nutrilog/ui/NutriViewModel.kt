package com.watson.nutrilog.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.watson.nutrilog.R
import com.watson.nutrilog.data.CsvExport
import com.watson.nutrilog.data.NutriSettings
import com.watson.nutrilog.data.SettingsStore
import com.watson.nutrilog.data.db.CachedProduct
import com.watson.nutrilog.data.db.DayTotal
import com.watson.nutrilog.data.db.EntrySource
import com.watson.nutrilog.data.db.FoodEntry
import com.watson.nutrilog.data.db.Meal
import com.watson.nutrilog.data.db.NutriDatabase
import com.watson.nutrilog.data.db.Totals
import com.watson.nutrilog.data.db.totals
import com.watson.nutrilog.data.net.DetectedFood
import com.watson.nutrilog.data.net.GeminiClient
import com.watson.nutrilog.data.net.ImageCompressor
import com.watson.nutrilog.data.net.OpenFoodFactsClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * 畫面。沿用 LocalReader 的做法：sealed interface + when 分派，不引入導航函式庫。
 *
 * 只有 [Today] 是根，其他一律按返回鍵就回 Today，所以不需要真正的返回堆疊。
 * [EditEntry] 沒有參數 —— 正在編輯的內容放在 ViewModel 的 draft 上，
 * 這樣三種輸入來源（手動／條碼／拍照）都能先塞好草稿再切過去。
 */
sealed interface Screen {
    data object Today : Screen
    data object History : Screen
    data object Settings : Screen
    data object EditEntry : Screen
    data object Barcode : Screen
    data object TextLookup : Screen
    data object Review : Screen
}

/** AI 辨識的三個階段。照片與文字描述共用。 */
sealed interface AnalysisState {
    data object Analyzing : AnalysisState
    data class Ready(val items: List<AnalysisItem>) : AnalysisState
    data class Failed(val reason: String) : AnalysisState
}

/** 確認畫面裡的一列：模型認出來的東西，加上「要不要記錄」。 */
data class AnalysisItem(val food: DetectedFood, val selected: Boolean = true)

/**
 * 這次辨識是從哪裡來的。
 *
 * 「重試」必須知道要重跑哪一件事 —— 之前是讓畫面自己記著照片 URI，
 * 但文字辨識加進來之後那個做法就不成立了。
 */
sealed interface AnalysisSource {
    data class Photo(val uri: Uri) : AnalysisSource
    data class Text(val query: String) : AnalysisSource
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
    )

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
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NutriViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = NutriDatabase.get(application).dao()
    private val settingsStore = SettingsStore(application)
    private val openFoodFacts = OpenFoodFactsClient()
    private val gemini = GeminiClient()

    var screen by mutableStateOf<Screen>(Screen.Today)
        private set
    var settings by mutableStateOf(NutriSettings())
        private set
    var selectedDate by mutableStateOf(LocalDate.now())
        private set
    var entries by mutableStateOf<List<FoodEntry>>(emptyList())
        private set
    /** 月曆目前顯示的月份 */
    var visibleMonth by mutableStateOf(YearMonth.now())
        private set
    /** 該月每一天的合計，key 是 "yyyy-MM-dd"。沒紀錄的日子不會出現在 map 裡。 */
    var monthTotals by mutableStateOf<Map<String, DayTotal>>(emptyMap())
        private set
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

    /** 匯出結果訊息。顯示完就該清掉，離開設定頁時一併清。 */
    var exportMessage by mutableStateOf<String?>(null)
        private set

    val totals: Totals get() = entries.totals()

    init {
        viewModelScope.launch { settingsStore.settingsFlow.collect { settings = it } }
        // 換月份就換一條 Flow，和換日期同樣的道理
        viewModelScope.launch {
            snapshotFlow { visibleMonth }
                .flatMapLatest { month ->
                    dao.observeRange(month.atDay(1).toString(), month.atEndOfMonth().toString())
                }
                .collect { totals -> monthTotals = totals.associateBy { it.date } }
        }
        // 換日期就要換一條 Flow。用 snapshotFlow 把 Compose state 轉成 Flow，
        // flatMapLatest 會自動取消上一天的訂閱，不會累積。
        viewModelScope.launch {
            snapshotFlow { selectedDate }
                .flatMapLatest { dao.observeDay(it.toString()) }
                .collect { entries = it }
        }
    }

    // --- 導航 ---

    fun goTo(target: Screen) { screen = target }

    fun backToToday() {
        exportMessage = null
        screen = Screen.Today
    }

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

    // --- 編輯 ---

    /**
     * 開一張空白表單。
     *
     * [meal] 給「從某一餐的區塊點進來」用；沒指定就依現在時間猜，
     * 猜錯使用者改一下就好，總比每次都要選。
     */
    fun startNewEntry(meal: Meal? = null) {
        draft = EntryDraft(meal = meal ?: guessMeal())
        screen = Screen.EditEntry
    }

    fun startEdit(entry: FoodEntry) {
        draft = EntryDraft.of(entry)
        screen = Screen.EditEntry
    }

    /** 條碼／拍照流程用：先把草稿填好再切到表單，讓使用者確認後才入庫。 */
    fun startPrefilled(prefilled: EntryDraft) {
        draft = prefilled.copy(meal = guessMeal())
        screen = Screen.EditEntry
    }

    fun updateDraft(newDraft: EntryDraft) { draft = newDraft }

    fun saveDraft() {
        if (!draft.isValid) return
        val entry = draft.toEntry(selectedDate)
        viewModelScope.launch {
            dao.upsert(entry)
            screen = Screen.Today
        }
    }

    /** 刪掉正在編輯的那一筆。新增中的草稿還沒進資料庫，沒得刪。 */
    fun deleteCurrentDraft() {
        val id = draft.id ?: return
        viewModelScope.launch {
            dao.findEntry(id)?.let { dao.delete(it) }
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

    fun reportMissingApiKey() {
        analysisState = AnalysisState.Failed(NO_API_KEY)
        screen = Screen.Review
    }

    fun openTextLookup() { screen = Screen.TextLookup }

    fun analyzePhoto(uri: Uri) = startAnalysis(AnalysisSource.Photo(uri))

    fun analyzeText(query: String) {
        if (query.isBlank()) return
        startAnalysis(AnalysisSource.Text(query.trim()))
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
        val key = settings.geminiApiKey
        // 這裡仍然要擋一次：從相機回來的期間設定可能被改掉，
        // 而這裡才是真正會把 key 送出去的地方。
        if (key.isBlank()) {
            reportMissingApiKey()
            return
        }
        lastSource = source
        analysisMeal = guessMeal()
        analysisState = AnalysisState.Analyzing
        screen = Screen.Review
        viewModelScope.launch {
            val model = settings.geminiModel
            val result = when (source) {
                is AnalysisSource.Text -> gemini.analyzeDescription(source.query, key, model)
                is AnalysisSource.Photo ->
                    // 壓縮失敗（檔案壞了、格式不支援）也要走同一條錯誤路徑，
                    // 不然使用者只會看到轉圈停住
                    ImageCompressor.toBase64Jpeg(getApplication(), source.uri)
                        .mapCatching { base64 ->
                            gemini.analyzeFood(base64, key, model).getOrThrow()
                        }
            }
            analysisState = result.fold(
                onSuccess = { AnalysisState.Ready(it.map(::AnalysisItem)) },
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

    /** 一次寫入所有勾選的項目。存完就回今天，使用者要細調再點進去改。 */
    fun saveAnalysisSelection() {
        val current = analysisState as? AnalysisState.Ready ?: return
        val chosen = current.items.filter { it.selected }
        if (chosen.isEmpty()) return
        val meal = analysisMeal
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            dao.insertAll(chosen.map { it.food.toEntry(selectedDate, meal, now) })
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
            exportMessage = runCatching {
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
    }
}

/** 模型的估算值 -> 可以入庫的一筆紀錄。 */
private fun DetectedFood.toEntry(date: LocalDate, meal: Meal, loggedAt: Long) = FoodEntry(
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

private fun Double.roundTo1(): Double = Math.round(this * 10.0) / 10.0

private fun Double.asInputValue(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

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
