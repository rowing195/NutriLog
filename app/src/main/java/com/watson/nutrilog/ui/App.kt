package com.watson.nutrilog.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import kotlin.math.pow
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.watson.nutrilog.R
import com.watson.nutrilog.data.CsvExport
import com.watson.nutrilog.data.SearchMode
import java.io.File
import java.time.LocalDate

/**
 * 根 composable：把 ViewModel 的狀態分派到各畫面，並持有所有跨 App 的啟動器。
 *
 * 沿用 LocalReader 的做法，不引入導航函式庫 —— 畫面只有幾個，
 * 而且除了 Today 以外都是「開一個、按返回就關掉」，不需要真正的返回堆疊。
 * 各畫面本身都是無狀態的，只吃資料與 lambda，ViewModel 不往下傳。
 */
@Composable
fun NutriLogApp(viewModel: NutriViewModel) {
    val context = LocalContext.current

    // TakePicture 只回傳成功與否，圖存到我們事先指定的 URI，
    // 所以要把它記住才知道等一下要分析哪一張。
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        pendingPhotoUri?.let { if (success) viewModel.analyzePhoto(it) }
    }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(viewModel::analyzePhoto) }

    // 沒設 key 就先攔下來。等使用者拍完照才說「你沒設 key」，
    // 等於白拍一張，而且他還得自己想到問題出在設定頁。
    val startCamera = {
        if (!viewModel.hasApiKey()) {
            viewModel.reportMissingApiKey()
        } else {
            // 每張都用新檔名。沿用同一個檔名時，相機 App 有時會因為
            // 檔案已存在而直接失敗，而且舊圖也可能被誤讀成新拍的。
            val uri = newPhotoUri(context)
            pendingPhotoUri = uri
            takePicture.launch(uri)
        }
    }

    val startGallery = {
        if (!viewModel.hasApiKey()) {
            viewModel.reportMissingApiKey()
        } else {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    // 走 SAF 讓使用者自己挑存檔位置：不需要儲存權限，
    // 而且檔案落在使用者看得到的地方，不會跟著 app 一起被解除安裝。
    val createCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(CsvExport.MIME_TYPE)
    ) { uri -> uri?.let(viewModel::exportCsv) }

    val openCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importCsv) }

    // Drive 授權的同意畫面。它是 PendingIntent 不是普通 Intent，所以走
    // StartIntentSenderForResult —— ViewModel 拿不到 Activity，發射一定要在這裡。
    val driveConsent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> viewModel.onConsentResult(result.data) }

    viewModel.pendingConsent?.let { pending ->
        LaunchedEffect(pending) {
            driveConsent.launch(IntentSenderRequest.Builder(pending.intentSender).build())
            viewModel.consentLaunched()
        }
    }

    val startScanner = {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode -> barcode.rawValue?.let(viewModel::lookupBarcode) }
            // 掃描模組要從 Play 服務下載，不是每台裝置都成功（模擬器尤其容易失敗）。
            // 失敗不能靜悄悄，要明講並讓使用者走手動輸入。
            .addOnFailureListener { viewModel.failBarcodeScanner(context.getString(R.string.barcode_scanner_unavailable)) }
        Unit
    }

    // **所有畫面共用同一套轉場：新的那張紙由下往上蓋上來，蓋滿了才跑內容。**
    // 今日頁留在底下不動被蓋住（`ExitTransition.None`），返回就整段倒轉。
    //
    // 這一套取代了原本的 `Crossfade(500ms)`。除了節奏之外，還順手解掉一個坑：
    // **交叉淡入淡出那段期間兩個畫面都是半透明的**，穿過去看到的是 Activity 的
    // `windowBackground` —— 而那是 XML 主題給的淺色（深淺是 app 自己的偏好設定，
    // XML 那一側不知道使用者選了什麼），深色模式下白底會從縫裡透出來。
    // 不透明的紙沒有半透明那一段，問題自然消失 —— 但底下那層不透明底色留著，
    // 它還担著冷啟動那幾幀。
    //
    // **這次是往裡面走還是往回走，要在進 `AnimatedContent` 之前就知道。**
    // 往回走時動的是上面那張紙，底下露出來的那張只是被讓出來的，它必須從第一幀
    // 就是完整的 —— 讓它跟著「蓋滿了才進來」那套走的話，返回會變成「設定滑走之後
    // 露出一張白紙，今日頁最後才跳出來」（實測連拍，紙都滑到七成了底下還是空的）。
    // `transitionSpec` 裡算得出方向，但那裡的值傳不到內容 lambda，所以在外面自己記。
    val target = viewModel.screen
    var lastDepth by remember { mutableIntStateOf(target.depth) }
    val forward = remember(target) { (target.depth > lastDepth).also { lastDepth = target.depth } }

    // **疊放順序也要自己記，不能寫死 1／0。** `AnimatedContent` 的 zIndex 是每張在
    // **進場那一刻**決定、之後退場沿用的。寫死「往裡走 1、往回走 0」的話：
    // 設定 → API 管理 → 金鑰 → 返回（API 管理這次是以 0 進場的）→ 再返回（設定也是 0），
    // 兩張同分時後加入的畫在上面，API 管理往下滑走的動畫整段被實心的設定頁蓋住 ——
    // 看起來就是「沒有動畫」。只要是「退回中間層之後再退一次」都會中，不限設定。
    //
    // 改成跟著導航累加：往裡走疊在目前最上面那張之上（+1），往回走壓在正在走的
    // 那張之下（−1）。動的那張永遠在上面，不管中間進出過幾層。
    var topZ by remember { mutableFloatStateOf(0f) }
    val targetZ = remember(target) { (if (forward) topZ + 1f else topZ - 1f).also { topZ = it } }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    AnimatedContent(
        targetState = target,
        transitionSpec = {
            // 往裡面走＝新畫面整張紙由下往上蓋上來，今日頁留在底下不動被蓋住
            // （`ExitTransition.None`）；往回走就直接倒轉，舊畫面往下退出去、
            // 底下那張原地不動地露出來。
            //
            // z 序要跟著方向翻：往裡面走時新的那張要在上面才蓋得住，往回走時
            // 正在退場的那張要在上面才看得到它退。
            // 前進／後退用外面算好的那一份 [forward]：疊放順序也是照它算的，兩邊各算各的
            // 話，萬一轉場被打斷時滑動方向與上下順序會對不起來。
            // **蓋的方向由「動的是哪個畫面」決定，不是由前進／後退決定。**
            // 前進時動的是新來的那張，後退時動的是正在走的那張 —— 同一個畫面
            // 從哪邊來就從哪邊回去，這樣返回才是真正的倒轉。
            val moving = if (forward) targetState else initialState
            val edge: (Int) -> Int = if (moving.coversDownward) { h -> -h } else { h -> h }
            if (forward) {
                slideInVertically(tween(COVER_MS, easing = CoverEasing), edge) togetherWith
                    // **不能用 `ExitTransition.None`**：那等於沒有退場動畫，舊畫面
                    // 會在轉場的第一幀就被丟掉，底下露出來的是根部那層底色，
                    // 「蓋住今日頁」就變成「蓋住一張空白的紙」。KeepUntil… 是留著
                    // 不動、等整段轉場結束才收，那才是「被蓋住」。
                    ExitTransition.KeepUntilTransitionsFinished
            } else {
                EnterTransition.None togetherWith
                    slideOutVertically(tween(COVER_MS, easing = CoverEasing), edge)
            }.apply { targetContentZIndex = targetZ }
        },
        label = "screen",
    ) { screen ->
    // 「蓋滿了沒」。每個畫面自己的進場動畫（月曆逐週落下、設定逐列右進…）都排在
    // 這之後才開始 —— 兩件事同時做的話，紙還在升、內容已經在動，讀起來是一團亂。
    // 沒有自己那套的畫面就吃底下這個預設淡入，不會在蓋滿的瞬間硬跳出來。
    //
    // **交棒的點是「蓋到 [COVER_HANDOFF]」而不是「轉場結束」。** 光等
    // `currentState` 翻面的話，[CoverEasing] 那條尾巴很長的曲線會留下一段
    // 「紙看起來早就到了、畫面卻還是空白」的空檔。實測（倍率 8、連拍上時間戳，
    // 各跑兩次）：等轉場結束是空白 3～4 幀、舊畫面消失到新內容出現隔 1635／1917ms；
    // 交棒點拉到 0.97 之後是 1～2 幀、隔 1077／1295ms —— 換算回正常速度省掉約 70ms。
    //
    // 剩下那 3% 是紙最後才走到的那一條邊（由下往上的是最上面、由上往下的是
    // 最下面），而那時候紙早就實心了（[SHEET_SOLID_AT] 在 0.8 就到頂），
    // 底下不會透出來。
    //
    // `!forward` 那半邊是給**返回**用的：那個方向兩張都要立刻是完整的
    // （上面那張要看得到它滑走，底下那張要看得到它被讓出來），一個都不能等。
    //
    // 用 `derivedStateOf` 包起來：`coverProgress` 每一幀都在變，直接拿去比大小
    // 等於整個畫面每幀重組一次，包成布林值之後只有真的翻面才通知讀它的人。
    val coverProgress = transition.animateFloat(
        transitionSpec = { tween(COVER_MS, easing = CoverEasing) },
        label = "coverProgress",
    ) { if (it == EnterExitState.PreEnter) 0f else 1f }
    val covered by remember(coverProgress, forward) {
        derivedStateOf { !forward || coverProgress.value >= COVER_HANDOFF }
    }
    val bodyAlpha by animateFloatAsState(
        targetValue = if (covered) 1f else 0f,
        animationSpec = tween(BODY_FADE_MS, easing = CoverEasing),
        label = "body",
    )
    CompositionLocalProvider(LocalScreenEntered provides covered) {
    // **整張紙的濃度跟著它走了多遠**（[sheetAlphaAt]）：剛起步時只有
    // [SHEET_ALPHA_FROM]，底下那頁還看得到；一路往上走一路變實，走到
    // [SHEET_SOLID_AT] 就完全不透明。**重點是它在紙走完之前就已經實心了**
    // —— 紙最後才走到的那一條邊（由下往上時是報頭「肥胖日記」那塊）如果到那裡還
    // 半透明，舊畫面就會和新畫面自己的進場動畫疊在一起，兩層一起動。
    //
    // 這取代了原本掛在紙前緣的那段空間漸層。那個作法的漸層是跟著邊緣跑的，
    // 所以最後正好停在報頭上，剛好造成上面說的那個重疊 —— 方向對了、位置錯了。
    // 濃度改成跟時間走就沒有這個問題：不管邊緣走到哪，該實心的時候就是實心。
    //
    // 讀 `coverProgress` 要在 `graphicsLayer` 的 lambda 裡，不是在組合階段 ——
    // 它每一幀都在變，寫在外面等於整個畫面每幀重組一次。
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (forward) sheetAlphaAt(coverProgress.value) else 1f }
            .background(MaterialTheme.colorScheme.background)
    ) {
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = bodyAlpha }) {
    when (screen) {
        Screen.Today -> TodayScreen(
            date = viewModel.selectedDate,
            settings = viewModel.settings,
            weekStart = viewModel.weekStart,
            entriesFlow = viewModel::entriesFlow,
            weekTotalsFlow = viewModel::weekTotalsFlow,
            onPickDay = viewModel::showDate,
            // 一週長條只走得到同一週，跨週靠這兩顆；再遠就開月曆
            onShiftWeek = { weeks -> viewModel.shiftDay(weeks * 7) },
            onBackToToday = { viewModel.showDate(LocalDate.now()) },
            onOpenEntry = viewModel::startEdit,
            onDeleteEntry = viewModel::deleteEntry,
            pendingUndo = viewModel.pendingUndo,
            onUndoDelete = viewModel::undoDelete,
            onAddManual = { viewModel.startNewEntry() },
            // 從某一餐的「還沒記」點進來只是「記住這一餐」，開哪一條路由選單決定
            onTargetMeal = viewModel::setPendingMeal,
            onAddPhoto = startCamera,
            onAddFromGallery = startGallery,
            // 這個畫面同時是常吃清單，不需要 key 也能用，
            // 真的要送文字去 AI 時 analyzeText 自己會再擋一次沒 key 的情況
            onAddText = viewModel::openTextLookup,
            onAddBarcode = viewModel::openBarcode,
            onOpenHistory = viewModel::openHistory,
            onOpenSearch = viewModel::openSearch,
            onOpenSettings = { viewModel.goTo(Screen.Settings) },
        )

        Screen.EditEntry -> {
            BackHandler { viewModel.backToToday() }
            EditEntryScreen(
                draft = viewModel.draft,
                showExtendedByDefault = viewModel.settings.showExtendedNutrients,
                onDraftChange = viewModel::updateDraft,
                onSave = viewModel::saveDraft,
                // 新增中的草稿還沒進資料庫，沒有東西可刪，所以不給刪除鈕
                onDelete = if (viewModel.draft.id != null) viewModel::deleteCurrentDraft else null,
                onClose = viewModel::backToToday,
            )
        }

        Screen.Barcode -> {
            BackHandler { viewModel.backToToday() }
            BarcodeScreen(
                state = viewModel.barcodeState,
                onScan = startScanner,
                onLookup = viewModel::lookupBarcode,
                onUseProduct = viewModel::useProduct,
                onManualInstead = viewModel::startNewEntry,
                onClose = viewModel::backToToday,
            )
        }

        Screen.TextLookup -> {
            BackHandler { viewModel.backToToday() }
            TextLookupScreen(
                targetDate = viewModel.selectedDate,
                frequent = viewModel.frequentFoods,
                recent = viewModel.recentFoods,
                onReuseSuggestion = viewModel::reuse,
                onLookup = viewModel::analyzeText,
                // 沒選搜尋來源的話「AI 查」那顆按不下去，helper 會講去哪裡選
                searchAvailable = viewModel.settings.searchMode != SearchMode.OFF,
                onClose = viewModel::backToToday,
            )
        }

        Screen.Review -> {
            BackHandler { viewModel.backToToday() }
            ReviewScreen(
                state = viewModel.analysisState ?: AnalysisState.Analyzing,
                meal = viewModel.analysisMeal,
                onMealChange = viewModel::updateAnalysisMeal,
                onToggle = viewModel::toggleAnalysisItem,
                onMultiplierChange = viewModel::updateAnalysisMultiplier,
                onSave = viewModel::saveAnalysisSelection,
                onRetry = viewModel::retryAnalysis,
                onOpenSettings = { viewModel.goTo(Screen.Settings) },
                onManualInstead = viewModel::startNewEntry,
                onClose = viewModel::backToToday,
            )
        }

        Screen.Search -> {
            BackHandler { viewModel.backToToday() }
            SearchScreen(
                query = viewModel.searchQuery,
                targetDate = viewModel.selectedDate,
                results = viewModel.searchResults,
                frequent = viewModel.frequentFoods,
                recent = viewModel.recentFoods,
                onQueryChange = viewModel::updateSearchQuery,
                onOpenDay = viewModel::showDate,
                onReuseEntry = viewModel::reuse,
                onReuseSuggestion = viewModel::reuse,
                onClose = viewModel::backToToday,
            )
        }

        Screen.History -> {
            BackHandler { viewModel.backToToday() }
            HistoryScreen(
                month = viewModel.visibleMonth,
                totals = viewModel.monthTotals,
                settings = viewModel.settings,
                selectedDate = viewModel.selectedDate,
                onShiftMonth = viewModel::shiftMonth,
                onOpenDay = viewModel::showDate,
                onClose = viewModel::backToToday,
            )
        }

        // 這一頁的返回鍵由 SettingsMenuScreen 自己接 —— 關閉時那幾列要先退場，
        // 觸發的入口和動畫必須在同一個地方，留在這裡的話返回鍵會直接跳掉。
        Screen.Settings -> {
            SettingsMenuScreen(
                settings = viewModel.settings,
                onOpen = viewModel::openSettingsPage,
                onClose = viewModel::backToToday,
            )
        }

        // 設定是整個 app 唯一有兩層的地方，所以這裡的返回鍵回的是設定選單而不是
        // 今日頁。子頁進得去卻只能一路退回今日，等於每改一項設定都要重新點兩次。
        is Screen.SettingsDetail -> {
            BackHandler { viewModel.goTo(Screen.Settings) }
            SettingsDetailScreen(
                page = screen.page,
                settings = viewModel.settings,
                dataMessage = viewModel.dataMessage,
                importPreview = viewModel.importPreview,
                onChange = viewModel::updateSettings,
                onExportCsv = { createCsv.launch(viewModel.suggestedCsvName()) },
                // 篩 text/csv 會讓一半的檔案在選擇器裡是灰的 —— 各家檔案管理員
                // 回報的 MIME 從 text/plain 到 application/octet-stream 都有。
                // 寧可全部讓選，解析不出來時有明確的訊息接住。
                onImportCsv = { openCsv.launch(arrayOf("*/*")) },
                onConfirmImport = viewModel::confirmImport,
                onCancelImport = viewModel::cancelImport,
                driveMessage = viewModel.driveMessage,
                driveBusy = viewModel.driveBusy,
                onConnectDrive = viewModel::connectDrive,
                onBackupNow = viewModel::backupNow,
                onDisconnectDrive = viewModel::disconnectDrive,
                onOpenService = viewModel::openApiKey,
                onBack = { viewModel.goTo(Screen.Settings) },
            )
        }

        // 第三層：某一個服務的 key。返回回到「AI 影像辨識」那一頁，
        // 一路退回今日頁的話，改完 key 想接著換模型就要重點三次。
        is Screen.ApiKeyDetail -> {
            BackHandler { viewModel.openSettingsPage(SettingsPage.AI) }
            ApiKeyScreen(
                service = screen.service,
                settings = viewModel.settings,
                onChange = viewModel::updateSettings,
                onBack = { viewModel.openSettingsPage(SettingsPage.AI) },
            )
        }
    }
    }
    }
    }
    }
    }
}

