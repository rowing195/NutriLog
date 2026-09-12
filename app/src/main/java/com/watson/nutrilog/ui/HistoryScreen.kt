package com.watson.nutrilog.ui

import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
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
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

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
    /** 每一天的運動消耗，格子與月摘要的超標判斷要用加上它之後的目標。 */
    activeCaloriesMap: Map<LocalDate, Double>,
    onShiftMonth: (Long) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    /** 打開 AI 週報／月報，帶著正在看的那個月。 */
    onOpenReports: (YearMonth) -> Unit,
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

    // 進場時整張月曆由上往下逐週落下。**星期列不跟著動** —— 它每個月都一樣，
    // 而且左右滑換月時它本來就釘在分頁器外面不滑；進場讓它動等於同一個元件
    // 講兩套規則。不動的表頭配上落下的內容，也正好是這套版面的紙與墨分工。
    //
    // 一次性的：`shown` 只翻一次。之後滑到別的月份是**新組出來的頁**，那時
    // transition 早就落定在 true，新的那幾列直接是最終狀態 —— 換月不會重播，
    // 不然水平的換頁動畫和垂直的落下會在同一段時間裡打架。
    // 等那張紙蓋滿了才開始落（`LocalScreenEntered`，見 App.kt）——
    // 紙還在升、格子已經在落，兩件事疊在一起讀起來是一團亂。
    val enter = updateTransition(LocalScreenEntered.current, label = "historyEnter")

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
                // **HorizontalPager 預設是 CenterVertically。** 不指定 Top 的話，
                // 月曆會被垂直置中、和上面的星期列之間裂出一大條空白，而空白還會
                // 隨著月份有幾週而變 —— 星期列和第一排格子必須是連著的。
                verticalAlignment = Alignment.Top,
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
                        activeCaloriesMap = activeCaloriesMap,
                        onOpenDay = onOpenDay,
                        enter = enter,
                    )
                    // 概況接在最後一週後面，當作落下的最後一階 —— 不接的話它會在
                    // 上面幾列還在落的時候就先站好，變成整段動畫少了收尾。
                    val summarySlide by enter.animateFloat(
                        transitionSpec = {
                            tween(
                                durationMillis = GRID_DROP_MS,
                                delayMillis = weekRowCount(pageMonth) * GRID_STEP_MS,
                                easing = LinearOutSlowInEasing,
                            )
                        },
                        label = "summary",
                    ) { if (it) 1f else 0f }
                    Box(
                        Modifier.graphicsLayer {
                            alpha = summarySlide
                            translationY = (1f - summarySlide) * GRID_DROP_DP.toPx()
                        }
                    ) {
                        Column {
                            MonthSummary(pageMonth, totals, settings, activeCaloriesMap)
                            // 接在月摘要後面、跟著同一段落下動畫：報表講的就是「這一段期間」，
                            // 和摘要是同一件事的延伸。長相跟設定選單的列一樣，安靜、不搶月曆的戲。
                            Hairline(Modifier.padding(top = 14.dp))
                            MenuRow(
                                title = stringResource(R.string.report_entry),
                                summary = "",
                                onClick = { onOpenReports(pageMonth) },
                            )
                            Hairline()
                        }
                    }
                }
            }
            // **「回到本月」放在畫面最底下，不在報頭裡。** 兩個理由：
            //
            // 一、它是一個動作，不是這個月的內容 —— 擺在報頭就得決定「要不要跟著
            // 月份一起滑」，而兩種都不對（跟著滑等於它屬於某個月；不跟著滑則報頭
            // 高度會隨月份忽高忽低，底下整張月曆跟著上下跳）。放到分頁器外面就
            // 沒有這個問題：它出現時吃掉的是月曆底下那塊空白，格子一格都不會動。
            //
            // 二、月曆底下本來就空一大片，而這是這個畫面唯一的動作。
            //
            // 空心章：形狀講「這是一個動作」，空心講「它是次要的、不是每次進來
            // 都要按的那種」——和設定頁的「立即備份」同一個處理。
            //
            // 進出要有動畫：它是**跟著換月出現的**，直接彈出來會像畫面閃了一下。
            // 從底邊往上展開（`expandFrom = Bottom`）配上它釘在畫面下緣的位置，
            // 讀起來就是「從下緣升上來」——和常吃頁那組說明文字同一套動作語彙。
            //
            // **它不是被觸發的動畫，是跟著手指走的。** 和報頭那個月份同一套：
            // 讀分頁器的即時位移，直接拿來當這顆章的長度與濃淡。
            //
            // 綁的是**前後兩個月的中心點**：本月正中間是 0（完全收起來），鄰月正
            // 中間是 1（完全長出來），中間照比例。再往前往後就固定在 1，不會因為
            // 滑得更遠而繼續變 —— 「離本月很遠」和「離本月更遠」對這顆章來說是
            // 同一件事。
            //
            // 這樣就沒有「響應」這個問題了：它不需要等任何東西被觸發，拖多少動
            // 多少；放開手指之後跟著分頁器自己的吸附動畫走完，兩側箭頭與「回到
            // 本月」那種跳頁也一樣，全部共用分頁器的那一條曲線。
            BackToThisMonthStamp(
                pagerState = pagerState,
                todayPage = monthPageOf(YearMonth.from(today)),
                onClick = { onShiftMonth(monthsBetween(month, today)) },
            )
        }
    }
}

