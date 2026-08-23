package com.watson.nutrilog.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.watson.nutrilog.R
import com.watson.nutrilog.data.CsvExport
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

    // Crossfade 而不是直接 when：原本畫面切換是硬切，Today 開設定/歷史等
    // 附屬畫面時整個畫面瞬間跳掉，跟其他地方陸續做掉的動畫比起來特別突兀。
    // 預設 300ms 太快、人眼幾乎看不出有淡入淡出，拉到 500ms 才看得明顯。
    Crossfade(
        targetState = viewModel.screen,
        animationSpec = tween(durationMillis = 500),
        label = "screen",
    ) { screen ->
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
            onAddManual = { viewModel.startNewEntry() },
            onAddForMeal = { meal -> viewModel.startNewEntry(meal) },
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

        Screen.Settings -> {
            BackHandler { viewModel.backToToday() }
            SettingsScreen(
                settings = viewModel.settings,
                exportMessage = viewModel.exportMessage,
                onChange = viewModel::updateSettings,
                onExportCsv = { createCsv.launch(viewModel.suggestedCsvName()) },
                onClose = viewModel::backToToday,
            )
        }
    }
    }
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
