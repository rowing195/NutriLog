package com.watson.nutrilog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.watson.nutrilog.R
import com.watson.nutrilog.data.db.Meal

/**
 * 共用的輸入表單：手動、條碼帶出、拍照辨識三條路最後都匯流到這裡，
 * 讓使用者在入庫前有最後一次確認與修正的機會。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEntryScreen(
    draft: EntryDraft,
    showExtendedByDefault: Boolean,
    onDraftChange: (EntryDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onClose: () -> Unit,
) {
    var showAdvanced by remember { mutableStateOf(showExtendedByDefault) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (draft.id == null) R.string.entry_new_title else R.string.entry_edit_title
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, stringResource(R.string.cancel))
                    }
                },
                actions = {
                    if (onDelete != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete))
                        }
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                // 沒有這一行，鍵盤彈出來就會蓋住「儲存」——
                // 而且蓋住的部分點下去是打在鍵盤上，等於把數字打進上一個欄位。
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onDraftChange(draft.copy(name = it)) },
                label = { Text(stringResource(R.string.entry_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            MealPicker(draft.meal) { onDraftChange(draft.copy(meal = it)) }

            OutlinedTextField(
                value = draft.servingText,
                onValueChange = { onDraftChange(draft.copy(servingText = it)) },
                label = { Text(stringResource(R.string.entry_serving)) },
                placeholder = { Text(stringResource(R.string.entry_serving_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            NumberField(
                label = stringResource(R.string.nutrient_calories) + "（" + stringResource(R.string.unit_kcal) + "）",
                value = draft.calories,
            ) { onDraftChange(draft.copy(calories = it)) }
            NumberField(
                label = stringResource(R.string.nutrient_protein) + "（g）",
                value = draft.protein,
            ) { onDraftChange(draft.copy(protein = it)) }
            NumberField(
                label = stringResource(R.string.nutrient_fat) + "（g）",
                value = draft.fat,
            ) { onDraftChange(draft.copy(fat = it)) }
            NumberField(
                label = stringResource(R.string.nutrient_carbs) + "（g）",
                value = draft.carbs,
            ) { onDraftChange(draft.copy(carbs = it)) }

            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(stringResource(R.string.entry_advanced))
                Icon(
                    if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                )
            }

            if (showAdvanced) {
                NumberField(
                    label = stringResource(R.string.nutrient_sugar) + "（g）",
                    value = draft.sugar,
                ) { onDraftChange(draft.copy(sugar = it)) }
                NumberField(
                    label = stringResource(R.string.nutrient_sodium) + "（mg）",
                    value = draft.sodium,
                ) { onDraftChange(draft.copy(sodium = it)) }
                NumberField(
                    label = stringResource(R.string.nutrient_fiber) + "（g）",
                    value = draft.fiber,
                ) { onDraftChange(draft.copy(fiber = it)) }
                NumberField(
                    label = stringResource(R.string.nutrient_satfat) + "（g）",
                    value = draft.satFat,
                ) { onDraftChange(draft.copy(satFat = it)) }
            }

            Button(
                onClick = onSave,
                enabled = draft.isValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save))
            }
            if (!draft.isValid) {
                Text(
                    stringResource(R.string.entry_name_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message, draft.name)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealPicker(selected: Meal, onSelect: (Meal) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Meal.entries.forEach { meal ->
            FilterChip(
                selected = meal == selected,
                onClick = { onSelect(meal) },
                label = { Text(meal.label()) },
            )
        }
    }
}

/**
 * 只收數字的欄位。
 *
 * 這裡刻意**不**在每次按鍵時把字串轉成數字再轉回來 —— 那樣會讓
 * 「12.」這種打到一半的中間狀態被吃掉，游標也會亂跳。只擋掉明顯不是數字的字元，
 * 真正的解析留到儲存那一刻。
 */
@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onChange(raw.filter { it.isDigit() || it == '.' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}
