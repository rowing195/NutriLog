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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.NutriSettings
import com.watson.nutrilog.data.db.DayTotal
import com.watson.nutrilog.ui.theme.NutrientColors
import java.time.LocalDate

/**
 * 每天一列的總覽。合計是資料庫用 GROUP BY 算的，
 * 不必把整段期間的明細撈進記憶體再自己加。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    days: List<DayTotal>,
    settings: NutriSettings,
    onOpenDay: (LocalDate) -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, stringResource(R.string.close))
                    }
                },
            )
        },
    ) { inner ->
        if (days.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.history_empty))
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(days, key = { it.date }) { day ->
                DayRow(day, settings) {
                    // date 是我們自己寫進去的 ISO 字串，理論上一定 parse 得過；
                    // 真的壞了就當沒點到，不要讓歷史清單整個閃退。
                    runCatching { LocalDate.parse(day.date) }.getOrNull()?.let(onOpenDay)
                }
            }
        }
    }
}

@Composable
private fun DayRow(day: DayTotal, settings: NutriSettings, onClick: () -> Unit) {
    val over = settings.calorieTarget > 0 && day.kcal > settings.calorieTarget
    Card(
        shape = CardShape,
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
                val date = runCatching { LocalDate.parse(day.date) }.getOrNull()
                Text(
                    date?.displayLabel() ?: day.date,
                    style = MaterialTheme.typography.bodyLarge,
                )
                MacroSummaryText(day.proteinG, day.fatG, day.carbsG)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    day.kcal.fmtInt() + " " + stringResource(R.string.unit_kcal),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (over) NutrientColors.Over else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    day.itemCount.toString() + " 筆",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
