package com.watson.nutrilog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.db.FoodSuggestion
import com.watson.nutrilog.ui.theme.NutriFieldShape
import com.watson.nutrilog.ui.theme.nutriFieldColors
import java.time.LocalDate

/**
 * 常吃清單 ＋ 文字辨識，合成一個畫面。
 *
 * 這條路的典型情境是「忘記拍照，事後想到才補登」——這時候第一反應通常是
 * 「這不是常吃的那個嗎」，而不是想打字給 AI 猜。所以常吃清單放最上面，
 * 點了就直接帶進表單；真的找不到才往下用文字描述、交給 AI 估算。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextLookupScreen(
    targetDate: LocalDate,
    frequent: List<FoodSuggestion>,
    recent: List<FoodSuggestion>,
    onReuseSuggestion: (FoodSuggestion) -> Unit,
    onLookup: (String) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var previewing by remember { mutableStateOf<FoodSuggestion?>(null) }
    val submit = { if (query.isNotBlank()) onLookup(query) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.text_lookup_title)) },
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
                .imePadding(),
        ) {
            // 跟搜尋頁一樣：可能是從別天跳回來才補登，不講的話不知道會記到哪天。
            if (targetDate != LocalDate.now()) {
                Text(
                    stringResource(R.string.search_target_date, targetDate.displayLabel()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // FoodLibrary 內部是 TabRow + HorizontalPager 兩個手足元件，
            // 得放進 Column（而不是 Box）才會上下疊放而不是互相蓋住。
            Column(Modifier.weight(1f).fillMaxWidth()) {
                FoodLibrary(frequent, recent) { previewing = it }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.text_lookup_divider),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.text_lookup_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                TextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.text_lookup_label)) },
                    placeholder = { Text(stringResource(R.string.text_lookup_placeholder)) },
                    // 單行 + 送出鍵：這裡打完通常就想直接查，不需要換行
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                    shape = NutriFieldShape,
                    colors = nutriFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    onClick = submit,
                    enabled = query.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.text_lookup_go))
                }
            }
        }
    }

    previewing?.let { suggestion ->
        SuggestionSheet(
            suggestion = suggestion,
            onDismiss = { previewing = null },
            onAdd = {
                previewing = null
                onReuseSuggestion(suggestion)
            },
        )
    }
}
