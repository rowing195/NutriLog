package com.watson.nutrilog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.NutriSettings
import com.watson.nutrilog.data.db.Meal
import com.watson.nutrilog.ui.theme.numeric
import kotlin.math.roundToInt

/**
 * 模型辨識結果的確認畫面。
 *
 * 這一步不能省：模型給的是估算值，直接寫進紀錄等於在使用者的飲食資料裡
 * 塞它自己編的數字。存進去之後仍然可以在今天的清單點進去逐項細改，
 * 所以這裡只做「要不要記錄」的取捨，不重複做一整套編輯欄位。
 *
 * 勾選框刻意是**方的**，跟餐別那種圓的單選分開 —— 形狀本身就在講
 * 「這裡可以複選」還是「只能挑一個」，不必等使用者點下去才發現。
 */
@Composable
fun ReviewScreen(
    state: AnalysisState,
    settings: NutriSettings,
    /** 這次失敗的是拍照還是文字，失敗面板要改對應的那一組設定。 */
    isPhoto: Boolean,
    onSettingsChange: (NutriSettings) -> Unit,
    meal: Meal,
    onMealChange: (Meal) -> Unit,
    onToggle: (Int) -> Unit,
    onMultiplierChange: (Int, Double) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onManualInstead: () -> Unit,
    onClose: () -> Unit,
) {
    // **「辨識失敗」對使用者來說有兩種**：真的出錯（[AnalysisState.Failed]），
    // 以及模型有回話但一項都沒認出來（[AnalysisState.Ready] 而 items 是空的）。
    // 後者從畫面上看起來一樣是白跑一趟，而且它正是換個模型最可能救得回來的情況 ——
    // 只認 Failed 的話，使用者會說「我這邊失敗沒有跳面板」。
    val looksFailed = state is AnalysisState.Failed ||
        (state is AnalysisState.Ready && state.items.isEmpty())
    // 失敗的當下直接把面板升上來，不用再多按一顆「換一家」——
    // 會走到這裡就表示這次已經沒救了，換設定是唯一還能做的事。
    // 收起來之後還是回得去：底下那顆章照舊在。
    var showSwitch by rememberSaveable(state) { mutableStateOf(looksFailed) }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.review_title),
                closeLabel = stringResource(R.string.cancel),
                onClose = onClose,
            )
        },
    ) { inner ->
        // 辨識中 → 結果／失敗 換的時候交叉淡換，不要整頁一幀就換掉。
        // contentKey 用「是哪一種畫面」而不是 state 本身：勾選、改份數都會換一個新的 Ready，
        // 拿 state 當 key 的話每按一下就重播一次淡入，而且清單自己記的狀態會被丟掉。
        AnimatedContent(
            targetState = state,
            contentKey = { it.reviewKind() },
            transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(150)) },
            label = "reviewState",
        ) { state ->
        when (state) {
            AnalysisState.Analyzing -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp))
                // 不用 CircularProgressIndicator：那顆轉圈是所有 app 都一樣的那顆，
                // 而且圓形在這個滿是規線的版面上很突兀。
                IndeterminateRule()
                Text(
                    stringResource(R.string.photo_analyzing),
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is AnalysisState.Failed -> FailureBody(
                hasSheet = showSwitch,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .padding(horizontal = 22.dp),
                reason = state.reason,
                onRetry = {
                    showSwitch = true
                    onRetry()
                },
                onSwitchProvider = { showSwitch = true },
                onOpenSettings = onOpenSettings,
                onManualInstead = onManualInstead,
            )

            is AnalysisState.Ready ->
                if (state.items.isEmpty()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(inner)
                            .padding(horizontal = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Text(
                            stringResource(R.string.review_nothing_found),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                        // 面板開著時它自己有一顆「重試」，這裡再擺一顆章就是兩顆
                        // 同時在畫面上。退成純文字的次要出路，收起面板就變回章。
                        if (showSwitch) {
                            Text(
                                stringResource(R.string.add_manual),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onManualInstead)
                                    .padding(vertical = 8.dp),
                            )
                        } else {
                            StampButton(
                                label = stringResource(R.string.switch_provider_title),
                                onClick = { showSwitch = true },
                            )
                            TextAction(stringResource(R.string.add_manual), onClick = onManualInstead)
                        }
                    }
                } else {
                    ReadyBody(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(inner),
                        state = state,
                        meal = meal,
                        onMealChange = onMealChange,
                        onToggle = onToggle,
                        onMultiplierChange = onMultiplierChange,
                        onSave = onSave,
                    )
                }
        }
        }
    }

        // 失敗才升上來。狀態一變（重試成功、或換了一次新的辨識）rememberSaveable 的
        // key 就跟著變，面板自己會回到「該不該開」的初始值，不必另外手動收。
        if (showSwitch && looksFailed) {
            ProviderSwitchSheet(
                settings = settings,
                isPhoto = isPhoto,
                onChange = onSettingsChange,
                onRetry = onRetry,
                onDismiss = { showSwitch = false },
            )
        }
    }
}

