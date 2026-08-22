package com.watson.nutrilog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watson.nutrilog.R
import com.watson.nutrilog.ui.theme.NutrientColors

/** 表單裡正在填的那個數字欄位。null 代表沒有，鍵盤收起來、儲存鈕露出來。 */
private enum class NumField(val unitRes: Int?, val unitText: String) {
    CALORIES(R.string.unit_kcal, ""),
    PROTEIN(null, "g"),
    FAT(null, "g"),
    CARBS(null, "g"),
    SUGAR(null, "g"),
    SODIUM(null, "mg"),
    FIBER(null, "g"),
    SATFAT(null, "g"),
}

/**
 * 共用的輸入表單：手動、條碼帶出、拍照辨識三條路最後都匯流到這裡，
 * 讓使用者在入庫前有最後一次確認與修正的機會。
 *
 * 兩個和前一版不一樣的地方，都是為了同一件事 —— 讓「改四個數字」不再是苦差事：
 *
 * 1. 核心四項排成 **2×2** 而不是四個直排欄位。彼此看得見，才對得出
 *    「這組數字合不合理」；直排時第四個欄位往往已經捲出畫面。
 * 2. 數字用**自己畫的鍵盤**，不叫系統鍵盤。系統鍵盤會蓋住儲存鈕 ——
 *    而被蓋住的地方點下去不是沒反應，是把數字打進上一個聚焦的欄位，
 *    事故現場很難看出來（專案 CLAUDE.md 記的就是這件事）。
 */
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
    var focused by remember { mutableStateOf<NumField?>(null) }
    // 聚焦後第一次按鍵直接覆蓋掉原值：會點這個格子，通常就是因為不同意
    // 現在的數字（多半是 AI 估的），接在後面幾乎永遠是錯的。
    var fresh by remember { mutableStateOf(false) }

    val scheme = MaterialTheme.colorScheme

    // 鍵盤開著時返回鍵先收鍵盤，而不是直接把整張表單關掉
    if (focused != null) {
        BackHandler { focused = null }
    }

    fun valueOf(field: NumField): String = when (field) {
        NumField.CALORIES -> draft.calories
        NumField.PROTEIN -> draft.protein
        NumField.FAT -> draft.fat
        NumField.CARBS -> draft.carbs
        NumField.SUGAR -> draft.sugar
        NumField.SODIUM -> draft.sodium
        NumField.FIBER -> draft.fiber
        NumField.SATFAT -> draft.satFat
    }

    fun setValue(field: NumField, raw: String) {
        onDraftChange(
            when (field) {
                NumField.CALORIES -> draft.copy(calories = raw)
                NumField.PROTEIN -> draft.copy(protein = raw)
                NumField.FAT -> draft.copy(fat = raw)
                NumField.CARBS -> draft.copy(carbs = raw)
                NumField.SUGAR -> draft.copy(sugar = raw)
                NumField.SODIUM -> draft.copy(sodium = raw)
                NumField.FIBER -> draft.copy(fiber = raw)
                NumField.SATFAT -> draft.copy(satFat = raw)
            }
        )
    }

    Scaffold(
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    // 自己拼的 topBar 不像 M3 TopAppBar 會自動避開狀態列
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.cancel), style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    stringResource(
                        if (draft.id == null) R.string.entry_new_title else R.string.entry_edit_title
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                // 新增中的草稿還沒進資料庫，沒有東西可刪 —— 位子留著，
                // 不然標題會因為有沒有刪除鈕而左右跳動
                if (onDelete != null) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            Icons.Default.Delete,
                            stringResource(R.string.delete),
                            Modifier.size(18.dp),
                            tint = scheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Box(Modifier.size(48.dp))
                }
            }
        },
        bottomBar = {
            if (focused != null) {
                NumberKeypad(
                    label = fieldLabel(focused!!),
                    onKey = { key ->
                        val field = focused!!
                        setValue(field, applyKey(valueOf(field), key, fresh))
                        fresh = false
                    },
                    onDone = { focused = null },
                )
            } else {
                Column(
                    Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 22.dp, vertical = 12.dp)
                ) {
                    SaveButton(enabled = draft.isValid, onClick = onSave)
                    if (!draft.isValid) {
                        Text(
                            stringResource(R.string.entry_name_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                // 名稱與份量還是走系統鍵盤（那是文字），所以這行仍然要留
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
        ) {
            FieldLabel(stringResource(R.string.entry_name_label))
            PlainTextField(
                value = draft.name,
                onValueChange = { onDraftChange(draft.copy(name = it)) },
                placeholder = stringResource(R.string.entry_name),
                textStyle = MaterialTheme.typography.titleLarge,
                onFocus = { focused = null },
            )
            Hairline(Modifier.padding(top = 12.dp))

            MealPicker(draft.meal, Modifier.padding(vertical = 14.dp)) {
                onDraftChange(draft.copy(meal = it))
            }

            FieldLabel(stringResource(R.string.entry_serving))
            PlainTextField(
                value = draft.servingText,
                onValueChange = { onDraftChange(draft.copy(servingText = it)) },
                placeholder = stringResource(R.string.entry_serving_hint),
                textStyle = MaterialTheme.typography.bodyLarge,
                onFocus = { focused = null },
            )
            Hairline(Modifier.padding(top = 12.dp))

            NumberGrid(
                cells = listOf(
                    NumberCellSpec(NumField.CALORIES, stringResource(R.string.nutrient_calories), stringResource(R.string.unit_kcal), NutrientColors.Calories),
                    NumberCellSpec(NumField.PROTEIN, stringResource(R.string.nutrient_protein), "g", NutrientColors.Protein),
                    NumberCellSpec(NumField.FAT, stringResource(R.string.nutrient_fat), "g", NutrientColors.Fat),
                    NumberCellSpec(NumField.CARBS, stringResource(R.string.nutrient_carbs), "g", NutrientColors.Carbs),
                ),
                valueOf = ::valueOf,
                focused = focused,
                big = true,
                modifier = Modifier.padding(top = 14.dp),
                onFocus = { focused = it; fresh = true },
            )

            MacroCrossCheck(draft)

            TextButton(
                onClick = { showAdvanced = !showAdvanced },
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.entry_advanced), style = MaterialTheme.typography.bodyMedium)
                Icon(
                    if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }

            if (showAdvanced) {
                NumberGrid(
                    cells = listOf(
                        NumberCellSpec(NumField.SUGAR, stringResource(R.string.nutrient_sugar), "g", null),
                        NumberCellSpec(NumField.SODIUM, stringResource(R.string.nutrient_sodium), "mg", null),
                        NumberCellSpec(NumField.FIBER, stringResource(R.string.nutrient_fiber), "g", null),
                        NumberCellSpec(NumField.SATFAT, stringResource(R.string.nutrient_satfat), "g", null),
                    ),
                    valueOf = ::valueOf,
                    focused = focused,
                    big = false,
                    modifier = Modifier.padding(bottom = 8.dp),
                    onFocus = { focused = it; fresh = true },
                )
            }

            Box(Modifier.height(16.dp))
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message, draft.name)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.delete), color = NutrientColors.Over)
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

@Composable
private fun SaveButton(enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        stringResource(R.string.save),
        style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 1.sp),
        color = if (enabled) scheme.inverseOnSurface else scheme.outline,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(PillShape)
            .background(if (enabled) scheme.inverseSurface else scheme.surfaceContainerHigh)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
    )
}

