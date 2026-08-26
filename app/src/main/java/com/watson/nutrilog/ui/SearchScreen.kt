package com.watson.nutrilog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.db.FoodEntry
import com.watson.nutrilog.data.db.FoodSuggestion
import com.watson.nutrilog.ui.theme.numeric
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 搜尋 ＋ 個人食物庫。
 *
 * 兩種狀態：沒輸入時是食物庫（常吃／最近兩頁），有輸入就整個換成逐筆搜尋結果。
 * 這兩種列的長相本來就不同 —— 食物庫的一列是聚合的品項（沒有單一日期），
 * 搜尋結果的一列是某天的某一筆（有日期）。混在同一個捲動區會看不出來是兩種東西，
 * 所以是切換而不是並排。
 *
 * 點一列**直接進編輯表單**。原本中間隔了一張細節面板（攤開營養素、按「加入」才進表單），
 * 理由是「同名不同規格的兩列擺在一起時先確認一下比較不會加錯」—— 但表單本身就把
 * 完整營養素攤開了，而且要按「儲存」才會真的入庫，那才是真正的確認步驟。
 * 中間那一層等於同一件事確認兩次，而且點錯的代價只是按個取消。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    query: String,
    targetDate: LocalDate,
    results: List<FoodEntry>,
    frequent: List<FoodSuggestion>,
    recent: List<FoodSuggestion>,
    onQueryChange: (String) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onReuseEntry: (FoodEntry) -> Unit,
    onReuseSuggestion: (FoodSuggestion) -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.search_title),
                closeLabel = stringResource(R.string.close),
                onClose = onClose,
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding(),
        ) {
            NutriTextField(
                value = query,
                onValueChange = onQueryChange,
                label = stringResource(R.string.search_label),
                placeholder = stringResource(R.string.search_placeholder),
                leading = { SearchMark(MaterialTheme.colorScheme.onSurfaceVariant) },
                // 清除鈕只在有內容時出現：手機上要使用者自己選取後刪掉太費事
                trailing = if (query.isEmpty()) null else {
                    {
                        CircleIconButton(
                            onClick = { onQueryChange("") },
                            size = 24.dp,
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                            borderWidth = 1.dp,
                        ) { CloseMark(MaterialTheme.colorScheme.onSurfaceVariant, size = 12.dp) }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 8.dp),
            )

            // 加入的紀錄會落在「目前選的那一天」，跟其他新增路徑一致。
            // 但搜尋畫面看不到日期列，不講的話使用者無從得知自己正在補登哪一天 ——
            // 尤其他很可能是剛從搜尋結果跳去某一天才回到這裡的。
            if (targetDate != LocalDate.now()) {
                Text(
                    stringResource(R.string.search_target_date, targetDate.displayLabel()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
                )
            }

            if (query.isBlank()) {
                FoodLibrary(frequent, recent, onReuseSuggestion)
            } else {
                SearchResults(results, onOpenDay, onReuseEntry)
            }
        }
    }
}

/** 常吃／最近兩頁，可以左右滑動切換。TextLookupScreen 也共用這個元件。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FoodLibrary(
    frequent: List<FoodSuggestion>,
    recent: List<FoodSuggestion>,
    onReuse: (FoodSuggestion) -> Unit,
) {
    val pager = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    NutriTabs(
        labels = listOf(
            stringResource(R.string.search_tab_frequent),
            stringResource(R.string.search_tab_recent),
        ),
        selectedIndex = pager.currentPage,
        onSelect = { scope.launch { pager.animateScrollToPage(it) } },
    )
    Hairline()
    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
        // 「最近」那頁不顯示次數：它的排序依據就是日期，次數在那裡只是雜訊
        val items = if (page == 0) frequent else recent
        if (items.isEmpty()) {
            EmptyLibraryHint(isFrequentPage = page == 0)
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
            ) {
                items(items, key = { it.name + "|" + it.servingText }) { suggestion ->
                    Column {
                        SuggestionRow(
                            suggestion = suggestion,
                            showTimes = page == 0,
                            onClick = { onReuse(suggestion) },
                        )
                        Hairline()
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryHint(isFrequentPage: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(
                if (isFrequentPage) R.string.search_empty_frequent else R.string.search_empty_recent
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 食物庫的一列，固定兩行。
 *
 * 第二行塞了份量、次數與最後日期三樣東西，窄螢幕會擠 ——
 * 讓份量文字先被截斷，因為次數與日期才是這一頁提供的資訊，
 * 份量在點進去之後看得到。
 *
 * 不用 Card：這套色票的卡片底色和背景只差 3%，整片卡會糊成一塊，
 * 分隔改交給列與列之間的細線。
 */
@Composable
private fun SuggestionRow(
    suggestion: FoodSuggestion,
    showTimes: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                suggestion.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (suggestion.servingText.isNotBlank()) {
                    Text(
                        suggestion.servingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Text(
                    suggestionStats(suggestion, showTimes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                )
            }
        }
        Text(
            suggestion.calories.fmtInt(),
            style = MaterialTheme.typography.titleMedium.numeric(),
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun suggestionStats(suggestion: FoodSuggestion, showTimes: Boolean): String {
    val last = runCatching { LocalDate.parse(suggestion.lastDate) }.getOrNull()
    val lastLabel = last?.let { it.monthValue.toString() + "/" + it.dayOfMonth } ?: suggestion.lastDate
    return if (showTimes) {
        stringResource(R.string.search_stats_frequent, suggestion.times, lastLabel)
    } else {
        stringResource(R.string.search_stats_recent, lastLabel)
    }
}

/** 逐筆搜尋結果，日期新到舊。點一列跳到那天，右側「＋」照這筆再記一筆。 */
@Composable
private fun SearchResults(
    results: List<FoodEntry>,
    onOpenDay: (LocalDate) -> Unit,
    onReuse: (FoodEntry) -> Unit,
) {
    if (results.isEmpty()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.search_no_result),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
    ) {
        items(results, key = { it.id }) { entry ->
            Column {
                ResultRow(
                    entry = entry,
                    onClick = {
                        runCatching { LocalDate.parse(entry.date) }.getOrNull()?.let(onOpenDay)
                    },
                    onReuse = { onReuse(entry) },
                )
                Hairline()
            }
        }
    }
}

@Composable
private fun ResultRow(entry: FoodEntry, onClick: () -> Unit, onReuse: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            val date = runCatching { LocalDate.parse(entry.date) }.getOrNull()
            val dateLabel = date?.let { it.monthValue.toString() + "/" + it.dayOfMonth } ?: entry.date
            Text(
                dateLabel + " " + entry.mealType.label() + " · " + entry.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (entry.servingText.isNotBlank()) {
                    Text(
                        entry.servingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Text(
                    entry.calories.fmtInt() + " " + stringResource(R.string.unit_kcal),
                    style = MaterialTheme.typography.bodySmall.numeric(),
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                )
            }
        }
        // 實線圈：它在一列裡要跟「整列點擊」分開，所以框要看得見
        CircleIconButton(onClick = onReuse) {
            PlusMark(MaterialTheme.colorScheme.onSurface, size = 13.dp, stroke = 1.8.dp)
        }
    }
}
