package com.watson.nutrilog.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watson.nutrilog.R
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
    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.review_title),
                closeLabel = stringResource(R.string.cancel),
                onClose = onClose,
            )
        },
    ) { inner ->
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .padding(horizontal = 22.dp),
                reason = state.reason,
                onRetry = onRetry,
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
                        StampButton(
                            label = stringResource(R.string.add_manual),
                            onClick = onManualInstead,
                        )
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
    Column(modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f),
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
            itemsIndexed(state.items) { index, item ->
                ItemRow(
                    item = item,
                    onToggle = { onToggle(index) },
                    onMultiplierChange = { mult -> onMultiplierChange(index, mult) },
                )
            }
            item { Box(Modifier.padding(bottom = 16.dp)) }
        }
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
    }
}

@Composable
private fun ItemRow(
    item: AnalysisItem,
    onToggle: () -> Unit,
    onMultiplierChange: (Double) -> Unit,
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
                    color = if (item.selected) scheme.onSurface else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    detailLine(food.servingText, food.proteinG, food.fatG, food.carbsG),
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
                color = if (item.selected) scheme.onSurface else scheme.outline,
            )
        }
        // 份數只在勾選的那幾項展開：沒要記錄的東西不需要調份量，
        // 全部都展開會讓這一頁看起來像五張表單疊在一起。
        if (item.selected) {
            PortionMultiplierBar(
                multiplier = item.multiplier,
                onMultiplierChange = onMultiplierChange,
                compact = true,
                modifier = Modifier.padding(start = 32.dp, bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun FailureBody(
    modifier: Modifier,
    reason: String,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onManualInstead: () -> Unit,
) {
    // 沒設 key 是最常見的失敗，而且解法完全不同（去設定，不是重試），
    // 所以獨立成一種畫面而不是丟一段錯誤字串了事。
    val missingKey = reason == NutriViewModel.NO_API_KEY
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(
            if (missingKey) stringResource(R.string.photo_no_key) else reason,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 24.dp),
        )
        StampButton(
            label = stringResource(if (missingKey) R.string.go_to_settings else R.string.retry),
            onClick = if (missingKey) onOpenSettings else onRetry,
        )
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