@Composable
private fun FieldLabel(text: String) {
    SectionLabel(text, Modifier.padding(top = 10.dp, bottom = 4.dp))
}

/**
 * 沒有外框的輸入欄。
 *
 * 用 [BasicTextField] 而不是 OutlinedTextField：這套版面用細線分隔，
 * M3 那圈膠囊外框在這裡是唯一一個有邊框的東西，看起來像貼錯的元件。
 */
@Composable
private fun PlainTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: androidx.compose.ui.text.TextStyle,
    onFocus: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box {
        if (value.isEmpty()) {
            Text(placeholder, style = textStyle, color = scheme.outline)
        }
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it) },
            textStyle = textStyle.merge(LocalTextStyle.current.copy(color = scheme.onSurface)),
            singleLine = true,
            cursorBrush = SolidColor(scheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                // 點文字欄位就把數字鍵盤收起來，不然兩套鍵盤會疊在一起
                .clickable(onClick = onFocus),
        )
    }
}

private data class NumberCellSpec(
    val field: NumField,
    val label: String,
    val unit: String,
    /** 對應的營養素色；進階四項沒有專屬色就給 null，不畫圓點 */
    val dot: Color?,
)

@Composable
private fun NumberGrid(
    cells: List<NumberCellSpec>,
    valueOf: (NumField) -> String,
    focused: NumField?,
    big: Boolean,
    modifier: Modifier = Modifier,
    onFocus: (NumField) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { spec ->
                    NumberCell(
                        spec = spec,
                        value = valueOf(spec.field),
                        active = focused == spec.field,
                        big = big,
                        modifier = Modifier.weight(1f),
                        onClick = { onFocus(spec.field) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberCell(
    spec: NumberCellSpec,
    value: String,
    active: Boolean,
    big: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .border(1.dp, if (active) scheme.primary else scheme.outlineVariant, shape)
            .background(if (active) scheme.surfaceContainerLow else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            spec.dot?.let {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(it)
                )
            }
            Text(
                spec.label,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                // 空值顯示破折號而不是 0 ——「沒填」和「真的是 0」必須分得開
                value.ifBlank { "—" },
                style = if (big) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp, lineHeight = 26.sp)
                },
                color = if (value.isBlank()) scheme.outline.copy(alpha = 0.5f) else scheme.onSurface,
                maxLines = 1,
            )
            Text(
                spec.unit,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                color = scheme.outline,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

/**
 * 三大營養素換算回熱量，跟填的熱量對照。
 *
 * AI 估的數字最常在這裡出錯 —— 它可能給了合理的熱量卻配上兜不攏的三大營養素。
 * 差超過兩成就講一聲；不擋儲存，因為真實食物本來就有誤差，這只是提醒。
 */
@Composable
private fun MacroCrossCheck(draft: EntryDraft) {
    val scheme = MaterialTheme.colorScheme
    val p = draft.protein.toDoubleOrNull() ?: 0.0
    val f = draft.fat.toDoubleOrNull() ?: 0.0
    val c = draft.carbs.toDoubleOrNull() ?: 0.0
    val stated = draft.calories.toDoubleOrNull() ?: 0.0
    val computed = p * 4 + f * 9 + c * 4

    if (computed <= 0.0) return

    val mismatch = stated > 0 && kotlin.math.abs(computed - stated) / stated > 0.2
    Text(
        if (mismatch) {
            stringResource(R.string.entry_kcal_mismatch, computed.fmtInt(), stated.fmtInt())
        } else {
            stringResource(R.string.entry_kcal_check, computed.fmtInt())
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (mismatch) NutrientColors.Over else scheme.outline,
        modifier = Modifier.padding(top = 10.dp),
    )
}

/**
 * 自己畫的數字鍵盤。
 *
 * 只有十二顆鍵，所以比系統鍵盤矮得多 —— 表單本身還看得見，
 * 填完一格直接點下一格，不必為了看清楚而先收鍵盤。
 */
@Composable
private fun NumberKeypad(label: String, onKey: (String) -> Unit, onDone: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .navigationBarsPadding()
    ) {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.keypad_editing, label),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.keypad_done),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDone)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        KEYS.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    Text(
                        key,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
                        textAlign = TextAlign.Center,
                        color = scheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onKey(key) }
                            .padding(vertical = 13.dp),
                    )
                }
            }
        }
    }
}

