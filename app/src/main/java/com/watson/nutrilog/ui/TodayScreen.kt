package com.watson.nutrilog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.NutriSettings
import com.watson.nutrilog.data.db.DayTotal
import com.watson.nutrilog.data.db.FoodEntry
import com.watson.nutrilog.data.db.Meal
import com.watson.nutrilog.data.db.Totals
import com.watson.nutrilog.data.db.totals
import com.watson.nutrilog.ui.theme.numeric
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
    /** 告訴 ViewModel「接下來這一筆要記進哪一餐」。null＝沒指定，照時間猜。 */
    onTargetMeal: (Meal?) -> Unit,
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
    // 只拿來在選單抬頭顯示「記一筆 · 晚餐」。真正決定記到哪一餐的是 ViewModel 那份
    // pendingMeal —— 這裡再存一份是為了讓使用者看得到「我點的那一餐有被記住」，
    // 不然從某一餐的「還沒記」點進來，跳出的選單跟從角落點進來一模一樣。
    var addMenuMeal by remember { mutableStateOf<Meal?>(null) }

    fun openAddMenu(meal: Meal?) {
        addMenuMeal = meal
        onTargetMeal(meal)
        showAddSheet = true
    }

    // 選單展開時把後面整片模糊掉。Modifier.blur 只在 API 31+ 有效，minSdk 是 26 ——
    // 舊機器上這行等於沒作用，所以遮罩那層一定要留著，那才是共通的退路。
    val backdropBlur by animateDpAsState(
        targetValue = if (showAddSheet) 16.dp else 0.dp,
        animationSpec = tween(220),
        label = "backdropBlur",
    )

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
    // 這裡曾經試過改用 currentPage（過半頁就動的即時值）取代 settledPage，
    // 想讓上方週長條更早跟著動、看起來更即時。單純一頁一頁滑沒事，
    // 但「回到今天」或從月曆點一個很遠的日子這種長距離跳頁會真的壞掉：
    // 上面 animateScrollToPage 對遠距離目標會先跳近、再補一小段動畫，
    // 過程中 currentPage 會經過好幾個中繼頁；這條 collector 只要看到
    // currentPage 變就馬上 onPickDay 認定那是新的日期，於是在動畫真正
    // 到達終點之前就把中繼頁的日期「鎖」成正式的 selectedDate ——
    // 實測過：從很遠的一天按「回到今天」，會停在今天的**前一天**，
    // 「回到今天」的字樣還留在畫面上，因為當下的日期其實還沒真的到今天。
    // settledPage 保證整個動畫完全停下來才觸發，不會有中繼頁被誤認成
    // 終點的問題。之後如果要做即時跟隨，得改成只用來畫視覺效果
    // （例如選取框的位置），不能拿去當作「這就是新日期」去 commit。
    // 拖曳跨週時，WeekPageContent 的借位 overlay 已經把新的一週滑到畫面上了
    // （純視覺，見那個函式的文件）；如果放開手指後 weekStart 真的改變，又讓
    // weekPagerState 自己 animateScrollToPage 一次，等於把使用者剛看過的
    // 位移動畫再演一次，肉眼看起來像「換了兩次週」（實測過）。這個旗標記
    // 「這次的 weekStart 變化是不是日分頁器拖曳造成的」，是的話畫面早就在
    // 對的位置，週分頁器只要悄悄 scrollToPage 把真正的狀態接上，不必再動畫；
    // 其他來源（週長條箭頭、回到今天、月曆跳頁…）事前沒有 overlay 動畫可看，
    // 要保留 animateScrollToPage 才看得出「跳到哪裡去了」。
    // 用 remember 而不是普通 var：LaunchedEffect(dayPagerState) 只在第一次組成時
    // 啟動一次，它的協程閉包永遠指著那次組成當下的變數；LaunchedEffect(weekStart)
    // 每次 weekStart 變都會重啟，用的是重啟當下那次組成的閉包 —— 兩個閉包如果各自
    // 指著普通 var，其實是兩個互不相通的變數，寫的那個跟讀的那個對不上。
    // remember 回傳同一個物件的 identity 不會變，兩邊閉包才能真的共用同一格狀態。
    val weekChangeFromDaySwipe = remember { mutableStateOf(false) }
    LaunchedEffect(dayPagerState) {
        snapshotFlow { dayPagerState.settledPage }.collect { page ->
            val d = dayOfPage(page)
            if (d != currentDate.value) {
                val newWeekStart = d.minusDays((d.dayOfWeek.value % 7).toLong())
                weekChangeFromDaySwipe.value = newWeekStart != currentWeekStart.value
                onPickDay(d)
            }
        }
    }
    LaunchedEffect(weekStart) {
        val target = weekPageOf(weekStart)
        if (weekPagerState.currentPage != target) {
            if (weekChangeFromDaySwipe.value) {
                weekPagerState.scrollToPage(target)
            } else {
                weekPagerState.animateScrollToPage(target)
            }
        }
        weekChangeFromDaySwipe.value = false
    }
    LaunchedEffect(weekPagerState) {
        snapshotFlow { weekPagerState.settledPage }.collect { page ->
            val ws = weekOfPage(page)
            val deltaWeeks = ChronoUnit.WEEKS.between(currentWeekStart.value, ws)
            if (deltaWeeks != 0L) onShiftWeek(deltaWeeks)
        }
    }

    // 整頁包一層 Box：角落那顆章與它展開的選單要蓋在 Scaffold 之上，
    // 又要能自己吃掉導覽列的 inset，所以不走 Scaffold 的 floatingActionButton 槽。
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        // 只在真的要模糊時才掛上去：blur 會多開一層離屏合成，
        // 沒展開選單的時候不需要一直付這個成本。
        modifier = if (backdropBlur > 0.dp) Modifier.blur(backdropBlur) else Modifier,
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
                    dayPagerState = dayPagerState,
                    weekOfPage = ::weekOfPage,
                    dayPageOf = ::dayPageOf,
                    weekTotalsFlow = weekTotalsFlow,
                    today = today,
                    target = settings.calorieTarget,
                    onPickDay = onPickDay,
                    onShiftWeek = onShiftWeek,
                )
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
                // 「還沒記」開的是同一個新增選單，不是直接跳空白表單 ——
                // 那個 ＋ 跟角落那顆章長得像，就該做一樣的事，只是多帶了一個餐別。
                onAddForMeal = { meal -> openAddMenu(meal) },
            )
        }
    }

        AddMenu(
            expanded = showAddSheet,
            mealLabel = addMenuMeal?.label(),
            // 從角落那顆章開的沒有指定餐別，要把上一次記住的那一餐清掉
            onToggle = { if (showAddSheet) showAddSheet = false else openAddMenu(null) },
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
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 22.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 報頭。App 名稱擺在這裡而不是只有日期 —— 這一頁是整個 app 的封面。
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, letterSpacing = 1.sp),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            // 只有離開今天才出現：在今天的時候它是一顆永遠沒作用的按鈕
            if (!isToday) {
                Text(
                    stringResource(R.string.back_to_today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable(onClick = onBackToToday)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            } else {
                // 純數字加斜線，套襯線不會碰到中文
                Text(
                    date.year.toString() + " / " + "%02d".format(date.monthValue),
                    style = MaterialTheme.typography.bodyMedium.numeric(),
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            HeaderIcon(onClick = onOpenSearch) { SearchMark(scheme.onSurface) }
            HeaderIcon(onClick = onOpenHistory) { CalendarMark(scheme.onSurfaceVariant) }
            HeaderIcon(onClick = onOpenSettings) {
                SlidersMark(scheme.onSurfaceVariant, background = scheme.background)
            }
        }
        Rule(Modifier.padding(top = 8.dp))
    }
}

/** 報頭上的圖示不加框（一排框會把報頭壓死），但可點區要墊到 44dp。 */
@Composable
private fun HeaderIcon(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
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
    dayPagerState: PagerState,
    weekOfPage: (Int) -> LocalDate,
    dayPageOf: (LocalDate) -> Int,
    weekTotalsFlow: (LocalDate) -> Flow<List<DayTotal>>,
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
        Box(
            Modifier.size(28.dp).clickable { onShiftWeek(-1) },
            contentAlignment = Alignment.Center,
        ) { ChevronMark(scheme.outline, pointsLeft = true) }
        HorizontalPager(
            state = pagerState,
            // 跟日分頁同一個理由：一次滑動最多只換一週，不管滑多快。
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                pagerSnapDistance = PagerSnapDistance.atMost(1),
            ),
            modifier = Modifier.weight(1f),
        ) { page ->
            WeekPageContent(
                weekStart = weekOfPage(page),
                weekTotalsFlow = weekTotalsFlow,
                today = today,
                target = target,
                dayPagerState = dayPagerState,
                dayPageOf = dayPageOf,
                onPickDay = onPickDay,
            )
        }
        Box(
            Modifier.size(28.dp).clickable { onShiftWeek(1) },
            contentAlignment = Alignment.Center,
        ) { ChevronMark(scheme.outline, pointsLeft = false) }
    }
}

