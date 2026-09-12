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
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.AiProvider
import com.watson.nutrilog.data.AppIcon
import com.watson.nutrilog.data.ApiService
import com.watson.nutrilog.data.SearchMode
import com.watson.nutrilog.data.DarkModePreference
import kotlin.math.roundToInt
import com.watson.nutrilog.data.ActivitySource
import com.watson.nutrilog.data.HealthDiagnostics
import com.watson.nutrilog.data.RecordOrigins
import com.watson.nutrilog.data.NutriSettings
import com.watson.nutrilog.data.WatchWearMode
import androidx.compose.foundation.layout.size
import com.watson.nutrilog.ui.theme.NutrientColors
import com.watson.nutrilog.ui.theme.numeric
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.Spacer

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
    /** 健康連線那一列的摘要要看系統權限，只看設定值會在權限被收回後仍然顯示「讀取運動」。 */
    healthSupported: Boolean,
    healthReadOn: Boolean,
    healthWriteOn: Boolean,
    onOpen: (SettingsPage) -> Unit,
    onClose: () -> Unit,
) {
    // 進場等那張紙蓋滿了才開始（`LocalScreenEntered`，見 App.kt）；關閉時自己翻回
    // false 讓每一列原路退出去，退完才真的離開這個畫面。
    val entered = LocalScreenEntered.current
    var closing by remember { mutableStateOf(false) }
    val transition = updateTransition(entered && !closing, label = "settingsEnter")

    // **關閉要等那幾列先退完。** 直接呼叫 onClose 的話畫面立刻換掉，退場動畫根本
    // 來不及播 —— 使用者看到的就是「進來有動畫、離開沒有」，比兩邊都沒有還怪。
    //
    // `closing` 同時擋掉這段期間的重複觸發（連按關閉、退場中還去點某一列）。
    val requestClose = { closing = true }
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

    val healthSummary = stringResource(
        when {
            !healthSupported -> R.string.health_summary_unsupported
            healthReadOn && healthWriteOn -> R.string.health_summary_read_write
            healthReadOn -> R.string.health_summary_read
            healthWriteOn -> R.string.health_summary_write
            else -> R.string.health_summary_off
        }
    )

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

                // **只有內容在動，規線不動。** 規線是這套版面的骨架，骨架跟著內容
                // 一起飛進來的話整頁會晃；讓線先在、墨一列一列蓋上去，才是「紙已經
                // 印好」的那個意思。所以 graphicsLayer 掛在 MenuRow 上而不是外面
                // 這層 —— 掛外面 Hairline 會一起走。
                Column {
                    MenuRow(
                        title = stringResource(page.titleRes()),
                        summary = page.summary(settings, healthSummary),
                        // 退場途中不接點擊：那幾百毫秒裡畫面還在，點下去會在
                        // 關閉的路上又開一個子頁。
                        onClick = { if (!closing) onOpen(page) },
                        modifier = Modifier.graphicsLayer {
                            alpha = slide
                            translationX = (1f - slide) * ROW_SLIDE_DP.toPx()
                        },
                    )
                    Hairline()
                }
            }
        }
    }
}

/**
 * 一列滑進來要多久、從多右邊開始、整批延後多久起跑。
 *
 * 位移曾經是 64dp，看起來像整頁被推了一把而不是列自己就定位。收到 28dp 之後
 * 節奏還在，推擠感沒了 —— 這一列本來就是滿版寬，不需要走那麼遠才讀得出方向。
 */
private const val ROW_SLIDE_MS = 260
private const val ROW_LEAD_MS = 70
private val ROW_SLIDE_DP = 28.dp

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
internal fun MenuRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
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
    SettingsPage.HEALTH -> R.string.settings_health
    SettingsPage.AI -> R.string.settings_ai
    SettingsPage.DRIVE -> R.string.drive_section
    SettingsPage.DATA -> R.string.settings_data
}

