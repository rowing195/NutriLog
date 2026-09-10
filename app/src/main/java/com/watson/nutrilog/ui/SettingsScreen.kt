package com.watson.nutrilog.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.widget.Toast
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
    onOpenBmrCalculator: () -> Unit,
    isHealthConnectSupported: Boolean = true,
    healthConnectAuthorized: Boolean = false,
    healthConnectReadAuthorized: Boolean = false,
    healthSyncBusy: Boolean = false,
    healthMessage: String? = null,
    onToggleHealthConnect: (Boolean) -> Unit = {},
    onToggleReadExerciseCalories: (Boolean) -> Unit = {},
    onSyncAllHealthConnect: () -> Unit = {},
    onClose: () -> Unit,
) {
    val context = LocalContext.current

    var targetsExpanded by rememberSaveable { mutableStateOf(false) }
    var aiExpanded by rememberSaveable { mutableStateOf(false) }
    var healthExpanded by rememberSaveable { mutableStateOf(false) }
    var driveExpanded by rememberSaveable { mutableStateOf(false) }
    var dataExpanded by rememberSaveable { mutableStateOf(false) }
    var appearanceExpanded by rememberSaveable { mutableStateOf(false) }
    var nutrientsExpanded by rememberSaveable { mutableStateOf(false) }

    val allExpanded = targetsExpanded && aiExpanded && healthExpanded && driveExpanded && dataExpanded && appearanceExpanded && nutrientsExpanded

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (allExpanded) "全部折疊" else "全部展開",
                    style = MaterialTheme.typography.labelMedium,
                    color = NutrientColors.Accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            val target = !allExpanded
                            targetsExpanded = target
                            aiExpanded = target
                            healthExpanded = target
                            driveExpanded = target
                            dataExpanded = target
                            appearanceExpanded = target
                            nutrientsExpanded = target
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            // 1. 每日目標與體態
            CollapsibleSection(
                title = stringResource(R.string.settings_targets),
                expanded = targetsExpanded,
                onToggle = { targetsExpanded = !targetsExpanded },
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, NutrientColors.FieldBorder, RoundedCornerShape(6.dp))
                        .clickable { onOpenBmrCalculator() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.bmr_calc_open),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "計算 ›",
                        style = MaterialTheme.typography.titleMedium,
                        color = NutrientColors.Accent,
                        modifier = Modifier.padding(start = 12.dp),
                    )
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
            }

            // 2. AI 服務與 API 金鑰
            CollapsibleSection(
                title = "AI 服務與金鑰設定",
                expanded = aiExpanded,
                onToggle = { aiExpanded = !aiExpanded },
            ) {
                ApiKeySection(
                    title = stringResource(R.string.settings_gemini),
                    currentKey = settings.geminiApiKey,
                    keyLabel = stringResource(R.string.settings_api_key),
                    keyHint = stringResource(R.string.settings_api_key_hint),
                    onSaveKey = { newKey ->
                        onChange(settings.copy(geminiApiKey = newKey))
                    },
                )

                Hairline(Modifier.padding(vertical = 8.dp))

                ApiKeySection(
                    title = stringResource(R.string.settings_nvidia),
                    currentKey = settings.nvidiaApiKey,
                    keyLabel = stringResource(R.string.settings_nvidia_key),
                    keyHint = stringResource(R.string.settings_nvidia_key_hint),
                    onSaveKey = { newKey ->
                        onChange(settings.copy(nvidiaApiKey = newKey))
                    },
                )

                Hairline(Modifier.padding(vertical = 8.dp))

                ApiKeySection(
                    title = stringResource(R.string.settings_tavily),
                    currentKey = settings.tavilyApiKey,
                    keyLabel = stringResource(R.string.settings_tavily_key),
                    keyHint = stringResource(R.string.settings_tavily_key_hint),
                    onSaveKey = { newKey ->
                        onChange(settings.copy(tavilyApiKey = newKey))
                    },
                )
            }

            // 3. 健康連線 (Samsung Health)
            val isConnected = (settings.healthConnectSyncEnabled && healthConnectAuthorized) ||
                    (settings.readExerciseCalories && healthConnectReadAuthorized)

            CollapsibleSection(
                title = stringResource(R.string.health_connect_section),
                expanded = healthExpanded,
                onToggle = { healthExpanded = !healthExpanded },
            ) {
                if (!isHealthConnectSupported) {
                    Text(
                        stringResource(R.string.health_connect_not_supported),
                        style = MaterialTheme.typography.bodySmall,
                        color = NutrientColors.Accent,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(
                                stringResource(R.string.health_connect_title),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                if (isConnected)
                                    stringResource(R.string.health_connect_connected)
                                else
                                    stringResource(R.string.health_connect_not_connected),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isConnected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        NutriSwitch(
                            checked = isConnected,
                            onCheckedChange = onToggleHealthConnect,
                        )
                    }

                    if (isConnected) {
                        StampButton(
                            label = stringResource(R.string.health_connect_sync_all),
                            onClick = onSyncAllHealthConnect,
                            color = Color.Transparent,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                if (healthSyncBusy) {
                    IndeterminateRule(Modifier.padding(top = 4.dp))
                }
                healthMessage?.let {
                    Text(
                        withNumerals(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // 4. Google 雲端備份
            CollapsibleSection(
                title = stringResource(R.string.drive_section),
                expanded = driveExpanded,
                onToggle = { driveExpanded = !driveExpanded },
            ) {
                if (!settings.driveBackupEnabled) {
                    StampButton(
                        label = stringResource(R.string.drive_connect),
                        onClick = onConnectDrive,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                } else {
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
                    StampButton(
                        label = stringResource(R.string.drive_backup_now),
                        onClick = onBackupNow,
                        color = Color.Transparent,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    StampButton(
                        label = stringResource(R.string.drive_disconnect),
                        onClick = onDisconnectDrive,
                        destructive = true,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
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

            // 5. 本機資料管理 (CSV)
            CollapsibleSection(
                title = stringResource(R.string.settings_data),
                expanded = dataExpanded,
                onToggle = { dataExpanded = !dataExpanded },
            ) {
                StampButton(
                    label = stringResource(R.string.export_csv),
                    onClick = onExportCsv,
                    modifier = Modifier.padding(top = 4.dp),
                )
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

            // 6. 外觀設定
            CollapsibleSection(
                title = stringResource(R.string.settings_appearance),
                expanded = appearanceExpanded,
                onToggle = { appearanceExpanded = !appearanceExpanded },
            ) {
                BallotRow(
                    labels = DarkModePreference.entries.map { it.label() },
                    selectedIndex = DarkModePreference.entries.indexOf(settings.darkMode),
                    onSelect = { onChange(settings.copy(darkMode = DarkModePreference.entries[it])) },
                )
            }

            // 7. 擴充營養素顯示
            CollapsibleSection(
                title = stringResource(R.string.settings_show_extended),
                expanded = nutrientsExpanded,
                onToggle = { nutrientsExpanded = !nutrientsExpanded },
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "顯示糖、鈉、膳食纖維與飽和脂肪",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(end = 16.dp),
                    )
                    NutriSwitch(
                        checked = settings.showExtendedNutrients,
                        onCheckedChange = { onChange(settings.copy(showExtendedNutrients = it)) },
                    )
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
private fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val angle by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "chevron_rotate",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, NutrientColors.FieldBorder, RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(angle),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Hairline(Modifier.padding(bottom = 6.dp))
                content()
            }
        }
    }
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

@Composable
private fun ApiKeySection(
    title: String,
    currentKey: String,
    keyLabel: String,
    keyHint: String,
    onSaveKey: (String) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var inputKey by remember(currentKey) { mutableStateOf(currentKey) }
    var showSavedFeedback by remember { mutableStateOf(false) }
    val isModified = inputKey.trim() != currentKey.trim()
    val hasKey = currentKey.isNotBlank()

    // 若使用者未點擊確認按鈕便離開畫面，安全保留已輸入之金鑰
    DisposableEffect(inputKey) {
        onDispose {
            val trimmed = inputKey.trim()
            if (trimmed != currentKey.trim()) {
                onSaveKey(trimmed)
            }
        }
    }

    SectionTitle(title)

    NutriTextField(
        value = inputKey,
        onValueChange = {
            inputKey = it
            showSavedFeedback = false
        },
        label = keyLabel,
        placeholder = keyHint,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextAction(
                stringResource(R.string.settings_paste),
                onClick = {
                    clipboardText(context)?.let {
                        inputKey = it.trim()
                        showSavedFeedback = false
                    }
                },
            )
            if (inputKey.isNotEmpty()) {
                TextAction(
                    stringResource(R.string.settings_key_clear),
                    color = MaterialTheme.colorScheme.outline,
                    onClick = {
                        inputKey = ""
                        showSavedFeedback = false
                    },
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (showSavedFeedback) {
                Text(
                    stringResource(R.string.settings_key_saved),
                    style = MaterialTheme.typography.bodySmall,
                    color = NutrientColors.Accent,
                )
            } else if (hasKey && !isModified) {
                Text(
                    stringResource(R.string.settings_key_set),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            PillButton(
                label = if (isModified) stringResource(R.string.settings_key_save) else stringResource(R.string.settings_key_confirm),
                filled = isModified || !hasKey,
                onClick = {
                    val trimmed = inputKey.trim()
                    onSaveKey(trimmed)
                    showSavedFeedback = true
                    focusManager.clearFocus()
                    Toast.makeText(context, context.getString(R.string.settings_key_saved_toast), Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

private fun clipboardText(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    return manager?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
}
