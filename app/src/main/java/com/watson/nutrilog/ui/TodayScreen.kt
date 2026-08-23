package com.watson.nutrilog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.NutriSettings
import com.watson.nutrilog.data.db.DayTotal
import com.watson.nutrilog.data.db.FoodEntry
import com.watson.nutrilog.data.db.Meal
import com.watson.nutrilog.data.db.Totals
import com.watson.nutrilog.data.db.totals
import com.watson.nutrilog.ui.theme.NutrientColors
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * 日分頁的頁碼半徑。以「開啟這個畫面那一刻」為基準日，左右各留這麼多天，
 * 換算成頁碼給 [HorizontalPager]。用得到的範圍遠小於這個數字，
 * 但頁碼只是個 Int，留寬一點不花任何成本。
 */
private const val DAY_RADIUS = 36_500
private const val DAY_PAGE_COUNT = DAY_RADIUS * 2 + 1
private const val WEEK_RADIUS = 5_200
private const val WEEK_PAGE_COUNT = WEEK_RADIUS * 2 + 1

@Composable
fun TodayScreen(
    date: LocalDate,
    settings: NutriSettings,
    weekStart: LocalDate,
    entriesFlow: (LocalDate) -> Flow<List<FoodEntry>>,
    weekTotalsFlow: (LocalDate) -> Flow<List<DayTotal>>,
    onPickDay: (LocalDate) -> Unit,
    onShiftWeek: (Long) -> Unit,
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

    // 兩個分頁器共用同一個基準日：day pager 一頁一天，week pager 一頁一週，
    // 頁碼都是「離基準日差幾天／幾週」換算出來的，換日或換週時互相同步。
    val epoch = remember { LocalDate.now() }
    val epochWeekStart = remember(epoch) { epoch.minusDays((epoch.dayOfWeek.value % 7).toLong()) }

    fun dayPageOf(d: LocalDate) = DAY_RADIUS + ChronoUnit.DAYS.between(epoch, d).toInt()
    fun dayOfPage(page: Int) = epoch.plusDays((page - DAY_RADIUS).toLong())
    fun weekPageOf(ws: LocalDate) = WEEK_RADIUS + ChronoUnit.WEEKS.between(epochWeekStart, ws).toInt()
    fun weekOfPage(page: Int) = epochWeekStart.plusWeeks((page - WEEK_RADIUS).toLong())

    val dayPagerState = rememberPagerState(
        initialPage = dayPageOf(date),
        pageCount = { DAY_PAGE_COUNT },
    )
    val weekPagerState = rememberPagerState(
        initialPage = weekPageOf(weekStart),
        pageCount = { WEEK_PAGE_COUNT },
    )

    // LaunchedEffect(pagerState) 只在第一次組成時啟動一次（pagerState 這個 key
    // 整個生命週期都不會變），裡面的協程若直接讀外面的 date/weekStart，抓到的
    // 會是「第一次組成當下」那個值，之後永遠不會更新 —— 用 rememberUpdatedState
    // 包起來，每次比對才會用當下真正的值，而不是啟動當下的舊值。
    //
    // 這裡曾經漏了這一步：分頁器自己滑動觸發 onPickDay，selectedDate 真的變了，
    // 但這條 collector 手上的 weekStart 還停在剛開啟畫面時的那個舊值，算出來的
    // 週數差就是錯的，於是又呼叫一次 onShiftWeek 疊加上去，一路滾雪球下去。
    // 實測過：從今天單純滑一次「前一天」，會直接跳掉十幾天。
    val currentDate = rememberUpdatedState(date)
    val currentWeekStart = rememberUpdatedState(weekStart)

    // 外部改變日期（開別的畫面回來、月曆跳頁、「回到今天」…）就把分頁器滑過去；
    // 使用者自己滑出來的頁碼在呼叫 onPickDay 之前就已經跟 date 一致，這裡是 no-op。
    // Compose 的 Pager 對距離很遠的目標會先跳近再補一段動畫，所以「回到今天」
    // 這種一次跳一年的情況也不會真的把中間每一頁都劃過去。
    LaunchedEffect(date) {
        val target = dayPageOf(date)
        if (dayPagerState.currentPage != target) dayPagerState.animateScrollToPage(target)
    }
    LaunchedEffect(dayPagerState) {
        snapshotFlow { dayPagerState.settledPage }.collect { page ->
            val d = dayOfPage(page)
            if (d != currentDate.value) onPickDay(d)
        }
    }
    LaunchedEffect(weekStart) {
        val target = weekPageOf(weekStart)
        if (weekPagerState.currentPage != target) weekPagerState.animateScrollToPage(target)
    }
    LaunchedEffect(weekPagerState) {
        snapshotFlow { weekPagerState.settledPage }.collect { page ->
            val ws = weekOfPage(page)
            val deltaWeeks = ChronoUnit.WEEKS.between(currentWeekStart.value, ws)
            if (deltaWeeks != 0L) onShiftWeek(deltaWeeks)
        }
    }

    Scaffold(
        // 標頭和一週長條不進捲動區：換日是隨時要按得到的，
        // 捲到下面才發現要先捲回去才能換天很煩。
        topBar = {
            // MainActivity 開了 enableEdgeToEdge，而這個 topBar 是自己拼的 Column
            // 不是 M3 的 TopAppBar —— 沒有這行標題會直接畫到狀態列的時鐘上面。
            Column(Modifier.statusBarsPadding()) {
                HeaderRow(
                    date = date,
                    isToday = date == today,
                    onBackToToday = onBackToToday,
                    onOpenSearch = onOpenSearch,
                    onOpenHistory = onOpenHistory,
                    onOpenSettings = onOpenSettings,
                )
                WeekStrip(
                    pagerState = weekPagerState,
                    weekOfPage = ::weekOfPage,
                    weekTotalsFlow = weekTotalsFlow,
                    selected = date,
                    today = today,
                    target = settings.calorieTarget,
                    onPickDay = onPickDay,
                    onShiftWeek = onShiftWeek,
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_entry), style = MaterialTheme.typography.titleSmall)
            }
        },
    ) { inner ->
        HorizontalPager(
            state = dayPagerState,
            // Compose 的 fling 預設看滑動速度決定要跳幾頁，快速一撥可能一次跳十幾天
            // ——完全違反「一次滑動＝換一天」的直覺（實測過，真的會發生）。
            // 鎖成最多一頁，不管滑多快都只換一天，跟日記本翻頁的手感一致。
            flingBehavior = PagerDefaults.flingBehavior(
                state = dayPagerState,
                pagerSnapDistance = PagerSnapDistance.atMost(1),
            ),
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) { page ->
            DayPage(
                date = dayOfPage(page),
                entriesFlow = entriesFlow,
                settings = settings,
                onOpenEntry = onOpenEntry,
                onAddForMeal = onAddForMeal,
            )
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

@Composable
private fun HeaderRow(
    date: LocalDate,
    isToday: Boolean,
    onBackToToday: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(
            date.year.toString() + " 年 " + date.monthValue + " 月",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        // 只有離開今天才出現：在今天的時候它是一顆永遠沒作用的按鈕
        if (!isToday) {
            TextButton(onClick = onBackToToday) {
                Text(stringResource(R.string.back_to_today), style = MaterialTheme.typography.bodyMedium)
            }
        }
        IconButton(onClick = onOpenSearch) {
            Icon(Icons.Default.Search, stringResource(R.string.search_title), Modifier.size(20.dp))
        }
        IconButton(onClick = onOpenHistory) {
            Icon(Icons.Default.DateRange, stringResource(R.string.history_title), Modifier.size(20.dp))
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, stringResource(R.string.settings_title), Modifier.size(20.dp))
        }
    }
}

/**
 * 一週長條。換日和「這幾天吃得鬆或緊」用同一個元件解決 ——
 * 原本那一列只顯示一天，佔掉整整一列卻只講一個日期。
 *
 * 長條高度是當天熱量佔目標的比例，超標整條轉紅。空白的那幾天
 * 一眼就看得出是漏記還是真的沒吃。中間那排日期本身是 [HorizontalPager]
 * 的一頁，可以左右滑動換週，兩側箭頭做一樣的事，滑動與點按共用同一條路徑。
 */
@Composable
private fun WeekStrip(
    pagerState: PagerState,
    weekOfPage: (Int) -> LocalDate,
    weekTotalsFlow: (LocalDate) -> Flow<List<DayTotal>>,
    selected: LocalDate,
    today: LocalDate,
    target: Int,
    onPickDay: (LocalDate) -> Unit,
    onShiftWeek: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onShiftWeek(-1) }, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                stringResource(R.string.prev_week),
                Modifier.size(18.dp),
                tint = scheme.onSurfaceVariant,
            )
        }
        HorizontalPager(
            state = pagerState,
            // 跟日分頁同一個理由：一次滑動最多只換一週，不管滑多快。
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                pagerSnapDistance = PagerSnapDistance.atMost(1),
            ),
            modifier = Modifier.weight(1f),
        ) { page ->
            WeekRow(
                weekStart = weekOfPage(page),
                weekTotalsFlow = weekTotalsFlow,
                selected = selected,
                today = today,
                target = target,
                onPickDay = onPickDay,
            )
        }
        IconButton(onClick = { onShiftWeek(1) }, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                stringResource(R.string.next_week),
                Modifier.size(18.dp),
                tint = scheme.onSurfaceVariant,
            )
        }
    }
}

