package com.watson.nutrilog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.NutriSettings
import com.watson.nutrilog.data.db.DayTotal
import com.watson.nutrilog.ui.theme.numeric
import com.watson.nutrilog.ui.theme.NutrientColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * 月曆式歷史。一格一天，格子裡直接寫當天熱量。
 *
 * 比起清單，月曆的價值在於**看得出空白**：哪幾天忘了記、
 * 連續幾天超標，一眼就有形狀。清單只會讓有紀錄的日子擠在一起，
 * 反而看不出中間漏了幾天。
 *
 * 一週從星期日開始（台灣的日曆慣例）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    month: YearMonth,
    totals: Map<String, DayTotal>,
    settings: NutriSettings,
    selectedDate: LocalDate,
    onShiftMonth: (Long) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onClose: () -> Unit,
) {
    val today = LocalDate.now()

    // 頁碼＝離「開啟這個畫面那個月」差幾個月，和今日頁那兩個分頁器同一套算法。
    val epochMonth = remember { YearMonth.now() }
    fun monthPageOf(m: YearMonth) =
        MONTH_RADIUS + ChronoUnit.MONTHS.between(epochMonth, m).toInt()
    fun monthOfPage(page: Int) = epochMonth.plusMonths((page - MONTH_RADIUS).toLong())

    val pagerState = rememberPagerState(
        initialPage = monthPageOf(month),
        pageCount = { MONTH_PAGE_COUNT },
    )

    // LaunchedEffect(pagerState) 只在第一次組成時啟動一次，裡面的協程直接讀外面的
    // month 會永遠抓到「開啟畫面當下」那個舊值 —— 和今日頁那個滾雪球 bug 同一類陷阱，
    // 所以包 rememberUpdatedState。
    val currentMonth = rememberUpdatedState(month)

    // 外部改月份（兩側箭頭、「回到本月」）就把分頁器滑過去；使用者自己滑出來的頁碼
    // 在 onShiftMonth 之前就已經和 month 一致，這裡是 no-op。
    LaunchedEffect(month) {
        val target = monthPageOf(month)
        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }
    // 用 settledPage 而不是 currentPage：後者過半頁就變，長距離跳頁（「回到本月」）
    // 途中會經過好幾個中繼頁，拿中繼頁去 commit 就會停在錯的月份。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val m = monthOfPage(page)
            val delta = ChronoUnit.MONTHS.between(currentMonth.value, m)
            if (delta != 0L) onShiftMonth(delta)
        }
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.history_title),
                closeLabel = stringResource(R.string.close),
                onClose = onClose,
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MonthHeader(
                pagerState = pagerState,
                monthOfPage = ::monthOfPage,
                month = month,
                today = today,
                onShiftMonth = onShiftMonth,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            // 星期列每個月都一樣，留在分頁器外面不要跟著滑 —— 一模一樣的東西
            // 滑過去只會讓人以為畫面卡住了。
            WeekdayHeader(Modifier.padding(horizontal = 22.dp))
            HorizontalPager(
                state = pagerState,
                // 和今日頁兩個分頁器同一個理由：一次滑動最多只換一個月，不管滑多快。
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    pagerSnapDistance = PagerSnapDistance.atMost(1),
                ),
                modifier = Modifier.weight(1f),
            ) { page ->
                val pageMonth = monthOfPage(page)
                Column(
                    Modifier.padding(horizontal = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MonthGrid(
                        month = pageMonth,
                        totals = totals,
                        settings = settings,
                        today = today,
                        selectedDate = selectedDate,
                        onOpenDay = onOpenDay,
                    )
                    MonthSummary(pageMonth, totals, settings)
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(
    pagerState: PagerState,
    monthOfPage: (Int) -> YearMonth,
    month: YearMonth,
    today: LocalDate,
    onShiftMonth: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clickable { onShiftMonth(-1) },
            contentAlignment = Alignment.Center,
        ) { ChevronMark(scheme.onSurfaceVariant, pointsLeft = true, size = 18.dp) }

        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            MonthTitle(pagerState, monthOfPage)
            // **「回到本月」不跟著滑。** 它是一個動作、不是這個月的內容，而且它會
            // 隨著月份出現與消失 —— 讓它跟著滑，整個報頭的高度就會在拖曳過程中
            // 忽高忽低，底下的月曆跟著上下跳。
            if (month != YearMonth.from(today)) {
                Text(
                    stringResource(R.string.back_to_this_month),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { onShiftMonth(monthsBetween(month, today)) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        Box(
            Modifier.size(36.dp).clickable { onShiftMonth(1) },
            contentAlignment = Alignment.Center,
        ) { ChevronMark(scheme.onSurfaceVariant, pointsLeft = false, size = 18.dp) }
    }
}

/**
 * 報頭那個「2026 / 09」，**拖月曆的時候跟著手指走**。
 *
 * 它不能直接做成分頁器的一部分：兩側站著箭頭，而箭頭不該跟著滑。所以改用**借位** ——
 * 讀月分頁器的即時位移（`currentPage` ＋ `currentPageOffsetFraction`），把這個月的字
 * 往手指的反方向推，鄰月的字從旁邊補進來。這是今日頁 `WeekPageContent` 畫鄰週預覽
 * 的同一招：**純視覺，從頭到尾不去碰分頁器自己的捲動狀態**。
 *
 * 反過來做（用即時值去驅動另一個分頁器的位置）就是今日頁那兩個 bug 的共同根源，
 * 不要再走那條路。
 *
 * **一步走「一個標題寬 ＋ 一段間隔」，不是走整條的寬度。** 走整條寬的話，拖到一半時
 * 兩個月份各自貼在左右兩端、中間空一大片，讀起來是兩個東西而不是一條被拖動的帶子。
 * 走標題寬則是首尾相接的跑馬燈：舊的往外走多少，新的就跟進來多少。
 *
 * 為了讓「靜止時鄰月看不見」這個不變量成立，外框只有**一個標題那麼寬**
 * （`IntrinsicSize.Max`）並且切邊 —— 鄰月停在框外一個間隔的地方，切得乾乾淨淨。
 * 框如果放寬到整條，鄰月就會直接露在旁邊。
 */
@Composable
private fun MonthTitle(pagerState: PagerState, monthOfPage: (Int) -> YearMonth) {
    // 這兩個值在拖曳的每一幀都會變，所以讀取要關在這個小元件裡 ——
    // 寫在外層的話整個月曆（含每一格）都會跟著每幀重組。
    val page = pagerState.currentPage
    val offset = pagerState.currentPageOffsetFraction
    val gapPx = with(LocalDensity.current) { MONTH_TITLE_GAP.toPx() }
    Box(
        Modifier.width(IntrinsicSize.Max).clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        for (delta in -1..1) {
            val m = monthOfPage(page + delta)
            Text(
                m.year.toString() + " / " + "%02d".format(m.monthValue),
                // 純數字加斜線，套襯線不會碰到中文
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp).numeric(),
                // size 是這個 Text 自己的寬度：三個月份的字寬一樣，所以三份用的
                // 步距也一樣，不必再去量外框（BoxWithConstraints 是 SubcomposeLayout，
                // 撐不起 IntrinsicSize，量不了）。
                modifier = Modifier.graphicsLayer {
                    translationX = (delta - offset) * (size.width + gapPx)
                },
            )
        }
    }
}

@Composable
private fun WeekdayHeader(modifier: Modifier = Modifier) {
    Column(modifier) {
        Hairline(Modifier.padding(bottom = 8.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            WEEKDAYS.forEachIndexed { index, label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                    // 週末用淡一點的朱紅，跟平日區隔但不搶戲
                    color = if (index == 0 || index == 6) {
                        NutrientColors.Over.copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    totals: Map<String, DayTotal>,
    settings: NutriSettings,
    today: LocalDate,
    selectedDate: LocalDate,
    onOpenDay: (LocalDate) -> Unit,
) {
    val firstDay = month.atDay(1)
    // ISO 的星期一是 1、星期日是 7。要排成「日一二三四五六」，
    // 星期日的前置空格數就是 0，所以取 value % 7。
    val leadingBlanks = firstDay.dayOfWeek.value % 7
    val daysInMonth = month.lengthOfMonth()
    val totalCells = leadingBlanks + daysInMonth
    val rows = (totalCells + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(rows) { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(7) { column ->
                    val cellIndex = row * 7 + column
                    val dayOfMonth = cellIndex - leadingBlanks + 1
                    if (dayOfMonth in 1..daysInMonth) {
                        val date = month.atDay(dayOfMonth)
                        DayCell(
                            date = date,
                            total = totals[date.toString()],
                            settings = settings,
                            isToday = date == today,
                            isSelected = date == selectedDate,
                            isFuture = date.isAfter(today),
                            onClick = { onOpenDay(date) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        // 佔位讓格線對齊，不然月初月末會歪掉
                        Box(Modifier.weight(1f).aspectRatio(0.85f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    total: DayTotal?,
    settings: NutriSettings,
    isToday: Boolean,
    isSelected: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kcal = total?.kcal ?: 0.0
    val severity = overSeverity(kcal, settings.calorieTarget)
    val severityColor = when (severity) {
        OverSeverity.OVER -> NutrientColors.Over
        OverSeverity.WARNING -> NutrientColors.Warning
        OverSeverity.NORMAL -> null
    }
    val scheme = MaterialTheme.colorScheme

    // 底色深淺代表「吃了多少」，讓整個月一眼看得出鬆緊；
    // 超標另外用朱紅／赭色，因為那是不同性質的資訊，不是「更多一點」而已。
    //
    // 這一版把圓角收成 2dp、每格固定畫一圈細線 —— 整個月看起來像一張印好的
    // 表格而不是一堆圓角磁磚，跟今日頁那些規線是同一套語言。
    val fill = when {
        total == null -> Color.Transparent
        severityColor != null -> severityColor.copy(alpha = 0.16f)
        else -> {
            val ratio = if (settings.calorieTarget > 0) {
                (kcal / settings.calorieTarget).coerceIn(0.0, 1.0).toFloat()
            } else {
                0.5f
            }
            scheme.onSurface.copy(alpha = 0.05f + 0.16f * ratio)
        }
    }

    Box(
        modifier
            .aspectRatio(0.85f)
            .clip(CellShape)
            .background(fill)
            .border(
                if (isSelected) 1.5.dp else 1.dp,
                if (isSelected) scheme.onSurface else scheme.outlineVariant,
                CellShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp).numeric(),
                // 未來的日期壓淡：它們永遠是空的，不該看起來像「忘了記錄」
                color = when {
                    isFuture -> scheme.onSurfaceVariant.copy(alpha = 0.4f)
                    else -> scheme.onSurface
                },
            )
            if (total != null) {
                Text(
                    kcal.fmtInt(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp).numeric(),
                    color = severityColor ?: scheme.onSurfaceVariant,
                )
            }
        }
        // 今天用一條底線標記，跟今日頁那條週長條的選取記號是同一個做法 ——
        // 不用粗體，因為襯線數字加粗在這個字級上幾乎看不出差別。
        if (isToday) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(scheme.onSurface)
            )
        }
    }
}

/**
 * 這個月的概況。逐日看不出來的「平均吃多少、記了幾天」放在這裡。
 *
 * **`totals` 涵蓋前後各一個月**（月曆可以滑，鄰月在拖到一半時就得畫得出來，
 * 見 `NutriViewModel` 那條 query），所以這裡要自己濾掉不屬於這個月的日子 ——
 * 不濾的話「記了幾天」會把鄰月的也算進來。日期是 ISO 字串，比前綴就夠了。
 */
@Composable
private fun MonthSummary(month: YearMonth, totals: Map<String, DayTotal>, settings: NutriSettings) {
    val prefix = "%04d-%02d".format(month.year, month.monthValue)
    val mine = remember(prefix, totals) { totals.filterKeys { it.startsWith(prefix) } }
    if (mine.isEmpty()) {
        Text(
            stringResource(R.string.history_empty_month),
            Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val loggedDays = mine.size
    val average = mine.values.sumOf { it.kcal } / loggedDays
    val overDays = mine.values.count { settings.calorieTarget > 0 && it.kcal > settings.calorieTarget }
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Hairline()
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SummaryStat(stringResource(R.string.history_stat_days), loggedDays.toString(), null)
            SummaryStat(stringResource(R.string.history_stat_average), average.fmtInt(), null)
            if (overDays > 0) {
                SummaryStat(
                    stringResource(R.string.history_stat_over),
                    overDays.toString(),
                    NutrientColors.Over,
                )
            }
        }
    }
}

/** 月概況的一欄。跟今日頁的三大營養素同一個排法：小標在上、襯線數字在下。 */
@Composable
private fun SummaryStat(label: String, value: String, tint: Color?) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SectionLabel(label)
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp).numeric(),
            color = tint ?: scheme.onSurface,
        )
    }
}

/**
 * 月分頁的頁碼半徑。以「開啟這個畫面那一刻」為基準月，前後各留這麼多個月
 * （約一百年），換算成頁碼給 [HorizontalPager]。用得到的範圍遠小於這個數字，
 * 但頁碼只是個 Int，留寬一點不花任何成本 —— 和今日頁那兩個分頁器同一套。
 */
/** 標題跑馬燈裡兩個月份之間的間隔，見 [MonthTitle]。 */
private val MONTH_TITLE_GAP = 28.dp

private const val MONTH_RADIUS = 1_200
private const val MONTH_PAGE_COUNT = MONTH_RADIUS * 2 + 1

/** 幾乎方角。圓角磁磚會把整個月看成一堆按鈕，這裡要的是一張印好的表格。 */
private val CellShape = RoundedCornerShape(2.dp)

private val WEEKDAYS = listOf("日", "一", "二", "三", "四", "五", "六")

private fun monthsBetween(from: YearMonth, to: LocalDate): Long =
    (YearMonth.from(to).year - from.year) * 12L + (to.monthValue - from.monthValue)
