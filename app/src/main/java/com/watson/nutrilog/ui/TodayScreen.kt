package com.watson.nutrilog.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.unit.dp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.NutriSettings
import com.watson.nutrilog.data.db.FoodEntry
import com.watson.nutrilog.data.db.Meal
import com.watson.nutrilog.data.db.Totals
import com.watson.nutrilog.data.db.totals
import com.watson.nutrilog.ui.theme.NutrientColors
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    date: LocalDate,
    entries: List<FoodEntry>,
    totals: Totals,
    settings: NutriSettings,
    onShiftDay: (Long) -> Unit,
    onBackToToday: () -> Unit,
    onOpenEntry: (FoodEntry) -> Unit,
    onAddManual: () -> Unit,
    onAddForMeal: (Meal) -> Unit,
    onAddPhoto: () -> Unit,
    onAddFromGallery: () -> Unit,
    onAddText: () -> Unit,
    onAddBarcode: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val today = LocalDate.now()
    var showAddSheet by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Default.Search, stringResource(R.string.search_title))
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Default.DateRange, stringResource(R.string.history_title))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, stringResource(R.string.settings_title))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, stringResource(R.string.add_entry))
            }
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DateRow(
                    date = date,
                    today = today,
                    onShiftDay = onShiftDay,
                    onBackToToday = onBackToToday,
                )
            }
            item { SummaryCard(totals, settings) }

            // 四餐一律都顯示，空的也留著並寫 0。
            //
            // 原本只列有紀錄的餐別，但那樣「今天還沒吃早餐」和「今天忘了記早餐」
            // 在畫面上長得一模一樣（兩者都是不存在）。固定四格之後，
            // 空的那一格本身就是提醒，點下去還能直接補登該餐。
            Meal.entries.forEach { meal ->
                val ofMeal = entries.filter { it.mealType == meal }
                item(key = "header-" + meal.name) {
                    MealHeader(meal, ofMeal.totals().calories)
                }
                if (ofMeal.isEmpty()) {
                    item(key = "empty-" + meal.name) {
                        EmptyMealRow(onClick = { onAddForMeal(meal) })
                    }
                } else {
                    items(ofMeal, key = { it.id }) { entry ->
                        EntryRow(entry, onClick = { onOpenEntry(entry) })
                    }
                }
            }
            // 最後一筆不要被 FAB 蓋住
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showAddSheet) {
        AddEntrySheet(
            onDismiss = { showAddSheet = false },
            onPick = { action -> showAddSheet = false; action() },
            onAddManual = onAddManual,
            onAddPhoto = onAddPhoto,
            onAddFromGallery = onAddFromGallery,
            onAddText = onAddText,
            onAddBarcode = onAddBarcode,
        )
    }
}

/**
 * 所有輸入方式的入口。
 *
 * 用 bottom sheet 而不是展開式 FAB：選項有文字說明，
 * 而展開的小 FAB 只有圖示，第一次用的人猜不出哪個是哪個。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEntrySheet(
    onDismiss: () -> Unit,
    onPick: (() -> Unit) -> Unit,
    onAddManual: () -> Unit,
    onAddPhoto: () -> Unit,
    onAddFromGallery: () -> Unit,
    onAddText: () -> Unit,
    onAddBarcode: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 32.dp)) {
            // 「常吃／文字輸入」擺第一個：多半是忘記拍照事後補登，
            // 這時通常會先想到「這不是常吃的那個嗎」，該畫面同時給了常吃清單，
            // 找不到才用文字描述交給 AI，不用每次都繞去搜尋頁。
            SheetRow(stringResource(R.string.add_text)) { onPick(onAddText) }
            SheetRow(stringResource(R.string.add_manual)) { onPick(onAddManual) }
            SheetRow(stringResource(R.string.add_photo)) { onPick(onAddPhoto) }
            SheetRow(stringResource(R.string.add_photo_gallery)) { onPick(onAddFromGallery) }
            SheetRow(stringResource(R.string.add_barcode)) { onPick(onAddBarcode) }
        }
    }
}

@Composable
private fun SheetRow(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp),
    )
}

@Composable
private fun DateRow(
    date: LocalDate,
    today: LocalDate,
    onShiftDay: (Long) -> Unit,
    onBackToToday: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onShiftDay(-1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.prev_day))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.displayLabel(today), style = MaterialTheme.typography.titleMedium)
            if (date != today) {
                TextButton(onClick = onBackToToday) { Text(stringResource(R.string.back_to_today)) }
            }
        }
        IconButton(onClick = { onShiftDay(1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_day))
        }
    }
}

@Composable
private fun SummaryCard(totals: Totals, settings: NutriSettings) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CalorieRing(consumed = totals.calories, target = settings.calorieTarget)

            Text(
                stringResource(
                    R.string.calories_consumed,
                    totals.calories.fmtInt(),
                    settings.calorieTarget,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MacroStat(
                    label = stringResource(R.string.nutrient_protein),
                    value = totals.proteinG,
                    target = settings.proteinTargetG,
                    unit = stringResource(R.string.unit_gram),
                    color = NutrientColors.Protein,
                    modifier = Modifier.weight(1f),
                )
                MacroStat(
                    label = stringResource(R.string.nutrient_fat),
                    value = totals.fatG,
                    target = settings.fatTargetG,
                    unit = stringResource(R.string.unit_gram),
                    color = NutrientColors.Fat,
                    modifier = Modifier.weight(1f),
                )
                MacroStat(
                    label = stringResource(R.string.nutrient_carbs),
                    value = totals.carbsG,
                    target = settings.carbsTargetG,
                    unit = stringResource(R.string.unit_gram),
                    color = NutrientColors.Carbs,
                    modifier = Modifier.weight(1f),
                )
            }

            if (settings.showExtendedNutrients) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "糖 " + totals.sugarG.fmt() + " g　鈉 " + totals.sodiumMg.fmtInt() +
                        " mg　纖維 " + totals.fiberG.fmt() + " g　飽和脂肪 " +
                        totals.satFatG.fmt() + " g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MealHeader(meal: Meal, kcal: Double) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            meal.symbol() + "  " + meal.label(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            kcal.fmtInt() + " " + stringResource(R.string.unit_kcal),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EntryRow(entry: FoodEntry, onClick: () -> Unit) {
    Card(
        shape = SmallCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                if (entry.servingText.isNotBlank()) {
                    Text(
                        entry.servingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MacroSummaryText(entry.proteinG, entry.fatG, entry.carbsG)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    entry.calories.fmtInt(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.unit_kcal),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyMealRow(onClick: () -> Unit) {
    Card(
        shape = SmallCardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.meal_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "0",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