/** 一週長條裡的一頁：七個 [DayColumn]，資料是這一週自己的 Flow，跟目前選到哪一週無關。 */
@Composable
private fun WeekRow(
    weekStart: LocalDate,
    weekTotalsFlow: (LocalDate) -> Flow<List<DayTotal>>,
    selected: LocalDate,
    today: LocalDate,
    target: Int,
    onPickDay: (LocalDate) -> Unit,
) {
    val totals by remember(weekStart) { weekTotalsFlow(weekStart) }.collectAsState(initial = emptyList())
    val byDate = remember(totals) { totals.associateBy { it.date } }
    val over = NutrientColors.Over

    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(7) { index ->
            val day = weekStart.plusDays(index.toLong())
            DayColumn(
                day = day,
                kcal = byDate[day.toString()]?.kcal ?: 0.0,
                target = target,
                isSelected = day == selected,
                isFuture = day.isAfter(today),
                overColor = over,
                onClick = { onPickDay(day) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DayColumn(
    day: LocalDate,
    kcal: Double,
    target: Int,
    isSelected: Boolean,
    isFuture: Boolean,
    overColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val isOver = target > 0 && kcal > target
    val fraction = if (target > 0) (kcal / target).coerceIn(0.0, 1.0).toFloat() else 0f
    val weekday = day.dayOfWeek.value % 7

    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) scheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            WEEKDAYS[weekday],
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isSelected -> scheme.primary
                // 週末用淡一點的紅，跟平日區隔但不搶戲
                weekday == 0 || weekday == 6 -> overColor.copy(alpha = if (isFuture) 0.3f else 0.7f)
                else -> scheme.outline
            },
        )
        Text(
            day.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.sp),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            // 未來的日期壓淡：它們永遠是空的，不該看起來像「忘了記錄」
            color = when {
                isSelected -> scheme.onSurface
                isFuture -> scheme.outline.copy(alpha = 0.45f)
                else -> scheme.onSurfaceVariant
            },
        )
        Box(
            Modifier
                .width(5.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (isSelected) scheme.surfaceContainerLowest else scheme.surfaceContainerHigh),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (kcal > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(if (isOver) 1f else fraction)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            when {
                                isOver -> overColor
                                isSelected -> scheme.primary
                                else -> NutrientColors.Meals[1]
                            }
                        )
                )
            }
        }
    }
}

