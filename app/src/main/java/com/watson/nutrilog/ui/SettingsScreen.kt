package com.watson.nutrilog.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
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
import com.watson.nutrilog.ui.theme.NumberFontFamily
import com.watson.nutrilog.ui.theme.NutriFieldShape
import com.watson.nutrilog.ui.theme.nutriFieldColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: NutriSettings,
    exportMessage: String?,
    onChange: (NutriSettings) -> Unit,
    onExportCsv: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, stringResource(R.string.close))
                    }
                },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_appearance))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                DarkModePreference.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = settings.darkMode == option,
                        onClick = { onChange(settings.copy(darkMode = option)) },
                        shape = SegmentedButtonDefaults.itemShape(index, DarkModePreference.entries.size),
                        label = { Text(option.label()) },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

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

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_show_extended))
                    Text(
                        stringResource(R.string.settings_show_extended_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.showExtendedNutrients,
                    onCheckedChange = { onChange(settings.copy(showExtendedNutrients = it)) },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle(stringResource(R.string.settings_gemini))
            Text(
                stringResource(R.string.settings_api_key_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextField(
                value = settings.geminiApiKey,
                onValueChange = { onChange(settings.copy(geminiApiKey = it.trim())) },
                label = { Text(stringResource(R.string.settings_api_key)) },
                placeholder = { Text(stringResource(R.string.settings_api_key_hint)) },
                singleLine = true,
                // key 不該直接顯示在畫面上 —— 截圖或旁人看到就等於外流
                visualTransformation = PasswordVisualTransformation(),
                shape = NutriFieldShape,
                colors = nutriFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = {
                clipboardText(context)?.let { onChange(settings.copy(geminiApiKey = it.trim())) }
            }) {
                Text(stringResource(R.string.settings_paste))
            }
            ModelField(
                value = settings.geminiModel,
                onChange = { onChange(settings.copy(geminiModel = it)) },
            )
            Text(
                stringResource(R.string.settings_model_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle(stringResource(R.string.settings_data))
            Text(
                stringResource(R.string.export_csv_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.export_csv))
            }
            exportMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
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
    TextField(
        value = if (value == 0) "" else value.toString(),
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(5)
            onChange(digits.toIntOrNull()?.coerceIn(NutriSettings.MIN_TARGET, max) ?: 0)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        // 輸入的內容一定是純數字，套襯線不會碰到中文 label（label 是另一個 Text，不吃這個 textStyle）
        textStyle = LocalTextStyle.current.copy(fontFamily = NumberFontFamily),
        shape = NutriFieldShape,
        colors = nutriFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 目前只有這三個模型能用，改下拉選單就不會再有「名稱打錯」這種輸入錯誤。 */
private val GEMINI_MODELS = listOf("gemini-3.6-flash", "gemini-3.7-flash", "gemini-3.5-flash-lite")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelField(value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        TextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_model)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            textStyle = LocalTextStyle.current.copy(fontFamily = NumberFontFamily),
            shape = NutriFieldShape,
            colors = nutriFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            GEMINI_MODELS.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model, fontFamily = NumberFontFamily) },
                    onClick = {
                        onChange(model)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun clipboardText(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    return manager?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
}
