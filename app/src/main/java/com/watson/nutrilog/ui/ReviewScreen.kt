package com.watson.nutrilog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.db.Meal
import com.watson.nutrilog.ui.theme.NumberFontFamily
import kotlin.math.roundToInt

/**
 * 模型辨識結果的確認畫面。
 *
 * 這一步不能省：模型給的是估算值，直接寫進紀錄等於在使用者的飲食資料裡
 * 塞它自己編的數字。存進去之後仍然可以在今天的清單點進去逐項細改，
 * 所以這裡只做「要不要記錄」的取捨，不重複做一整套編輯欄位。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    state: AnalysisState,
    meal: Meal,
    onMealChange: (Meal) -> Unit,
    onToggle: (Int) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onManualInstead: () -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, stringResource(R.string.close))
                    }
                },
            )
        },
    ) { inner ->
        when (state) {
            AnalysisState.Analyzing -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.photo_analyzing))
            }

            is AnalysisState.Failed -> FailureBody(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .padding(24.dp),
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
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(stringResource(R.string.review_nothing_found))
                        OutlinedButton(onClick = onManualInstead, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.add_manual))
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
    onSave: () -> Unit,
) {
    val selectedCount = state.items.count { it.selected }
    Column(modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.review_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Text(
                    stringResource(R.string.review_meal),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item { MealPicker(meal, onSelect = onMealChange) }
            itemsIndexed(state.items) { index, item ->
                ItemRow(item, onToggle = { onToggle(index) })
            }
        }
        Button(
            onClick = onSave,
            enabled = selectedCount > 0,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.photo_save_selected, selectedCount))
        }
    }
}

@Composable
private fun ItemRow(item: AnalysisItem, onToggle: () -> Unit) {
    val food = item.food
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(checked = item.selected, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(food.name, style = MaterialTheme.typography.titleMedium)
                if (food.servingText.isNotBlank()) {
                    Text(
                        food.servingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                MacroSummaryText(food.proteinG, food.fatG, food.carbsG)
                Text(
                    stringResource(R.string.photo_confidence, (food.confidence * 100).roundToInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                food.calories.fmtInt(),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = NumberFontFamily),
            )
        }
        Hairline()
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            if (missingKey) stringResource(R.string.photo_no_key) else reason,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (missingKey) {
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.go_to_settings))
            }
        } else {
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.retry))
            }
        }
        OutlinedButton(onClick = onManualInstead, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.add_manual))
        }
    }
}