/** 日分頁的一頁：這一天自己的紀錄，資料是這一天自己的 Flow，跟目前選到哪天無關。 */
@Composable
private fun DayPage(
    date: LocalDate,
    entriesFlow: (LocalDate) -> Flow<List<FoodEntry>>,
    settings: NutriSettings,
    onOpenEntry: (FoodEntry) -> Unit,
    onAddForMeal: (Meal) -> Unit,
) {
    val entries by remember(date) { entriesFlow(date) }.collectAsState(initial = emptyList())
    val totals = remember(entries) { entries.totals() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 22.dp),
    ) {
        item { Hairline() }
        item { Budget(entries, totals, settings) }
        item { Hairline() }
        item { Macros(totals, settings) }
        item { Hairline() }

        Meal.entries.forEachIndexed { index, meal ->
            val ofMeal = entries.filter { it.mealType == meal }
            // 每一餐前面都加一條線，跟上一餐隔開 —— 不然固定顯示的四餐
            // 只靠標題的 padding 分隔，看起來像同一塊區域，早午晚點心之間沒有間隔。
            if (index > 0) {
                item(key = "sep-" + meal.name) { Hairline(Modifier.padding(top = 10.dp, bottom = 2.dp)) }
            }
            item(key = "header-" + meal.name) { MealHeader(meal, ofMeal) }
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
        item { Spacer(Modifier.height(80.dp)) }
    }
}