@Composable
private fun MonthHeader(
    pagerState: PagerState,
    monthOfPage: (Int) -> YearMonth,
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

        // 報頭只剩月份本身，所以它的高度是固定的 —— 拖曳過程中報頭不會長高縮矮，
        // 底下的月曆也就不會跟著上下跳。「回到本月」搬到畫面最底下了。
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            MonthTitle(pagerState, monthOfPage)
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
    activeCaloriesMap: Map<LocalDate, Double>,
    onOpenDay: (LocalDate) -> Unit,
    enter: Transition<Boolean>,
) {
    val firstDay = month.atDay(1)
    // ISO 的星期一是 1、星期日是 7。要排成「日一二三四五六」，
    // 星期日的前置空格數就是 0，所以取 value % 7。
    val leadingBlanks = firstDay.dayOfWeek.value % 7
    val daysInMonth = month.lengthOfMonth()
    val rows = weekRowCount(month)

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(rows) { row ->
            // 一週一階。列數本來就封頂在 6（一個月最多跨六週），所以這裡不需要
            // 設定選單那種「錯開總長度有上限」的算法 —— 最壞情況也只錯開 240ms。
            val slide by enter.animateFloat(
                transitionSpec = {
                    tween(
                        durationMillis = GRID_DROP_MS,
                        delayMillis = row * GRID_STEP_MS,
                        easing = LinearOutSlowInEasing,
                    )
                },
                label = "week",
            ) { if (it) 1f else 0f }

            Row(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = slide
                        translationY = (1f - slide) * GRID_DROP_DP.toPx()
                    },
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
                            activeCalories = activeCaloriesMap[date] ?: 0.0,
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
    activeCalories: Double,
    isToday: Boolean,
    isSelected: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kcal = total?.kcal ?: 0.0
    // 和今日頁同一個判斷：那天有運動，額度就跟著變多
    val goal = effectiveCalorieTarget(
        settings.calorieTarget,
        activeCalories,
        settings.readExerciseCalories,
        settings.exerciseEatBackPercent,
    )
    val severity = overSeverity(kcal, goal)
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
            val ratio = if (goal > 0) {
                (kcal / goal).coerceIn(0.0, 1.0).toFloat()
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
private fun MonthSummary(
    month: YearMonth,
    totals: Map<String, DayTotal>,
    settings: NutriSettings,
    activeCaloriesMap: Map<LocalDate, Double>,
) {
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
    val overDays = mine.values.count { day ->
        val active = runCatching { LocalDate.parse(day.date) }.getOrNull()?.let { activeCaloriesMap[it] } ?: 0.0
        val goal = effectiveCalorieTarget(
            settings.calorieTarget,
            active,
            settings.readExerciseCalories,
            settings.exerciseEatBackPercent,
        )
        goal > 0 && day.kcal > goal
    }
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
/**
 * 進場時一週落下多遠、多久、每一週之間錯開多少。
 *
 * 星期列不在這套動畫裡（見 [HistoryScreen] 裡 `enter` 那段），概況接在最後一週
 * 後面當收尾，所以整段的長度是「週數 × [GRID_STEP_MS] ＋ [GRID_DROP_MS]」，
 * 最壞情況（六週）約 640ms。
 *
 * **放慢的時候距離要跟著加。** 260ms／14dp 改成 340ms／18dp：只延長時間不加距離，
 * 同一段路走更久，讀起來是「卡卡的」而不是「慢慢落下」——速度感是距離除以時間，
 * 要的是把兩個一起往上調。
 *
 * 曲線用 [LinearOutSlowInEasing]（起步就有速度、末段收慢）而不是兩頭都慢的
 * `FastOutSlowInEasing`：這是「落下」，落體不會先慢慢加速；而且起步慢的曲線
 * 配上放慢之後的時長，第一時間會讀成沒反應。
 */
private const val GRID_DROP_MS = 340
private const val GRID_STEP_MS = 50
private val GRID_DROP_DP = 18.dp

/** 這個月要排幾週。[MonthGrid] 與概況的落下順序共用同一個算法。 */
private fun weekRowCount(month: YearMonth): Int =
    (month.atDay(1).dayOfWeek.value % 7 + month.lengthOfMonth() + 6) / 7

/**
 * 「回到本月」那顆空心章。**沒有進出場動畫，因為它是被拖出來的。**
 *
 * 高度與濃淡都直接讀分頁器的即時位移（見 [monthsFromToday]），所以拖多少動多少、
 * 放開手指之後跟著吸附動畫走完，兩側箭頭與跳頁也共用同一條曲線。用 tween 補一段
 * 進出場反而會有兩個問題：拖的時候它不動（要等 settle），以及它自己那條曲線和
 * 分頁器的曲線各走各的。
 *
 * **兩個值都在 `layout` / `graphicsLayer` 的 lambda 裡讀**，不是在組合階段讀：
 * `currentPageOffsetFraction` 在拖曳的每一幀都會變，寫在外面等於整個月曆每幀重組
 * 一次。寫在這兩個 lambda 裡只會重新量測與重畫這一顆章。
 *
 * 高度用 `layout` 而不是 `height()`：章有多高是它自己量出來的（54dp 的印章加上下
 * 內距），寫死一個數字之後改了 [StampButton] 這裡就會悄悄對不上。底邊錨定
 * （`place` 到負的 y）讓它從畫面下緣長出來，外面那層 `clipToBounds` 負責切掉
 * 還沒長出來的部分。
 */
@Composable
private fun BackToThisMonthStamp(
    pagerState: PagerState,
    todayPage: Int,
    onClick: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().clipToBounds()) {
        StampButton(
            label = stringResource(R.string.back_to_this_month),
            onClick = onClick,
            color = Color.Transparent,
            modifier = Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val shown = pagerState.monthsFromToday(todayPage)
                    val height = (placeable.height * shown).roundToInt()
                    layout(placeable.width, height) {
                        placeable.place(0, height - placeable.height)
                    }
                }
                .graphicsLayer { alpha = pagerState.monthsFromToday(todayPage) }
                .padding(horizontal = 22.dp, vertical = 8.dp),
        )
    }
}

/**
 * 現在離本月多遠，夾在 0..1。
 *
 * 0 ＝ 本月正中間、1 ＝ 鄰月正中間**或更遠**。夾住上限是刻意的：滑到三個月前和
 * 滑到一年前，對這顆章來說是同一件事。
 */
private fun PagerState.monthsFromToday(todayPage: Int): Float =
    ((currentPage + currentPageOffsetFraction) - todayPage).absoluteValue.coerceIn(0f, 1f)

/** 標題跑馬燈裡兩個月份之間的間隔，見 [MonthTitle]。 */
private val MONTH_TITLE_GAP = 28.dp

private const val MONTH_RADIUS = 1_200
private const val MONTH_PAGE_COUNT = MONTH_RADIUS * 2 + 1

/** 幾乎方角。圓角磁磚會把整個月看成一堆按鈕，這裡要的是一張印好的表格。 */
private val CellShape = RoundedCornerShape(2.dp)

private val WEEKDAYS = listOf("日", "一", "二", "三", "四", "五", "六")

private fun monthsBetween(from: YearMonth, to: LocalDate): Long =
    (YearMonth.from(to).year - from.year) * 12L + (to.monthValue - from.monthValue)