/**
 * 週分頁裡的一頁。平常就是這一週的 [WeekRow]；日分頁拖過週界時，額外從旁邊
 * 滑進鄰週的 [WeekRow]，整個效果只靠 [graphicsLayer] 位移，完全不去動
 * [weekPagerState][WeekStrip] 自己的捲動狀態 —— 這一頁本身在週分頁器眼中
 * 從頭到尾都停在原地沒有捲動，鄰週只是「借位」畫在旁邊而已。
 *
 * 這樣做才安全：如果改成真的去捲動週分頁器本身來做這個轉場，就會撞上
 * [WeekRow] 文件裡記的那個問題 —— 一個分頁器的畫面位置被日分頁器的即時拖曳
 * 值驅動，等於又造出一條「靠即時值決定分頁器狀態」的路，那正是前面兩個
 * bug 共同的根源。純粹疊一層借位的畫面、不碰任何分頁器的滾動狀態，
 * 才不會重蹈覆轍。
 *
 * 借位的距離頂多一整頁寬（`overflowBefore`／`overflowAfter` 都夾在 0..1），
 * 單一次連續拖曳正常不會超過這個範圍。
 */
@Composable
private fun WeekPageContent(
    weekStart: LocalDate,
    weekTotalsFlow: (LocalDate) -> Flow<List<DayTotal>>,
    today: LocalDate,
    target: Int,
    dayPagerState: PagerState,
    dayPageOf: (LocalDate) -> Int,
    onPickDay: (LocalDate) -> Unit,
) {
    val weekStartPage = dayPageOf(weekStart)
    val pillPosition = (dayPagerState.currentPage + dayPagerState.currentPageOffsetFraction) - weekStartPage
    val overflowAfter = (pillPosition - 6f).coerceIn(0f, 1f)
    val overflowBefore = (-pillPosition).coerceIn(0f, 1f)

    BoxWithConstraints(Modifier.fillMaxWidth().clipToBounds()) {
        val rowWidthPx = with(LocalDensity.current) { maxWidth.toPx() }

        Box(Modifier.graphicsLayer { translationX = (overflowBefore - overflowAfter) * rowWidthPx }) {
            WeekRow(weekStart, weekTotalsFlow, today, target, dayPagerState, dayPageOf, onPickDay)
        }
        if (overflowAfter > 0f) {
            Box(Modifier.graphicsLayer { translationX = (1f - overflowAfter) * rowWidthPx }) {
                WeekRow(weekStart.plusWeeks(1), weekTotalsFlow, today, target, dayPagerState, dayPageOf, onPickDay)
            }
        }
        if (overflowBefore > 0f) {
            Box(Modifier.graphicsLayer { translationX = (overflowBefore - 1f) * rowWidthPx }) {
                WeekRow(weekStart.minusWeeks(1), weekTotalsFlow, today, target, dayPagerState, dayPageOf, onPickDay)
            }
        }
    }
}