/**
 * 蓋版的時長與曲線，全 app 的畫面切換共用這一條。
 *
 * 換掉的是原本的 `Crossfade(500ms)`。**交叉淡入淡出那段期間兩個畫面都是半透明的**，
 * 深色模式下 Activity 的白色 `windowBackground` 會從縫裡透出來（見 CLAUDE.md
 * 那一節）—— 不透明的紙由下往上蓋沒有半透明那一段，那個坑順手就沒了。
 */
private const val COVER_MS = 320
private const val BODY_FADE_MS = 180
private val CoverEasing = CubicBezierEasing(0.32f, 0f, 0.18f, 1f)

/** 紙剛起步時的不透明度，以及走到幾成就完全實心。 */
private const val SHEET_ALPHA_FROM = 0.5f
private const val SHEET_SOLID_AT = 0.8f

/**
 * 紙走到 [progress] 時的不透明度。指數 1.5 是為了讓中途比線性更透一點
 * （走到一半時約 75%，線性會是 81%）—— 線性讀起來像紙一離地就實心了，
 * 而「越往上越難看到背後」正是這段漸層要講的事。
 */
private fun sheetAlphaAt(progress: Float): Float =
    lerp(SHEET_ALPHA_FROM, 1f, (progress / SHEET_SOLID_AT).coerceAtMost(1f).pow(1.5f))

