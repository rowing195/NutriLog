package com.watson.nutrilog.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.watson.nutrilog.R
import com.watson.nutrilog.data.CsvExport
import com.watson.nutrilog.data.HealthConnectSync
import androidx.health.connect.client.PermissionController
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.watson.nutrilog.ui.theme.NutrientColors

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

    val cameraPrefs = remember { context.getSharedPreferences("camera_pending", Context.MODE_PRIVATE) }

    // 三重保險防止相機開啟期間 Activity 重建導致 URI 遺失：
    // 1. rememberSaveable 跨 Activity 重建保持
    // 2. viewModel.pendingCameraPhotoUri 保持在 ViewModel
    // 3. SharedPreferences 跨行程重啟保持
    var pendingPhotoUriString by rememberSaveable { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uriString = pendingPhotoUriString
            ?: viewModel.pendingCameraPhotoUri?.toString()
            ?: cameraPrefs.getString("pending_uri", null)
        val uri = uriString?.let { Uri.parse(it) }
        val hasData = uri != null && isPhotoValid(context, uri)

        // 確保相機返回時若 ViewModel 重建，立即恢復快取金鑰
        val cachedKey = cameraPrefs.getString("cached_gemini_key", "") ?: ""
        if (viewModel.settings.geminiApiKey.isBlank() && cachedKey.isNotBlank()) {
            val cachedModel = cameraPrefs.getString("cached_gemini_model", "") ?: ""
            viewModel.restoreApiKey(cachedKey, cachedModel)
        }

        Log.d("NutriLogCamera", "TakePicture result: success=$success, uri=$uri, hasData=$hasData, hasKey=${viewModel.settings.geminiApiKey.isNotBlank()}")

        // 部分三星裝置與相機版本在拍照確認後回傳 RESULT_CANCELED (success=false)，
        // 但檔案已確實寫入。因此只要 success 為 true 或檔案有寫入有效資料，均觸發辨識。
        if ((success || hasData) && uri != null) {
            viewModel.analyzePhoto(uri)
        } else if (uri != null) {
            // 使用者真的取消且無照片資料，嘗試清理 0 位元組空檔案
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (_: Exception) {}
        }

        // 清理暫存狀態
        pendingPhotoUriString = null
        viewModel.pendingCameraPhotoUri = null
        cameraPrefs.edit().remove("pending_uri").apply()
    }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(viewModel::analyzePhoto) }

    val startCamera = {
        // 每張都用新檔名。沿用同一個檔名時，相機 App 有時會因為
        // 檔案已存在而直接失敗，而且舊圖也可能被誤讀成新拍的。
        val uri = newPhotoUri(context)
        pendingPhotoUriString = uri.toString()
        viewModel.pendingCameraPhotoUri = uri
        cameraPrefs.edit()
            .putString("pending_uri", uri.toString())
            .putString("cached_gemini_key", viewModel.settings.geminiApiKey)
            .putString("cached_gemini_model", viewModel.settings.geminiModel)
            .apply()

        // 明確授權相機 App 讀寫權限，避免部分相機 App 或 ROM 權限不足
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val resolved = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            for (info in resolved) {
                context.grantUriPermission(
                    info.activityInfo.packageName,
                    uri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        } catch (e: Exception) {
            Log.e("NutriLogCamera", "grantUriPermission failed: ${e.message}")
        }

        takePicture.launch(uri)
    }

    val startGallery = {
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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

    // Health Connect（健康連線）之權限請求發射器
    val healthPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        viewModel.onHealthPermissionsResult(granted)
    }

    if (viewModel.pendingHealthPermissionRequest) {
        LaunchedEffect(Unit) {
            healthPermissionLauncher.launch(HealthConnectSync.REQUIRED_PERMISSIONS)
            viewModel.onHealthPermissionRequestLaunched()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAppResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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

    // Crossfade 而不是直接 when：原本畫面切換是硬切，Today 開設定/歷史等
    // 附屬畫面時整個畫面瞬間跳掉，跟其他地方陸續做掉的動畫比起來特別突兀。
    // 預設 300ms 太快、人眼幾乎看不出有淡入淡出，拉到 500ms 才看得明顯。
    //
    // Crossfade 的兩個畫面在交叉的那段期間都是半透明的，穿過去看到的是 Activity 的
    // windowBackground —— 而那個是 XML 主題給的淺色（themes.xml 是 Material.Light，
    // 而且深色模式是 app 內部的偏好設定，XML 那一側根本不知道使用者選了什麼）。
    // 深色模式下兩層暗畫面各透一點，白底就從縫裡透出來，看起來像每換一次畫面就閃一下。
    // 墊一層跟著 Compose 主題走的不透明底色，透出來的就會是這個主題自己的背景色。
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
            activeCaloriesMap = viewModel.activeCaloriesMap,
            workoutSessionsMap = viewModel.workoutSessionsMap,
            onFetchActiveCalories = viewModel::fetchActiveCalories,
            onRefreshActiveCalories = viewModel::refreshActiveCalories,
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
                activeCaloriesMap = viewModel.activeCaloriesMap,
                onShiftMonth = viewModel::shiftMonth,
                onOpenDay = viewModel::showDate,
                onOpenWeeklyReport = { viewModel.openReportCenter() },
                onClose = viewModel::backToToday,
            )
        }

        Screen.WeeklyReport -> {
            BackHandler { viewModel.goTo(Screen.History) }
            WeeklyReportScreen(
                selectedTab = viewModel.reportTab,
                onTabSelect = viewModel::selectReportTab,
                weekStart = viewModel.weeklyReportWeekStart,
                currentWeekStart = viewModel.weekStart,
                uiState = viewModel.weeklyReportUiState,
                onShiftWeek = viewModel::shiftWeeklyReportWeek,
                onGenerate = { viewModel.generateWeeklyReport(forceRefresh = true) },
                onApplyRecommendation = viewModel::applyRecommendedTargets,
                activeMonth = viewModel.monthlyReportMonth,
                currentMonth = YearMonth.now(),
                monthlyUiState = viewModel.monthlyReportUiState,
                onShiftMonth = viewModel::shiftMonthlyReportMonth,
                onGenerateMonthly = { viewModel.generateMonthlyReport(forceRefresh = true) },
                settings = viewModel.settings,
                onOpenSettings = { viewModel.goTo(Screen.Settings) },
                onClose = { viewModel.goTo(Screen.History) },
            )
        }

        Screen.Settings -> {
            BackHandler { viewModel.backToToday() }
            SettingsScreen(
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
                onOpenBmrCalculator = viewModel::openBmrDialog,
                isHealthConnectSupported = viewModel.isHealthConnectSupported,
                healthConnectAuthorized = viewModel.healthConnectAuthorized,
                healthConnectReadAuthorized = viewModel.healthConnectReadAuthorized,
                healthSyncBusy = viewModel.healthSyncBusy,
                healthMessage = viewModel.healthMessage,
                onToggleHealthConnect = viewModel::toggleHealthConnect,
                onToggleReadExerciseCalories = viewModel::toggleReadExerciseCalories,
                onSyncAllHealthConnect = viewModel::syncAllToHealthConnect,
                onClose = viewModel::backToToday,
            )
        }
    }
    }

        val bgMsg = viewModel.backgroundReportMessage
        val isGenerating = viewModel.isWeeklyReportGenerating || viewModel.isMonthlyReportGenerating
        if (viewModel.screen != Screen.WeeklyReport && (isGenerating || bgMsg != null)) {
            val text = if (isGenerating) {
                stringResource(R.string.report_bg_generating)
            } else {
                bgMsg ?: stringResource(R.string.report_bg_finished)
            }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp, start = 20.dp, end = 20.dp)
                    .zIndex(10f)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, NutrientColors.FieldBorder, RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable {
                        viewModel.openReportCenter()
                        viewModel.dismissBackgroundReportMessage()
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            color = NutrientColors.Accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp),
                        )
                    } else {
                        Text("✦", color = NutrientColors.Accent, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        if (viewModel.showBmrDialog) {
            BmrCalculatorDialog(
                initialSettings = viewModel.settings,
                onApply = { updated ->
                    viewModel.updateSettings(updated.copy(profileConfigured = true))
                    viewModel.closeBmrDialog()
                },
                onDismiss = viewModel::dismissBmrDialog,
            )
        }
    }
}

/**
 * 檢查照片 URI 是否具有有效寫入資料（避免三星相機回傳 resultCode=0 但檔案已寫入的狀況，或取消未拍留下的空檔）。
 */
private fun isPhotoValid(context: Context, uri: Uri): Boolean {
    return try {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
        val size = pfd?.statSize ?: 0L
        pfd?.close()
        if (size > 0L) return true

        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.read() != -1
        } ?: false
    } catch (e: Exception) {
        Log.e("NutriLogCamera", "isPhotoValid error: ${e.message}")
        false
    }
}

/**
 * 拍照的暫存檔。走 FileProvider 換成 content://：從 Android 7 起，
 * 把 file:// 丟給別的 App 會直接 FileUriExposedException。
 */
private fun newPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "photos").apply { if (!exists()) mkdirs() }
    val file = File(dir, "meal_${System.currentTimeMillis()}.jpg")
    try {
        if (!file.exists()) {
            file.createNewFile()
        }
    } catch (e: Exception) {
        Log.e("NutriLogCamera", "newPhotoUri createNewFile failed: ${e.message}")
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