/**
 * 一週長條裡的七個 [DayColumn]，資料是這一週自己的 Flow，跟目前選到哪一週無關。
 * [WeekPageContent] 借位畫鄰週時也是呼叫這個函式。
 *
 * 選取底色與粗體的連續位置**只從 [dayPagerState] 自己的一組數字算**（`currentPage`
 * 加 `currentPageOffsetFraction`），不要混用 settle 後才變的已選日期。這兩個來源
 * 更新的時間點不一樣：`currentPage` 拖過半頁那一刻就會先跳，settle 後的日期要等
 * 整個手勢結束才變 —— 拖過半頁但手指還沒放開的那個瞬間，`currentPageOffsetFraction`
 * 已經是相對於 *新* 的 `currentPage` 在算，如果拿它去配舊的已選日期算出來的鄰居，
 * 方向會對不上，於是出現「明明往前一天拖，結果亮了後一天」這種瞬間錯亂（實測過）；
 * 或是底色跟上了但文字粗體卻慢半拍才變（也實測過）。`currentPage` 和
 * `currentPageOffsetFraction` 是同一個 pager 在同一瞬間讀出來的一組數字，
 * 兩者相加永遠是連續、方向正確的值，[DayColumn] 內部的粗體/文字色判斷也是從這組
 * 數字（`pillAlpha`）算，不會有這問題。
 *
 * 這裡只**讀** dayPagerState 拿來畫畫面，不會拿它去 commit 新日期或驅動別的
 * 分頁器滾動 —— 那條路線之前踩過真的會壞的 bug（見上面 TodayScreen 裡的長註解），
 * 純讀取當渲染參數才是安全的用法。
 */
