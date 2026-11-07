package com.watson.nutrilog.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.db.MissingField
import com.watson.nutrilog.data.db.drinkVolumeMl
import com.watson.nutrilog.ui.theme.numeric
import kotlinx.coroutines.launch

/**
 * 資料健檢：列出缺鈉、缺水量的品項，就地補上。設定「本地資料管理」底下的第三層。
 *
 * 一列是一個品項（名稱 + 份量），補一次就填進那個品項**所有缺的紀錄**，已經有數字的不動。
 * 逐筆列出來的話，同一杯珍奶記過三十次就是三十列一樣的東西。
 */
@Composable
fun DataCheckScreen(
    /** null 是還沒查到：那時候不畫「沒有缺⋯」，不然一打開會先閃一下說沒有。 */
    missingSodium: List<MissingField>?,
    missingWater: List<MissingField>?,
    message: String?,
    onFill: (DataCheckField, MissingField, Double) -> Unit,
    onBack: () -> Unit,
) {
    val pager = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    // 同時只展開一列，不然畫面上會留著一排打到一半的輸入框
    var expanded by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pager.settledPage) { expanded = null }

    Scaffold(
        modifier = Modifier.dismissKeyboardOnTap(),
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.data_check_title),
                closeLabel = stringResource(R.string.settings_back),
                onClose = onBack,
            )
        },
    ) { inner ->
        // 包一層要 fillMaxWidth：NutriTabs 靠 IntrinsicSize 收寬度（見 SearchScreen 同一段）
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding()
                .enterSlide(),
        ) {
            NutriTabs(
                labels = listOf(
                    stringResource(R.string.data_check_tab_sodium, missingSodium.orEmpty().size),
                    stringResource(R.string.data_check_tab_water, missingWater.orEmpty().size),
                ),
                selectedIndex = pager.currentPage,
                onSelect = { scope.launch { pager.animateScrollToPage(it) } },
            )
            Hairline()
            Column(Modifier.padding(horizontal = 22.dp)) {
                val shownMessage = rememberLastNonNull(message)
                Reveal(message != null) {
                    Text(
                        withNumerals(shownMessage.orEmpty()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
            ) { page ->
                val field = if (page == 0) DataCheckField.SODIUM else DataCheckField.WATER
                val items = (if (page == 0) missingSodium else missingWater) ?: return@HorizontalPager
                // 補完最後一筆時，清單和空狀態那句話交叉淡換，不是一幀換掉
                AnimatedContent(
                    targetState = items.isEmpty(),
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                    label = "dataCheckEmpty",
                ) { empty ->
                    if (empty) {
                        Text(
                            stringResource(
                                if (field == DataCheckField.SODIUM) R.string.data_check_empty_sodium
                                else R.string.data_check_empty_water
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 24.dp),
                        )
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
                        ) {
                            items(items, key = { it.name + "|" + it.servingText }) { item ->
                                val key = field.name + "|" + item.name + "|" + item.servingText
                                // 補上之後那一列淡出、底下的滑上來
                                Column(Modifier.animateItem(fadeInSpec = rowFade, placementSpec = rowPlacement, fadeOutSpec = rowFade)) {
                                    MissingRow(
                                        item = item,
                                        field = field,
                                        expanded = expanded == key,
                                        onToggle = { expanded = if (expanded == key) null else key },
                                        onFill = { onFill(field, item, it) },
                                    )
                                    Hairline()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 一個品項。點一下在原地展開輸入框，數字先帶好：同一個品項別筆記過的優先（那是使用者
 * 自己記的），缺水量的再退一步用份量寫的容量 —— 飲料的水量就是它的容量，差的那一點
 * 糖與奶不值得讓使用者每一列自己打。
 */
@Composable
private fun MissingRow(
    item: MissingField,
    field: DataCheckField,
    expanded: Boolean,
    onToggle: () -> Unit,
    onFill: (Double) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val unit = stringResource(if (field == DataCheckField.SODIUM) R.string.unit_mg else R.string.unit_ml)
    val fromServing = if (field == DataCheckField.WATER) drinkVolumeMl(item.servingText) else null
    val prefill = item.known ?: fromServing
    val helper = when {
        item.known != null -> stringResource(R.string.data_check_prefill_known, item.known.fmt() + " " + unit)
        fromServing != null -> stringResource(R.string.data_check_prefill_serving, fromServing.fmt() + " " + unit)
        else -> null
    }
    var text by remember(item.name, item.servingText, field) {
        mutableStateOf(prefill?.asInputValue().orEmpty())
    }
    var invalid by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 10.dp),
        ) {
            Text(
                item.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 份量太長時先截份量，筆數要永遠看得到
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (item.servingText.isNotBlank()) {
                    Text(
                        item.servingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                val count = stringResource(R.string.data_check_entries, item.missing)
                Text(
                    withNumerals(if (item.servingText.isNotBlank()) "· $count" else count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                )
            }
        }
        Reveal(expanded) {
            Row(
                Modifier.padding(bottom = 12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                fun submit() {
                    val value = text.trim().toDoubleOrNull()
                    if (value == null || value < 0) {
                        invalid = true
                    } else {
                        focusManager.clearFocus()
                        onFill(value)
                    }
                }
                NutriTextField(
                    value = text,
                    onValueChange = { text = it; invalid = false },
                    numeric = true,
                    helper = helper,
                    isError = invalid,
                    trailing = {
                        Text(
                            unit,
                            style = MaterialTheme.typography.bodyMedium.numeric(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    // 鍵盤上的完成鍵只收鍵盤，補不補由那顆鈕決定
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.weight(1f),
                )
                // 48dp 的欄位配 32dp 的鈕，往下 8dp 才對得到欄位的中線
                PillButton(
                    label = stringResource(R.string.data_check_fill),
                    onClick = { submit() },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

private val rowFade = tween<Float>(220)
private val rowPlacement = tween<IntOffset>(220)