/** 確認畫面現在是哪一種：辨識中、失敗、沒認出東西、有結果。換種類才播轉場。 */
private fun AnalysisState.reviewKind(): Int = when (this) {
    AnalysisState.Analyzing -> 0
    is AnalysisState.Failed -> 1
    is AnalysisState.Ready -> if (items.isEmpty()) 2 else 3
}

@Composable
private fun ReadyBody(
    modifier: Modifier,
    state: AnalysisState.Ready,
    meal: Meal,
    onMealChange: (Meal) -> Unit,
    onToggle: (Int) -> Unit,
    onMultiplierChange: (Int, Double) -> Unit,
    onSave: () -> Unit,
) {
    val selectedCount = state.items.count { it.selected }
    // 正在打份數的是第幾項；null＝沒在打。一次只有一項，跟編輯表單的數字格一樣。
    // 不能拿 state 當 remember 的 key：每改一次份數 state 就換一個新物件，會把打到一半的字清掉。
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editText by remember { mutableStateOf("") }
    // 剛點進來的第一下按鍵直接覆蓋原值，同編輯表單
    var fresh by remember { mutableStateOf(false) }

    // **鍵盤升起來會蓋住靠下的那幾項**：清單跟著變矮，被擠出畫面的項目 LazyColumn 會
    // 直接從組合裡拿掉，掛在那一排身上的「捲進畫面」也就跟著被取消（實測過，完全沒捲）。
    // 所以在點下去的當下先記住那一項的下緣（那時它還看得到），等鍵盤排好版再由清單
    // 自己捲過去超出的那一段。
    val listState = rememberLazyListState()
    var revealBottom by remember { mutableStateOf<Int?>(null) }
    val keypad = updateTransition(editingIndex != null, label = "keypad")
    LaunchedEffect(editingIndex) {
        val bottom = revealBottom ?: return@LaunchedEffect
        revealBottom = null
        // 鍵盤是一路長上來的（見 KeypadSlot），要等它完全升好，清單剩多高才是真的；
        // 只等一幀量到的是半途的高度，會捲得不夠。
        snapshotFlow { keypad.currentState && !keypad.isRunning }.first { it }
        val overflow = bottom - listState.layoutInfo.viewportEndOffset
        if (overflow > 0) listState.animateScrollBy(overflow.toFloat())
    }

    /** 離開份數格：這時才換算那一項的熱量。 */
    fun commit() {
        val index = editingIndex ?: return
        editingIndex = null
        state.items.getOrNull(index)?.let { onMultiplierChange(index, typedPortion(editText, it.multiplier)) }
    }

    // 鍵盤開著時返回鍵先收鍵盤，而不是直接把整個確認畫面關掉
    BackHandler(enabled = editingIndex != null) { commit() }

    Column(modifier) {
        LazyColumn(
            state = listState,
            // 點空白處也算離開。列本身（勾選、份數）會先把點擊消費掉，傳得上來的只剩空白。
            modifier = Modifier.weight(1f).dismissKeyboardOnTap { commit() },
            contentPadding = PaddingValues(horizontal = 22.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.review_hint),
                    // 斜體：這是旁白，不是要填的東西
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
                )
            }
            item { SectionLabel(stringResource(R.string.review_meal)) }
            item { MealPicker(meal, Modifier.padding(top = 2.dp, bottom = 10.dp), onSelect = onMealChange) }
            itemsIndexed(state.items, key = { index, _ -> "item-$index" }) { index, item ->
                ItemRow(
                    item = item,
                    onToggle = {
                        commit()
                        onToggle(index)
                    },
                    onMultiplierChange = { mult ->
                        // 步進鍵已經把打到一半的字算進去了（見 PortionMultiplierBar），
                        // 這一項就不必再 commit 一次；別項還開著的話要先收掉它。
                        if (editingIndex == index) editingIndex = null else commit()
                        onMultiplierChange(index, mult)
                    },
                    typing = if (editingIndex == index) editText else null,
                    onTapValue = {
                        if (editingIndex != index) {
                            commit()
                            revealBottom = listState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.key == "item-$index" }
                                ?.let { it.offset + it.size }
                            editingIndex = index
                            editText = formatMultiplierValue(item.multiplier)
                        }
                        fresh = true
                    },
                )
            }
            item { Box(Modifier.padding(bottom = 16.dp)) }
        }
        KeypadSlot(
            open = keypad,
            keypad = {
                NumberKeypad(
                    label = stringResource(R.string.portion_multiplier_label),
                    onKey = { key ->
                        // 退場途中按到的鍵不算：那時已經沒有正在打的那一項了
                        if (editingIndex == null) return@NumberKeypad
                        val next = applyKey(editText, key, fresh)
                        if (acceptsPortionText(next)) editText = next
                        fresh = false
                    },
                    onDone = { commit() },
                )
            },
            closed = {
                Column(
                    Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 22.dp, vertical = 12.dp)
                ) {
                    StampButton(
                        label = stringResource(R.string.photo_save_selected, selectedCount),
                        enabled = selectedCount > 0,
                        onClick = onSave,
                    )
                }
            },
        )
    }
}