@Composable
private fun WeekRow(
    weekStart: LocalDate,
    weekTotalsFlow: (LocalDate) -> Flow<List<DayTotal>>,
    today: LocalDate,
    target: Int,
    dayPagerState: PagerState,
    dayPageOf: (LocalDate) -> Int,
    onPickDay: (LocalDate) -> Unit,
) {
    val totals by remember(weekStart) { weekTotalsFlow(weekStart) }.collectAsState(initial = emptyList())
    val byDate = remember(totals) { totals.associateBy { it.date } }
    val over = NutrientColors.Over

    val weekStartPage = dayPageOf(weekStart)
    val pillPosition = (dayPagerState.currentPage + dayPagerState.currentPageOffsetFraction) - weekStartPage

    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(7) { index ->
            val day = weekStart.plusDays(index.toLong())
            DayColumn(
                day = day,
                kcal = byDate[day.toString()]?.kcal ?: 0.0,
                target = target,
                isFuture = day.isAfter(today),
                overColor = over,
                pillAlpha = (1f - abs(pillPosition - index)).coerceIn(0f, 1f),
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
    isFuture: Boolean,
    overColor: Color,
    pillAlpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val isOver = target > 0 && kcal > target
    val severityColor = when (overSeverity(kcal, target)) {
        OverSeverity.OVER -> NutrientColors.Over
        OverSeverity.WARNING -> NutrientColors.Warning
        OverSeverity.NORMAL -> null
    }
    val fraction = if (target > 0) (kcal / target).coerceIn(0.0, 1.0).toFloat() else 0f
    val weekday = day.dayOfWeek.value % 7
    // 粗體、文字色、指示條都跟著 pillAlpha 這個連續值切換（過半才算選到），
    // 不要用外面 settle 才會變的已選日期 —— 不然拖動時底色已經跟到新的一天，
    // 文字卻要等放開手指才變粗，兩者步調對不上，使用者會覺得「反應慢半拍」。
    val isSelected = pillAlpha > 0.5f

    Column(
        modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            WEEKDAYS[weekday],
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isSelected -> scheme.onSurface
                // 週末用淡一點的朱紅，跟平日區隔但不搶戲
                weekday == 0 || weekday == 6 -> overColor.copy(alpha = if (isFuture) 0.3f else 0.7f)
                else -> scheme.outline
            },
        )
        // 柱子佔滿整格寬。原本是 5dp 的小藥丸，那個寬度看不出高度差，
        // 「這幾天吃得鬆或緊」是這條長條唯一存在的理由，柱子就該粗到看得出來。
        Box(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .background(if (isFuture) scheme.surfaceVariant else scheme.surfaceContainerHigh),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (kcal > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(if (isOver) 1f else fraction)
                        .background(
                            when {
                                severityColor != null -> severityColor
                                isSelected -> scheme.onSurface
                                else -> scheme.outline
                            }
                        )
                )
            }
        }
        Text(
            day.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp).numeric(),
            // 未來的日期壓淡：它們永遠是空的，不該看起來像「忘了記錄」
            color = when {
                isSelected -> scheme.onSurface
                isFuture -> scheme.outline.copy(alpha = 0.45f)
                else -> scheme.onSurfaceVariant
            },
        )
        // 選取記號用 pillAlpha 這個連續值畫，拖曳時才會跟著手指平滑移動，
        // 不是放開手指才「啪」地跳過去。
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(scheme.onSurface.copy(alpha = pillAlpha))
        )
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

    // animateItem() 預設是 Spring.StiffnessMediumLow，時間很短、肉眼幾乎看不出
    // 有淡入淡出，拉成 400ms 的 tween 才看得明顯。
    val itemFade = tween<Float>(400)
    val itemPlacement = tween<IntOffset>(400)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 22.dp),
    ) {
        item { Hairline() }
        item { Budget(entries, totals, settings) }
        item { Hairline() }
        item { Macros(totals, settings) }
        item { Hairline() }

        // 餐別之間不再另外畫線：每一列自己帶上緣細線之後，餐別標題上方那段空白
        // 本身就是分隔，再加一條會變成「標題被夾在兩條線中間」。
        Meal.entries.forEach { meal ->
            val ofMeal = entries.filter { it.mealType == meal }
            item(key = "header-" + meal.name) { MealHeader(meal, ofMeal) }
            if (ofMeal.isEmpty()) {
                // key 跟有紀錄時的 items 不同，所以「刪到剩空」那一刻在 LazyColumn
                // 眼中是舊 key 消失、新 key 出現，animateItem() 兩邊都套了才會
                // 接成一個連續的淡入淡出，不是硬切。
                item(key = "empty-" + meal.name) {
                    Box(Modifier.animateItem(fadeInSpec = itemFade, placementSpec = itemPlacement, fadeOutSpec = itemFade)) {
                        EmptyMealRow(onClick = { onAddForMeal(meal) })
                    }
                }
            } else {
                items(ofMeal, key = { it.id }) { entry ->
                    Box(Modifier.animateItem(fadeInSpec = itemFade, placementSpec = itemPlacement, fadeOutSpec = itemFade)) {
                        EntryRow(entry, onClick = { onOpenEntry(entry) })
                    }
                }
            }
        }
        // 角落那顆章是浮在內容之上的，不佔 Scaffold 的 innerPadding，
        // 所以最後一筆要自己留出它的高度，不然會被蓋住。
        item { Spacer(Modifier.height(92.dp)) }
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
    // 超標 10% 以內是橘色警示、超過 10% 才轉紅；severityColor 是 null 代表沒超標，
    // 沿用原本的中性色。
    val severityColor = when (overSeverity(consumed, target)) {
        OverSeverity.OVER -> NutrientColors.Over
        OverSeverity.WARNING -> NutrientColors.Warning
        OverSeverity.NORMAL -> null
    }

    Column(
        Modifier.padding(top = 18.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            // 大數字不掛「已經吃」的標籤：旁邊就是目標與剩餘，讀起來已經夠清楚，
            // 而標籤會把版面上最大的那個字往右擠掉一截。
            Row {
                Text(
                    consumed.fmtInt(),
                    style = MaterialTheme.typography.displayLarge.numeric(),
                    color = severityColor ?: scheme.onSurface,
                    modifier = Modifier.alignByBaseline(),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    stringResource(R.string.unit_kcal),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 17.sp,
                        fontStyle = FontStyle.Italic,
                    ).numeric(),
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Spacer(Modifier.weight(1f))
            // 目標為 0 等於關掉額度的意義，就不要講「還有 2000 的空間」
            if (target > 0) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.padding(bottom = 5.dp),
                ) {
                    LabelledNumber(
                        label = stringResource(R.string.budget_target),
                        number = target.toString(),
                        labelColor = scheme.onSurfaceVariant,
                        numberColor = scheme.onSurfaceVariant,
                        numberSize = 14.sp,
                    )
                    LabelledNumber(
                        label = stringResource(
                            if (over) R.string.budget_over else R.string.budget_left
                        ),
                        number = abs(remaining).fmtInt(),
                        labelColor = severityColor ?: scheme.onSurfaceVariant,
                        numberColor = severityColor ?: scheme.onSurface,
                        numberSize = 18.sp,
                    )
                }
            }
        }

        MealSegmentBar(entries = entries, target = target)

        // 額度條下面補一行小計。條子講的是形狀（哪一餐吃掉一大半），
        // 這一行才回答「所以早餐到底幾大卡」——兩個問題不必各佔一塊版面。
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Meal.entries.forEach { meal ->
                val kcal = entries.filter { it.mealType == meal }.sumOf { it.calories }
                LabelledNumber(
                    label = meal.shortLabel(),
                    number = if (kcal > 0) kcal.fmtInt() else "—",
                    labelColor = if (kcal > 0) scheme.onSurfaceVariant else scheme.outline,
                    numberColor = if (kcal > 0) scheme.onSurfaceVariant else scheme.outline,
                    numberSize = 13.sp,
                )
            }
        }
    }
}