/**
 * 把一次按鍵套到目前的字串上。
 *
 * 這裡刻意還是操作**字串**而不是數字：打到一半的 "12." 不是合法的 Double，
 * 每次按鍵都轉一次會把中間狀態吃掉。真正的解析留到儲存那一刻。
 */
private fun applyKey(current: String, key: String, fresh: Boolean): String {
    if (key == BACKSPACE) return current.dropLast(1)
    val base = if (fresh) "" else current
    return when {
        key == "." -> if (base.contains('.')) base else if (base.isEmpty()) "0." else base + "."
        // 開頭的 0 沒有意義，除非後面接小數點
        base == "0" -> key
        else -> base + key
    }
}

@Composable
private fun fieldLabel(field: NumField): String = stringResource(
    when (field) {
        NumField.CALORIES -> R.string.nutrient_calories
        NumField.PROTEIN -> R.string.nutrient_protein
        NumField.FAT -> R.string.nutrient_fat
        NumField.CARBS -> R.string.nutrient_carbs
        NumField.SUGAR -> R.string.nutrient_sugar
        NumField.SODIUM -> R.string.nutrient_sodium
        NumField.FIBER -> R.string.nutrient_fiber
        NumField.SATFAT -> R.string.nutrient_satfat
    }
)

private const val BACKSPACE = "⌫"
private val KEYS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", BACKSPACE)
