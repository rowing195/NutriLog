package com.watson.nutrilog.ui

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.DarkModePreference
import com.watson.nutrilog.data.NutriSettings
import androidx.compose.foundation.layout.size
import com.watson.nutrilog.ui.theme.NutrientColors
import com.watson.nutrilog.ui.theme.numeric

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
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
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        modifier = Modifier.dismissKeyboardOnTap(),
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.settings_title),
                closeLabel = stringResource(R.string.close),
                onClose = onClose,
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
            SectionTitle(stringResource(R.string.settings_appearance))
            // 圈選，不是 M3 的 SegmentedButton —— 它靠容器色分辨選中與否，
            // 在這套低對比色票上兩個狀態幾乎看不出差別。
            BallotRow(
                labels = DarkModePreference.entries.map { it.label() },
                selectedIndex = DarkModePreference.entries.indexOf(settings.darkMode),
                onSelect = { onChange(settings.copy(darkMode = DarkModePreference.entries[it])) },
            )

            Hairline(Modifier.padding(vertical = 10.dp))

            SectionTitle(stringResource(R.string.settings_targets))
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

            Hairline(Modifier.padding(vertical = 10.dp))

            SectionTitle(stringResource(R.string.settings_gemini))
            Text(
                stringResource(R.string.settings_api_key_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NutriTextField(
                value = settings.geminiApiKey,
                onValueChange = { onChange(settings.copy(geminiApiKey = it.trim())) },
                label = stringResource(R.string.settings_api_key),
                placeholder = stringResource(R.string.settings_api_key_hint),
                // key 不該直接顯示在畫面上 —— 截圖或旁人看到就等於外流
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            TextAction(
                stringResource(R.string.settings_paste),
                onClick = {
                    clipboardText(context)?.let { onChange(settings.copy(geminiApiKey = it.trim())) }
                },
            )
            ModelField(
                value = settings.geminiModel,
                onChange = { onChange(settings.copy(geminiModel = it)) },
            )
            Text(
                stringResource(R.string.settings_model_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Hairline(Modifier.padding(vertical = 10.dp))

            // 雲端擺在本地上面：連結之後備份是自動發生的，這一區才是預設的路；
            // 匯出／匯入是不依賴帳號的退路，退路擺在下面。
            SectionTitle(stringResource(R.string.drive_section))
            Text(
                stringResource(R.string.drive_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.drive_scope_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
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

            Hairline(Modifier.padding(vertical = 10.dp))

            SectionTitle(stringResource(R.string.settings_data))
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

/** 目前只有這三個模型能用，改下拉選單就不會再有「名稱打錯」這種輸入錯誤。 */
private val GEMINI_MODELS = listOf("gemini-3.6-flash", "gemini-3.7-flash", "gemini-3.5-flash-lite")

/**
 * 模型選擇：三個選項直接攤開，不用下拉選單。
 *
 * 原本是 `ExposedDropdownMenuBox` —— 但它彈出來的浮層是 M3 自己的容器
 * （圓角、陰影、Material 的底色），在這套方角紙面上是整個 app 唯一一個
 * 浮起來的 Material 元件，而且為了三個選項去蓋一整套 popup 樣式並不划算。
 *
 * 只有三個而且不會再多，攤開反而少一次點擊，也看得到彼此的差別。
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
