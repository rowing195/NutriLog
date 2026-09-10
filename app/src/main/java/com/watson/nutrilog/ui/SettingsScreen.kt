package com.watson.nutrilog.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.AiProvider
import com.watson.nutrilog.data.ApiService
import com.watson.nutrilog.data.SearchMode
import com.watson.nutrilog.data.DarkModePreference
import com.watson.nutrilog.data.NutriSettings
import androidx.compose.foundation.layout.size
import com.watson.nutrilog.ui.theme.NutrientColors
import com.watson.nutrilog.ui.theme.numeric
import kotlinx.coroutines.flow.first

/**
 * 設定的選單那一層。
 *
 * 原本是一整條長捲軸，六段疊在一起要捲很久才找得到東西。拆成兩層之後這一頁只負責
 * 「有哪些東西可以設定」，每一列右邊帶目前的值 —— 沒有摘要的話這頁只是一排名詞，
 * 使用者還是得每一頁點進去才知道自己設過什麼。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMenuScreen(
    settings: NutriSettings,
    onOpen: (SettingsPage) -> Unit,
    onClose: () -> Unit,
) {
    // 一次性的進場：`shown` 從 false 翻成 true，關閉時再翻回去讓每一列原路退出去。
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val transition = updateTransition(shown, label = "settingsEnter")

    // **關閉要等那幾列先退完。** 直接呼叫 onClose 的話畫面立刻換掉，退場動畫根本
    // 來不及播 —— 使用者看到的就是「進來有動畫、離開沒有」，比兩邊都沒有還怪。
    //
    // `closing` 同時擋掉這段期間的重複觸發（連按關閉、退場中還去點某一列）。
    var closing by remember { mutableStateOf(false) }
    val requestClose = {
        closing = true
        shown = false
    }
    // **等的是「動畫真的停了」，不是一段寫死的毫秒數。** 系統的動畫倍率
    //（開發者選項、或測試時調的 animator_duration_scale）只會縮放動畫，不會縮放
    // `delay()` —— 兩者寫死就必定對不上：倍率調快時畫面乾等，調慢時列還在半路
    // 畫面就換掉了。`currentState` 要等整個 transition 落定才會翻成 false。
    LaunchedEffect(closing) {
        if (closing) {
            snapshotFlow { transition.currentState }.first { !it }
            onClose()
        }
    }
    // 返回鍵和報頭那顆「關閉」是同一件事，所以 BackHandler 掛在這裡而不是 App.kt
    // —— 退場動畫由這個畫面自己管，觸發的入口就不能留在外面。
    BackHandler(onBack = requestClose)

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.settings_title),
                closeLabel = stringResource(R.string.close),
                onClose = requestClose,
            )
        },
    ) { inner ->
        // 進場時每一列從右邊依序滑進來，和角落那顆「記一筆」展開選單同一個手法
        // （`TodayScreen.AddMenu`）—— 那裡是把五個入口逐列滑出來，這裡是同一件事，
        // 只是容器從浮層換成整頁。關閉時原路退回去。
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 28.dp),
        ) {
            val pages = SettingsPage.entries
            pages.forEachIndexed { index, page ->
                val slide by transition.animateFloat(
                    transitionSpec = {
                        if (targetState) {
                            tween(
                                durationMillis = ROW_SLIDE_MS,
                                delayMillis = ROW_LEAD_MS + index * stepFor(pages.size),
                                easing = FastOutSlowInEasing,
                            )
                        } else {
                            // 退場比進場快一倍、也不留起跑的空檔：離開的時候沒有人在
                            // 欣賞動畫，慢吞吞地退只會變成「按了關閉還要等」。
                            tween(
                                durationMillis = ROW_EXIT_MS,
                                delayMillis = index * exitStepFor(pages.size),
                                easing = FastOutLinearInEasing,
                            )
                        }
                    },
                    label = "row",
                ) { if (it) 1f else 0f }

                Column(
                    Modifier.graphicsLayer {
                        alpha = slide
                        translationX = (1f - slide) * ROW_SLIDE_DP.toPx()
                    }
                ) {
                    MenuRow(
                        title = stringResource(page.titleRes()),
                        summary = page.summary(settings),
                        // 退場途中不接點擊：那幾百毫秒裡畫面還在，點下去會在
                        // 關閉的路上又開一個子頁。
                        onClick = { if (!closing) onOpen(page) },
                    )
                    Hairline()
                }
            }
        }
    }
}

/** 一列滑進來要多久、從多右邊開始、整批延後多久起跑。 */
private const val ROW_SLIDE_MS = 260
private const val ROW_LEAD_MS = 70
private val ROW_SLIDE_DP = 64.dp