/**
 * 「目標 2000」這種中文標籤配數字。
 *
 * 拆成兩個 Text 而不是一句話套 buildAnnotatedString：襯線字型沒有中文字符，
 * 整句一起套會 fallback 到系統襯線，那正是要避開的東西。分開排也比在字串裡
 * 用 indexOf 找數字位置可靠 —— 那招在數字剛好等於年份之類的巧合下會框錯段。
 */
@Composable
private fun LabelledNumber(
    label: String,
    number: String,
    labelColor: Color,
    numberColor: Color,
    numberSize: androidx.compose.ui.unit.TextUnit,
) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = labelColor,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            number,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = numberSize).numeric(),
            color = numberColor,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

@Composable
private fun MealSegmentBar(entries: List<FoodEntry>, target: Int) {
    val scheme = MaterialTheme.colorScheme
    val mealColors = NutrientColors.Meals
    val consumed = entries.sumOf { it.calories }
    val remainder = (target - consumed).coerceAtLeast(0.0)
    val severityColor = when (overSeverity(consumed, target)) {
        OverSeverity.OVER -> NutrientColors.Over
        OverSeverity.WARNING -> NutrientColors.Warning
        OverSeverity.NORMAL -> null
    }

    // 方角、8dp。圓角在這個尺寸只會把兩端的分段啃掉一截，看起來像沒對齊。
    Row(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
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
                    .background(severityColor ?: mealColors[index])
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
    val protein = NutrientColors.Protein
    val fat = NutrientColors.Fat
    val carbs = NutrientColors.Carbs

    Column(
        Modifier.padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 三欄各自一條「離目標多遠」的進度，取代原本那條「三者佔比」的組成條。
        //
        // 組成條回答的是「今天吃的結構長怎樣」，但那個問題上面那條依餐別分段的
        // 額度條已經回答過一次了（而且分得更細）。真正每天會問的是「蛋白質夠了沒」，
        // 那是達成率，組成條答不出來 —— 三者佔比一樣時，可能三個都不足也可能三個都爆。
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MacroColumn(stringResource(R.string.nutrient_protein), totals.proteinG, settings.proteinTargetG, protein, Modifier.weight(1f))
            MacroColumn(stringResource(R.string.nutrient_fat), totals.fatG, settings.fatTargetG, fat, Modifier.weight(1f))
            MacroColumn(stringResource(R.string.nutrient_carbs), totals.carbsG, settings.carbsTargetG, carbs, Modifier.weight(1f))
        }

        if (settings.showExtendedNutrients) {
            ExtraLine(totals)
        }
    }
}