/** 蓋到幾成就把棒子交給畫面自己的進場動畫。剩下的是最底下那幾十個 pixel。 */
private const val COVER_HANDOFF = 0.97f

/**
 * 「這一層的紙蓋滿了沒」。每個畫面自己的進場動畫都等這個變 true 才開始。
 *
 * 預設 true 是給預覽與測試用的：沒有被 [App] 那層包住時，畫面應該直接是最終狀態，
 * 而不是永遠停在還沒進場的樣子。
 */
val LocalScreenEntered = compositionLocalOf { true }

/**
 * 畫面的深度，只拿來判斷這次是「往裡面走」還是「往回走」。
 *
 * 設定是唯一有多層的地方（選單 → 子頁 → key 頁），所以它們的深度要遞增 ——
 * 從子頁返回選單也算往回走，紙要往下退而不是又蓋一次。
 */
private val Screen.depth: Int
    get() = when (this) {
        Screen.Today -> 0
        is Screen.SettingsDetail -> 2
        is Screen.ApiKeyDetail -> 3
        else -> 1
    }

/**
 * 這張紙是**由上往下蓋**的嗎。
 *
 * 分界是「從哪裡開的」：報頭那排圖示（搜尋、月曆、設定）站在畫面**頂端**，
 * 它們開出來的東西從底下升上來；右下角那顆「記一筆」站在**右下角**，它那五個
 * 入口（常吃／文字輸入、輸入營養素、拍照、相簿、條碼）開出來的東西就從頂端蓋下來。
 * 兩邊各自朝著自己入口的反方向走。
 *
 * **方向綁在畫面身上而不是綁在「這次是誰開的」。** 編輯表單也可以從紀錄列點進去、
 * 條碼與 AI 確認也會互相接力，那些路徑沒有「入口在畫面哪個角落」可講；讓同一個
 * 畫面永遠從同一邊來，比每條路各記一個方向好記，返回也才保證是原路。
 */
private val Screen.coversDownward: Boolean
    get() = when (this) {
        Screen.EditEntry, Screen.Barcode, Screen.TextLookup, Screen.Review -> true
        else -> false
    }

/**
 * 拍照的暫存檔。走 FileProvider 換成 content://：從 Android 7 起，
 * 把 file:// 丟給別的 App 會直接 FileUriExposedException。
 */
private fun newPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
    val file = File(dir, "meal_" + System.currentTimeMillis() + ".jpg")
    return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
}
