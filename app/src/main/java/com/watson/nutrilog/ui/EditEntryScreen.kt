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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watson.nutrilog.R
import com.watson.nutrilog.ui.theme.numeric
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

    // 份數縮放基準值與倍率：若草稿本身有儲存倍率（例如 2.0x），則自動還原出 1.0x 基準草稿
    var multiplier by remember(draft.id) { mutableStateOf(if (draft.portionMultiplier > 0) draft.portionMultiplier else 1.0) }
    var baseDraft by remember(draft.id) {
        mutableStateOf(
            if (draft.portionMultiplier > 0 && draft.portionMultiplier != 1.0) {
                draft.deriveBase(draft.portionMultiplier)
            } else {
                draft
            }
        )
    }

    val scheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current

    /**
     * 點數字格：先把文字欄位的焦點放掉，系統鍵盤才會收起來。
     *
     * 少了這一行，系統鍵盤和自製數字鍵盤會同時佔著畫面底部 —— 使用者以為自己
     * 在按數字，其實按在系統鍵盤上，數字進了上一個聚焦的文字欄位。
     * 反方向（點文字欄位收掉數字鍵盤）走的是 NutriTextField 的 onFocusChanged。
     */
    fun focusNumber(field: NumField) {
        focusManager.clearFocus()
        focused = field
        fresh = true
    }

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
        val updated = when (field) {
            NumField.CALORIES -> draft.copy(calories = raw)
            NumField.PROTEIN -> draft.copy(protein = raw)
            NumField.FAT -> draft.copy(fat = raw)
            NumField.CARBS -> draft.copy(carbs = raw)
            NumField.SUGAR -> draft.copy(sugar = raw)
            NumField.SODIUM -> draft.copy(sodium = raw)
            NumField.FIBER -> draft.copy(fiber = raw)
            NumField.SATFAT -> draft.copy(satFat = raw)
        }
        if (multiplier == 1.0) {
            baseDraft = updated
        } else {
            baseDraft = updated.deriveBase(multiplier)
        }
        onDraftChange(updated)
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(
                    if (draft.id == null) R.string.entry_new_title else R.string.entry_edit_title
                ),
                closeLabel = stringResource(R.string.cancel),
                onClose = onClose,
                // 新增中的草稿還沒進資料庫，沒有東西可刪。刪除是唯一用暖紅的按鍵，
                // 框刻意畫淡：要點得到，但不該是這個畫面上最顯眼的東西。
                trailing = if (onDelete == null) null else {
                    {
                        CircleIconButton(
                            onClick = { confirmDelete = true },
                            borderColor = scheme.outlineVariant,
                            borderWidth = 1.dp,
                        ) { TrashMark(scheme.error) }
                    }
                },
            )
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
                    // 不能存的時候整顆退成外框章，理由用 helper 講一句就好，不彈東西。
                    StampButton(
                        label = stringResource(R.string.save),
                        enabled = draft.isValid,
                        onClick = onSave,
                        helper = if (draft.isValid) null else stringResource(R.string.entry_name_required),
                    )
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
            NutriTextField(
                value = draft.name,
                onValueChange = {
                    onDraftChange(draft.copy(name = it))
                    baseDraft = baseDraft.copy(name = it)
                },
                label = stringResource(R.string.entry_name_label),
                placeholder = stringResource(R.string.entry_name),
                // 點文字欄位就把自製數字鍵盤收起來，不然系統鍵盤會疊在它上面
                onFocusChanged = { if (it) focused = null },
                modifier = Modifier.padding(top = 18.dp),
            )

            MealPicker(draft.meal, Modifier.padding(top = 8.dp, bottom = 8.dp)) {
                onDraftChange(draft.copy(meal = it))
                baseDraft = baseDraft.copy(meal = it)
            }

            NutriTextField(
                value = draft.servingText,
                onValueChange = {
                    onDraftChange(draft.copy(servingText = it))
                    if (multiplier == 1.0) {
                        baseDraft = baseDraft.copy(servingText = it)
                    } else {
                        baseDraft = baseDraft.copy(servingText = scaleServingText(it, 1.0 / multiplier))
                    }
                },
                label = stringResource(R.string.entry_serving),
                placeholder = stringResource(R.string.entry_serving_hint),
                onFocusChanged = { if (it) focused = null },
            )

            PortionMultiplierBar(
                multiplier = multiplier,
                onMultiplierChange = { newMult ->
                    multiplier = newMult
                    onDraftChange(draft.scaleFromBase(baseDraft, newMult))
                },
                modifier = Modifier.padding(top = 14.dp),
            )

            Hairline(Modifier.padding(top = 14.dp))

            // 熱量滿版一格、三大營養素橫排三格，不是四格等大的 2×2。
            // 等大等於說這四個數字一樣重要，但熱量才是這一頁的主角 ——
            // 而且這個階層跟今日頁（大數字 ＋ 三欄）是同一個。
            NumberCell(
                spec = NumberCellSpec(
                    NumField.CALORIES,
                    stringResource(R.string.nutrient_calories),
                    stringResource(R.string.unit_kcal),
                    NutrientColors.Calories,
                ),
                value = valueOf(NumField.CALORIES),
                active = focused == NumField.CALORIES,
                big = true,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                onClick = { focusNumber(NumField.CALORIES) },
            )
            Row(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    NumberCellSpec(NumField.PROTEIN, stringResource(R.string.nutrient_protein), "g", NutrientColors.Protein),
                    NumberCellSpec(NumField.FAT, stringResource(R.string.nutrient_fat), "g", NutrientColors.Fat),
                    NumberCellSpec(NumField.CARBS, stringResource(R.string.nutrient_carbs), "g", NutrientColors.Carbs),
                ).forEach { spec ->
                    NumberCell(
                        spec = spec,
                        value = valueOf(spec.field),
                        active = focused == spec.field,
                        big = false,
                        modifier = Modifier.weight(1f),
                        onClick = { focusNumber(spec.field) },
                    )
                }
            }

            MacroCrossCheck(draft)

            Row(
                Modifier
                    .clickable { showAdvanced = !showAdvanced }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    stringResource(R.string.entry_advanced),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                // 同一個 chevron 轉九十度當展開記號，不另外拿一組上下箭頭圖示
                Box(Modifier.rotate(if (showAdvanced) 90f else -90f)) {
                    ChevronMark(scheme.outline, pointsLeft = true, size = 14.dp)
                }
            }

            if (showAdvanced) {
                // 進階四項沒有專屬色也不是主角，維持 2×2 的等大方格就好 ——
                // 核心那四個才需要「熱量比較大」的階層。
                Column(
                    Modifier.padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        NumberCellSpec(NumField.SUGAR, stringResource(R.string.nutrient_sugar), "g", null),
                        NumberCellSpec(NumField.SODIUM, stringResource(R.string.nutrient_sodium), "mg", null),
                        NumberCellSpec(NumField.FIBER, stringResource(R.string.nutrient_fiber), "g", null),
                        NumberCellSpec(NumField.SATFAT, stringResource(R.string.nutrient_satfat), "g", null),
                    ).chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { spec ->
                                NumberCell(
                                    spec = spec,
                                    value = valueOf(spec.field),
                                    active = focused == spec.field,
                                    big = false,
                                    modifier = Modifier.weight(1f),
                                    onClick = { focusNumber(spec.field) },
                                )
                            }
                        }
                    }
                }
            }

            Box(Modifier.height(16.dp))
        }
    }

    if (confirmDelete && onDelete != null) {
        NutriDialog(
            title = stringResource(R.string.delete_title),
            message = stringResource(R.string.delete_message, draft.name),
            confirmLabel = stringResource(R.string.delete),
            cancelLabel = stringResource(R.string.cancel),
            destructive = true,
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
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
private fun NumberCell(
    spec: NumberCellSpec,
    value: String,
    active: Boolean,
    big: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // 跟輸入框同一套語言：外框 ＋ 一條比其他三邊重的底規線，聚焦時外框轉墨色、
    // 底線轉朱紅。這一格本來就是個欄位，只是它的鍵盤是自己畫的那副。
    Box(
        modifier
            .clip(NutriFieldShape)
            .background(if (active) scheme.surfaceContainerLowest else scheme.surfaceContainerLow)
            .border(
                1.dp,
                if (active) scheme.onSurface else NutrientColors.FieldBorder,
                NutriFieldShape,
            )
            .clickable(onClick = onClick)
    ) {
    Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            Modifier.padding(top = 2.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                // 空值顯示破折號而不是 0 ——「沒填」和「真的是 0」必須分得開
                value.ifBlank { "—" },
                style = (if (big) {
                    MaterialTheme.typography.displaySmall
                } else {
                    MaterialTheme.typography.headlineSmall
                }).numeric().let {
                    // 破折號照數字的字級畫會變成一條很長的橫線，看起來像畫壞的分隔線。
                    // 只縮字級、不動 lineHeight —— 這樣填進數字時格子的高度不會跳。
                    if (value.isBlank()) it.copy(fontSize = it.fontSize * 0.55f) else it
                },
                color = when {
                    value.isBlank() -> scheme.outline.copy(alpha = 0.6f)
                    active -> NutrientColors.Accent
                    else -> scheme.onSurface
                },
                maxLines = 1,
            )
            Text(
                spec.unit,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (big) 13.sp else 11.sp,
                    fontStyle = FontStyle.Italic,
                ).numeric(),
                color = scheme.outline,
                modifier = Modifier.padding(bottom = if (big) 6.dp else 3.dp),
            )
        }
    }
        // 底規線。貼著外框底部畫滿整格寬，才做得出「只有下面那條比較重」——
        // 用 border 做不到單邊加粗。
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(if (active) 3.dp else 2.dp)
                .background(if (active) NutrientColors.Accent else scheme.onSurface)
        )
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
    val color = if (mismatch) NutrientColors.Over else scheme.outline
    Row(
        Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Text(
            if (mismatch) {
                stringResource(R.string.entry_kcal_mismatch, computed.fmtInt(), stated.fmtInt())
            } else {
                stringResource(R.string.entry_kcal_check, computed.fmtInt())
            },
            // 斜體：這是旁白，不是表單的一部分
            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
            color = color,
        )
    }
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
            .background(scheme.surfaceVariant)
            .navigationBarsPadding()
    ) {
        // 鍵盤是浮在表單上的另一層，用 2px 重規線把它跟表單切開，
        // 不是用細線 —— 細線在這裡看起來像表單自己的一列。
        Rule()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 9.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.keypad_editing, label),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            PillButton(stringResource(R.string.keypad_done), onClick = onDone)
        }
        // 圓的，因為整頁其他東西都是方的與線性的 —— 一片全是方格的鍵盤在紙感
        // 版面上會像試算表。三級：數字有圈、小數點圈變淡（還能按，但不是主角）、
        // 刪除完全沒有圈，不看標籤也分得出哪個是破壞性的。
        Column(
            Modifier.padding(start = 22.dp, end = 22.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KEYS.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { key ->
                        RoundKey(
                            onClick = { onKey(key) },
                            modifier = Modifier.weight(1f),
                            ringed = key != BACKSPACE,
                            dimmed = key == ".",
                        ) {
                            if (key == BACKSPACE) {
                                BackspaceMark(scheme.onSurfaceVariant)
                            } else {
                                Text(
                                    key,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontSize = 28.sp,
                                    ).numeric(),
                                    color = if (key == ".") scheme.onSurfaceVariant else scheme.onSurface,
                                )
                            }
                        }
                    }
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