@Composable
private fun SettingsPage.summary(settings: NutriSettings, healthSummary: String): String = when (this) {
    SettingsPage.APPEARANCE -> settings.darkMode.label()
    SettingsPage.TARGETS ->
        settings.calorieTarget.toString() + " " + stringResource(R.string.unit_kcal)
    SettingsPage.HEALTH -> healthSummary
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
    healthSupported: Boolean,
    healthReadAuthorized: Boolean,
    healthWriteAuthorized: Boolean,
    healthBusy: Boolean,
    healthResult: HealthSyncResult?,
    onSetReadExercise: (Boolean) -> Unit,
    onSetHealthWrite: (Boolean) -> Unit,
    onSyncHealthNow: () -> Unit,
    healthDiagnostics: HealthDiagnostics?,
    onRunHealthDiagnostics: () -> Unit,
    onSelectIcon: (AppIcon) -> Unit,
    showBmrCalculator: Boolean,
    onOpenBmr: () -> Unit,
    onApplyBmr: (NutriSettings) -> Unit,
    onSetWearMode: (WatchWearMode) -> Unit,
    onSetEatBack: (Int) -> Unit,
    onCloseBmr: () -> Unit,
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
            // 子頁的內容也由右邊進來，和選單那一層同一個方向 —— **設定不管幾層
            // 都是往右邊那個方向長出來的**，往裡面走一層就再從右邊來一次。
            Column(
                Modifier.enterSlide(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            when (page) {
                SettingsPage.APPEARANCE -> AppearanceSection(settings, onChange, onSelectIcon)
                SettingsPage.TARGETS ->
                    TargetsSection(settings, onChange, onOpenBmr, onSetWearMode, onSetEatBack)
                SettingsPage.HEALTH -> HealthSection(
                    settings, healthSupported, healthReadAuthorized, healthWriteAuthorized,
                    healthBusy, healthResult, onSetReadExercise, onSetHealthWrite, onSyncHealthNow,
                    healthDiagnostics, onRunHealthDiagnostics,
                )
                SettingsPage.AI -> AiSection(settings, onChange, onOpenService)
                SettingsPage.DRIVE -> DriveSection(
                    settings, driveMessage, driveBusy, onConnectDrive, onBackupNow, onDisconnectDrive,
                )
                SettingsPage.DATA -> DataSection(dataMessage, onExportCsv, onImportCsv)
            }
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

    if (showBmrCalculator) {
        BmrCalculatorDialog(initialSettings = settings, onApply = onApplyBmr, onDismiss = onCloseBmr)
    }
}

@Composable
private fun AppearanceSection(
    settings: NutriSettings,
    onChange: (NutriSettings) -> Unit,
    onSelectIcon: (AppIcon) -> Unit,
) {
    // 圈選，不是 M3 的 SegmentedButton —— 它靠容器色分辨選中與否，
    // 在這套低對比色票上兩個狀態幾乎看不出差別。
    BallotRow(
        labels = DarkModePreference.entries.map { it.label() },
        selectedIndex = DarkModePreference.entries.indexOf(settings.darkMode),
        onSelect = { onChange(settings.copy(darkMode = DarkModePreference.entries[it])) },
    )

    Hairline(Modifier.padding(vertical = 10.dp))

    SectionLabel(stringResource(R.string.settings_app_icon))
    // 一排四個，第二排放剩下的 —— 不用 FlowRow（實驗性 API），列數固定看得出來
    AppIcon.entries.chunked(4).forEach { row ->
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            row.forEach { icon ->
                IconChoice(
                    icon = icon,
                    selected = icon == settings.appIcon,
                    onClick = { onSelectIcon(icon) },
                    modifier = Modifier.weight(1f),
                )
            }
            // 最後一排不足四個時補空白，剩下的才不會被撐寬
            repeat(4 - row.size) { Box(Modifier.weight(1f)) }
        }
    }

    Text(
        withNumerals(stringResource(R.string.app_icon_note)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

/**
 * 一款圖示。**預覽是照 adaptive icon 的規矩畫的**：底色填滿，前景放大到 1.5 倍再切邊 ——
 * 前景本來就只佔中央 72/108，照原比例畫會變成縮在中間的一小塊，和桌面上看到的不一樣。
 *
 * 選中用墨框，不是色塊：這套版面裡「選中」一律是墨，朱紅留給超標與破壞性動作。
 */
@Composable
private fun IconChoice(
    icon: AppIcon,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) scheme.onSurface else NutrientColors.FieldBorder,
                )
                .clickable(onClick = onClick)
                .padding(3.dp)
                .clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            val (foreground, background) = iconArt(icon)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colorResource(background)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(foreground),
                    contentDescription = null,
                    // 放大要用 graphicsLayer：fillMaxSize(1.5f) 會被夾回父層的大小，
                    // 畫出來等於沒放大，預覽就比桌面上的圖示小一圈
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.5f
                            scaleY = 1.5f
                        },
                )
            }
        }
        Text(
            stringResource(icon.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 這一款的前景與底色，要和 mipmap-anydpi-v26 裡同名的 adaptive icon 一致。 */
private fun iconArt(icon: AppIcon): Pair<Int, Int> = when (icon) {
    AppIcon.DEFAULT -> R.mipmap.ic_launcher_foreground to R.color.ic_launcher_background
    AppIcon.CAT -> R.mipmap.ic_launcher_cat_foreground to R.color.ic_launcher_background_paper
    AppIcon.HAT -> R.mipmap.ic_launcher_hat_foreground to R.color.ic_launcher_background_paper
    AppIcon.KEYBOARD -> R.mipmap.ic_launcher_keyboard_foreground to R.color.ic_launcher_background_paper
    AppIcon.INK -> R.mipmap.ic_launcher_foreground to R.color.ic_launcher_background_ink
    AppIcon.VERMILION -> R.mipmap.ic_launcher_foreground to R.color.ic_launcher_background_vermilion
    AppIcon.BOWL -> R.drawable.ic_launcher_bowl_foreground to R.color.ic_launcher_background_paper
}

private fun AppIcon.labelRes(): Int = when (this) {
    AppIcon.DEFAULT -> R.string.app_icon_default
    AppIcon.CAT -> R.string.app_icon_cat
    AppIcon.HAT -> R.string.app_icon_hat
    AppIcon.KEYBOARD -> R.string.app_icon_keyboard
    AppIcon.INK -> R.string.app_icon_ink
    AppIcon.VERMILION -> R.string.app_icon_vermilion
    AppIcon.BOWL -> R.string.app_icon_bowl
}

/** 回補比例的選項。數字本身就是百分比，100 在畫面上寫「全額」。 */
private val EAT_BACK_CHOICES = listOf(25, 50, 75, 100)

@Composable
private fun TargetsSection(
    settings: NutriSettings,
    onChange: (NutriSettings) -> Unit,
    onOpenBmr: () -> Unit,
    onSetWearMode: (WatchWearMode) -> Unit,
    onSetEatBack: (Int) -> Unit,
) {
    // 放在欄位上面：算完按套用會直接改下面這四格，入口擺在它們前面讀起來是因果順序。
    MenuRow(title = stringResource(R.string.bmr_open), summary = "", onClick = onOpenBmr)
    Hairline()

    // **只有在「讀取運動消耗」開著時才出現**：關掉的話沒有手錶資料可以填日常活動那一段，
    // 選「整天配戴」只會得到一個沒人補的低目標。
    // 擺在四格目標上面，因為切換它會當場改掉底下的熱量。
    if (settings.readExerciseCalories) {
        val allDay = settings.watchWearMode == WatchWearMode.ALL_DAY
        SectionLabel(stringResource(R.string.watch_wear_title))
        BallotRow(
            labels = listOf(
                stringResource(R.string.watch_wear_all_day),
                stringResource(R.string.watch_wear_workout),
            ),
            selectedIndex = if (allDay) 0 else 1,
            onSelect = {
                onSetWearMode(if (it == 0) WatchWearMode.ALL_DAY else WatchWearMode.WORKOUT_ONLY)
            },
        )
        Text(
            stringResource(
                if (allDay) R.string.watch_wear_help_all_day else R.string.watch_wear_help_workout
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Hairline(Modifier.padding(vertical = 10.dp))

        // 全額回補會把手錶高估的那一段一起吃回去，所以預設只補一半。
        SectionLabel(stringResource(R.string.exercise_eatback_title))
        BallotRow(
            labels = EAT_BACK_CHOICES.map {
                if (it == 100) stringResource(R.string.exercise_eatback_full) else "$it%"
            },
            selectedIndex = EAT_BACK_CHOICES.indexOf(settings.exerciseEatBackPercent)
                .coerceAtLeast(0),
            onSelect = { onSetEatBack(EAT_BACK_CHOICES[it]) },
        )
        Text(
            stringResource(R.string.exercise_eatback_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Hairline(Modifier.padding(vertical = 10.dp))
    }
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

/**
 * 健康連線。**讀運動與寫飲食是兩個開關、各要各的權限**：使用者可能只想讓運動消耗
 * 算進目標，而不想把吃了什麼寫進 Samsung Health。
 */
@Composable
private fun HealthSection(
    settings: NutriSettings,
    supported: Boolean,
    readAuthorized: Boolean,
    writeAuthorized: Boolean,
    busy: Boolean,
    result: HealthSyncResult?,
    onSetRead: (Boolean) -> Unit,
    onSetWrite: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    diagnostics: HealthDiagnostics?,
    onRunDiagnostics: () -> Unit,
) {
    if (!supported) {
        Text(
            stringResource(R.string.health_not_supported),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    // 開關亮不亮看「設定想開 且 系統真的有給權限」：只看設定的話，權限在系統那邊被收回之後
    // 開關仍然亮著，數字卻永遠是 0，使用者找不到原因。
    SwitchRow(
        title = stringResource(R.string.health_read_title),
        help = stringResource(R.string.health_read_help),
        checked = settings.readExerciseCalories && readAuthorized,
        onCheckedChange = onSetRead,
    )
    Hairline()
    val writeOn = settings.healthConnectSyncEnabled && writeAuthorized
    SwitchRow(
        title = stringResource(R.string.health_write_title),
        help = stringResource(R.string.health_write_help),
        checked = writeOn,
        onCheckedChange = onSetWrite,
    )
    if (writeOn) {
        Hairline()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StampButton(
                label = stringResource(R.string.health_sync_now),
                onClick = onSyncNow,
                enabled = !busy,
                color = Color.Transparent,
            )
            Spacer(Modifier.weight(1f))
            if (settings.lastHealthSyncAt > 0) {
                Text(
                    withNumerals(
                        stringResource(R.string.health_last_sync, lastBackupLabel(settings.lastHealthSyncAt))
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (busy) IndeterminateRule()
    result?.let {
        Text(
            withNumerals(
                when (it) {
                    is HealthSyncResult.Written -> stringResource(R.string.health_sync_done, it.count)
                    is HealthSyncResult.Failed -> stringResource(R.string.health_sync_failed, it.reason)
                    HealthSyncResult.PermissionDenied -> stringResource(R.string.health_permission_denied)
                    HealthSyncResult.NotSupported -> stringResource(R.string.health_not_supported)
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Rule(Modifier.padding(top = 14.dp, bottom = 10.dp))
    SectionLabel(stringResource(R.string.health_diag_title))
    Text(
        stringResource(R.string.health_diag_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    StampButton(
        label = stringResource(R.string.health_diag_run),
        onClick = onRunDiagnostics,
        color = Color.Transparent,
        modifier = Modifier.padding(top = 10.dp),
    )
    diagnostics?.let { DiagnosticsReport(it) }
}

/**
 * 健康連線裡原始有什麼。**「無資料」和「0」要看得出差別** —— 前者是三星沒寫進來
 * （或沒授權），後者是有資料但那天沒動，兩件事往完全不同的方向修。
 */
@Composable
private fun DiagnosticsReport(d: HealthDiagnostics) {
    val none = stringResource(R.string.health_diag_none)
    val yes = stringResource(R.string.health_diag_yes)
    val no = stringResource(R.string.health_diag_no)
    val writersNone = stringResource(R.string.health_diag_writers_none)
    // 不帶引數取回含 %1$s 的原字串：底下是普通函式，不能再呼叫 stringResource
    val withCountFmt = stringResource(R.string.health_diag_with_count)
    fun kcal(v: Double?) = v?.let { "%,d".format(it.roundToInt()) } ?: none
    // 合計值後面掛紀錄筆數。**合計有數字卻是 0 筆**，代表那個值不是任何 app 寫進來的
    fun counted(value: String, o: RecordOrigins?) =
        if (o == null) value else String.format(withCountFmt, value, o.count)

    val rows = listOf(
        stringResource(R.string.health_diag_date) to d.date.toString(),
        stringResource(R.string.health_diag_active) to counted(kcal(d.activeKcal), d.activeOrigins),
        stringResource(R.string.health_diag_total) to counted(kcal(d.totalKcal), d.totalOrigins),
        // 和上一列一樣的總消耗，只查到現在為止。**兩個一樣就代表它不會跟著時間累加**，
        // 那就不能拿來當「今天到現在動了多少」。
        stringResource(R.string.health_diag_total_so_far) to kcal(d.totalKcalSoFar),
        stringResource(R.string.health_diag_basal) to counted(kcal(d.basalKcal), d.basalOrigins),
        stringResource(R.string.health_diag_steps) to
            counted(d.steps?.let { "%,d".format(it) } ?: none, d.stepsOrigins),
        stringResource(R.string.health_diag_sessions) to
            stringResource(R.string.health_diag_sessions_count, d.chosen.workoutSessions.size),
        // 同一個型別出現兩個來源，就是合計把兩份疊起來了（手機自己那份步數預設不去重）
        stringResource(R.string.health_diag_read_at) to "%02d:%02d".format(d.readAt.hour, d.readAt.minute),
        stringResource(R.string.health_diag_writers) to
            listOfNotNull(d.activeOrigins, d.totalOrigins, d.stepsOrigins, d.basalOrigins)
                .flatMap { it.packages }
                .distinct()
                .ifEmpty { listOf(writersNone) }
                .joinToString("、"),
        stringResource(R.string.health_diag_chosen) to
            // 讀不到時只寫「無資料」，後面再掛一個 0 會讓人以為那是量到的值
            if (d.chosen.source == ActivitySource.NONE) {
                sourceName(d.chosen.source)
            } else {
                "${sourceName(d.chosen.source)} ${d.chosen.calories.roundToInt()}"
            },
        stringResource(R.string.health_diag_permissions) to listOf(
            stringResource(R.string.health_diag_active) to d.grantedActiveCalories,
            stringResource(R.string.health_diag_total) to d.grantedTotalCalories,
            stringResource(R.string.health_diag_steps) to d.grantedSteps,
            stringResource(R.string.health_diag_exercise) to d.grantedExercise,
            stringResource(R.string.health_diag_basal) to d.grantedBasal,
        ).joinToString(" · ") { (label, ok) -> "$label " + (if (ok) yes else no) },
    )

    Column(Modifier.padding(top = 12.dp)) {
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(96.dp),
                )
                Text(withNumerals(value), style = MaterialTheme.typography.bodySmall)
            }
        }
        // 每一場都列出來：自動偵測的健走常常切成好幾段，只給總數看不出這件事
        d.chosen.workoutSessions.forEach { session ->
            Text(
                withNumerals(
                    "  ${session.title} ${session.timeRangeText} " +
                        "${session.durationMinutes} 分 ${session.calories.roundToInt()} kcal"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        d.chosen.unavailableReason?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun sourceName(source: ActivitySource): String = stringResource(
    when (source) {
        ActivitySource.ACTIVE_CALORIES -> R.string.exercise_source_active
        ActivitySource.WORKOUT_SESSIONS -> R.string.exercise_source_workouts
        ActivitySource.STEPS -> R.string.exercise_source_steps
        ActivitySource.NONE -> R.string.health_diag_none
    }
)

@Composable
private fun SwitchRow(title: String, help: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 開關固定 46dp 寬，左邊文字要自己留出間距，不然會貼到它身上（同每日目標頁那一列）
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title)
            Text(
                help,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        NutriSwitch(checked = checked, onCheckedChange = onCheckedChange)
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

    // 和文字辨識同一種圖選：兩者都是「這件事交給哪一家」。說明只講一件使用者不知道的事 ——
    // 不用另外填金鑰。
    SectionLabel(stringResource(R.string.settings_report_provider))
    BallotRow(
        labels = AiProvider.entries.map { it.label },
        selectedIndex = AiProvider.entries.indexOf(settings.reportProvider),
        onSelect = { onChange(settings.copy(reportProvider = AiProvider.entries[it])) },
    )
    Text(
        stringResource(R.string.settings_report_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

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
    // 只接回身型、沒有新紀錄時不要講「會新增 0 筆（ ～ ）」
    if (preview.newEntries.isNotEmpty()) {
        add(
            stringResource(
                R.string.import_summary_new,
                preview.newEntries.size,
                preview.firstDate.orEmpty(),
                preview.lastDate.orEmpty(),
            )
        )
    }
    if (preview.duplicates > 0) {
        add(stringResource(R.string.import_summary_duplicates, preview.duplicates))
    }
    if (preview.skipped > 0) {
        add(stringResource(R.string.import_summary_skipped, preview.skipped))
    }
    preview.profile?.let { add(stringResource(R.string.import_summary_profile, it.calorieTarget)) }
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
