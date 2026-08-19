package com.watson.nutrilog.ui

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    onAddPhoto: () -> Unit,
    onAddFromGallery: () -> Unit,
    onAddBarcode: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val today = LocalDate.now()
    var showAddSheet by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
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

            if (entries.isEmpty()) {
                item { EmptyHint() }
            } else {
                // 依餐別分組。空的餐別整段不顯示，免得畫面被四個空標題佔滿。
                Meal.entries.forEach { meal ->
                    val ofMeal = entries.filter { it.mealType == meal }
                    if (ofMeal.isNotEmpty()) {
                        item(key = "header-" + meal.name) {
                            MealHeader(meal, ofMeal.totals().calories)
                        }
                        items(ofMeal, key = { it.id }) { entry ->
                            EntryRow(entry, onClick = { onOpenEntry(entry) })
                        }
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
            onAddBarcode = onAddBarcode,
        )
    }
}

/**
 * 三種輸入方式的入口。
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
    onAddBarcode: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 32.dp)) {
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
    Card(shape = CardShape) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NutrientBar(
                label = stringResource(R.string.nutrient_calories),
                value = totals.calories,
                target = settings.calorieTarget,
                unit = stringResource(R.string.unit_kcal),
                color = NutrientColors.Calories,
            )
            NutrientBar(
                label = stringResource(R.string.nutrient_protein),
                value = totals.proteinG,
                target = settings.proteinTargetG,
                unit = stringResource(R.string.unit_gram),
                color = NutrientColors.Protein,
            )
            NutrientBar(
                label = stringResource(R.string.nutrient_fat),
                value = totals.fatG,
                target = settings.fatTargetG,
                unit = stringResource(R.string.unit_gram),
                color = NutrientColors.Fat,
            )
            NutrientBar(
                label = stringResource(R.string.nutrient_carbs),
                value = totals.carbsG,
                target = settings.carbsTargetG,
                unit = stringResource(R.string.unit_gram),
                color = NutrientColors.Carbs,
            )
            if (settings.showExtendedNutrients) {
                Text(
                    "糖 " + totals.sugarG.fmt() + " g · 鈉 " + totals.sodiumMg.fmtInt() +
                        " mg · 纖維 " + totals.fiberG.fmt() + " g · 飽和脂肪 " +
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
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(meal.label(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            kcal.fmtInt() + " " + stringResource(R.string.unit_kcal),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EntryRow(entry: FoodEntry, onClick: () -> Unit) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.bodyLarge)
                if (entry.servingText.isNotBlank()) {
                    Text(
                        entry.servingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MacroSummaryText(entry.proteinG, entry.fatG, entry.carbsG)
            }
            Text(
                entry.calories.fmtInt() + " " + stringResource(R.string.unit_kcal),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun EmptyHint() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.today_empty), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.today_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