/**
 * 一個營養素一欄：標籤、數值／目標、一條達成率。
 *
 * 標籤是中文所以維持無襯線，數值與「/目標」是純數字才套襯線 —— 兩者分成
 * 不同的 Text，中文絕對不會吃到那套沒有中文字符的襯線字型。
 */
@Composable
private fun MacroColumn(
    label: String,
    value: Double,
    target: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = severityTint(value, target)
    val fraction = if (target > 0) (value / target).coerceIn(0.0, 1.0).toFloat() else 0f

    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        SectionLabel(label)
        Row {
            Text(
                value.fmtInt(),
                style = MaterialTheme.typography.headlineSmall.numeric(),
                color = tint ?: scheme.onSurface,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                "/$target",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp).numeric(),
                color = scheme.outline,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(scheme.surfaceContainerHigh)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(3.dp)
                    .background(tint ?: color)
            )
        }
    }
}

/**
 * 進階營養素。
 *
 * 原本是四個有底色的 chip，還為了窄螢幕加了橫向捲動 —— 而那排能捲的幅度只有
 * 幾十 dp，手指很容易滑過頭把位移傳給外層的日分頁器，變成不小心換了一天，
 * 所以又補了一個 nestedScroll 去吃掉溢出的捲動。整串工程是為了「四個色塊要排一列」
 * 而長出來的。這裡改成一行純文字：不捲動，就沒有那條路徑，那些程式碼一起消失。
 */
