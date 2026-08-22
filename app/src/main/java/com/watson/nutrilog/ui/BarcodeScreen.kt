package com.watson.nutrilog.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.db.CachedProduct

/**
 * 條碼查詢。
 *
 * 掃描器（Play 服務的 Code Scanner）不是每台裝置都叫得出來 ——
 * 它要動態下載模組，模擬器上常常失敗。所以**手動輸入條碼永遠可用**，
 * 而且走的是完全相同的查詢與入庫路徑，不是次等的備案。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScreen(
    state: BarcodeState,
    onScan: () -> Unit,
    onLookup: (String) -> Unit,
    onUseProduct: (CachedProduct, Double) -> Unit,
    onManualInstead: () -> Unit,
    onClose: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.barcode_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, stringResource(R.string.close))
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.add_barcode))
            }

            OutlinedTextField(
                value = code,
                onValueChange = { raw -> code = raw.filter { it.isDigit() }.take(14) },
                label = { Text(stringResource(R.string.barcode_input)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { onLookup(code) },
                enabled = code.isNotBlank() && state !is BarcodeState.Loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.barcode_lookup))
            }

            when (state) {
                BarcodeState.Idle -> Unit

                BarcodeState.Loading -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                    Text(stringResource(R.string.barcode_searching))
                }

                BarcodeState.NotFound -> NoticeWithManualFallback(
                    text = stringResource(R.string.barcode_not_found),
                    onManualInstead = onManualInstead,
                )

                is BarcodeState.Failed -> NoticeWithManualFallback(
                    text = state.message,
                    onManualInstead = onManualInstead,
                )

                is BarcodeState.Found -> FoundCard(state, onUseProduct)
            }
        }
    }
}

@Composable
private fun FoundCard(state: BarcodeState.Found, onUseProduct: (CachedProduct, Double) -> Unit) {
    val product = state.product
    // 換一個商品就要重算預設份量，所以 key 帶上條碼
    var grams by remember(product.barcode) {
        mutableStateOf(product.defaultGrams().let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() })
    }
    // 用細框而不是實色卡片：這套色票的卡片底和背景只差 3%，填色等於沒填
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(product.name, style = MaterialTheme.typography.titleMedium)
            product.brand?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.fromCache) {
                Text(
                    stringResource(R.string.barcode_from_cache),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                stringResource(R.string.barcode_per_100g),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                per100gSummary(product),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = grams,
                onValueChange = { raw -> grams = raw.filter { it.isDigit() || it == '.' }.take(6) },
                label = { Text(stringResource(R.string.barcode_grams)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onUseProduct(product, grams.toDoubleOrNull() ?: 100.0) },
                enabled = (grams.toDoubleOrNull() ?: 0.0) > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@Composable
private fun NoticeWithManualFallback(text: String, onManualInstead: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onManualInstead, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.add_manual))
        }
    }
}

/** 沒有的欄位就不列出來，避免看起來像「這個食物含 0 g 蛋白質」。 */
private fun per100gSummary(product: CachedProduct): String = buildList {
    product.caloriesPer100g?.let { add(it.fmt() + " kcal") }
    product.proteinPer100g?.let { add("蛋白 " + it.fmt() + " g") }
    product.fatPer100g?.let { add("脂肪 " + it.fmt() + " g") }
    product.carbsPer100g?.let { add("碳水 " + it.fmt() + " g") }
}.joinToString(" · ").ifEmpty { "這筆商品沒有營養素資料" }