/**
 * 相鄰兩列之間的間隔。
 *
 * **不能寫死。** 現在是五列，往後設定長到七八列是遲早的事，而寫死 45ms 的話
 * 八列就是 315ms 的錯開 —— 加上一列自己要走的 [ROW_SLIDE_MS]，最後一列要等到
 * 快 650ms 才停，使用者已經在等它了。所以改成**整批錯開的總長度有上限**
 * （[STAGGER_BUDGET_MS]）：列數少的時候維持 45ms 的節奏感，列數多了就自動收緊，
 * 不管幾列，最後一列開始動的時間都不會超過那個預算。
 */
private fun stepFor(count: Int): Int =
    (STAGGER_BUDGET_MS / (count - 1).coerceAtLeast(1)).coerceAtMost(ROW_STEP_MAX_MS)

private const val ROW_STEP_MAX_MS = 45
private const val STAGGER_BUDGET_MS = 180

/** 退場的間隔，和 [stepFor] 同一個道理，只是預算更小。 */
private fun exitStepFor(count: Int): Int =
    (EXIT_BUDGET_MS / (count - 1).coerceAtLeast(1)).coerceAtMost(EXIT_STEP_MAX_MS)

private const val ROW_EXIT_MS = 160
private const val EXIT_STEP_MAX_MS = 30
private const val EXIT_BUDGET_MS = 100