/**
 * 吃了多少，以及額度被哪一餐吃掉。
 *
 * 主數字是**已經吃多少**。之前擺的是「還可以吃」（剩餘額度），
 * 但實際用下來，打開 app 最先想確認的是「我今天吃了什麼程度」——
 * 剩餘額度是從那個數字推出來的第二個問題，所以退到下面那一行。
 *
 * 下面那條依餐別分段，這是原本的環做不到的：同樣是吃掉 1500 kcal，
 * 「午餐一次吃掉一大半」和「三餐平均」是完全不同的一天，看形狀就分得出來。
 */
@Composable
private fun Budget(entries: List<FoodEntry>, totals: Totals, settings: NutriSettings) {
    val scheme = MaterialTheme.colorScheme
    val target = settings.calorieTarget
    val consumed = totals.calories
    val remaining = target - consumed
    val over = remaining < 0
    val overColor = NutrientColors.Over

    Column(
        Modifier.padding(top = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.calories_eaten),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                consumed.fmtInt(),
                style = MaterialTheme.typography.displayLarge,
                color = if (over) overColor else scheme.onSurface,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.unit_kcal),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }

        // 目標為 0 等於關掉這條線的意義，就不要講「還有 2000 的空間」
        if (target > 0) {
            Text(
                if (over) {
                    stringResource(R.string.calories_budget_over, target, abs(remaining).fmtInt())
                } else {
                    stringResource(R.string.calories_budget_left, target, remaining.fmtInt())
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (over) overColor else scheme.onSurfaceVariant,
            )
        }

        MealSegmentBar(entries = entries, target = target, over = over)
    }
}

@Composable
private fun MealSegmentBar(entries: List<FoodEntry>, target: Int, over: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val mealColors = NutrientColors.Meals
    val overColor = NutrientColors.Over
    val consumed = entries.sumOf { it.calories }
    val remainder = (target - consumed).coerceAtLeast(0.0)

    Row(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(scheme.surfaceContainerHigh),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Meal.entries.forEachIndexed { index, meal ->
            val kcal = entries.filter { it.mealType == meal }.sumOf { it.calories }
            if (kcal <= 0.0) return@forEachIndexed
            Box(
                Modifier
                    .weight(kcal.toFloat())
                    .fillMaxHeight()
                    .background(if (over) overColor else mealColors[index])
            )
        }
        if (remainder > 0.0) {
            Box(
                Modifier
                    .weight(remainder.toFloat())
                    .fillMaxHeight()
                    .background(scheme.surfaceContainerHigh)
            )
        }
    }
}

/**
 * 三大營養素。
 *
 * 條子畫的是**組成**（三者換算成熱量後的佔比），圖例才講目標達成率。
 * 兩個問題一個元件回答：「今天吃的結構長怎樣」和「蛋白質夠不夠」。
 * 三根各自的及格條只答得出後者，而且三根等長時看不出誰佔多數。
 */