@Composable
private fun ItemRow(
    item: AnalysisItem,
    onToggle: () -> Unit,
    onMultiplierChange: (Double) -> Unit,
    typing: String?,
    onTapValue: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val food = item.food
    Column(Modifier.fillMaxWidth()) {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SquareCheck(item.selected)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    food.name,
                    style = MaterialTheme.typography.titleMedium,
                    // 沒勾的那幾項壓淡，勾選的狀態才看得出是兩群東西
                    color = feedbackColor(if (item.selected) scheme.onSurface else scheme.onSurfaceVariant, "reviewName"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    withNumerals(
                        detailLine(food.servingText, food.proteinG, food.fatG, food.carbsG, food.waterMl)
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                    color = scheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 把握度是模型自己講的，用襯線數字排在旁邊當註記，不做成進度條 ——
                // 進度條會讓它看起來像個可以調整的東西。
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        stringResource(R.string.photo_confidence_label),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                        color = scheme.outline,
                    )
                    Text(
                        (food.confidence * 100).roundToInt().toString() + "%",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp).numeric(),
                        color = scheme.outline,
                    )
                }
            }
            Text(
                food.calories.fmtInt(),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp).numeric(),
                color = feedbackColor(if (item.selected) scheme.onSurface else scheme.outline, "reviewKcal"),
            )
        }
        // **取消勾選時份數這一排照樣留著**，只是變淡、點不動。以前是整排收掉，
        // 那一項一下子矮一截、底下的項目全部往上跳 —— 連續取消好幾項時，
        // 下一下很容易點到別項。勾回來就恢復，調好的份數也還在。
        PortionMultiplierBar(
            multiplier = item.multiplier,
            onMultiplierChange = onMultiplierChange,
            typing = typing,
            onTapValue = onTapValue,
            compact = true,
            enabled = item.selected,
            modifier = Modifier.padding(start = 32.dp, bottom = 10.dp),
        )
    }
}

@Composable
private fun FailureBody(
    modifier: Modifier,
    /**
     * 底下那張「換一家再試」的面板開著沒有。開著的時候這裡**不擺章** ——
     * 面板自己就有一顆重試，兩顆一模一樣的章同時在畫面上，使用者得先想一下
     * 它們是不是同一件事（而且這套版面的規矩是一個畫面只有一顆章）。
     */
    hasSheet: Boolean,
    reason: String,
    onRetry: () -> Unit,
    onSwitchProvider: () -> Unit,
    onOpenSettings: () -> Unit,
    onManualInstead: () -> Unit,
) {
    // 沒設 key 是最常見的失敗，而且解法完全不同（去設定，不是重試），
    // 所以獨立成一種畫面而不是丟一段錯誤字串了事。
    // 前綴比對而不是相等：後面接的是缺哪一家的 key（見 reportMissingApiKey）
    val missingKey = reason.startsWith(NutriViewModel.NO_API_KEY)
    val missingProvider = reason.removePrefix(NutriViewModel.NO_API_KEY).removePrefix(":")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(
            if (missingKey) stringResource(R.string.photo_no_key, missingProvider) else reason,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 24.dp),
        )
        if (!hasSheet) {
            StampButton(
                label = stringResource(if (missingKey) R.string.go_to_settings else R.string.retry),
                onClick = if (missingKey) onOpenSettings else onRetry,
            )
            TextAction(stringResource(R.string.switch_provider_title), onClick = onSwitchProvider)
        }
        // 次要出路用純文字，不要再放一顆框 —— 一個畫面只有一顆印章
        Text(
            stringResource(R.string.add_manual),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onManualInstead)
                .padding(vertical = 8.dp),
        )
    }
}
