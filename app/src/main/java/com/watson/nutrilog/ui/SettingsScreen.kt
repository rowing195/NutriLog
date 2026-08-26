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
import com.watson.nutrilog.ui.theme.NumberFontFamily

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
                .padding(horizontal = 22.dp, vertical = 8.dp),
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

            SectionTitle(stringResource(R.string.settings_data))
            Text(
                stringResource(R.string.export_csv_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StampButton(
                label = stringResource(R.string.export_csv),
                onClick = onExportCsv,
                modifier = Modifier.padding(top = 4.dp),
            )
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
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp, fontFamily = NumberFontFamily,
                    ),
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