@Composable
private fun Macros(totals: Totals, settings: NutriSettings) {
    val scheme = MaterialTheme.colorScheme
    val protein = NutrientColors.Protein
    val fat = NutrientColors.Fat
    val carbs = NutrientColors.Carbs

    // 蛋白質與碳水 4 kcal/g、脂肪 9 kcal/g
    val pKcal = totals.proteinG * 4
    val fKcal = totals.fatG * 9
    val cKcal = totals.carbsG * 4

    Column(
        Modifier.padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(scheme.surfaceContainerHigh),
        ) {
            listOf(pKcal to protein, fKcal to fat, cKcal to carbs).forEach { (kcal, color) ->
                if (kcal <= 0.0) return@forEach
                Box(
                    Modifier
                        .weight(kcal.toFloat())
                        .fillMaxHeight()
                        .background(color)
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MacroLegend(stringResource(R.string.nutrient_protein), totals.proteinG, settings.proteinTargetG, protein)
            MacroLegend(stringResource(R.string.nutrient_fat), totals.fatG, settings.fatTargetG, fat)
            MacroLegend(stringResource(R.string.nutrient_carbs), totals.carbsG, settings.carbsTargetG, carbs)
        }

        // 橫向捲動：窄螢幕四個 chip 排一列會被裁掉，捲動比自動換行更符合
        // 這排「順手看一眼」的定位，不需要為了塞進一行而把字縮更小。
        if (settings.showExtendedNutrients) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                ExtraChip(stringResource(R.string.nutrient_sugar), totals.sugarG.fmt(), "g")
                ExtraChip(stringResource(R.string.nutrient_sodium), totals.sodiumMg.fmtInt(), "mg")
                ExtraChip(stringResource(R.string.nutrient_fiber), totals.fiberG.fmt(), "g")
                ExtraChip(stringResource(R.string.nutrient_satfat), totals.satFatG.fmt(), "g")
            }
        }
    }
}

@Composable
private fun MacroLegend(label: String, value: Double, target: Int, color: Color) {
    val scheme = MaterialTheme.colorScheme
    val over = target > 0 && value > target
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Row {
            Text(
                value.fmtInt(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (over) NutrientColors.Over else scheme.onSurface,
            )
            Text(
                "/" + target + " " + label,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** 進階營養素。原本是一串用全形空白隔開的長句，那讀起來像一個句子而不是四個數值。 */
@Composable
private fun ExtraChip(label: String, value: String, unit: String) {
    val scheme = MaterialTheme.colorScheme
    Text(
        label + " " + value + " " + unit,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
        color = scheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/**
 * 餐別標題。四餐一律都顯示，空的也留著。
 *
 * 只列有紀錄的餐別時，「今天還沒吃早餐」和「今天忘了記早餐」在畫面上
 * 長得一模一樣（兩者都是不存在）。固定四格之後，空的那一格本身就是提醒。
 */
@Composable
private fun MealHeader(meal: Meal, ofMeal: List<FoodEntry>) {
    val scheme = MaterialTheme.colorScheme
    val empty = ofMeal.isEmpty()
    val color = if (empty) scheme.outline else scheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(meal.label(), style = MaterialTheme.typography.titleSmall, color = color)
        Text(
            if (empty) "—" else ofMeal.totals().calories.fmtInt() + " " + stringResource(R.string.unit_kcal),
            style = MaterialTheme.typography.bodySmall,
            color = if (empty) scheme.outline else scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EntryRow(entry: FoodEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detailLine(entry.servingText, entry.proteinG, entry.fatG, entry.carbsG),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            entry.calories.fmtInt(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun EmptyMealRow(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Default.Add,
            null,
            Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Text(
            stringResource(R.string.meal_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
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
            // 這時通常會先想到「這不是常吃的那個嗎」，該畫面同時給了常吃清單。
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

private val WEEKDAYS = listOf("日", "一", "二", "三", "四", "五", "六")