@Composable
private fun ExtraLine(totals: Totals) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ExtraItem(stringResource(R.string.nutrient_sugar), totals.sugarG.fmt(), "g")
        ExtraItem(stringResource(R.string.nutrient_sodium), totals.sodiumMg.fmtInt(), "mg")
        ExtraItem(stringResource(R.string.nutrient_fiber), totals.fiberG.fmt(), "g")
        ExtraItem(stringResource(R.string.nutrient_satfat), totals.satFatG.fmt(), "g")
    }
}

@Composable
private fun ExtraItem(label: String, value: String, unit: String) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
            color = scheme.outline,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            "$value $unit",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp).numeric(),
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
    }
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
        // 只放數字，不重複 kcal —— 上面那個大數字已經講過單位了
        Text(
            if (empty) "—" else ofMeal.totals().calories.fmtInt(),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp).numeric(),
            color = if (empty) scheme.outline else scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EntryRow(entry: FoodEntry, onClick: () -> Unit) {
    Column {
        // 每一列自己帶一條上緣細線，餐別標題和第一筆之間也就有了分隔。
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
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
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp).numeric(),
            )
        }
    }
}

@Composable
private fun EmptyMealRow(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 最淡的一圈：只是提示這裡可以點，不搶戲。
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(1.dp, scheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) { PlusMark(scheme.outline, size = 10.dp, stroke = 1.4.dp) }
            Text(
                stringResource(R.string.meal_empty),
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = scheme.outline,
            )
        }
    }
}

private class AddOption(
    val label: String,
    val icon: @Composable () -> Unit,
    val action: () -> Unit,
)

/**
 * 所有輸入方式的入口：角落一顆印章，點下去把五個入口攤開。
 *
 * 收成角落而不是滿版一條，是為了不吃掉捲動區的高度；但**不能退回舊版那顆
 * 只有一個 ＋ 的 FAB** —— 那顆 ＋ 同時是五件事，畫面上完全看不出來。
 * 所以角落這顆長得是印章的樣子（實心墨底＋內縮一圈細框），跟它展開後的面板
 * 是同一個東西的兩個狀態，而不是「一顆按鈕」加「一張不相干的 sheet」。
 *
 * 展開不是整塊淡入：五列**由下往上逐列滑進來**（每列差 45ms），角落那顆章同時
 * 轉 45 度把 ＋ 變成 ✕。這樣「這張面板是從那顆章長出來的」才看得出來，
 * 而不是憑空蓋上一層。
 *
 * 不用 ModalBottomSheet：它自帶圓角、拖曳把手與 M3 的容器色，
 * 在這套方角紙面上是唯一一個圓角的東西，看起來像貼錯的元件。
 */