/** 選單的一列：標題、目前的值、指向右邊的箭頭。 */
@Composable
private fun MenuRow(title: String, summary: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(
            withNumerals(summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChevronMark(MaterialTheme.colorScheme.outline, pointsLeft = false)
    }
}

@Composable
private fun SettingsPage.titleRes(): Int = when (this) {
    SettingsPage.APPEARANCE -> R.string.settings_appearance
    SettingsPage.TARGETS -> R.string.settings_targets
    SettingsPage.AI -> R.string.settings_ai
    SettingsPage.DRIVE -> R.string.drive_section
    SettingsPage.DATA -> R.string.settings_data
}

@Composable
private fun SettingsPage.summary(settings: NutriSettings): String = when (this) {
    SettingsPage.APPEARANCE -> settings.darkMode.label()
    SettingsPage.TARGETS ->
        settings.calorieTarget.toString() + " " + stringResource(R.string.unit_kcal)
    SettingsPage.AI -> settings.textProvider.label
    SettingsPage.DRIVE -> stringResource(
        if (settings.driveBackupEnabled) R.string.drive_summary_on else R.string.drive_summary_off
    )
    SettingsPage.DATA -> stringResource(R.string.settings_data_summary)
}

/**
 * 設定的子頁。內容照 [page] 分派，外框（報頭、捲動、鍵盤處理）共用一份 ——
 * 每一頁各寫一次 Scaffold 的話，之後改內距或收鍵盤的規則就要改五個地方。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDetailScreen(
    page: SettingsPage,
    settings: NutriSettings,
    dataMessage: String?,
    importPreview: ImportPreview?,
    driveMessage: String?,
    driveBusy: Boolean,
    onChange: (NutriSettings) -> Unit,
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onConnectDrive: () -> Unit,
    onBackupNow: () -> Unit,
    onDisconnectDrive: () -> Unit,
    onOpenService: (ApiService) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.dismissKeyboardOnTap(),
        topBar = {
            ScreenTopBar(
                title = stringResource(page.titleRes()),
                // 這裡是「返回」不是「關閉」：它回的是設定選單，不是離開設定
                closeLabel = stringResource(R.string.settings_back),
                onClose = onBack,
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                // 同 EditEntryScreen：不加的話鍵盤會蓋住下半部的欄位
                .imePadding()
                .verticalScroll(rememberScrollState())
                // 底部留得比上面多：捲到最後一顆章時，8dp 加上手勢列的內距
                // 看起來像貼在畫面邊緣上，整頁會有一種還沒排完就被切掉的感覺。
                .padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (page) {
                SettingsPage.APPEARANCE -> AppearanceSection(settings, onChange)
                SettingsPage.TARGETS -> TargetsSection(settings, onChange)
                SettingsPage.AI -> AiSection(settings, onChange, onOpenService)
                SettingsPage.DRIVE -> DriveSection(
                    settings, driveMessage, driveBusy, onConnectDrive, onBackupNow, onDisconnectDrive,
                )
                SettingsPage.DATA -> DataSection(dataMessage, onExportCsv, onImportCsv)
            }
        }
    }

    importPreview?.let { preview ->
        NutriDialog(
            title = stringResource(R.string.import_confirm_title),
            message = importSummary(preview),
            confirmLabel = stringResource(R.string.import_confirm),
            cancelLabel = stringResource(R.string.cancel),
            onConfirm = onConfirmImport,
            onDismiss = onCancelImport,
        )
    }
}

@Composable
private fun AppearanceSection(settings: NutriSettings, onChange: (NutriSettings) -> Unit) {
    // 圈選，不是 M3 的 SegmentedButton —— 它靠容器色分辨選中與否，
    // 在這套低對比色票上兩個狀態幾乎看不出差別。
    BallotRow(
        labels = DarkModePreference.entries.map { it.label() },
        selectedIndex = DarkModePreference.entries.indexOf(settings.darkMode),
        onSelect = { onChange(settings.copy(darkMode = DarkModePreference.entries[it])) },
    )

}

@Composable
private fun TargetsSection(settings: NutriSettings, onChange: (NutriSettings) -> Unit) {
    TargetField(
        label = stringResource(R.string.nutrient_calories) + "（" + stringResource(R.string.unit_kcal) + "）",
        value = settings.calorieTarget,
        max = NutriSettings.MAX_CALORIE_TARGET,
    ) { onChange(settings.copy(calorieTarget = it)) }
    TargetField(
        label = stringResource(R.string.nutrient_protein) + "（g）",
        value = settings.proteinTargetG,
        max = NutriSettings.MAX_MACRO_TARGET,
    ) { onChange(settings.copy(proteinTargetG = it)) }
    TargetField(
        label = stringResource(R.string.nutrient_fat) + "（g）",
        value = settings.fatTargetG,
        max = NutriSettings.MAX_MACRO_TARGET,
    ) { onChange(settings.copy(fatTargetG = it)) }
    TargetField(
        label = stringResource(R.string.nutrient_carbs) + "（g）",
        value = settings.carbsTargetG,
        max = NutriSettings.MAX_MACRO_TARGET,
    ) { onChange(settings.copy(carbsTargetG = it)) }

    Hairline(Modifier.padding(vertical = 10.dp))

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 開關固定 46dp 寬，說明文字要自己讓出間距，不然會頂到它身上
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(stringResource(R.string.settings_show_extended))
            Text(
                stringResource(R.string.settings_show_extended_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        NutriSwitch(
            checked = settings.showExtendedNutrients,
            onCheckedChange = { onChange(settings.copy(showExtendedNutrients = it)) },
        )
    }

}

@Composable
private fun AiSection(
    settings: NutriSettings,
    onChange: (NutriSettings) -> Unit,
    onOpenService: (ApiService) -> Unit,
) {
    SectionLabel(stringResource(R.string.settings_text_provider))
    BallotRow(
        labels = AiProvider.entries.map { it.label },
        selectedIndex = AiProvider.entries.indexOf(settings.textProvider),
        onSelect = { onChange(settings.copy(textProvider = AiProvider.entries[it])) },
    )
    // 不講的話，選了 OpenRouter 的人會以為拍照也換過去了，然後困惑為什麼
    // 明明選了別家卻還是要填 Gemini 的 key。
    Text(
        stringResource(R.string.settings_photo_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Hairline(Modifier.padding(vertical = 10.dp))

    // 搜尋的選擇擺在這裡而不是藏在某一家的子頁裡：它跟「用哪一家」是同一個決定的
    // 兩半，分開放的話使用者得先猜「這個開關屬於誰」，而它其實兩家都影響。
    SectionLabel(stringResource(R.string.settings_web_search))
    BallotRow(
        labels = SearchMode.entries.map { stringResource(it.labelRes()) },
        selectedIndex = SearchMode.entries.indexOf(settings.searchMode),
        onSelect = { onChange(settings.copy(searchMode = SearchMode.entries[it])) },
    )
    // 這裡不解釋「查網路是什麼」：使用者是在常吃頁看到「AI 查」按不下去、
    // 被 helper 送來這裡的，那顆章已經用做的講完了。小標叫「AI 查詢」就是為了
    // 讓他一眼對得上是哪一列。
    //
    // 兩條路的限制完全不同，只在選到的時候講，不然三段說明擠在一起沒人讀
    if (settings.searchMode != SearchMode.OFF) {
        Text(
            stringResource(
                if (settings.searchMode == SearchMode.OPENROUTER)
                    R.string.settings_search_openrouter_note
                else R.string.settings_search_tavily_note
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }

    Hairline(Modifier.padding(vertical = 10.dp))

    // 每個服務的 key 各自一頁：欄位不一樣，全部攤在這裡又會變回長捲軸
    SectionLabel(stringResource(R.string.settings_keys), Modifier.padding(bottom = 4.dp))
    ApiService.entries.forEach { service ->
        MenuRow(
            title = service.label,
            summary = stringResource(
                if (service.keyOf(settings).isBlank()) R.string.settings_key_unset
                else R.string.settings_key_set
            ),
            onClick = { onOpenService(service) },
        )
        Hairline()
    }
}

@Composable
private fun SearchMode.labelRes(): Int = when (this) {
    SearchMode.OFF -> R.string.settings_search_off
    SearchMode.OPENROUTER -> R.string.provider_openrouter_label
    SearchMode.TAVILY -> R.string.provider_tavily_label
}

private fun ApiService.keyOf(settings: NutriSettings): String = when (this) {
    ApiService.GEMINI -> settings.geminiApiKey
    ApiService.OPENROUTER -> settings.openRouterApiKey
    ApiService.TAVILY -> settings.tavilyApiKey
}

/**
 * 一個服務的 key（外加它自己的設定）。設定裡唯一的第三層。
 *
 * 三個服務共用這個外框而不是各寫一頁：差別只有「help 文案、key 存到哪個欄位、
 * 底下要不要接模型選單」，各寫一份的話收鍵盤與內距的規則就要維護三次。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyScreen(
    service: ApiService,
    settings: NutriSettings,
    onChange: (NutriSettings) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val setKey: (String) -> Unit = { raw ->
        val value = raw.trim()
        onChange(
            when (service) {
                ApiService.GEMINI -> settings.copy(geminiApiKey = value)
                ApiService.OPENROUTER -> settings.copy(openRouterApiKey = value)
                ApiService.TAVILY -> settings.copy(tavilyApiKey = value)
            }
        )
    }

    Scaffold(
        modifier = Modifier.dismissKeyboardOnTap(),
        topBar = {
            ScreenTopBar(
                title = service.label,
                closeLabel = stringResource(R.string.settings_back),
                onClose = onBack,
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 三家講的是同一件事，不必各寫一句 —— 會自己去申請 key 的人不需要
            // 被教「去哪裡申請」和「不會上傳」。
            Text(
                stringResource(R.string.settings_key_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NutriTextField(
                value = service.keyOf(settings),
                onValueChange = setKey,
                label = stringResource(R.string.settings_api_key),
                placeholder = stringResource(R.string.settings_api_key_hint),
                // key 不該直接顯示在畫面上 —— 截圖或旁人看到就等於外流
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            TextAction(
                stringResource(R.string.settings_paste),
                onClick = { clipboardText(context)?.let(setKey) },
            )

            when (service) {
                // Gemini 的型號固定幾個，攤開讓人選
                ApiService.GEMINI -> {
                    ModelField(
                        value = settings.geminiModel,
                        onChange = { onChange(settings.copy(geminiModel = it)) },
                    )
                }
                // OpenRouter 有幾百個模型，列不完也不該替使用者挑，所以是自由文字
                ApiService.OPENROUTER -> {
                    NutriTextField(
                        value = settings.openRouterModel,
                        onValueChange = { onChange(settings.copy(openRouterModel = it.trim())) },
                        label = stringResource(R.string.settings_openrouter_model),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.settings_openrouter_model_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Tavily 只是搜尋，沒有模型可以挑
                ApiService.TAVILY -> Unit
            }
        }
    }
}

/**
 * 雲端排在「資料」前面（見 [SettingsPage] 的順序）：連結之後備份是自動發生的，
 * 這是預設的路；匯出／匯入是不依賴帳號的退路，退路擺在下面。
 */
@Composable
private fun DriveSection(
    settings: NutriSettings,
    driveMessage: String?,
    driveBusy: Boolean,
    onConnectDrive: () -> Unit,
    onBackupNow: () -> Unit,
    onDisconnectDrive: () -> Unit,
) {
    Text(
        stringResource(R.string.drive_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (!settings.driveBackupEnabled) {
        // 還沒連結時這是整頁的主要動作，所以是實心墨章，跟匯出同一個長相。
        StampButton(
            label = stringResource(R.string.drive_connect),
            onClick = onConnectDrive,
            modifier = Modifier.padding(top = 4.dp),
        )
    } else {
        // 帳號與上次備份時間都要講：少了它們，使用者沒辦法確認這件事到底
        // 有沒有在動，而備份最怕的就是「以為有在備份」。
        if (settings.driveAccount.isNotBlank()) {
            Text(
                withNumerals(stringResource(R.string.drive_account, settings.driveAccount)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            withNumerals(
                stringResource(R.string.drive_last_backup, lastBackupLabel(settings.lastBackupAt))
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 連結之後備份就是自動的了，這顆只是「現在就跑一次」——
        // 形狀留著（看得出跟下面幾顆是同一類東西），份量用空心退掉。
        StampButton(
            label = stringResource(R.string.drive_backup_now),
            onClick = onBackupNow,
            color = Color.Transparent,
            modifier = Modifier.padding(top = 4.dp),
        )
        // 同樣是章，只是轉朱紅：形狀一致才看得出它跟上面那顆是同一層的動作，
        // 顏色負責講「這顆會關掉一直在幫你做事的東西」。
        StampButton(
            label = stringResource(R.string.drive_disconnect),
            onClick = onDisconnectDrive,
            destructive = true,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    // 轉圈的圓形在這個滿是規線的版面上很突兀，用規線自己的語彙表達等待
    if (driveBusy) {
        IndeterminateRule(Modifier.padding(top = 4.dp))
    }
    driveMessage?.let {
        Text(
            withNumerals(it),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }

}

@Composable
private fun DataSection(
    dataMessage: String?,
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit,
) {
    // 匯出與匯入是同一件事的兩個方向，所以共用一段敘述、擺在一起，
    // 結果訊息也只有一行 —— 兩行訊息並排會分不清哪一行是誰的。
    Text(
        stringResource(R.string.csv_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    StampButton(
        label = stringResource(R.string.export_csv),
        onClick = onExportCsv,
        modifier = Modifier.padding(top = 4.dp),
    )
    // 同樣是章，只有底色退一階：形狀相同才讀得出「這兩個是一對」，
    // 深灰負責講「這一顆是反方向的那個」。
    StampButton(
        label = stringResource(R.string.import_csv),
        onClick = onImportCsv,
        color = NutrientColors.StampSecondary,
        modifier = Modifier.padding(top = 8.dp),
    )
    dataMessage?.let {
        Text(
            withNumerals(it),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * 確認面板的內文。三句話分開放，是因為後兩種情況常常不存在 ——
 * 硬湊成一句就會變成「另外 0 筆已經有了」這種讀起來像出錯的句子。
 */
@Composable
private fun importSummary(preview: ImportPreview): String = buildList {
    add(
        stringResource(
    R.string.import_summary_new,
    preview.newEntries.size,
    preview.firstDate.orEmpty(),
    preview.lastDate.orEmpty(),
        )
    )
    if (preview.duplicates > 0) {
        add(stringResource(R.string.import_summary_duplicates, preview.duplicates))
    }
    if (preview.skipped > 0) {
        add(stringResource(R.string.import_summary_skipped, preview.skipped))
    }
}.joinToString(" ")

/** 上次備份的時間。今天以內講時分，跨天就講日期 —— 「昨天備份過」是使用者真正在意的事。 */
@Composable
private fun lastBackupLabel(millis: Long): String {
    if (millis <= 0) return stringResource(R.string.drive_never)
    val moment = java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDateTime()
    val time = "%02d:%02d".format(moment.hour, moment.minute)
    return if (moment.toLocalDate() == java.time.LocalDate.now()) time
    else moment.toLocalDate().toString() + " " + time
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun DarkModePreference.label(): String = stringResource(
    when (this) {
        DarkModePreference.SYSTEM -> R.string.dark_mode_system
        DarkModePreference.LIGHT -> R.string.dark_mode_light
        DarkModePreference.DARK -> R.string.dark_mode_dark
    }
)

/**
 * 目標值欄位。空字串當 0（等於關掉那條進度條的意義），
 * 上限只是防呆，避免手滑多打一個 0 讓進度條永遠貼在左邊。
 */
@Composable
private fun TargetField(label: String, value: Int, max: Int, onChange: (Int) -> Unit) {
    NutriTextField(
        value = if (value == 0) "" else value.toString(),
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(5)
            onChange(digits.toIntOrNull()?.coerceIn(NutriSettings.MIN_TARGET, max) ?: 0)
        },
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        // 輸入的內容一定是純數字，套襯線不會碰到中文 label（label 是另一個 Text）
        numeric = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 目前支援的模型，改清單就不會再有「名稱打錯」這種輸入錯誤。 */
private val GEMINI_MODELS = listOf(
    "gemini-3.8-flash",
    "gemini-3.7-flash",
    "gemini-3.6-flash",
    "gemini-3.5-flash-lite",
)

/**
 * 模型選擇：選項直接攤開，不用下拉選單。
 *
 * 原本是 `ExposedDropdownMenuBox` —— 但它彈出來的浮層是 M3 自己的容器
 * （圓角、陰影、Material 的底色），在這套方角紙面上是整個 app 唯一一個
 * 浮起來的 Material 元件，而且為了少數幾個選項去蓋一整套 popup 樣式並不划算。
 *
 * 選項不多，直接攤開反而少一次點擊，也看得到彼此的差別。
 * 用垂直的圈選（而不是 [BallotRow] 那種橫排）是因為模型名稱很長，排不成一列。
 */
@Composable
private fun ModelField(value: String, onChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column {
        SectionLabel(stringResource(R.string.settings_model), Modifier.padding(bottom = 4.dp))
        GEMINI_MODELS.forEach { model ->
            val active = model == value
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onChange(model) }
                    .padding(vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (active) scheme.onSurface else Color.Transparent)
                        .border(
                            1.5.dp,
                            if (active) scheme.onSurface else NutrientColors.FieldBorder,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (active) {
                        Box(
                            Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(scheme.inverseOnSurface)
                        )
                    }
                }
                // 型號是純英數，套襯線不會碰到中文
                Text(
                    model,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp).numeric(),
                    color = if (active) scheme.onSurface else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun clipboardText(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    return manager?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
}