@Composable
private fun AddMenu(
    expanded: Boolean,
    /** 從某一餐的「還沒記」開進來時，把那一餐寫在抬頭上讓使用者知道有被記住。 */
    mealLabel: String?,
    onToggle: () -> Unit,
    onPick: (() -> Unit) -> Unit,
    onAddManual: () -> Unit,
    onAddPhoto: () -> Unit,
    onAddFromGallery: () -> Unit,
    onAddText: () -> Unit,
    onAddBarcode: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    if (expanded) BackHandler { onToggle() }

    // 「常吃／文字輸入」擺第一個：多半是忘記拍照事後補登，
    // 這時通常會先想到「這不是常吃的那個嗎」，該畫面同時給了常吃清單。
    val options = listOf(
        AddOption(stringResource(R.string.add_text), { ListMark(scheme.onSurface) }, onAddText),
        AddOption(stringResource(R.string.add_manual), { GridMark(scheme.onSurface) }, onAddManual),
        AddOption(stringResource(R.string.add_photo), { CameraMark(scheme.onSurface) }, onAddPhoto),
        AddOption(stringResource(R.string.add_photo_gallery), { ImageMark(scheme.onSurface) }, onAddFromGallery),
        AddOption(stringResource(R.string.add_barcode), { BarcodeMark(scheme.onSurface) }, onAddBarcode),
    )

    val transition = updateTransition(expanded, label = "addMenu")
    val cover by transition.animateFloat(
        transitionSpec = { tween(if (targetState) 220 else 170) },
        label = "cover",
    ) { if (it) 1f else 0f }
    val plusRotation by transition.animateFloat(
        transitionSpec = { tween(300, easing = FastOutSlowInEasing) },
        label = "plus",
    ) { if (it) 45f else 0f }

    Box(Modifier.fillMaxSize()) {
        // 收完就整塊不畫，才不會擋住底下的點擊。判斷式要把 expanded 也算進去 ——
        // 只看動畫值的話，展開那一幀 cover 還是 0，內容永遠不會被組出來。
        if (expanded || cover > 0.004f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = cover }
                    .background(scheme.inverseSurface.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggle,
                    )
            )
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    // 讓出角落那顆章的位置，面板不要壓在它上面
                    .padding(bottom = 92.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = cover
                        translationY = (1f - cover) * 20.dp.toPx()
                    }
                    .background(scheme.background)
                    // 吸收落在面板空白處的點擊，不要穿透到下面的遮罩去關掉自己
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {}
            ) {
                Rule()
                SectionLabel(
                    stringResource(R.string.add_entry) +
                        (mealLabel?.let { "　·　" + it } ?: ""),
                    Modifier.padding(start = 22.dp, top = 14.dp, bottom = 10.dp),
                    color = if (mealLabel != null) scheme.onSurface else scheme.onSurfaceVariant,
                )
                options.forEachIndexed { index, option ->
                    val slide by transition.animateFloat(
                        transitionSpec = {
                            if (targetState) {
                                tween(260, delayMillis = 70 + index * 45, easing = FastOutSlowInEasing)
                            } else {
                                tween(120)
                            }
                        },
                        label = "row",
                    ) { if (it) 1f else 0f }

                    Hairline()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = slide
                                translationX = (1f - slide) * 64.dp.toPx()
                            }
                            .clickable { onPick(option.action) }
                            .padding(horizontal = 22.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // 序號是純排版的裝飾，不多講任何一句話 —— 這排本來就只有
                        // 五個選項，需要的是節奏感，不是再多五行說明文字。
                        Text(
                            "%02d".format(index + 1),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp).numeric(),
                            color = scheme.outline,
                        )
                        Text(
                            option.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .border(1.dp, scheme.outlineVariant, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { option.icon() }
                    }
                }
            }
        }

        // 角落的章。跟滿版那顆是同一個元件的收合狀態：實心墨底、內縮 4dp 一圈細框。
        //
        // 這顆按鈕整個是畫出來的、沒有任何文字節點，所以一定要自己掛
        // contentDescription —— 不然讀螢幕的人和 UI 測試都摸不到它。
        val addLabel = stringResource(R.string.add_entry)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp)
                .size(60.dp)
                .background(scheme.inverseSurface)
                .clickable(onClick = onToggle)
                .semantics { contentDescription = addLabel }
                .padding(4.dp),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .border(1.dp, scheme.onSurfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.graphicsLayer { rotationZ = plusRotation }) {
                    PlusMark(scheme.inverseOnSurface, size = 22.dp, stroke = 1.8.dp)
                }
            }
        }
    }
}

private val WEEKDAYS = listOf("日", "一", "二", "三", "四", "五", "六")
